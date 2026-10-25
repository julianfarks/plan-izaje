package co.planizaje.core.geo

import co.planizaje.core.model.Elemento
import co.planizaje.core.model.Forma
import co.planizaje.core.model.Pt
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Geometría plana en metros. Convención: x a la derecha, y hacia abajo, rotación horaria en grados. */
object Geometria {

    fun distancia(a: Pt, b: Pt): Double = hypot(b.x - a.x, b.y - a.y)

    fun rotar(p: Pt, grados: Double, centro: Pt = Pt(0.0, 0.0)): Pt {
        val r = Math.toRadians(grados)
        val dx = p.x - centro.x
        val dy = p.y - centro.y
        return Pt(centro.x + dx * cos(r) - dy * sin(r), centro.y + dx * sin(r) + dy * cos(r))
    }

    /** Ángulo (grados, 0 = hacia arriba/norte del plano, horario) del vector a→b. */
    fun rumbo(a: Pt, b: Pt): Double {
        val g = Math.toDegrees(atan2(b.x - a.x, -(b.y - a.y)))
        return (g + 360.0) % 360.0
    }

    /** Contorno del elemento en coordenadas absolutas. */
    fun contorno(e: Elemento): List<Pt> {
        val c = Pt(e.x, e.y)
        return when (e.tipo.forma) {
            Forma.GRUA -> {
                // El punto (x, y) de una grúa es su eje de giro (columna). El camión se extiende
                // 30 % del largo hacia delante (cabina) y 70 % hacia atrás.
                val hw = e.ancho / 2
                val l = e.largo
                listOf(Pt(-hw, -0.3 * l), Pt(hw, -0.3 * l), Pt(hw, 0.7 * l), Pt(-hw, 0.7 * l))
                    .map { rotar(Pt(it.x + e.x, it.y + e.y), e.rotacion, c) }
            }
            Forma.RECT, Forma.SIMBOLO, Forma.TEXTO -> {
                val hw = e.ancho / 2
                val hl = e.largo / 2
                listOf(Pt(-hw, -hl), Pt(hw, -hl), Pt(hw, hl), Pt(-hw, hl))
                    .map { rotar(Pt(it.x + e.x, it.y + e.y), e.rotacion, c) }
            }
            Forma.CIRCULO, Forma.PUNTO -> {
                val r = max(e.ancho, e.largo) / 2
                (0 until 16).map { i ->
                    val a = 2 * Math.PI * i / 16
                    Pt(e.x + r * cos(a), e.y + r * sin(a))
                }
            }
            Forma.LINEA, Forma.FLECHA, Forma.COTA, Forma.POLIGONO ->
                puntosAbsolutos(e)
        }
    }

    fun puntosAbsolutos(e: Elemento): List<Pt> {
        val c = Pt(e.x, e.y)
        val base = if (e.puntos.size >= 2) e.puntos else puntosPorDefecto(e)
        return base.map { rotar(Pt(it.x + e.x, it.y + e.y), e.rotacion, c) }
    }

    fun puntosPorDefecto(e: Elemento): List<Pt> = when (e.tipo.forma) {
        Forma.POLIGONO -> {
            val hw = e.ancho / 2
            val hl = e.largo / 2
            listOf(Pt(-hw, -hl), Pt(hw, -hl), Pt(hw, hl), Pt(-hw, hl))
        }
        else -> listOf(Pt(0.0, -e.largo / 2), Pt(0.0, e.largo / 2))
    }

    fun esCerrado(e: Elemento): Boolean = when (e.tipo.forma) {
        Forma.LINEA, Forma.FLECHA, Forma.COTA -> false
        else -> true
    }

    fun distanciaPuntoSegmento(p: Pt, a: Pt, b: Pt): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val l2 = dx * dx + dy * dy
        if (l2 == 0.0) return distancia(p, a)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / l2).coerceIn(0.0, 1.0)
        return distancia(p, Pt(a.x + t * dx, a.y + t * dy))
    }

    fun segmentosSeCortan(p1: Pt, p2: Pt, q1: Pt, q2: Pt): Boolean {
        fun orient(a: Pt, b: Pt, c: Pt): Double = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        fun enSeg(a: Pt, b: Pt, c: Pt) =
            min(a.x, b.x) - 1e-9 <= c.x && c.x <= max(a.x, b.x) + 1e-9 &&
                min(a.y, b.y) - 1e-9 <= c.y && c.y <= max(a.y, b.y) + 1e-9
        val d1 = orient(q1, q2, p1)
        val d2 = orient(q1, q2, p2)
        val d3 = orient(p1, p2, q1)
        val d4 = orient(p1, p2, q2)
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true
        if (abs(d1) < 1e-12 && enSeg(q1, q2, p1)) return true
        if (abs(d2) < 1e-12 && enSeg(q1, q2, p2)) return true
        if (abs(d3) < 1e-12 && enSeg(p1, p2, q1)) return true
        if (abs(d4) < 1e-12 && enSeg(p1, p2, q2)) return true
        return false
    }

    fun distanciaSegmentos(p1: Pt, p2: Pt, q1: Pt, q2: Pt): Double {
        if (segmentosSeCortan(p1, p2, q1, q2)) return 0.0
        return minOf(
            distanciaPuntoSegmento(p1, q1, q2), distanciaPuntoSegmento(p2, q1, q2),
            distanciaPuntoSegmento(q1, p1, p2), distanciaPuntoSegmento(q2, p1, p2),
        )
    }

    fun puntoEnPoligono(p: Pt, poly: List<Pt>): Boolean {
        if (poly.size < 3) return false
        var dentro = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.y > p.y) != (b.y > p.y) &&
                p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x
            ) dentro = !dentro
            j = i
        }
        return dentro
    }

    private fun segmentos(poly: List<Pt>, cerrado: Boolean): List<Pair<Pt, Pt>> {
        if (poly.size < 2) return emptyList()
        val s = poly.zipWithNext()
        return if (cerrado && poly.size > 2) s + (poly.last() to poly.first()) else s
    }

    /** Distancia mínima en planta entre una polilínea (recorrido) y la forma de un elemento. 0 si se tocan o solapan. */
    fun distanciaPolilineaElemento(ruta: List<Pt>, e: Elemento): Double {
        val forma = contorno(e)
        val cerrado = esCerrado(e)
        if (ruta.isEmpty() || forma.isEmpty()) return Double.POSITIVE_INFINITY
        if (cerrado && ruta.any { puntoEnPoligono(it, forma) }) return 0.0
        val segRuta = if (ruta.size == 1) listOf(ruta[0] to ruta[0]) else ruta.zipWithNext()
        var mejor = Double.POSITIVE_INFINITY
        for ((a, b) in segRuta) for ((c, d) in segmentos(forma, cerrado)) {
            mejor = min(mejor, distanciaSegmentos(a, b, c, d))
        }
        return mejor
    }

    /** Distancia mínima del punto p al elemento (0 si está dentro). Útil para seleccionar con el dedo. */
    fun distanciaPuntoElemento(p: Pt, e: Elemento): Double = distanciaPolilineaElemento(listOf(p), e)

    data class Caja(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
        val ancho get() = maxX - minX
        val alto get() = maxY - minY
        val centro get() = Pt((minX + maxX) / 2, (minY + maxY) / 2)
        fun union(o: Caja) = Caja(min(minX, o.minX), min(minY, o.minY), max(maxX, o.maxX), max(maxY, o.maxY))
        fun expandir(m: Double) = Caja(minX - m, minY - m, maxX + m, maxY + m)
    }

    fun caja(pts: List<Pt>): Caja? {
        if (pts.isEmpty()) return null
        return Caja(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
    }

    fun cajaElementos(elems: List<Elemento>): Caja? =
        elems.mapNotNull { caja(contorno(it).ifEmpty { listOf(Pt(it.x, it.y)) }) }.reduceOrNull { a, b -> a.union(b) }

    fun redondear(v: Double, paso: Double): Double = if (paso <= 0) v else Math.round(v / paso) * paso

    fun longitudPolilinea(pts: List<Pt>): Double = pts.zipWithNext().sumOf { (a, b) -> distancia(a, b) }

    fun hipotenusa(a: Double, b: Double) = sqrt(a * a + b * b)
}
