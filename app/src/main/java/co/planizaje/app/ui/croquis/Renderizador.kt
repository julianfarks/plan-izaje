package co.planizaje.app.ui.croquis

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import co.planizaje.core.calc.Motor
import co.planizaje.core.geo.Geometria
import co.planizaje.core.model.Croquis
import co.planizaje.core.model.Elemento
import co.planizaje.core.model.Fase
import co.planizaje.core.model.Forma
import co.planizaje.core.model.Proyecto
import co.planizaje.core.model.Pt
import co.planizaje.core.model.TipoElemento
import co.planizaje.core.util.Unidades
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Transformación plano (m) → pantalla/papel (px). */
data class Vista(val escala: Float, val offX: Float, val offY: Float) {
    fun sx(x: Double) = (offX + x * escala).toFloat()
    fun sy(y: Double) = (offY + y * escala).toFloat()
    fun mundo(px: Float, py: Float) = Pt(((px - offX) / escala).toDouble(), ((py - offY) / escala).toDouble())

    companion object {
        fun ajustar(caja: Geometria.Caja?, ancho: Float, alto: Float, margen: Float): Vista {
            val c = (caja ?: Geometria.Caja(-10.0, -10.0, 10.0, 10.0)).let {
                if (it.ancho < 4 || it.alto < 4) Geometria.Caja(it.centro.x - 5, it.centro.y - 5, it.centro.x + 5, it.centro.y + 5) else it
            }
            val s = min((ancho - 2 * margen) / c.ancho, (alto - 2 * margen) / c.alto).toFloat().coerceIn(2f, 400f)
            val cx = c.centro
            return Vista(s, (ancho / 2 - cx.x * s).toFloat(), (alto / 2 - cx.y * s).toFloat())
        }
    }
}

data class Opciones(
    /** Tamaño base de texto y trazos (px por "dp"). En pantalla = densidad; en PDF ≈ 0,6. */
    val u: Float,
    val seleccion: String? = null,
    val cuadricula: Boolean = true,
    val paso: Double = 1.0,
    val faseResaltada: String? = null,
    val manijas: Boolean = false,
    val capasOcultas: Set<String> = emptySet(),
)

enum class TipoManija { ROTAR, REDIMENSIONAR, VERTICE }
data class Manija(val tipo: TipoManija, val x: Float, val y: Float, val indice: Int = -1)

object Renderizador {

    // Colores (ARGB). Los significados nunca dependen solo del color: hay letras, rayados y etiquetas.
    private const val TINTA = 0xFF1B1F24.toInt()
    private const val GRIS = 0xFF8A8F98.toInt()
    private const val AZUL = 0xFF16263D.toInt()
    private const val AMBAR = 0xFFF2A900.toInt()
    private const val ROJO = 0xFFC62828.toInt()
    private const val VERDE = 0xFF2E7D32.toInt()
    private const val MARRON = 0xFF6D4C41.toInt()
    private const val SEL = 0xFF1E88E5.toInt()

    private val ORDEN_CAPAS = listOf(Elemento.CAPA_GENERAL, Elemento.CAPA_RIESGOS, Elemento.CAPA_MANIOBRA, Elemento.CAPA_ANOTACIONES)

    private fun relleno(c: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = c }
    private fun trazo(c: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = c; strokeWidth = w; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private fun texto(c: Int, size: Float, negrita: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = c; textSize = size; textAlign = Paint.Align.CENTER
        typeface = if (negrita) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    }

    fun dibujar(cv: Canvas, p: Proyecto, v: Vista, o: Opciones, ancho: Float, alto: Float) {
        val cr = p.croquis
        if (o.cuadricula) cuadricula(cv, v, o, ancho, alto)
        val ocultas = o.capasOcultas + cr.capas.filter { !it.visible }.map { it.id }
        val elems = cr.elementos.filter { it.capaId !in ocultas }
            .sortedBy { ORDEN_CAPAS.indexOf(it.capaId).let { i -> if (i < 0) 99 else i } }
        elems.filter { it.tipo != TipoElemento.GRUA && it.tipo.forma != Forma.PUNTO && it.capaId != Elemento.CAPA_ANOTACIONES }.forEach { elemento(cv, it, v, o) }
        maniobra(cv, p, v, o)
        elems.filter { it.tipo == TipoElemento.GRUA }.forEach { elemento(cv, it, v, o) }
        elems.filter { it.tipo.forma == Forma.PUNTO || (it.capaId == Elemento.CAPA_ANOTACIONES && it.tipo != TipoElemento.GRUA) }.forEach { elemento(cv, it, v, o) }
        cr.elemento(o.seleccion)?.let { seleccion(cv, it, v, o) }
    }

    // ------------------------------------------------------------- cuadrícula y escala

    fun pasoVisible(v: Vista, o: Opciones): Double {
        var paso = o.paso
        while (paso * v.escala < 14 * o.u) paso *= if (paso.toString().startsWith("2")) 2.5 else 2.0
        return paso
    }

    private fun cuadricula(cv: Canvas, v: Vista, o: Opciones, ancho: Float, alto: Float) {
        val paso = pasoVisible(v, o)
        val fino = trazo(0x22000000, 0.6f * o.u)
        val grueso = trazo(0x3A000000, 0.9f * o.u)
        val a = v.mundo(0f, 0f)
        val b = v.mundo(ancho, alto)
        var x = floor(a.x / paso) * paso
        var n = 0
        while (x <= b.x && n < 400) {
            val gx = v.sx(x)
            cv.drawLine(gx, 0f, gx, alto, if (abs((x / (paso * 5)) - Math.round(x / (paso * 5))) < 1e-6) grueso else fino)
            x += paso; n++
        }
        var y = floor(a.y / paso) * paso
        n = 0
        while (y <= b.y && n < 400) {
            val gy = v.sy(y)
            cv.drawLine(0f, gy, ancho, gy, if (abs((y / (paso * 5)) - Math.round(y / (paso * 5))) < 1e-6) grueso else fino)
            y += paso; n++
        }
    }

    /** Barra de escala gráfica con longitud "redonda". */
    fun barraEscala(cv: Canvas, v: Vista, u: Float, x: Float, y: Float, maxPx: Float) {
        val maxM = maxPx / v.escala
        val e = 10.0.pow(floor(log10(maxM.toDouble())))
        val largo = listOf(5 * e, 2 * e, e).first { it <= maxM }
        val px = (largo * v.escala).toFloat()
        val tr = trazo(TINTA, 1.6f * u)
        val fondo = relleno(0xE6FFFFFF.toInt())
        cv.drawRoundRect(RectF(x - 6 * u, y - 18 * u, x + px + 6 * u, y + 8 * u), 4 * u, 4 * u, fondo)
        cv.drawLine(x, y, x + px, y, tr)
        cv.drawLine(x, y - 4 * u, x, y + 4 * u, tr)
        cv.drawLine(x + px, y - 4 * u, x + px, y + 4 * u, tr)
        cv.drawRect(x, y - 3 * u, x + px / 2, y, relleno(TINTA))
        cv.drawText("${Unidades.fmt(largo, 1)} m", x + px / 2, y - 6 * u, texto(TINTA, 10 * u, true))
    }

    // ------------------------------------------------------------- elementos

    private fun path(pts: List<Pt>, v: Vista, cerrar: Boolean): Path = Path().apply {
        pts.forEachIndexed { i, p -> if (i == 0) moveTo(v.sx(p.x), v.sy(p.y)) else lineTo(v.sx(p.x), v.sy(p.y)) }
        if (cerrar) close()
    }

    private fun rayado(cv: Canvas, contorno: Path, v: Vista, o: Opciones, color: Int, separacionPx: Float) {
        cv.save()
        cv.clipPath(contorno)
        val b = RectF(); contorno.computeBounds(b, true)
        val t = trazo(color, 0.8f * o.u)
        var x = b.left - b.height()
        while (x < b.right) { cv.drawLine(x, b.bottom, x + b.height(), b.top, t); x += separacionPx }
        cv.restore()
    }

    fun nombre(e: Elemento) = e.etiqueta.ifBlank { e.tipo.etiqueta }

    private fun elemento(cv: Canvas, e: Elemento, v: Vista, o: Opciones) {
        val u = o.u
        when (e.tipo.forma) {
            Forma.RECT -> rect(cv, e, v, o)
            Forma.GRUA -> grua(cv, e, v, o)
            Forma.CIRCULO -> {
                val r = (max(e.ancho, e.largo) / 2 * v.escala).toFloat().coerceAtLeast(3 * u)
                val cx = v.sx(e.x); val cy = v.sy(e.y)
                if (e.tipo == TipoElemento.ARBOL) {
                    cv.drawCircle(cx, cy, r, relleno(0x552E7D32))
                    cv.drawCircle(cx, cy, r, trazo(VERDE, 1.4f * u))
                    cv.drawCircle(cx, cy, 2 * u, relleno(MARRON))
                } else {
                    cv.drawCircle(cx, cy, r, relleno(0xFF455A64.toInt()))
                    cv.drawCircle(cx, cy, r + 2 * u, trazo(TINTA, 1f * u))
                }
                etiquetaSiCabe(cv, e, cx, cy + r + 12 * u, u, nombre(e))
            }
            Forma.PUNTO -> {
                val cx = v.sx(e.x); val cy = v.sy(e.y)
                val recog = e.tipo == TipoElemento.RECOGIDA
                val color = if (recog) VERDE else AZUL
                val r = 11 * u
                if (recog) cv.drawCircle(cx, cy, r, relleno(color))
                else cv.drawRect(cx - r, cy - r, cx + r, cy + r, relleno(color))
                cv.drawCircle(cx, cy, r + 3 * u, trazo(0xFFFFFFFF.toInt(), 2 * u))
                val tp = texto(0xFFFFFFFF.toInt(), 12 * u, true)
                cv.drawText(if (recog) "R" else "D", cx, cy + 4.5f * u, tp)
                val alt = e.alto?.let { " · h ${Unidades.fmt(it, 1)} m" } ?: ""
                etiquetaFondo(cv, nombre(e) + alt, cx, cy + r + 15 * u, u, color)
            }
            Forma.LINEA, Forma.FLECHA -> {
                val pts = Geometria.puntosAbsolutos(e)
                when (e.tipo) {
                    TipoElemento.LINEA_ELECTRICA -> {
                        e.distanciaMinima?.let { d ->
                            val banda = trazo(0x33C62828, (2 * d * v.escala).toFloat()).apply { strokeCap = Paint.Cap.ROUND }
                            cv.drawPath(path(pts, v, false), banda)
                        }
                        val t = trazo(ROJO, 2.2f * u).apply { pathEffect = DashPathEffect(floatArrayOf(10 * u, 5 * u), 0f) }
                        cv.drawPath(path(pts, v, false), t)
                        val m = medio(pts)
                        val kv = e.tensionKv?.let { " ${Unidades.fmt(it, 1)} kV" } ?: ""
                        val dm = e.distanciaMinima?.let { " · dist. mín. ${Unidades.fmt(it, 1)} m" } ?: ""
                        etiquetaFondo(cv, "⚡ ${nombre(e)}$kv$dm", v.sx(m.x), v.sy(m.y) - 10 * u, u, ROJO)
                    }
                    TipoElemento.ESLINGA -> cv.drawPath(path(pts, v, false), trazo(MARRON, 2.4f * u))
                    else -> {
                        cv.drawPath(path(pts, v, false), trazo(TINTA, 1.8f * u))
                        if (e.tipo.forma == Forma.FLECHA && pts.size >= 2) punta(cv, pts[pts.size - 2], pts.last(), v, u, TINTA)
                        if (e.etiqueta.isNotBlank()) { val m = medio(pts); etiquetaFondo(cv, e.etiqueta, v.sx(m.x), v.sy(m.y) - 8 * u, u, TINTA) }
                    }
                }
            }
            Forma.COTA -> {
                val pts = Geometria.puntosAbsolutos(e)
                if (pts.size < 2) return
                val a = pts.first(); val b = pts.last()
                val t = trazo(AZUL, 1.3f * u)
                cv.drawLine(v.sx(a.x), v.sy(a.y), v.sx(b.x), v.sy(b.y), t)
                punta(cv, b, a, v, u, AZUL); punta(cv, a, b, v, u, AZUL)
                val ang = atan2((b.y - a.y), (b.x - a.x))
                val nx = -sin(ang).toFloat() * 6 * u; val ny = cos(ang).toFloat() * 6 * u
                cv.drawLine(v.sx(a.x) - nx, v.sy(a.y) - ny, v.sx(a.x) + nx, v.sy(a.y) + ny, t)
                cv.drawLine(v.sx(b.x) - nx, v.sy(b.y) - ny, v.sx(b.x) + nx, v.sy(b.y) + ny, t)
                val d = Geometria.distancia(a, b)
                val txt = (if (e.medidaExacta) "" else "≈ ") + "${Unidades.fmt(d, 2)} m" + if (e.etiqueta.isNotBlank()) " · ${e.etiqueta}" else ""
                val m = Pt((a.x + b.x) / 2, (a.y + b.y) / 2)
                etiquetaFondo(cv, txt, v.sx(m.x), v.sy(m.y) - 8 * u, u, AZUL)
            }
            Forma.POLIGONO -> {
                val pts = Geometria.puntosAbsolutos(e)
                val pa = path(pts, v, true)
                if (e.tipo == TipoElemento.ZONA_EXCLUSION) {
                    cv.drawPath(pa, relleno(0x26C62828))
                    rayado(cv, pa, v, o, 0x66C62828, 9 * u)
                    cv.drawPath(pa, trazo(ROJO, 2f * u).apply { pathEffect = DashPathEffect(floatArrayOf(8 * u, 4 * u), 0f) })
                    val c = Geometria.caja(pts)!!.centro
                    etiquetaFondo(cv, "ZONA DE EXCLUSIÓN" + if (e.etiqueta.isNotBlank()) " · ${e.etiqueta}" else "", v.sx(c.x), v.sy(c.y), u, ROJO)
                } else {
                    cv.drawPath(pa, relleno(0x1A16263D))
                    cv.drawPath(pa, trazo(TINTA, 1.6f * u))
                    if (e.etiqueta.isNotBlank()) { val c = Geometria.caja(pts)!!.centro; etiquetaFondo(cv, e.etiqueta, v.sx(c.x), v.sy(c.y), u, TINTA) }
                }
            }
            Forma.TEXTO -> {
                val tp = texto(TINTA, 13 * u, true)
                cv.drawText(e.etiqueta.ifBlank { "Texto" }, v.sx(e.x), v.sy(e.y), tp)
            }
            Forma.SIMBOLO -> {
                val cx = v.sx(e.x); val cy = v.sy(e.y)
                val r = 10 * u
                cv.drawCircle(cx, cy, r, relleno(0xFFFFFFFF.toInt()))
                cv.drawCircle(cx, cy, r, trazo(MARRON, 1.6f * u))
                cv.drawText(if (e.tipo == TipoElemento.GANCHO) "G" else "Ω", cx, cy + 4.5f * u, texto(MARRON, 12 * u, true))
                etiquetaSiCabe(cv, e, cx, cy + r + 12 * u, u, nombre(e))
            }
        }
    }

    private fun rect(cv: Canvas, e: Elemento, v: Vista, o: Opciones) {
        val u = o.u
        val pts = Geometria.contorno(e)
        val pa = path(pts, v, true)
        val (fill, stroke, hatch) = when (e.tipo) {
            TipoElemento.EDIFICIO -> Triple(0xFFCFD8DC.toInt(), 0xFF37474F.toInt(), 0x5537474F)
            TipoElemento.MURO, TipoElemento.COLUMNA -> Triple(0xFF546E7A.toInt(), TINTA, null)
            TipoElemento.CUBIERTA -> Triple(0xFFE0E0E0.toInt(), 0xFF616161.toInt(), 0x44616161)
            TipoElemento.ESTRUCTURA_METALICA -> Triple(0x22455A64, 0xFF455A64.toInt(), null)
            TipoElemento.CALLE -> Triple(0xFFB0BEC5.toInt(), 0xFF78909C.toInt(), null)
            TipoElemento.ANDEN -> Triple(0xFFE6E2D8.toInt(), 0xFF9E9E9E.toInt(), null)
            TipoElemento.CAMION -> Triple(0xFF90A4AE.toInt(), AZUL, null)
            TipoElemento.CARGA -> Triple(0xFFFFD54F.toInt(), 0xFF8D6E00.toInt(), null)
            TipoElemento.OBSTACULO -> Triple(0x33C62828, ROJO, null)
            TipoElemento.BALANCIN -> Triple(0xFFA1887F.toInt(), MARRON, null)
            TipoElemento.ESTABILIZADOR -> Triple(0xFF616161.toInt(), TINTA, null)
            else -> Triple(0x2216263D, TINTA, null)
        }
        cv.drawPath(pa, relleno(fill))
        if (hatch != null) rayado(cv, pa, v, o, hatch, 7 * u)
        cv.drawPath(pa, trazo(stroke, 1.6f * u))
        val cx = v.sx(e.x); val cy = v.sy(e.y)
        when (e.tipo) {
            TipoElemento.ESTRUCTURA_METALICA, TipoElemento.OBSTACULO -> {
                cv.drawLine(v.sx(pts[0].x), v.sy(pts[0].y), v.sx(pts[2].x), v.sy(pts[2].y), trazo(stroke, 1.2f * u))
                cv.drawLine(v.sx(pts[1].x), v.sy(pts[1].y), v.sx(pts[3].x), v.sy(pts[3].y), trazo(stroke, 1.2f * u))
            }
            TipoElemento.CALLE -> {
                val c = Geometria.rotar(Pt(e.x, e.y - e.largo / 2 + 0.5), e.rotacion, Pt(e.x, e.y))
                val d = Geometria.rotar(Pt(e.x, e.y + e.largo / 2 - 0.5), e.rotacion, Pt(e.x, e.y))
                cv.drawLine(v.sx(c.x), v.sy(c.y), v.sx(d.x), v.sy(d.y), trazo(0xFFFFFFFF.toInt(), 1.5f * u).apply { pathEffect = DashPathEffect(floatArrayOf(10 * u, 8 * u), 0f) })
            }
            TipoElemento.CAMION -> {
                val cab = listOf(Pt(-e.ancho / 2, -e.largo / 2), Pt(e.ancho / 2, -e.largo / 2), Pt(e.ancho / 2, -e.largo / 2 + 2.0), Pt(-e.ancho / 2, -e.largo / 2 + 2.0))
                    .map { Geometria.rotar(Pt(it.x + e.x, it.y + e.y), e.rotacion, Pt(e.x, e.y)) }
                cv.drawPath(path(cab, v, true), relleno(AZUL))
            }
            TipoElemento.CARGA -> cv.drawText("C", cx, cy + 5 * u, texto(0xFF5D4600.toInt(), 13 * u, true))
            else -> {}
        }
        // Etiqueta con dimensiones
        val anchoPx = (min(e.ancho, e.largo) * v.escala).toFloat()
        val dims = if (e.tipo == TipoElemento.MURO || e.tipo == TipoElemento.COLUMNA || e.tipo == TipoElemento.ESTABILIZADOR) ""
        else " ${Unidades.fmt(e.ancho, 1)}×${Unidades.fmt(e.largo, 1)} m" + (e.alto?.let { " · h ${Unidades.fmt(it, 1)} m" } ?: "")
        val aprox = if (e.medidaExacta || dims.isBlank()) "" else " (≈)"
        if (anchoPx > 26 * u) etiquetaFondo(cv, nombre(e) + dims + aprox, cx, cy + 4 * u, u, stroke)
        else etiquetaSiCabe(cv, e, cx, cy + (max(e.ancho, e.largo) / 2 * v.escala).toFloat() + 12 * u, u, nombre(e))
    }

    private fun grua(cv: Canvas, e: Elemento, v: Vista, o: Opciones) {
        val u = o.u
        val c = Pt(e.x, e.y)
        fun r(x: Double, y: Double) = Geometria.rotar(Pt(e.x + x, e.y + y), e.rotacion, c)
        val w = e.ancho; val l = e.largo
        // Estabilizadores
        val ae = e.anchoEstabilizado
        if (ae != null && ae > w) {
            val t = trazo(0xFF424242.toInt(), 3f * u)
            for (yy in listOf(0.05 * l, 0.62 * l)) {
                val a = r(-ae / 2, yy); val b = r(ae / 2, yy)
                cv.drawLine(v.sx(a.x), v.sy(a.y), v.sx(b.x), v.sy(b.y), t)
                for (pp in listOf(a, b)) {
                    val s = (0.35 * v.escala).toFloat().coerceAtLeast(3 * u)
                    cv.drawRect(v.sx(pp.x) - s, v.sy(pp.y) - s, v.sx(pp.x) + s, v.sy(pp.y) + s, relleno(0xFF616161.toInt()))
                }
            }
        }
        val cuerpo = listOf(r(-w / 2, -0.3 * l), r(w / 2, -0.3 * l), r(w / 2, 0.7 * l), r(-w / 2, 0.7 * l))
        cv.drawPath(path(cuerpo, v, true), relleno(0xFFFFE08A.toInt()))
        cv.drawPath(path(cuerpo, v, true), trazo(AZUL, 1.8f * u))
        val cab = listOf(r(-w / 2, -0.3 * l), r(w / 2, -0.3 * l), r(w / 2, -0.11 * l), r(-w / 2, -0.11 * l))
        cv.drawPath(path(cab, v, true), relleno(AZUL))
        // Flecha de frente
        val f1 = r(0.0, -0.3 * l); val f0 = r(0.0, -0.15 * l)
        punta(cv, f0, Pt(f1.x + (f1.x - f0.x) * 0.6, f1.y + (f1.y - f0.y) * 0.6), v, u, AMBAR)
        // Columna (eje de giro)
        cv.drawCircle(v.sx(e.x), v.sy(e.y), (0.5 * v.escala).toFloat().coerceAtLeast(5 * u), relleno(AMBAR))
        cv.drawCircle(v.sx(e.x), v.sy(e.y), (0.5 * v.escala).toFloat().coerceAtLeast(5 * u), trazo(AZUL, 2f * u))
        val pie = r(0.0, 0.75 * l)
        etiquetaFondo(cv, "GRÚA · ${nombre(e).takeIf { e.etiqueta.isNotBlank() } ?: "eje de giro"}", v.sx(pie.x), v.sy(pie.y) + 12 * u, u, AZUL)
    }

    private fun medio(pts: List<Pt>): Pt {
        if (pts.size < 2) return pts.firstOrNull() ?: Pt(0.0, 0.0)
        val i = (pts.size - 1) / 2
        return Pt((pts[i].x + pts[i + 1].x) / 2, (pts[i].y + pts[i + 1].y) / 2)
    }

    private fun punta(cv: Canvas, desde: Pt, hasta: Pt, v: Vista, u: Float, color: Int) {
        val x2 = v.sx(hasta.x); val y2 = v.sy(hasta.y)
        val ang = atan2((y2 - v.sy(desde.y)).toDouble(), (x2 - v.sx(desde.x)).toDouble())
        val L = 11 * u
        val p = Path().apply {
            moveTo(x2, y2)
            lineTo((x2 - L * cos(ang - 0.42)).toFloat(), (y2 - L * sin(ang - 0.42)).toFloat())
            lineTo((x2 - L * cos(ang + 0.42)).toFloat(), (y2 - L * sin(ang + 0.42)).toFloat())
            close()
        }
        cv.drawPath(p, relleno(color))
    }

    fun etiquetaFondo(cv: Canvas, txt: String, x: Float, y: Float, u: Float, color: Int) {
        val tp = texto(color, 10.5f * u, true)
        val w = tp.measureText(txt)
        cv.drawRoundRect(RectF(x - w / 2 - 4 * u, y - 11 * u, x + w / 2 + 4 * u, y + 4 * u), 4 * u, 4 * u, relleno(0xE6FFFFFF.toInt()))
        cv.drawText(txt, x, y, tp)
    }

    private fun etiquetaSiCabe(cv: Canvas, e: Elemento, x: Float, y: Float, u: Float, txt: String) {
        if (e.etiqueta.isNotBlank() || e.tipo == TipoElemento.ARBOL || e.tipo == TipoElemento.POSTE) etiquetaFondo(cv, txt, x, y, u, TINTA)
    }

    // ------------------------------------------------------------- maniobra

    private fun maniobra(cv: Canvas, p: Proyecto, v: Vista, o: Opciones) {
        val u = o.u
        val cr = p.croquis
        val g = cr.grua
        val centro = g?.let { Pt(it.x, it.y) }
        p.maniobra.fases.forEachIndexed { i, f ->
            val res = o.faseResaltada == null || o.faseResaltada == f.id
            val alpha = if (res) 0xFF else 0x55
            val ruta = Motor.rutaFase(f, cr)
            if (centro != null && ruta.isNotEmpty()) {
                val rf = Motor.radioFase(f, cr)
                // Radio de trabajo (círculo punteado)
                rf.radio?.let { rad ->
                    val t = trazo((alpha shl 24) or 0x1E88E5, 1.2f * u).apply { pathEffect = DashPathEffect(floatArrayOf(4 * u, 4 * u), 0f) }
                    cv.drawCircle(v.sx(centro.x), v.sy(centro.y), (rad * v.escala).toFloat(), t)
                    val ang = Math.toRadians(-35.0 - i * 18)
                    val lx = v.sx(centro.x + rad * cos(ang)); val ly = v.sy(centro.y + rad * sin(ang))
                    etiquetaFondo(cv, "R${i + 1} ${if (rf.exacto) "" else "≈ "}${Unidades.fmt(rad, 2)} m", lx, ly, u, 0xFF1565C0.toInt())
                }
                // Pluma (línea continua gruesa desde el eje de giro a cada extremo)
                val tp = trazo((alpha shl 24) or 0x16263D, 3.2f * u)
                listOfNotNull(cr.elemento(f.origenId), cr.elemento(f.destinoId)).forEach { pt ->
                    cv.drawLine(v.sx(centro.x), v.sy(centro.y), v.sx(pt.x), v.sy(pt.y), tp)
                }
            }
            // Trayectoria de la carga (discontinua ámbar con flecha)
            if (ruta.size >= 2) {
                val t = trazo((alpha shl 24) or 0xE09A00, 3f * u).apply { pathEffect = DashPathEffect(floatArrayOf(12 * u, 6 * u), 0f) }
                cv.drawPath(path(ruta, v, false), t)
                punta(cv, ruta[ruta.size - 2], ruta.last(), v, u * 1.2f, (alpha shl 24) or 0xE09A00)
                f.recorrido.forEach { w -> cv.drawCircle(v.sx(w.x), v.sy(w.y), 4 * u, relleno((alpha shl 24) or 0xE09A00)) }
                val m = medio(ruta)
                etiquetaFondo(cv, "Fase ${i + 1}" + if (f.nombre.isNotBlank()) ": ${f.nombre}" else "", v.sx(m.x), v.sy(m.y) + 16 * u, u, 0xFF8D6E00.toInt())
            }
        }
    }

    // ------------------------------------------------------------- selección

    fun manijas(e: Elemento, v: Vista, u: Float): List<Manija> {
        if (e.bloqueado) return emptyList()
        return when (e.tipo.forma) {
            Forma.LINEA, Forma.FLECHA, Forma.COTA, Forma.POLIGONO ->
                Geometria.puntosAbsolutos(e).mapIndexed { i, p -> Manija(TipoManija.VERTICE, v.sx(p.x), v.sy(p.y), i) }
            Forma.PUNTO, Forma.TEXTO, Forma.SIMBOLO -> emptyList()
            Forma.CIRCULO -> {
                val r = max(e.ancho, e.largo) / 2
                listOf(Manija(TipoManija.REDIMENSIONAR, v.sx(e.x + r * 0.707), v.sy(e.y + r * 0.707)))
            }
            Forma.RECT, Forma.GRUA -> {
                val c = Geometria.contorno(e)
                val arriba = Pt((c[0].x + c[1].x) / 2, (c[0].y + c[1].y) / 2)
                val dx = arriba.x - e.x; val dy = arriba.y - e.y
                val len = Math.hypot(dx, dy).coerceAtLeast(0.01)
                val extra = 30 * u / v.escala
                val rot = Pt(arriba.x + dx / len * extra, arriba.y + dy / len * extra)
                val lista = mutableListOf(Manija(TipoManija.ROTAR, v.sx(rot.x), v.sy(rot.y)))
                if (e.tipo.forma == Forma.RECT) lista += Manija(TipoManija.REDIMENSIONAR, v.sx(c[2].x), v.sy(c[2].y))
                lista
            }
        }
    }

    private fun seleccion(cv: Canvas, e: Elemento, v: Vista, o: Opciones) {
        val u = o.u
        val t = trazo(SEL, 2f * u).apply { pathEffect = DashPathEffect(floatArrayOf(6 * u, 4 * u), 0f) }
        when (e.tipo.forma) {
            Forma.PUNTO, Forma.TEXTO, Forma.SIMBOLO -> cv.drawCircle(v.sx(e.x), v.sy(e.y), 20 * u, t)
            else -> {
                val pts = Geometria.contorno(e)
                cv.drawPath(path(pts, v, Geometria.esCerrado(e)), t)
            }
        }
        if (!o.manijas) return
        if (e.bloqueado) {
            etiquetaFondo(cv, "🔒 Bloqueado", v.sx(e.x), v.sy(e.y) - 26 * u, u, SEL)
            return
        }
        manijas(e, v, u).forEach { m ->
            if (m.tipo == TipoManija.ROTAR) {
                val c = Geometria.contorno(e)
                val a = Pt((c[0].x + c[1].x) / 2, (c[0].y + c[1].y) / 2)
                cv.drawLine(v.sx(a.x), v.sy(a.y), m.x, m.y, trazo(SEL, 1.2f * u))
            }
            cv.drawCircle(m.x, m.y, 9 * u, relleno(0xFFFFFFFF.toInt()))
            cv.drawCircle(m.x, m.y, 9 * u, trazo(SEL, 2.5f * u))
            if (m.tipo == TipoManija.ROTAR) cv.drawText("↻", m.x, m.y + 4 * u, texto(SEL, 12 * u, true))
        }
    }

    // ------------------------------------------------------------- leyenda

    data class ItemLeyenda(val simbolo: String, val texto: String, val color: Int)

    fun leyenda(p: Proyecto): List<ItemLeyenda> {
        val tipos = p.croquis.elementos.map { it.tipo }.toSet()
        val l = mutableListOf<ItemLeyenda>()
        if (TipoElemento.GRUA in tipos) l += ItemLeyenda("●", "Grúa: círculo ámbar = eje de giro; cabina azul = frente", AZUL)
        if (TipoElemento.RECOGIDA in tipos) l += ItemLeyenda("R", "Punto de recogida", VERDE)
        if (TipoElemento.DESCARGA in tipos) l += ItemLeyenda("D", "Punto de descarga", AZUL)
        if (p.maniobra.fases.isNotEmpty()) {
            l += ItemLeyenda("━", "Pluma (proyección en planta, línea continua)", AZUL)
            l += ItemLeyenda("╍▶", "Trayectoria prevista de la carga (discontinua)", 0xFFE09A00.toInt())
            l += ItemLeyenda("◌", "Radio de trabajo (≈ = medido sobre el croquis)", 0xFF1565C0.toInt())
        }
        if (TipoElemento.LINEA_ELECTRICA in tipos) l += ItemLeyenda("⚡", "Línea eléctrica y franja de distancia mínima", ROJO)
        if (TipoElemento.ZONA_EXCLUSION in tipos) l += ItemLeyenda("▨", "Zona de exclusión (rayado rojo)", ROJO)
        if (TipoElemento.EDIFICIO in tipos) l += ItemLeyenda("▤", "Edificio (h = altura)", 0xFF37474F.toInt())
        if (TipoElemento.CARGA in tipos) l += ItemLeyenda("C", "Carga", 0xFF8D6E00.toInt())
        if (TipoElemento.COTA in tipos) l += ItemLeyenda("↔", "Cota (≈ aproximada, sin ≈ = medida verificada)", AZUL)
        l += ItemLeyenda("(≈)", "Dimensión aproximada tomada del dibujo", GRIS)
        return l
    }

    // ------------------------------------------------------------- vista lateral

    /** Vista lateral ESQUEMÁTICA de una fase: no representa la geometría real de la pluma articulada. */
    fun elevacion(cv: Canvas, p: Proyecto, f: Fase, rect: RectF, u: Float) {
        val cr = p.croquis
        val g = cr.grua
        val rf = Motor.radioFase(f, cr)
        val radio = rf.radio ?: return
        val hg = f.alturaGancho ?: 0.0
        val carga = p.carga
        val altC = carga.alto ?: 1.0
        val largoC = carga.largo ?: 2.0
        // Obstáculos cruzados en planta, colocados a su distancia del eje de giro
        val ruta = Motor.rutaFase(f, cr)
        val obst = if (g == null) emptyList() else cr.elementos.filter {
            it.alto != null && it.tipo in setOf(TipoElemento.EDIFICIO, TipoElemento.MURO, TipoElemento.CUBIERTA, TipoElemento.ESTRUCTURA_METALICA, TipoElemento.OBSTACULO, TipoElemento.ARBOL, TipoElemento.POSTE) &&
                Geometria.distanciaPolilineaElemento(ruta + listOf(Pt(g.x, g.y)), it) < 0.01
        }
        val gx = g?.x ?: 0.0
        val gy = g?.y ?: 0.0
        val maxX = max(radio + largoC, obst.maxOfOrNull { Geometria.distancia(Pt(gx, gy), Pt(it.x, it.y)) + max(it.ancho, it.largo) / 2 } ?: 0.0) + 2
        val maxY = max(hg + 3, (obst.maxOfOrNull { it.alto ?: 0.0 } ?: 0.0) + 2) + 2
        val s = min((rect.width() - 30 * u) / (maxX + 4), (rect.height() - 30 * u) / maxY).toFloat()
        val x0 = rect.left + 20 * u + 4 * s
        val y0 = rect.bottom - 18 * u
        fun X(m: Double) = (x0 + m * s).toFloat()
        fun Y(m: Double) = (y0 - m * s).toFloat()
        cv.drawLine(rect.left, y0, rect.right, y0, trazo(TINTA, 1.5f * u))
        // Escala vertical
        var h = 0.0
        val tp = texto(GRIS, 8.5f * u)
        while (h <= maxY) { cv.drawLine(rect.left + 2 * u, Y(h), rect.left + 8 * u, Y(h), trazo(GRIS, 1f * u)); cv.drawText("${h.toInt()}", rect.left + 14 * u, Y(h) + 3 * u, tp); h += if (maxY > 25) 5.0 else 2.0 }
        // Obstáculos
        obst.forEach { o ->
            val gg = g ?: return@forEach
            val ao = o.alto ?: return@forEach
            val d = Geometria.distancia(Pt(gg.x, gg.y), Pt(o.x, o.y))
            val half = min(o.ancho, o.largo) / 2
            cv.drawRect(X(d - half), Y(ao), X(d + half), y0, relleno(0xFFCFD8DC.toInt()))
            cv.drawRect(X(d - half), Y(ao), X(d + half), y0, trazo(0xFF37474F.toInt(), 1.2f * u))
            etiquetaFondo(cv, "${nombre(o)} h ${Unidades.fmt(ao, 1)} m", X(d), Y(ao) - 4 * u, u * 0.9f, 0xFF37474F.toInt())
        }
        // Camión y columna esquemáticos
        cv.drawRect(X(-3.0), Y(1.2), X(3.0), y0, relleno(0xFFFFE08A.toInt()))
        cv.drawRect(X(-3.0), Y(1.2), X(3.0), y0, trazo(AZUL, 1.2f * u))
        val baseY = 2.5
        cv.drawLine(X(0.0), Y(1.2), X(0.0), Y(baseY), trazo(AZUL, 4f * u))
        // Pluma esquemática hasta la punta
        val punta = hg + 2.0
        cv.drawLine(X(0.0), Y(baseY), X(radio), Y(punta), trazo(AZUL, 3.2f * u))
        cv.drawLine(X(radio), Y(punta), X(radio), Y(hg), trazo(TINTA, 1f * u))
        // Carga
        cv.drawRect(X(radio - largoC / 2), Y(hg), X(radio + largoC / 2), Y(hg - altC), relleno(0xFFFFD54F.toInt()))
        cv.drawRect(X(radio - largoC / 2), Y(hg), X(radio + largoC / 2), Y(hg - altC), trazo(0xFF8D6E00.toInt(), 1.2f * u))
        // Cotas
        val cota = trazo(0xFF1565C0.toInt(), 1f * u)
        cv.drawLine(X(0.0), y0 + 10 * u, X(radio), y0 + 10 * u, cota)
        etiquetaFondo(cv, "Radio ${if (rf.exacto) "" else "≈ "}${Unidades.fmt(radio, 2)} m", X(radio / 2), y0 + 14 * u, u * 0.9f, 0xFF1565C0.toInt())
        if (f.alturaGancho != null) etiquetaFondo(cv, "Gancho h ${Unidades.fmt(hg, 1)} m", X(radio) + 40 * u, Y(hg), u * 0.9f, TINTA)
        val t = texto(GRIS, 8.5f * u).apply { textAlign = Paint.Align.LEFT }
        cv.drawText("VISTA LATERAL ESQUEMÁTICA — la pluma se dibuja recta; no representa la geometría real.", rect.left + 20 * u, rect.top + 10 * u, t)
    }
}
