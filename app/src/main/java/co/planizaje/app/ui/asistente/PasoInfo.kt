package co.planizaje.app.ui.asistente

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.planizaje.app.ui.comp.Ayuda
import co.planizaje.app.ui.comp.CampoFecha
import co.planizaje.app.ui.comp.CampoTexto
import co.planizaje.app.ui.comp.Confirmar
import co.planizaje.app.ui.comp.GaleriaAdjuntos
import co.planizaje.app.ui.comp.Seccion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PasoInfo(c: Ctx) {
    val p = c.p
    var nuevaRev by remember { mutableStateOf(false) }

    Ayuda("Empieza por lo básico. Solo los campos con * son necesarios para el plan; el resto puedes completarlo después.")

    Seccion("Proyecto", icono = Icons.Filled.Business) {
        CampoTexto(p.nombre, { v -> c.actualizar("Datos del proyecto") { it.copy(nombre = v) } }, "Nombre del proyecto", obligatorio = true, clave = "nombre", marcador = "Ej. Montaje de tanque en cubierta")
        CampoTexto(p.descripcion, { v -> c.actualizar("Datos del proyecto") { it.copy(descripcion = v) } }, "Descripción del trabajo", obligatorio = true, multilinea = true, clave = "descripcion")
        CampoTexto(p.ubicacion, { v -> c.actualizar("Datos del proyecto") { it.copy(ubicacion = v) } }, "Ubicación de la obra", obligatorio = true, clave = "ubicacion", marcador = "Dirección, ciudad")
        CampoFecha(p.fecha, { v -> c.actualizar("Datos del proyecto") { it.copy(fecha = v) } }, "Fecha de la maniobra", obligatorio = true, clave = "fecha")
        CampoTexto(p.cliente, { v -> c.actualizar("Datos del proyecto") { it.copy(cliente = v) } }, "Cliente", clave = "cliente")
        CampoTexto(p.empresa, { v -> c.actualizar("Datos del proyecto") { it.copy(empresa = v) } }, "Empresa responsable de la maniobra", clave = "empresa")
    }

    Seccion("Responsables", icono = Icons.Filled.Badge) {
        CampoTexto(p.elaboradoPor, { v -> c.actualizar("Responsables") { it.copy(elaboradoPor = v) } }, "Elaborado por", obligatorio = true, clave = "elaboradoPor")
        CampoTexto(p.revisorPrevisto, { v -> c.actualizar("Responsables") { it.copy(revisorPrevisto = v) } }, "Responsable de revisión", ayuda = "La revisión se registra en el paso 6, no aquí.")
        CampoTexto(p.aprobadorPrevisto, { v -> c.actualizar("Responsables") { it.copy(aprobadorPrevisto = v) } }, "Responsable de aprobación")
        Ayuda("La app no genera firmas. El PDF deja espacios para firmar a mano.")
    }

    Seccion("Fotos y documentos de la obra", icono = Icons.Filled.PhotoLibrary, desplegable = true, inicialAbierta = p.adjuntos.isNotEmpty()) {
        GaleriaAdjuntos("Adjuntos", p.adjuntos, { l -> c.actualizar("Adjuntos del proyecto") { it.copy(adjuntos = l) } })
    }

    Seccion("Advertencias y observaciones", icono = Icons.Filled.WarningAmber, desplegable = true, inicialAbierta = false) {
        CampoTexto(p.advertencias, { v -> c.actualizar("Advertencias") { it.copy(advertencias = v) } }, "Advertencias", multilinea = true)
        CampoTexto(p.observaciones, { v -> c.actualizar("Observaciones") { it.copy(observaciones = v) } }, "Observaciones", multilinea = true)
    }

    Seccion("Revisión del documento e historial", icono = Icons.Filled.History, desplegable = true, inicialAbierta = false, subtitulo = "Revisión actual: ${p.revision}") {
        Text("Cuando el plan cambie después de entregarlo, crea una nueva revisión. El número aparece en el PDF.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { nuevaRev = true }) { Text("Crear revisión ${p.revision + 1}") }
        val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CO")) }
        p.historial.takeLast(15).reversed().forEach { h ->
            Row {
                Text(fmt.format(Date(h.fecha)), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(8.dp))
                Text("Rev. ${h.revision} · ${h.descripcion}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (nuevaRev) Confirmar(
        "Nueva revisión", "El plan pasará a la revisión ${p.revision + 1}. La revisión humana registrada se borrará porque los datos pueden haber cambiado.",
        "Crear", onConfirmar = {
            c.actualizar("Nueva revisión ${p.revision + 1}") { it.copy(revision = it.revision + 1, revisionHumana = co.planizaje.core.model.RevisionHumana()) }
            nuevaRev = false
        }, onCancelar = { nuevaRev = false },
    )
}
