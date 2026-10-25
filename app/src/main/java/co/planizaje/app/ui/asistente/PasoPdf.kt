package co.planizaje.app.ui.asistente

import co.planizaje.app.ui.comp.avisar
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import co.planizaje.app.BuildConfig
import co.planizaje.app.pdf.GeneradorPdf
import co.planizaje.app.ui.comp.Archivos
import co.planizaje.app.ui.comp.Aviso
import co.planizaje.app.ui.comp.Confirmar
import co.planizaje.app.ui.comp.LocalAlmacen
import co.planizaje.app.ui.comp.Seccion
import co.planizaje.app.ui.theme.Paleta
import co.planizaje.core.data.Serializacion
import co.planizaje.core.model.Proyecto
import co.planizaje.core.model.VersionPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PasoPdf(c: Ctx) {
    val almacen = LocalAlmacen.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var generando by remember { mutableStateOf(false) }
    var avisoBorrador by remember { mutableStateOf(false) }
    val borrador = c.ev.estadoDocumento.esBorrador

    fun generar() {
        generando = true
        scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) { GeneradorPdf(almacen, BuildConfig.VERSION_NAME).generar(c.p, c.grua, c.accesorios) }
            }
            generando = false
            r.onSuccess { s ->
                almacen.actualizarProyecto(c.p.id, "PDF generado: rev. ${s.version.revision} v${s.version.numero}${if (s.version.borrador) " (borrador)" else ""}") {
                    it.copy(versionesPdf = it.versionesPdf + s.version)
                }
                Archivos.abrir(ctx, s.archivo, "application/pdf")
            }.onFailure { avisar(ctx, "No se pudo generar el PDF: ${it.message}", true) }
        }
    }

    ResumenEstado(c.ev.estadoDocumento, c.ev.puntosPorResolver)
    if (borrador) Aviso("Puedes generar el documento ahora, pero saldrá marcado como BORRADOR en todas las páginas y con la lista de lo que falta. No es apto para ejecutar la maniobra.")

    Button(onClick = { if (borrador) avisoBorrador = true else generar() }, enabled = !generando, modifier = Modifier.fillMaxWidth().height(58.dp)) {
        if (generando) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Icon(Icons.Filled.PictureAsPdf, null)
        Text(if (generando) "  Generando…" else if (borrador) "  Generar borrador PDF" else "  Generar plan PDF")
    }
    Text("Incluye portada, carga, grúa, aparejos, croquis con leyenda y vistas por fase, procedimiento, lista de comprobación, memoria de cálculo, fuentes e historial.", style = MaterialTheme.typography.bodySmall)

    ListaVersiones(c.p, Serializacion.huella(c.p, c.grua, c.accesorios))

    if (avisoBorrador) Confirmar(
        "Generar borrador", "El plan tiene ${c.ev.puntosPorResolver} punto(s) sin resolver. El PDF llevará la marca «BORRADOR · NO APTO PARA EJECUCIÓN».",
        "Generar borrador", onConfirmar = { avisoBorrador = false; generar() }, onCancelar = { avisoBorrador = false },
    )
}

@Composable
fun ListaVersiones(p: Proyecto, huellaActual: String?) {
    val almacen = LocalAlmacen.current
    val ctx = LocalContext.current
    if (p.versionesPdf.isEmpty()) return
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CO")) }
    Seccion("Documentos generados", icono = Icons.Filled.PictureAsPdf) {
        p.versionesPdf.sortedByDescending { it.fecha }.forEach { v ->
            val f = File(almacen.dirPlanes, v.archivo)
            val desact = huellaActual != null && v.huellaDatos != huellaActual
            FilaVersion(v, fmt.format(Date(v.fecha)), desact, f.exists(),
                onAbrir = { Archivos.abrir(ctx, f, "application/pdf") },
                onCompartir = { Archivos.compartir(ctx, f, "application/pdf", "Compartir plan de izaje") })
        }
    }
}

@Composable
fun FilaVersion(v: VersionPdf, fecha: String, desactualizado: Boolean, existe: Boolean, onAbrir: () -> Unit, onCompartir: () -> Unit) {
    Card(onClick = onAbrir, enabled = existe, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Revisión ${v.revision} · versión ${v.numero}", style = MaterialTheme.typography.titleSmall)
                Text("$fecha · ${if (v.borrador) "BORRADOR" else v.nota}", style = MaterialTheme.typography.bodySmall, color = if (v.borrador) Paleta.Revisar else Paleta.Ok)
                if (desactualizado) Text("! Los datos cambiaron después de generarlo: genera una versión nueva.", style = MaterialTheme.typography.bodySmall, color = Paleta.Mal)
                if (!existe) Text("El archivo ya no existe en el teléfono.", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onCompartir, enabled = existe) { Icon(Icons.Filled.Share, "Compartir") }
        }
    }
}
