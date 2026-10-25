package co.planizaje.app.ui.asistente

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import co.planizaje.app.data.nuevoId
import co.planizaje.app.ui.Rutas
import co.planizaje.app.ui.comp.Aviso
import co.planizaje.app.ui.comp.Ayuda
import co.planizaje.app.ui.comp.CampoTexto
import co.planizaje.app.ui.comp.Seccion
import co.planizaje.app.ui.croquis.Opciones
import co.planizaje.app.ui.croquis.Renderizador
import co.planizaje.app.ui.croquis.Vista
import co.planizaje.app.ui.theme.Paleta
import co.planizaje.core.geo.Geometria
import co.planizaje.core.model.Elemento
import co.planizaje.core.model.TipoElemento

@Composable
fun PasoCroquis(c: Ctx) {
    val cr = c.p.croquis
    val u = LocalDensity.current.density

    Ayuda("Dibuja el lugar con el dedo: edificios, calle, obstáculos, líneas eléctricas, la grúa y los puntos de recogida y descarga. Es un croquis esquemático: las medidas reales se introducen como números.")

    Seccion("Vista en planta", icono = Icons.Filled.Map) {
        Box(
            Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFBFAF7))
                .clickable { c.nav.navigate(Rutas.croquis(c.p.id)) },
        ) {
            Canvas(Modifier.fillMaxWidth().height(260.dp)) {
                val v = Vista.ajustar(Geometria.cajaElementos(cr.elementos)?.expandir(2.0), size.width, size.height, 16 * u)
                drawIntoCanvas { Renderizador.dibujar(it.nativeCanvas, c.p, v, Opciones(u = u * 0.8f, cuadricula = cr.cuadricula, paso = cr.pasoCuadricula), size.width, size.height) }
            }
            if (cr.elementos.isEmpty()) Text("Croquis vacío · toca para empezar", Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = { c.nav.navigate(Rutas.croquis(c.p.id)) }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Icon(Icons.Filled.Draw, null); Text("  Abrir editor de croquis")
        }
        if (cr.elementos.isEmpty()) {
            OutlinedButton(onClick = {
                val base = listOf(
                    Elemento(nuevoId(), TipoElemento.GRUA, Elemento.CAPA_MANIOBRA, 0.0, 0.0),
                    Elemento(nuevoId(), TipoElemento.RECOGIDA, Elemento.CAPA_MANIOBRA, -5.0, 3.0, etiqueta = "Recogida"),
                    Elemento(nuevoId(), TipoElemento.DESCARGA, Elemento.CAPA_MANIOBRA, 6.0, -6.0, etiqueta = "Descarga"),
                )
                c.actualizar("Croquis inicial") { it.copy(croquis = it.croquis.copy(elementos = base)) }
            }, modifier = Modifier.fillMaxWidth()) { Text("Empezar con grúa, recogida y descarga") }
        }
        val g = cr.grua != null
        val r = cr.elementos.any { it.tipo == TipoElemento.RECOGIDA }
        val d = cr.elementos.any { it.tipo == TipoElemento.DESCARGA }
        listOf("Grúa colocada" to g, "Punto de recogida" to r, "Punto de descarga" to d).forEach { (t, ok) ->
            Text((if (ok) "✓  " else "…  ") + t, color = if (ok) Paleta.Ok else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
        if (cr.elementos.any { it.tipo == TipoElemento.LINEA_ELECTRICA && it.distanciaMinima == null })
            Aviso("Hay líneas eléctricas sin distancia mínima indicada. Selecciónalas en el editor y completa ese dato.")
    }

    Seccion("Notas del croquis", desplegable = true, inicialAbierta = cr.nota.isNotBlank()) {
        CampoTexto(cr.nota, { v -> c.actualizar("Notas del croquis") { it.copy(croquis = it.croquis.copy(nota = v)) } }, "Notas", multilinea = true, marcador = "Ej. Medidas tomadas con flexómetro el 24/10")
    }
}
