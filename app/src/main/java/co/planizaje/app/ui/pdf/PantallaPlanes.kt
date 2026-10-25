package co.planizaje.app.ui.pdf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import co.planizaje.app.ui.Rutas
import co.planizaje.app.ui.asistente.FilaVersion
import co.planizaje.app.ui.comp.Archivos
import co.planizaje.app.ui.comp.BarraSuperior
import co.planizaje.app.ui.comp.LocalAlmacen
import co.planizaje.app.ui.comp.Vacio
import co.planizaje.core.data.Serializacion
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PantallaPlanes(nav: NavController, onAtras: () -> Unit) {
    val almacen = LocalAlmacen.current
    val ctx = LocalContext.current
    val proyectos by almacen.proyectos.collectAsState()
    val gruas by almacen.gruas.collectAsState()
    val accesorios by almacen.accesorios.collectAsState()
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CO")) }
    val conPdf = proyectos.filter { it.versionesPdf.isNotEmpty() }

    Scaffold(topBar = { BarraSuperior("Planes PDF", onAtras) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (conPdf.isEmpty()) item {
                Vacio(Icons.Filled.PictureAsPdf, "Aún no hay planes generados", "Los PDF se crean en el último paso del asistente.")
            }
            conPdf.forEach { p ->
                val huella = Serializacion.huella(p, gruas.firstOrNull { it.id == p.gruaId }, accesorios)
                item(key = "t-${p.id}") {
                    TextButton(onClick = { nav.navigate(Rutas.asistente(p.id, 6)) }) {
                        Text(p.titulo, style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(p.versionesPdf.sortedByDescending { it.fecha }, key = { it.id }) { v ->
                    val f = File(almacen.dirPlanes, v.archivo)
                    FilaVersion(v, fmt.format(Date(v.fecha)), v.huellaDatos != huella, f.exists(),
                        onAbrir = { Archivos.abrir(ctx, f, "application/pdf") },
                        onCompartir = { Archivos.compartir(ctx, f, "application/pdf", "Compartir plan de izaje") })
                }
            }
        }
    }
}
