package co.planizaje.app.ui.croquis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Roofing
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.AddRoad
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Fence
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.planizaje.app.ui.comp.Ayuda
import co.planizaje.app.ui.comp.CampoNumero
import co.planizaje.app.ui.comp.CampoTexto
import co.planizaje.app.ui.comp.FilaDato
import co.planizaje.app.ui.comp.FilaInterruptor
import co.planizaje.app.ui.comp.Selector
import co.planizaje.core.geo.Geometria
import co.planizaje.core.model.Capa
import co.planizaje.core.model.Elemento
import co.planizaje.core.model.Forma
import co.planizaje.core.model.Pt
import co.planizaje.core.model.TipoElemento
import co.planizaje.core.util.Unidades
import kotlin.math.abs

fun icono(t: TipoElemento): ImageVector = when (t) {
    TipoElemento.EDIFICIO -> Icons.Filled.Apartment
    TipoElemento.MURO -> Icons.Filled.Fence
    TipoElemento.COLUMNA -> Icons.Filled.ViewColumn
    TipoElemento.CUBIERTA -> Icons.Filled.Roofing
    TipoElemento.ESTRUCTURA_METALICA -> Icons.Filled.Construction
    TipoElemento.CALLE, TipoElemento.ANDEN -> Icons.Filled.AddRoad
    TipoElemento.CAMION, TipoElemento.GRUA -> Icons.Filled.LocalShipping
    TipoElemento.ESTABILIZADOR -> Icons.Filled.Square
    TipoElemento.CARGA -> Icons.Filled.Inventory2
    TipoElemento.RECOGIDA -> Icons.Filled.Upload
    TipoElemento.DESCARGA -> Icons.Filled.Download
    TipoElemento.OBSTACULO -> Icons.Filled.Block
    TipoElemento.ARBOL -> Icons.Filled.Forest
    TipoElemento.POSTE -> Icons.Filled.Circle
    TipoElemento.LINEA_ELECTRICA -> Icons.Filled.ElectricBolt
    TipoElemento.ZONA_EXCLUSION -> Icons.Filled.Warning
    TipoElemento.ESLINGA -> Icons.Filled.Link
    TipoElemento.GRILLETE, TipoElemento.GANCHO -> Icons.Filled.Anchor
    TipoElemento.BALANCIN -> Icons.Filled.HorizontalRule
    TipoElemento.TEXTO -> Icons.Filled.TextFields
    TipoElemento.COTA -> Icons.Filled.Straighten
    TipoElemento.LINEA -> Icons.AutoMirrored.Filled.ShowChart
    TipoElemento.FLECHA -> Icons.AutoMirrored.Filled.ArrowForward
    TipoElemento.POLIGONO -> Icons.Filled.Hexagon
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibliotecaObjetos(onElegir: (TipoElemento) -> Unit, onCerrar: () -> Unit) {
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val orden = listOf("Maniobra", "Vehículos", "Estructuras", "Entorno", "Riesgos", "Aparejos", "Dibujo")
    val grupos = TipoElemento.entries.groupBy { it.categoria }
    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = estado) {
        Text("Añadir al croquis", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Text("Se coloca en el centro de la pantalla. Luego puedes moverlo y ajustar sus medidas.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        LazyVerticalGrid(
            GridCells.Adaptive(96.dp),
            Modifier.fillMaxWidth().heightIn(max = 560.dp).navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            orden.forEach { cat ->
                val lista = grupos[cat] ?: return@forEach
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(cat, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
                items(lista) { t ->
                    Card(
                        onClick = { onElegir(t) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(10.dp).heightIn(min = 72.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(icono(t), null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(t.etiqueta, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PanelPropiedades(
    e: Elemento,
    capas: List<Capa>,
    gruaProyecto: String?,
    onCambio: ((Elemento) -> Elemento) -> Unit,
    onDuplicar: () -> Unit,
    onCopiar: () -> Unit,
    puedePegar: Boolean,
    onPegar: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CampoTexto(e.etiqueta, { v -> onCambio { it.copy(etiqueta = v) } }, if (e.tipo == TipoElemento.TEXTO) "Texto" else "Nombre / etiqueta", marcador = e.tipo.etiqueta)

        when (e.tipo.forma) {
            Forma.RECT, Forma.GRUA, Forma.CIRCULO -> {
                val circ = e.tipo.forma == Forma.CIRCULO
                if (e.tipo == TipoElemento.GRUA) {
                    Ayuda("El círculo ámbar es el eje de giro (columna) y desde él se miden los radios. La cabina azul indica el frente.${gruaProyecto?.let { " Grúa del proyecto: $it." } ?: ""}")
                }
                if (circ) {
                    CampoNumero(e.ancho, { v -> v?.let { d -> onCambio { it.copy(ancho = d, largo = d) } } }, "Diámetro", "m", min = 0.05)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CampoNumero(e.ancho, { v -> v?.let { d -> onCambio { it.copy(ancho = d) } } }, if (e.tipo == TipoElemento.GRUA) "Ancho camión" else "Ancho", "m", Modifier.weight(1f), min = 0.05)
                        CampoNumero(e.largo, { v -> v?.let { d -> onCambio { it.copy(largo = d) } } }, if (e.tipo == TipoElemento.GRUA) "Largo camión" else "Largo", "m", Modifier.weight(1f), min = 0.05)
                    }
                }
                if (e.tipo != TipoElemento.GRUA && e.tipo != TipoElemento.CALLE && e.tipo != TipoElemento.ANDEN)
                    CampoNumero(e.alto, { v -> onCambio { it.copy(alto = v) } }, "Altura", "m", ayuda = "Se usa para estimar holguras en la vista lateral")
                if (e.tipo == TipoElemento.GRUA)
                    CampoNumero(e.anchoEstabilizado, { v -> onCambio { it.copy(anchoEstabilizado = v) } }, "Ancho con estabilizadores", "m", ayuda = "Según el manual y la configuración elegida")
                if (!circ) CampoNumero(e.rotacion, { v -> onCambio { it.copy(rotacion = ((v ?: 0.0) % 360.0 + 360.0) % 360.0) } }, "Giro", "°", min = null)
            }
            Forma.PUNTO -> {
                CampoNumero(e.alto, { v -> onCambio { it.copy(alto = v) } }, "Altura del punto sobre el suelo", "m", ayuda = "Ej. cubierta a 9 m. Se usa para la altura de gancho.")
            }
            Forma.LINEA, Forma.FLECHA, Forma.COTA -> {
                val pts = Geometria.puntosAbsolutos(e)
                val l = Geometria.longitudPolilinea(pts)
                if (pts.size == 2) {
                    CampoNumero(l, { v ->
                        v?.takeIf { it > 0 }?.let { nl ->
                            onCambio { el ->
                                val base = if (el.puntos.size >= 2) el.puntos else Geometria.puntosPorDefecto(el)
                                val a = base[0]; val b = base[1]
                                val d = Geometria.distancia(a, b).takeIf { it > 0 } ?: 1.0
                                el.copy(puntos = listOf(a, Pt(a.x + (b.x - a.x) / d * nl, a.y + (b.y - a.y) / d * nl)))
                            }
                        }
                    }, "Longitud", "m", min = 0.01)
                } else FilaDato("Longitud total", Unidades.m(l))
                if (e.tipo == TipoElemento.LINEA_ELECTRICA) {
                    CampoNumero(e.tensionKv, { v -> onCambio { it.copy(tensionKv = v) } }, "Tensión nominal", "kV", ayuda = "Confírmala con el operador de red")
                    CampoNumero(e.distanciaMinima, { v -> onCambio { it.copy(distanciaMinima = v) } }, "Distancia mínima exigida", "m", ayuda = "Según la norma aplicable a esa tensión. La app no asume ningún valor.")
                }
            }
            Forma.POLIGONO -> {
                val pts = Geometria.puntosAbsolutos(e)
                var area = 0.0
                for (i in pts.indices) { val a = pts[i]; val b = pts[(i + 1) % pts.size]; area += a.x * b.y - b.x * a.y }
                FilaDato("Área aproximada", "${Unidades.fmt(abs(area) / 2, 1)} m²")
                FilaDato("Perímetro", Unidades.m(Geometria.longitudPolilinea(pts + pts.first())))
            }
            Forma.TEXTO, Forma.SIMBOLO -> {}
        }

        FilaInterruptor(
            "Medidas verificadas en campo", e.medidaExacta, { b -> onCambio { it.copy(medidaExacta = b) } },
            "Actívalo solo si introdujiste medidas reales tomadas en obra. Si mueves o estiras el objeto con el dedo, vuelve a «aproximada».",
        )
        Selector("Capa", capas, capas.firstOrNull { it.id == e.capaId }, { it.nombre }, { c -> onCambio { it.copy(capaId = c.id) } })
        FilaInterruptor("Bloquear (evita moverlo por accidente)", e.bloqueado, { b -> onCambio { it.copy(bloqueado = b) } })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onDuplicar) { Icon(Icons.Filled.Layers, null); Text(" Duplicar") }
            FilledTonalButton(onClick = onCopiar) { Icon(Icons.Filled.ContentCopy, null); Text(" Copiar") }
            if (puedePegar) FilledTonalButton(onClick = onPegar) { Icon(Icons.Filled.ContentPaste, null); Text(" Pegar") }
        }
        Text("Posición en el plano", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoNumero(e.x, { v -> v?.let { n -> onCambio { it.copy(x = n) } } }, "X", "m", Modifier.weight(1f), min = null)
            CampoNumero(e.y, { v -> v?.let { n -> onCambio { it.copy(y = n) } } }, "Y", "m", Modifier.weight(1f), min = null)
        }
    }
}

@Suppress("unused") private val usados = listOf(Icons.Filled.Flag, Icons.Filled.CropSquare, Icons.Filled.SquareFoot)
