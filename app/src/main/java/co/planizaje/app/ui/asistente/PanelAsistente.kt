package co.planizaje.app.ui.asistente

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.planizaje.app.ui.theme.Paleta
import co.planizaje.core.ia.Asistente
import co.planizaje.core.model.Paso

/**
 * Asistente de datos faltantes: resume lo que ya hay y lleva directamente al campo que falta.
 * Trabaja sobre los datos reales del proyecto actual.
 */
@Composable
fun PanelAsistente(c: Ctx, paso: Paso) {
    val r = remember(c.p, c.grua, c.ev) { Asistente.resumen(c.p, c.grua, c.ev, paso) }
    var abierto by rememberSaveable { mutableStateOf(false) }
    val delPaso = r.acciones.filter { it.paso == paso }
    val mostrar = if (abierto || paso == Paso.VERIFICACION) r.acciones else delPaso.take(3)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { abierto = !abierto }, verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(r.frase(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(if (abierto) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (abierto) "Ver menos" else "Ver todo")
            }
            mostrar.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .clickable { c.irACampo(a.paso, a.campo) }.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (a.urgente) "✕" else "…", color = if (a.urgente) Paleta.Mal else Paleta.Pendiente, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
                    Text(a.texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (a.paso != paso) Text("Paso ${a.paso.numero}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Ir", Modifier.size(18.dp))
                }
            }
            AnimatedVisibility(!abierto && r.acciones.size > mostrar.size && paso != Paso.VERIFICACION) {
                Text("Ver los ${r.acciones.size} puntos pendientes", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { abierto = true })
            }
        }
    }
}
