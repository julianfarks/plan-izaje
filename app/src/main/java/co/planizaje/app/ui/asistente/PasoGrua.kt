package co.planizaje.app.ui.asistente

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.planizaje.app.data.nuevoId
import co.planizaje.app.ui.Rutas
import co.planizaje.app.ui.comp.Aviso
import co.planizaje.app.ui.comp.Ayuda
import co.planizaje.app.ui.comp.ChipEstado
import co.planizaje.app.ui.comp.FilaDato
import co.planizaje.app.ui.comp.LocalAlmacen
import co.planizaje.app.ui.comp.Seccion
import co.planizaje.app.ui.comp.objetivo
import co.planizaje.app.ui.comp.Selector
import co.planizaje.app.ui.theme.Paleta
import co.planizaje.core.model.Grua
import co.planizaje.core.util.Unidades

@Composable
fun PasoGrua(c: Ctx) {
    val almacen = LocalAlmacen.current
    val g = c.grua

    Ayuda("Elige la grúa de tu biblioteca y la tabla de carga que corresponde a la configuración real de la maniobra (extensiones, estabilizadores y sector).")

    Seccion("Grúa del proyecto", icono = Icons.Filled.LocalShipping, modifier = Modifier.objetivo("grua")) {
        if (c.gruas.isEmpty()) {
            Text("Aún no tienes grúas registradas.", style = MaterialTheme.typography.bodyLarge)
        }
        c.gruas.forEach { gr ->
            val sel = gr.id == c.p.gruaId
            Card(
                onClick = {
                    c.actualizar("Grúa seleccionada: ${gr.nombreCorto}") {
                        it.copy(gruaId = gr.id, maniobra = it.maniobra.copy(
                            tablaId = gr.tablas.singleOrNull()?.id,
                            estabilizadoresId = gr.estabilizadores.singleOrNull()?.id,
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth().then(if (sel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(gr.nombreCorto, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                gr.numeroSerie.takeIf { it.isNotBlank() }?.let { "Serie $it" },
                                gr.placaVehiculo.takeIf { it.isNotBlank() }?.let { "Placa $it" },
                                "${gr.tablas.size} tabla(s)",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (sel) Icon(Icons.Filled.CheckCircle, "Seleccionada", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val nueva = Grua(id = nuevoId())
                almacen.guardarGrua(nueva)
                c.actualizar("Grúa seleccionada") { it.copy(gruaId = nueva.id) }
                c.nav.navigate(Rutas.grua(nueva.id))
            }) { Icon(Icons.Filled.Add, null); Text(" Registrar grúa") }
            if (g != null) OutlinedButton(onClick = { c.nav.navigate(Rutas.grua(g.id)) }) { Icon(Icons.Filled.Edit, null); Text(" Editar ficha") }
        }
    }

    if (g == null) return

    if (!g.identificada) Aviso("Completa fabricante, modelo y número de serie de la grúa. Así se asegura que la tabla de carga corresponde a tu equipo real.")

    Seccion("Configuración de la maniobra", icono = Icons.Filled.TableChart) {
        if (g.tablas.isEmpty()) {
            Aviso("Esta grúa no tiene tablas de carga. Sin tabla oficial la capacidad queda «no verificable». Cárgala desde el diagrama de carga de tu grúa.", Paleta.Mal, Paleta.MalFondo, "✕")
            Button(onClick = { c.nav.navigate(Rutas.importarTabla(g.id)) }, modifier = Modifier.fillMaxWidth()) { Text("Importar tabla (foto o PDF)") }
            OutlinedButton(onClick = { c.nav.navigate(Rutas.grua(g.id)) }) { Text("Escribirla a mano en la ficha") }
        } else {
            Selector(
                "Tabla de carga aplicable", g.tablas, g.tablas.firstOrNull { it.id == c.p.maniobra.tablaId },
                { t -> (if (t.identificacionPendiente) "⚠ " else "") + t.nombre.ifBlank { t.configuracionPluma.ifBlank { "Tabla sin nombre" } } + (if (t.identificacionPendiente) " (pendiente de identificación)" else "") },
                { t -> c.actualizar("Tabla de carga: ${t.nombre}") { it.copy(maniobra = it.maniobra.copy(tablaId = t.id, estabilizadoresId = t.estabilizadoresId ?: it.maniobra.estabilizadoresId)) } },
                clave = "tabla",
            )
        }
        if (g.estabilizadores.isNotEmpty()) {
            Selector(
                "Configuración de estabilizadores", g.estabilizadores, g.estabilizadores.firstOrNull { it.id == c.p.maniobra.estabilizadoresId },
                { it.nombre.ifBlank { "Sin nombre" } },
                { e -> c.actualizar("Estabilizadores: ${e.nombre}") { it.copy(maniobra = it.maniobra.copy(estabilizadoresId = e.id)) } },
            )
        }
        c.ev.resultados.firstOrNull { it.id == "tabla" }?.let { r ->
            ChipEstado(r.estado)
            r.datos.forEach { FilaDato(it.nombre, it.valor) }
            if (r.fuente.isNotBlank()) FilaDato("Fuente", r.fuente)
            r.mensajes.forEach { Aviso(it) }
        }
        val t = g.tablas.firstOrNull { it.id == c.p.maniobra.tablaId }
        if (t != null && t.puntos.isNotEmpty()) {
            Text("Valores de la tabla", style = MaterialTheme.typography.labelLarge)
            t.ordenados.forEach { pt ->
                FilaDato("Radio ${Unidades.m(pt.radio)}", Unidades.kg(pt.capacidad), pt.alturaMax?.let { "Altura máx. ${Unidades.m(it)}" } ?: "")
            }
            OutlinedButton(onClick = { c.nav.navigate(Rutas.tabla(g.id, t.id)) }) { Text("Revisar o corregir esta tabla") }
        }
    }
}
