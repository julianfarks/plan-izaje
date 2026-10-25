package co.planizaje.app.ui.croquis

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.planizaje.app.data.nuevoId
import co.planizaje.app.ui.comp.Archivos
import co.planizaje.app.ui.comp.BarraSuperior
import co.planizaje.app.ui.comp.LocalAlmacen
import co.planizaje.core.geo.Geometria
import co.planizaje.core.model.Croquis
import co.planizaje.core.model.Elemento
import co.planizaje.core.model.Forma
import co.planizaje.core.model.Pt
import co.planizaje.core.model.TipoElemento
import co.planizaje.core.util.Unidades
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

enum class Herramienta(val etiqueta: String) { SELECCIONAR("Seleccionar"), DIBUJAR("Dibujar"), MEDIR("Medir") }

/** Qué se está dibujando con el dedo en modo Dibujar. */
sealed class Trazo(val etiqueta: String) {
    class Elem(val tipo: TipoElemento) : Trazo(tipo.etiqueta)
    class Recorrido(val faseId: String, nombre: String) : Trazo("Recorrido de $nombre")
}

private sealed class Objetivo {
    data object Plano : Objetivo()
    data class Mover(val id: String, val inicial: Elemento) : Objetivo()
    data class Manejar(val id: String, val manija: Manija, val inicial: Elemento) : Objetivo()
}

fun capaPorDefecto(t: TipoElemento): String = when (t.categoria) {
    "Riesgos" -> Elemento.CAPA_RIESGOS
    "Maniobra", "Vehículos", "Aparejos" -> Elemento.CAPA_MANIOBRA
    "Dibujo" -> Elemento.CAPA_ANOTACIONES
    else -> Elemento.CAPA_GENERAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaCroquis(id: String, onAtras: () -> Unit) {
    val almacen = LocalAlmacen.current
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    val proyectos by almacen.proyectos.collectAsState()
    val proyecto = proyectos.firstOrNull { it.id == id } ?: run {
        Scaffold(topBar = { BarraSuperior("Croquis", onAtras) }) { pad -> Text("Proyecto no encontrado", Modifier.padding(pad).padding(16.dp)) }
        return
    }

    var cr by remember { mutableStateOf(proyecto.croquis) }
    val deshacer = remember { mutableStateListOf<Croquis>() }
    val rehacer = remember { mutableStateListOf<Croquis>() }
    var vista by remember { mutableStateOf<Vista?>(null) }
    var tam by remember { mutableStateOf(Offset(1f, 1f)) }
    var seleccion by remember { mutableStateOf<String?>(null) }
    var herramienta by remember { mutableStateOf(Herramienta.SELECCIONAR) }
    var trazo by remember { mutableStateOf<Trazo?>(null) }
    val puntosTrazo = remember { mutableStateListOf<Pt>() }
    val medida = remember { mutableStateListOf<Pt>() }
    var biblioteca by remember { mutableStateOf(false) }
    var elegirTrazo by remember { mutableStateOf(false) }
    var capasDlg by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var leyenda by remember { mutableStateOf(false) }
    var portapapeles by remember { mutableStateOf<Elemento?>(null) }
    var panelAbierto by remember { mutableStateOf(true) }
    val u = dens

    fun guardar(nuevo: Croquis, desc: String = "Croquis editado") {
        almacen.actualizarProyecto(id, desc) { it.copy(croquis = nuevo) }
    }
    fun confirmar(nuevo: Croquis, antes: Croquis = cr, desc: String = "Croquis editado") {
        if (nuevo == antes) { cr = nuevo; return }
        deshacer.add(antes); if (deshacer.size > 60) deshacer.removeAt(0)
        rehacer.clear()
        cr = nuevo
        guardar(nuevo, desc)
    }
    fun cambiarElem(eid: String, f: (Elemento) -> Elemento) {
        confirmar(cr.copy(elementos = cr.elementos.map { if (it.id == eid) f(it) else it }))
    }
    fun snap(p: Pt): Pt = if (cr.ajusteCuadricula) Pt(Geometria.redondear(p.x, cr.pasoCuadricula / 2), Geometria.redondear(p.y, cr.pasoCuadricula / 2)) else p
    fun ajustarVista() {
        vista = Vista.ajustar(Geometria.cajaElementos(cr.elementos)?.expandir(2.0), tam.x, tam.y, 40 * u)
    }
    fun centro(): Pt = vista?.mundo(tam.x / 2, tam.y / 2) ?: Pt(0.0, 0.0)
    fun capaBloqueada(e: Elemento) = cr.capas.firstOrNull { it.id == e.capaId }?.bloqueada == true
    fun movible(e: Elemento) = !e.bloqueado && !capaBloqueada(e)

    fun agregar(tipo: TipoElemento) {
        if (tipo == TipoElemento.GRUA && cr.grua != null) {
            Toast.makeText(ctx, "Ya hay una grúa en el croquis; se seleccionó.", Toast.LENGTH_SHORT).show()
            seleccion = cr.grua?.id; herramienta = Herramienta.SELECCIONAR; return
        }
        val c = snap(centro())
        val e = Elemento(nuevoId(), tipo, capaPorDefecto(tipo), c.x, c.y)
        confirmar(cr.copy(elementos = cr.elementos + e), desc = "Añadido: ${tipo.etiqueta}")
        seleccion = e.id
        herramienta = Herramienta.SELECCIONAR
        panelAbierto = true
    }

    fun elementoEn(px: Float, py: Float): Elemento? {
        val v = vista ?: return null
        val p = v.mundo(px, py)
        val tol = 22 * u / v.escala
        val ocultas = cr.capas.filter { !it.visible }.map { it.id }.toSet()
        return cr.elementos.filter { it.capaId !in ocultas }
            .map { e ->
                val d = when (e.tipo.forma) {
                    Forma.PUNTO, Forma.TEXTO, Forma.SIMBOLO -> Geometria.distancia(p, Pt(e.x, e.y))
                    else -> Geometria.distanciaPuntoElemento(p, e)
                }
                val area = when (e.tipo.forma) { Forma.PUNTO, Forma.TEXTO, Forma.SIMBOLO -> 0.0; else -> e.ancho * e.largo }
                Triple(e, d, area)
            }
            .filter { it.second <= tol }
            .minWithOrNull(compareBy<Triple<Elemento, Double, Double>> { if (it.second > 0) 1 else 0 }.thenBy { it.third }.thenBy { it.second })
            ?.first
    }

    fun manijaEn(px: Float, py: Float): Manija? {
        val v = vista ?: return null
        val e = cr.elemento(seleccion) ?: return null
        if (!movible(e)) return null
        return Renderizador.manijas(e, v, u).firstOrNull { hypot((it.x - px).toDouble(), (it.y - py).toDouble()) < 24 * u }
    }

    val p = proyecto.copy(croquis = cr)
    val sel = cr.elemento(seleccion)

    Scaffold(
        topBar = {
            BarraSuperior("Croquis del lugar", onAtras, subtitulo = "Vista en planta · esquemática", acciones = {
                IconButton(onClick = {
                    deshacer.removeLastOrNull()?.let { ant -> rehacer.add(cr); cr = ant; guardar(ant, "Deshacer en croquis") }
                }, enabled = deshacer.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Undo, "Deshacer") }
                IconButton(onClick = {
                    rehacer.removeLastOrNull()?.let { sig -> deshacer.add(cr); cr = sig; guardar(sig, "Rehacer en croquis") }
                }, enabled = rehacer.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Redo, "Rehacer") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más herramientas") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text((if (cr.cuadricula) "✓ " else "") + "Cuadrícula") }, { confirmar(cr.copy(cuadricula = !cr.cuadricula)); menu = false })
                        DropdownMenuItem({ Text((if (cr.ajusteCuadricula) "✓ " else "") + "Ajustar a cuadrícula") }, { confirmar(cr.copy(ajusteCuadricula = !cr.ajusteCuadricula)); menu = false })
                        DropdownMenuItem({ Text("Paso de cuadrícula: ${Unidades.fmt(cr.pasoCuadricula)} m") }, {
                            val pasos = listOf(0.5, 1.0, 2.0, 5.0)
                            val i = (pasos.indexOf(cr.pasoCuadricula) + 1) % pasos.size
                            confirmar(cr.copy(pasoCuadricula = pasos[i])); menu = false
                        })
                        DropdownMenuItem({ Text((if (leyenda) "✓ " else "") + "Mostrar leyenda") }, { leyenda = !leyenda; menu = false })
                        DropdownMenuItem({ Text("Capas…") }, { capasDlg = true; menu = false })
                        DropdownMenuItem({ Text("Vista previa / exportar imagen") }, {
                            menu = false
                            val f = exportarPng(ctx.cacheDir, p)
                            Archivos.abrir(ctx, f, "image/png")
                        })
                        DropdownMenuItem({ Text("Compartir imagen del croquis") }, {
                            menu = false
                            Archivos.compartir(ctx, exportarPng(ctx.cacheDir, p), "image/png", "Compartir croquis")
                        })
                    }
                }
            })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(herramienta == Herramienta.SELECCIONAR, { herramienta = Herramienta.SELECCIONAR; trazo = null; puntosTrazo.clear(); medida.clear() },
                    { Icon(Icons.Filled.NearMe, null) }, label = { Text("Mover") })
                NavigationBarItem(false, { biblioteca = true }, { Icon(Icons.Filled.AddBox, null) }, label = { Text("Añadir") })
                NavigationBarItem(herramienta == Herramienta.DIBUJAR, { elegirTrazo = true }, { Icon(Icons.Filled.Draw, null) }, label = { Text("Dibujar") })
                NavigationBarItem(herramienta == Herramienta.MEDIR, { herramienta = Herramienta.MEDIR; seleccion = null; medida.clear() },
                    { Icon(Icons.Filled.Straighten, null) }, label = { Text("Medir") })
                NavigationBarItem(false, {
                    val s = seleccion
                    if (s != null) { confirmar(cr.copy(elementos = cr.elementos.filter { it.id != s }), desc = "Elemento eliminado"); seleccion = null }
                    else Toast.makeText(ctx, "Toca un objeto para seleccionarlo y luego elimínalo", Toast.LENGTH_SHORT).show()
                }, { Icon(Icons.Filled.Delete, null) }, label = { Text("Eliminar") }, enabled = sel != null)
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).background(androidx.compose.ui.graphics.Color(0xFFFBFAF7))) {
            Canvas(
                Modifier.fillMaxSize()
                    .onSizeChanged { s ->
                        tam = Offset(s.width.toFloat(), s.height.toFloat())
                        if (vista == null) ajustarVista()
                    }
                    .pointerInput(herramienta, trazo) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val inicio = down.position
                            val v0 = vista ?: return@awaitEachGesture
                            var objetivo: Objetivo = Objetivo.Plano
                            if (herramienta == Herramienta.SELECCIONAR) {
                                val m = manijaEn(inicio.x, inicio.y)
                                val s = cr.elemento(seleccion)
                                objetivo = if (m != null && s != null) Objetivo.Manejar(s.id, m, s)
                                else {
                                    val e = elementoEn(inicio.x, inicio.y)
                                    if (e != null && e.id == seleccion && movible(e)) Objetivo.Mover(e.id, e) else Objetivo.Plano
                                }
                            }
                            val antes = cr
                            var movido = false
                            var multi = false
                            do {
                                val ev = awaitPointerEvent()
                                val activos = ev.changes.filter { it.pressed }
                                if (activos.size >= 2) {
                                    multi = true
                                    val zoom = ev.calculateZoom()
                                    val pan = ev.calculatePan()
                                    val c = ev.calculateCentroid()
                                    vista?.let { v ->
                                        val ns = (v.escala * zoom).coerceIn(2f, 600f)
                                        val k = ns / v.escala
                                        vista = Vista(ns, c.x - (c.x - v.offX) * k + pan.x, c.y - (c.y - v.offY) * k + pan.y)
                                    }
                                    ev.changes.forEach { it.consume() }
                                } else if (activos.size == 1 && !multi) {
                                    val ch = activos[0]
                                    if (!movido && (ch.position - inicio).getDistance() > viewConfiguration.touchSlop) movido = true
                                    if (movido) {
                                        val v = vista ?: v0
                                        when (val o = objetivo) {
                                            Objetivo.Plano -> {
                                                val d = ch.position - ch.previousPosition
                                                vista = v.copy(offX = v.offX + d.x, offY = v.offY + d.y)
                                            }
                                            is Objetivo.Mover -> {
                                                val dx = (ch.position.x - inicio.x) / v.escala
                                                val dy = (ch.position.y - inicio.y) / v.escala
                                                val np = snap(Pt(o.inicial.x + dx, o.inicial.y + dy))
                                                cr = cr.copy(elementos = cr.elementos.map { if (it.id == o.id) it.copy(x = np.x, y = np.y) else it })
                                            }
                                            is Objetivo.Manejar -> {
                                                val w = v.mundo(ch.position.x, ch.position.y)
                                                val e0 = o.inicial
                                                val nuevo = when (o.manija.tipo) {
                                                    TipoManija.ROTAR -> {
                                                        val paso = if (cr.ajusteCuadricula) 15.0 else 5.0
                                                        e0.copy(rotacion = Geometria.redondear(Geometria.rumbo(Pt(e0.x, e0.y), w), paso) % 360.0)
                                                    }
                                                    TipoManija.REDIMENSIONAR -> {
                                                        if (e0.tipo.forma == Forma.CIRCULO) {
                                                            val r = Geometria.distancia(Pt(e0.x, e0.y), w).coerceAtLeast(0.1)
                                                            val d = Geometria.redondear(2 * r / 1.0, 0.1)
                                                            e0.copy(ancho = d, largo = d, medidaExacta = false)
                                                        } else {
                                                            val l = Geometria.rotar(w, -e0.rotacion, Pt(e0.x, e0.y))
                                                            val an = Geometria.redondear(max(0.1, 2 * abs(l.x - e0.x)), 0.05)
                                                            val la = Geometria.redondear(max(0.1, 2 * abs(l.y - e0.y)), 0.05)
                                                            e0.copy(ancho = an, largo = la, medidaExacta = false)
                                                        }
                                                    }
                                                    TipoManija.VERTICE -> {
                                                        val base = if (e0.puntos.size >= 2) e0.puntos else Geometria.puntosPorDefecto(e0)
                                                        val ws = snap(w)
                                                        val l = Geometria.rotar(ws, -e0.rotacion, Pt(e0.x, e0.y))
                                                        val np = base.toMutableList()
                                                        if (o.manija.indice in np.indices) np[o.manija.indice] = Pt(l.x - e0.x, l.y - e0.y)
                                                        e0.copy(puntos = np, medidaExacta = false)
                                                    }
                                                }
                                                cr = cr.copy(elementos = cr.elementos.map { if (it.id == o.id) nuevo else it })
                                            }
                                        }
                                    }
                                    ch.consume()
                                }
                            } while (ev.changes.any { it.pressed })

                            if (!movido && !multi) {
                                // Toque simple
                                val w = v0.mundo(inicio.x, inicio.y)
                                when (herramienta) {
                                    Herramienta.SELECCIONAR -> { seleccion = elementoEn(inicio.x, inicio.y)?.id; if (seleccion != null) panelAbierto = true }
                                    Herramienta.DIBUJAR -> puntosTrazo.add(snap(w))
                                    Herramienta.MEDIR -> { if (medida.size >= 2) medida.clear(); medida.add(snap(w)) }
                                }
                            } else if (objetivo !is Objetivo.Plano && movido) {
                                confirmar(cr, antes, "Croquis editado")
                            }
                        }
                    },
            ) {
                val v = vista ?: return@Canvas
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    Renderizador.dibujar(
                        nc, p, v,
                        Opciones(u = u, seleccion = seleccion, cuadricula = cr.cuadricula, paso = cr.pasoCuadricula, manijas = herramienta == Herramienta.SELECCIONAR),
                        size.width, size.height,
                    )
                    dibujarBorrador(nc, v, u, puntosTrazo.toList(), trazo, medida.toList())
                    Renderizador.barraEscala(nc, v, u, 16 * u, size.height - 18 * u, 120 * u)
                }
            }

            // Botones de vista
            Column(Modifier.align(Alignment.TopEnd).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallFloatingActionButton(onClick = { ajustarVista() }) { Icon(Icons.Filled.FitScreen, "Ajustar todo a la pantalla") }
                SmallFloatingActionButton(onClick = {
                    val g = cr.grua ?: cr.elementos.firstOrNull()
                    val v = vista
                    if (v != null) {
                        val c = g?.let { Pt(it.x, it.y) } ?: Pt(0.0, 0.0)
                        vista = v.copy(offX = (tam.x / 2 - c.x * v.escala).toFloat(), offY = (tam.y / 2 - c.y * v.escala).toFloat())
                    }
                }) { Icon(Icons.Filled.CenterFocusStrong, "Centrar en la grúa") }
            }

            // Ayuda contextual / barra de dibujo
            Column(Modifier.align(Alignment.TopStart).padding(10.dp).widthIn(max = 280.dp)) {
                val txt = when {
                    herramienta == Herramienta.DIBUJAR && trazo != null -> "${trazo!!.etiqueta}: toca el plano para añadir puntos (${puntosTrazo.size})."
                    herramienta == Herramienta.MEDIR -> if (medida.size == 2) "Distancia ≈ ${Unidades.fmt(Geometria.distancia(medida[0], medida[1]), 2)} m (medida sobre el dibujo)" else "Toca dos puntos para medir."
                    cr.elementos.isEmpty() -> "Pulsa «Añadir» para colocar edificios, la grúa y los puntos de recogida y descarga."
                    sel == null -> "Toca un objeto para seleccionarlo. Un dedo arrastra el plano; dos dedos hacen zoom."
                    else -> "Arrastra el objeto seleccionado para moverlo. ↻ gira, ○ cambia el tamaño."
                }
                Text(txt, style = MaterialTheme.typography.bodySmall, modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(8.dp)).padding(8.dp))
                if (herramienta == Herramienta.DIBUJAR && trazo != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Button(onClick = {
                            val t = trazo
                            val pts = puntosTrazo.toList()
                            when (t) {
                                is Trazo.Elem -> {
                                    val min = if (t.tipo.forma == Forma.POLIGONO) 3 else 2
                                    if (pts.size >= min) {
                                        val o = pts.first()
                                        val e = Elemento(nuevoId(), t.tipo, capaPorDefecto(t.tipo), o.x, o.y, puntos = pts.map { Pt(it.x - o.x, it.y - o.y) })
                                        confirmar(cr.copy(elementos = cr.elementos + e), desc = "Dibujado: ${t.tipo.etiqueta}")
                                        seleccion = e.id
                                    } else Toast.makeText(ctx, "Necesitas al menos $min puntos", Toast.LENGTH_SHORT).show()
                                }
                                is Trazo.Recorrido -> almacen.actualizarProyecto(id, "Recorrido de la carga") { pr ->
                                    pr.copy(maniobra = pr.maniobra.copy(fases = pr.maniobra.fases.map { if (it.id == t.faseId) it.copy(recorrido = pts) else it }))
                                }
                                null -> {}
                            }
                            puntosTrazo.clear(); trazo = null; herramienta = Herramienta.SELECCIONAR
                        }) { Text("Terminar") }
                        OutlinedButton(onClick = { puntosTrazo.removeLastOrNull() }) { Text("Quitar punto") }
                        IconButton(onClick = { puntosTrazo.clear(); trazo = null; herramienta = Herramienta.SELECCIONAR }) { Icon(Icons.Filled.Close, "Cancelar") }
                    }
                }
                if (herramienta == Herramienta.MEDIR && medida.size == 2) {
                    FilledTonalButton(onClick = {
                        val a = medida[0]; val b = medida[1]
                        val e = Elemento(nuevoId(), TipoElemento.COTA, Elemento.CAPA_ANOTACIONES, a.x, a.y, puntos = listOf(Pt(0.0, 0.0), Pt(b.x - a.x, b.y - a.y)))
                        confirmar(cr.copy(elementos = cr.elementos + e), desc = "Cota añadida")
                        medida.clear()
                    }, modifier = Modifier.padding(top = 6.dp)) { Text("Guardar como cota") }
                }
            }

            if (leyenda) {
                Card(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 44.dp).widthIn(max = 260.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Leyenda", fontWeight = FontWeight.Bold)
                        Renderizador.leyenda(p).forEach { Text("${it.simbolo}  ${it.texto}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            // Panel de propiedades
            if (sel != null && herramienta == Herramienta.SELECCIONAR) {
                Card(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(6.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Renderizador.nombre(sel), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { panelAbierto = !panelAbierto }) { Text(if (panelAbierto) "Ocultar" else "Propiedades") }
                        IconButton(onClick = { seleccion = null }) { Icon(Icons.Filled.Close, "Deseleccionar") }
                    }
                    if (panelAbierto) {
                        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 12.dp)) {
                            androidx.compose.runtime.key(sel.id) { PanelPropiedades(
                                e = sel, capas = cr.capas, gruaProyecto = almacen.grua(proyecto.gruaId)?.nombreCorto,
                                onCambio = { f -> cambiarElem(sel.id, f) },
                                onDuplicar = {
                                    val d = sel.copy(id = nuevoId(), x = sel.x + 1.0, y = sel.y + 1.0, bloqueado = false)
                                    if (d.tipo == TipoElemento.GRUA) Toast.makeText(ctx, "Solo puede haber una grúa", Toast.LENGTH_SHORT).show()
                                    else { confirmar(cr.copy(elementos = cr.elementos + d), desc = "Elemento duplicado"); seleccion = d.id }
                                },
                                onCopiar = { portapapeles = sel; Toast.makeText(ctx, "Copiado", Toast.LENGTH_SHORT).show() },
                                puedePegar = portapapeles != null && portapapeles?.tipo != TipoElemento.GRUA,
                                onPegar = {
                                    portapapeles?.let { pp ->
                                        val c = snap(centro())
                                        val n = pp.copy(id = nuevoId(), x = c.x, y = c.y, bloqueado = false)
                                        confirmar(cr.copy(elementos = cr.elementos + n), desc = "Elemento pegado"); seleccion = n.id
                                    }
                                },
                            ) }
                        }
                    }
                }
            }
        }
    }

    if (biblioteca) BibliotecaObjetos(onElegir = { t -> biblioteca = false; agregar(t) }, onCerrar = { biblioteca = false })

    if (elegirTrazo) AlertDialog(
        onDismissRequest = { elegirTrazo = false },
        title = { Text("¿Qué quieres dibujar?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val opciones = listOf(TipoElemento.LINEA, TipoElemento.FLECHA, TipoElemento.POLIGONO, TipoElemento.ZONA_EXCLUSION, TipoElemento.LINEA_ELECTRICA, TipoElemento.ESLINGA, TipoElemento.COTA)
                opciones.forEach { t ->
                    TextButton(onClick = { trazo = Trazo.Elem(t); puntosTrazo.clear(); herramienta = Herramienta.DIBUJAR; seleccion = null; elegirTrazo = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(t.etiqueta, modifier = Modifier.fillMaxWidth())
                    }
                }
                proyecto.maniobra.fases.forEachIndexed { i, f ->
                    val nombre = f.nombre.ifBlank { "Fase ${i + 1}" }
                    TextButton(onClick = { trazo = Trazo.Recorrido(f.id, nombre); puntosTrazo.clear(); herramienta = Herramienta.DIBUJAR; seleccion = null; elegirTrazo = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Puntos de paso del recorrido · $nombre", modifier = Modifier.fillMaxWidth())
                    }
                }
                if (proyecto.maniobra.fases.isEmpty()) Text("Para dibujar el recorrido de la carga, crea primero las fases en el paso «Maniobra».", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { elegirTrazo = false }) { Text("Cerrar") } },
    )

    if (capasDlg) AlertDialog(
        onDismissRequest = { capasDlg = false },
        title = { Text("Capas") },
        text = {
            Column {
                Row { Text("Capa", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text("Ver", Modifier.width(56.dp), fontWeight = FontWeight.Bold); Text("Bloq.", Modifier.width(56.dp), fontWeight = FontWeight.Bold) }
                cr.capas.forEach { capa ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(capa.nombre, Modifier.weight(1f))
                        Box(Modifier.width(56.dp)) { Checkbox(capa.visible, { b -> confirmar(cr.copy(capas = cr.capas.map { if (it.id == capa.id) it.copy(visible = b) else it })) }) }
                        Box(Modifier.width(56.dp)) { Checkbox(capa.bloqueada, { b -> confirmar(cr.copy(capas = cr.capas.map { if (it.id == capa.id) it.copy(bloqueada = b) else it })) }) }
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text("Las capas ocultas tampoco se imprimen en el PDF. Las bloqueadas no se pueden mover.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { capasDlg = false }) { Text("Listo") } },
    )

    // Si el proyecto cambió desde otra pantalla (p. ej. recorrido), sincroniza sin perder la edición local.
    LaunchedEffect(proyecto.croquis) { if (proyecto.croquis != cr && deshacer.isEmpty()) cr = proyecto.croquis }
}

private fun dibujarBorrador(cv: android.graphics.Canvas, v: Vista, u: Float, pts: List<Pt>, trazo: Trazo?, medida: List<Pt>) {
    val pinta = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE; strokeWidth = 2.5f * u; color = 0xFF1E88E5.toInt()
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8 * u, 5 * u), 0f)
    }
    val punto = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E88E5.toInt() }
    if (trazo != null) {
        pts.zipWithNext().forEach { (a, b) -> cv.drawLine(v.sx(a.x), v.sy(a.y), v.sx(b.x), v.sy(b.y), pinta) }
        pts.forEach { cv.drawCircle(v.sx(it.x), v.sy(it.y), 6 * u, punto) }
    }
    if (medida.isNotEmpty()) {
        val m = android.graphics.Paint(pinta).apply { color = 0xFFD81B60.toInt() }
        medida.forEach { cv.drawCircle(v.sx(it.x), v.sy(it.y), 6 * u, android.graphics.Paint(punto).apply { color = 0xFFD81B60.toInt() }) }
        if (medida.size == 2) {
            cv.drawLine(v.sx(medida[0].x), v.sy(medida[0].y), v.sx(medida[1].x), v.sy(medida[1].y), m)
            val c = Pt((medida[0].x + medida[1].x) / 2, (medida[0].y + medida[1].y) / 2)
            Renderizador.etiquetaFondo(cv, "≈ ${Unidades.fmt(Geometria.distancia(medida[0], medida[1]), 2)} m", v.sx(c.x), v.sy(c.y) - 10 * u, u, 0xFFD81B60.toInt())
        }
    }
}

/** Exporta el croquis completo a un PNG de alta resolución. */
fun exportarPng(dir: File, p: co.planizaje.core.model.Proyecto, ancho: Int = 3000, alto: Int = 2100): File {
    val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp)
    cv.drawColor(0xFFFFFFFF.toInt())
    val u = ancho / 900f
    val v = Vista.ajustar(Geometria.cajaElementos(p.croquis.elementos)?.expandir(2.0), ancho.toFloat(), alto.toFloat(), 60 * u)
    Renderizador.dibujar(cv, p, v, Opciones(u = u, cuadricula = p.croquis.cuadricula, paso = p.croquis.pasoCuadricula), ancho.toFloat(), alto.toFloat())
    Renderizador.barraEscala(cv, v, u, 30 * u, alto - 30 * u, 200 * u)
    Renderizador.etiquetaFondo(cv, "VISTA EN PLANTA · CROQUIS ESQUEMÁTICO (no es un levantamiento topográfico)", ancho / 2f, 24 * u, u, 0xFF16263D.toInt())
    val f = File(dir, "croquis-${p.id.take(8)}.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()
    return f
}
