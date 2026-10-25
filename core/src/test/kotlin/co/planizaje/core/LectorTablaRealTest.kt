package co.planizaje.core

import co.planizaje.core.ia.Fragmento
import co.planizaje.core.ia.LecturaTabla
import co.planizaje.core.ia.LectorTabla
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Regresión con el texto que el OCR del teléfono (ML Kit, emulador) leyó realmente de las fotos de placas
 * aportadas por el usuario. Mide aciertos sin exigir perfección: lo que no se lee bien debe quedar dudoso o sin emparejar.
 */
class LectorTablaRealTest {

    private fun cargar(nombre: String): List<Fragmento> {
        val txt = javaClass.getResource("/ocr/$nombre.tsv")?.readText() ?: java.io.File("src/test/resources/ocr/$nombre.tsv").readText()
        return txt.lines().drop(1).filter { it.isNotBlank() }.map { l ->
            val c = l.split("\t")
            Fragmento(c[0], c[1].toFloat(), c[2].toFloat(), c[3].toFloat(), c[4].toFloat(), c[5].toFloat().takeIf { it >= 0 })
        }
    }

    data class Medida(val aciertos: Int, val errorSinMarcar: Int, val total: Int, val l: LecturaTabla)

    /** Errores sin marcar = valores con radio esperado pero capacidad distinta y NO marcados como dudosos (los peligrosos). */
    private fun medir(nombre: String, esperado: List<Pair<Double, Double>>, fila: String? = null): Medida {
        val l = LectorTabla.leer(cargar(nombre))
        val pts = (if (fila != null) listOfNotNull(l.tablas.firstOrNull { it.nombre.contains(fila) }) else l.tablas).flatMap { it.puntos }
        val ok = esperado.count { (r, c) -> pts.any { abs(it.radio - r) < 0.01 && abs(it.capacidad - c) < 0.5 } }
        val mal = pts.count { p -> !p.dudoso && esperado.any { abs(it.first - p.radio) < 0.01 } && esperado.none { abs(it.first - p.radio) < 0.01 && abs(it.second - p.capacidad) < 0.5 } }
        println("$nombre ${fila ?: ""}: aciertos $ok/${esperado.size}, errores sin marcar $mal, modelo='${l.fabricante} ${l.modelo}', tablas=${l.tablas.map { it.nombre + ":" + it.puntos.size }}")
        return Medida(ok, mal, esperado.size, l)
    }

    @Test fun hiab111() {
        val m = medir("hiab_111_b3_clx.jpg", listOf(3.1 to 3150.0, 4.3 to 2200.0, 5.9 to 1560.0, 7.8 to 1140.0, 9.8 to 900.0, 11.8 to 680.0))
        assertTrue(m.aciertos >= 5); assertTrue(m.errorSinMarcar == 0)
        assertTrue(m.l.modelo, m.l.modelo.contains("111"))
    }

    @Test fun hiab288() {
        val m = medir("hiab_288_e8_hipro.png", listOf(2.5 to 9200.0, 4.7 to 5100.0, 6.3 to 3550.0, 8.1 to 2540.0, 10.0 to 1880.0, 12.0 to 1480.0, 14.1 to 1160.0, 16.3 to 940.0, 16.6 to 800.0, 20.8 to 700.0, 22.8 to 600.0), "ADC")
        assertTrue(m.aciertos >= 8); assertTrue(m.errorSinMarcar == 0)
        assertTrue(m.l.modelo, m.l.modelo.contains("288"))
    }

    @Test fun pk13000() {
        val m = medir("palfinger_pk13000c.jpg", listOf(4.2 to 2730.0, 6.2 to 1710.0, 8.2 to 1210.0, 10.4 to 920.0, 12.5 to 740.0))
        assertTrue(m.aciertos == 5); assertTrue(m.errorSinMarcar == 0)
        assertTrue(m.l.tablas.flatMap { it.puntos }.none { it.capacidad == 4190.0 })
        // También lee la tabla de las extensiones manuales y descarta los pesos de las extensiones (45/35/30 kg)
        assertTrue(m.l.tablas.any { t -> t.puntos.map { it.radio to it.capacidad } == listOf(14.8 to 590.0, 16.8 to 490.0, 18.8 to 370.0) })
    }

    @Test fun pk23500() {
        val m = medir("palfinger_pk23500.png", listOf(4.6 to 4520.0, 6.1 to 3180.0, 8.0 to 2280.0, 10.1 to 1700.0, 12.3 to 1340.0, 14.4 to 1110.0, 16.5 to 960.0), "E")
        assertTrue(m.aciertos == 7); assertTrue(m.errorSinMarcar == 0)
        assertTrue(m.l.tablas.flatMap { it.puntos }.none { it.capacidad == 5600.0 })
    }

    @Test fun pm85028() {
        val m = medir("pm_85028_sp_pt.jpg", listOf(2.80 to 23000.0, 4.70 to 14270.0, 6.40 to 10200.0, 8.10 to 7670.0, 10.00 to 6000.0, 11.90 to 4840.0, 14.00 to 4000.0, 16.15 to 3365.0, 18.40 to 2910.0, 20.65 to 2570.0))
        // Foto difícil: casi nada se lee bien, pero lo mal leído debe quedar marcado y no pasar como bueno.
        assertTrue(m.errorSinMarcar == 0)
        assertTrue(m.l.hayDudosos)
    }
}

