package co.planizaje.app.ui.asistente

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.planizaje.app.ui.comp.Aviso
import co.planizaje.app.ui.comp.CampoFecha
import co.planizaje.app.ui.comp.CampoTexto
import co.planizaje.app.ui.comp.ChipEstado
import co.planizaje.app.ui.comp.ChipVeredicto
import co.planizaje.app.ui.comp.FilaDato
import co.planizaje.app.ui.comp.Seccion
import co.planizaje.app.ui.theme.Paleta
import co.planizaje.core.calc.EstadoDocumento
import co.planizaje.core.calc.Resultado
import co.planizaje.core.calc.Veredicto
import co.planizaje.core.model.RevisionHumana
import co.planizaje.core.util.Unidades

@Composable
fun ResumenEstado(estado: EstadoDocumento, pendientes: Int) {
    val (col, fondo, simb) = when {
        estado == EstadoDocumento.BORRADOR_NO_CUMPLE -> Triple(Paleta.Mal, Paleta.MalFondo, "✕")
        estado.esBorrador -> Triple(Paleta.Revisar, Paleta.RevisarFondo, "!")
        else -> Triple(Paleta.Ok, Paleta.OkFondo, "✓")
    }
    Column(Modifier.fillMaxWidth().background(fondo, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(simb, color = col, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
            Text("  Estado del plan", color = Color(0xFF1B1F24), style = MaterialTheme.typography.labelLarge)
        }
        Text(estado.etiqueta, color = col, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (pendientes > 0) Text("$pendientes punto(s) por resolver antes de poder presentarlo como listo.", color = Color(0xFF1B1F24), style = MaterialTheme.typography.bodyMedium)
        Text("La app nunca declara un izaje «seguro»: el plan debe revisarlo una persona competente.", color = Color(0xFF1B1F24), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PasoVerificacion(c: Ctx) {
    val ev = c.ev
    ResumenEstado(ev.estadoDocumento, ev.puntosPorResolver)

    Seccion("Resumen", icono = Icons.AutoMirrored.Filled.FactCheck) {
        FilaDato("Masa total suspendida", Unidades.kgYt(ev.masaTotal))
        FilaDato("Radio máximo", Unidades.m(ev.radioGobernante))
        FilaDato("Utilización máxima", Unidades.pct(ev.utilizacionMax))
    }

    if (ev.faltantes.isNotEmpty()) {
        Seccion("Datos que faltan", subtitulo = "Toca para ir al paso") {
            ev.faltantes.forEach { f ->
                Row(Modifier.fillMaxWidth().clickable { c.irAPaso(f.paso) }.padding(vertical = 6.dp)) {
                    Text("…  ", color = Paleta.Pendiente, fontWeight = FontWeight.Bold)
                    Text(f.texto, Modifier.weight(1f))
                    Text("Paso ${f.paso.numero}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    ev.resultados.groupBy { it.grupo }.forEach { (grupo, lista) ->
        Text(grupo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        lista.forEach { TarjetaResultado(it) { c.irAPaso(it.paso) } }
    }

    RevisionHumanaSeccion(c)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaResultado(r: Resultado, onIr: () -> Unit) {
    var abierto by rememberSaveable(r.id) { mutableStateOf(r.veredicto == Veredicto.NO_CUMPLE) }
    Card(
        Modifier.fillMaxWidth().clickable { abierto = !abierto },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(r.titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(r.valor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipEstado(r.estado)
                r.veredicto?.let { ChipVeredicto(it) }
            }
            r.mensajes.take(if (abierto) 99 else 1).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            AnimatedVisibility(abierto) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HorizontalDivider()
                    if (r.formula.isNotBlank()) { Text("Fórmula", style = MaterialTheme.typography.labelMedium); Text(r.formula, style = MaterialTheme.typography.bodyMedium) }
                    if (r.datos.isNotEmpty()) {
                        Text("Datos usados", style = MaterialTheme.typography.labelMedium)
                        r.datos.forEach { FilaDato(it.nombre, it.valor, it.origen) }
                    }
                    if (r.hipotesis.isNotEmpty()) {
                        Text("Hipótesis", style = MaterialTheme.typography.labelMedium)
                        r.hipotesis.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    if (r.fuente.isNotBlank()) FilaDato("Fuente técnica", r.fuente)
                    TextButton(onClick = onIr) { Text("Ir al paso ${r.paso.numero} para corregir") }
                }
            }
            if (!abierto) Text("Toca para ver fórmula, datos e hipótesis", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RevisionHumanaSeccion(c: Ctx) {
    val r = c.p.revisionHumana
    fun upd(desc: String, f: (RevisionHumana) -> RevisionHumana) = c.actualizar(desc) { it.copy(revisionHumana = f(it.revisionHumana)) }
    val borrador = c.ev.estadoDocumento.esBorrador
    val huella = co.planizaje.core.data.Serializacion.huellaRevision(c.p, c.grua, c.accesorios)
    val caducada = r.confirmaRevision && r.huellaDatos != huella
    Seccion("Revisión humana", icono = Icons.Filled.HowToReg, subtitulo = "Se registra en el plan; no reemplaza la firma") {
        if (caducada) Aviso("Los datos del plan cambiaron después de registrar la revisión. La revisión ya no es válida: debe revisarse de nuevo.", Paleta.Mal, Paleta.MalFondo, "✕")
        if (borrador) Aviso("Mientras el plan sea borrador no se puede registrar revisión ni aprobación. Resuelve primero los puntos pendientes.", Paleta.Pendiente, Paleta.PendienteFondo, "…")
        CampoTexto(r.revisadoPor, { v -> upd("Revisión humana") { it.copy(revisadoPor = v) } }, "Revisado por")
        CampoFecha(r.fechaRevision, { v -> upd("Revisión humana") { it.copy(fechaRevision = v) } }, "Fecha de revisión")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(r.confirmaRevision && !caducada, { b -> upd("Revisión humana") { it.copy(confirmaRevision = b, huellaDatos = if (b) huella else "", confirmaAprobacion = if (b) it.confirmaAprobacion else false) } }, enabled = !borrador && r.revisadoPor.isNotBlank())
            Text("Confirmo que revisé datos, cálculos, hipótesis y limitaciones", style = MaterialTheme.typography.bodyMedium)
        }
        CampoTexto(r.aprobadoPor, { v -> upd("Aprobación") { it.copy(aprobadoPor = v) } }, "Aprobado por")
        CampoFecha(r.fechaAprobacion, { v -> upd("Aprobación") { it.copy(fechaAprobacion = v) } }, "Fecha de aprobación")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(r.confirmaAprobacion && !caducada, { b -> upd("Aprobación") { it.copy(confirmaAprobacion = b) } }, enabled = !borrador && r.confirmaRevision && r.aprobadoPor.isNotBlank())
            Text("La persona indicada aprobó el plan", style = MaterialTheme.typography.bodyMedium)
        }
        CampoTexto(r.comentarios, { v -> upd("Comentarios de revisión") { it.copy(comentarios = v) } }, "Comentarios de la revisión", multilinea = true)
    }
}
