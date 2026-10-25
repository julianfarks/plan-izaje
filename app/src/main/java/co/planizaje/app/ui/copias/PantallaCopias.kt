package co.planizaje.app.ui.copias

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import co.planizaje.app.BuildConfig
import co.planizaje.app.ui.comp.Archivos
import co.planizaje.app.ui.comp.Aviso
import co.planizaje.app.ui.comp.Ayuda
import co.planizaje.app.ui.comp.BarraSuperior
import co.planizaje.app.ui.comp.LocalAlmacen
import co.planizaje.app.ui.comp.Seccion
import co.planizaje.app.ui.theme.Paleta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun PantallaCopias(onAtras: () -> Unit) {
    val almacen = LocalAlmacen.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var trabajando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var pendiente by remember { mutableStateOf<Uri?>(null) }

    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            trabajando = true
            scope.launch {
                val r = runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { almacen.exportarCopia(it, BuildConfig.VERSION_NAME) } } }
                trabajando = false
                mensaje = r.fold({ "Copia guardada correctamente. Guárdala fuera del teléfono (Drive, correo, PC)." }, { "No se pudo guardar la copia: ${it.message}" })
            }
        }
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendiente = uri }

    fun restaurar(uri: Uri, reemplazar: Boolean) {
        pendiente = null
        trabajando = true
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { almacen.importarCopia(it, reemplazar, BuildConfig.VERSION_NAME) } } }
            trabajando = false
            mensaje = r.fold(
                { s -> "Restauración completa: ${s.proyectos} proyectos, ${s.gruas} grúas, ${s.accesorios} accesorios y ${s.adjuntos} archivos." + (if (s.omitidos > 0) " ${s.omitidos} elementos dañados se omitieron." else "") + " Antes de restaurar se guardó una copia automática (${s.copiaPrevia})." },
                { "No se pudo restaurar: ${it.message}. Tus datos actuales no se modificaron." },
            )
        }
    }

    Scaffold(topBar = { BarraSuperior("Copias de seguridad", onAtras) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Ayuda("Todos tus datos se guardan solo en este teléfono, sin internet. Exporta una copia de vez en cuando y guárdala en otro sitio: si pierdes o cambias el teléfono, la restauras aquí.")
            if (trabajando) CircularProgressIndicator()
            mensaje?.let { Aviso(it, Paleta.Ok, Paleta.OkFondo, "i") }
            Seccion("Exportar", icono = Icons.Filled.Download) {
                Text("Crea un archivo .zip con proyectos, grúas, accesorios, croquis y adjuntos.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { exportar.launch("PlanIzaje-copia-${LocalDate.now()}.zip") }, enabled = !trabajando, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Exportar copia de seguridad") }
            }
            Seccion("Restaurar", icono = Icons.Filled.Restore) {
                Text("Elige un archivo de copia de PlanIzaje. Antes de cambiar nada, la app guarda automáticamente una copia del estado actual.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { importar.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !trabajando, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Restaurar desde archivo") }
            }
            val auto = remember(mensaje) { almacen.copiasAutomaticas() }
            if (auto.isNotEmpty()) Seccion("Copias automáticas", desplegable = true, inicialAbierta = false) {
                auto.forEach { f ->
                    TextButton(onClick = { Archivos.compartir(ctx, f, "application/zip", "Guardar copia") }) { Text(f.name) }
                }
            }
            Text("Al instalar una nueva versión de la app encima de la anterior, los datos se conservan. Si desinstalas la app, se borran: exporta antes una copia.", style = MaterialTheme.typography.bodySmall)
        }
    }

    pendiente?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendiente = null },
            title = { Text("¿Cómo restaurar?") },
            text = { Text("Combinar: añade lo que no tienes y, si un elemento existe en ambos, conserva el más reciente.\n\nReemplazar: deja exactamente lo que hay en la copia.") },
            confirmButton = { TextButton(onClick = { restaurar(uri, false) }) { Text("Combinar") } },
            dismissButton = { TextButton(onClick = { restaurar(uri, true) }) { Text("Reemplazar todo") } },
        )
    }
}
