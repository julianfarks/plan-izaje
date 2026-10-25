package co.planizaje.core.calc

import co.planizaje.core.model.Aparejo
import co.planizaje.core.model.Enganche
import co.planizaje.core.model.MetodoAngulo
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sqrt

data class ResultadoEslingas(
    val ok: Boolean,
    val error: String? = null,
    val anguloVertical: Double? = null,
    val ramalesPortantes: Int = 0,
    val factorAngulo: Double? = null,
    /** Tensión máxima en un ramal (kg-fuerza). */
    val tensionRamal: Double? = null,
    /** Tensiones individuales cuando hay reparto asimétrico. */
    val tensiones: List<Double> = emptyList(),
    /** Carga que debe resistir cada eslinga según su enganche (kg). */
    val cargaPorEslinga: Double? = null,
    val hipotesis: List<String> = emptyList(),
    val avisos: List<String> = emptyList(),
)

object CalculoEslingas {

    const val ANGULO_MAX_HABITUAL = 60.0

    fun ramalesPortantes(a: Aparejo): Pair<Int, String?> = when {
        a.ramales <= 2 -> a.ramales.coerceAtLeast(1) to null
        a.ramalesPortantes != null -> a.ramalesPortantes.coerceIn(1, a.ramales) to
            "Se consideran ${a.ramalesPortantes} ramales portantes de ${a.ramales}, según lo indicado por el usuario (debe estar justificado: balancín, ecualizador o reparto garantizado)."
        else -> 2 to "Con ${a.ramales} ramales se considera, de forma conservadora, que solo 2 soportan la carga, porque el reparto entre más ramales no está garantizado."
    }

    /** Ángulo respecto a la vertical a partir de longitud de ramal y separación entre puntos opuestos. */
    fun anguloDesdeGeometria(longitud: Double, separacion: Double): Double? {
        if (longitud <= 0 || separacion < 0) return null
        val s = (separacion / 2) / longitud
        if (s >= 1.0) return null
        return Math.toDegrees(asin(s))
    }

    /**
     * @param pesoKg masa que cuelga de las eslingas (kg)
     * @param cdgX desplazamiento del centro de gravedad respecto al centro entre puntos (m), solo 2 ramales
     */
    fun calcular(a: Aparejo, pesoKg: Double?, cdgX: Double? = null): ResultadoEslingas {
        if (pesoKg == null) return ResultadoEslingas(false, "Falta el peso de la carga")
        if (pesoKg <= 0) return ResultadoEslingas(false, "El peso debe ser mayor que cero")
        if (a.ramales !in 1..4) return ResultadoEslingas(false, "Número de ramales no válido (1 a 4)")
        val hip = mutableListOf<String>()
        val avisos = mutableListOf<String>()

        val angulo: Double = if (a.ramales == 1) {
            hip += "Un solo ramal vertical: ángulo 0°."
            0.0
        } else when (a.metodoAngulo) {
            MetodoAngulo.ANGULO_DIRECTO -> a.anguloVertical
                ?: return ResultadoEslingas(false, "Falta el ángulo de los ramales respecto a la vertical")
            MetodoAngulo.GEOMETRIA -> {
                val l = a.longitudRamal ?: return ResultadoEslingas(false, "Falta la longitud del ramal")
                val s = a.separacionPuntos ?: return ResultadoEslingas(false, "Falta la separación entre puntos de izaje")
                anguloDesdeGeometria(l, s)
                    ?: return ResultadoEslingas(false, "La eslinga es demasiado corta para esa separación (longitud ≤ separación/2)")
            }
        }
        if (angulo < 0 || angulo >= 90) return ResultadoEslingas(false, "El ángulo debe estar entre 0° y 90° respecto a la vertical")
        if (angulo > ANGULO_MAX_HABITUAL) {
            avisos += "Ángulo de ${"%.1f".format(angulo)}° respecto a la vertical (más de 60°): la mayoría de fabricantes y normas no lo admiten. Consulte la etiqueta y el manual del aparejo."
        }

        val (n, hipN) = ramalesPortantes(a)
        hipN?.let { hip += it }
        val cosA = cos(Math.toRadians(angulo))
        val factor = 1.0 / cosA
        hip += "Todos los ramales tienen el mismo ángulo y el gancho está sobre el centro de gravedad."

        var tensiones: List<Double>
        if (a.ramales == 2 && cdgX != null && a.separacionPuntos != null && a.separacionPuntos > 0) {
            val s = a.separacionPuntos
            val da = s / 2 + cdgX
            val db = s / 2 - cdgX
            if (da <= 0 || db <= 0) return ResultadoEslingas(false, "El centro de gravedad queda fuera de los puntos de izaje: la carga volcaría")
            val v1 = pesoKg * db / s
            val v2 = pesoKg * da / s
            tensiones = if (a.metodoAngulo == MetodoAngulo.GEOMETRIA && a.longitudRamal != null) {
                val h = sqrt(a.longitudRamal * a.longitudRamal - (s / 2) * (s / 2))
                val t1 = v1 / cos(atan(da / h))
                val t2 = v2 / cos(atan(db / h))
                hip += "Reparto asimétrico por centro de gravedad desplazado ${"%.2f".format(cdgX)} m; gancho sobre el centro de gravedad."
                listOf(t1, t2)
            } else {
                hip += "Reparto asimétrico por centro de gravedad desplazado; se aplica el mismo ángulo a ambos ramales."
                listOf(v1 * factor, v2 * factor)
            }
        } else {
            val t = pesoKg / (n * cosA)
            tensiones = List(n) { t }
        }
        val tMax = tensiones.max()
        val porEslinga = if (a.enganche == Enganche.CANASTA) {
            hip += "En canasta cada eslinga forma dos ramales: se compara 2 × tensión de ramal con su WLL en canasta."
            tMax * 2
        } else tMax
        if (a.enganche == Enganche.AHORCADO) {
            hip += "En ahorcado se usa el WLL de ahorcado de la etiqueta (no se aplica ningún factor inventado)."
        }
        return ResultadoEslingas(
            ok = true, anguloVertical = angulo, ramalesPortantes = n, factorAngulo = factor,
            tensionRamal = tMax, tensiones = tensiones, cargaPorEslinga = porEslinga,
            hipotesis = hip, avisos = avisos,
        )
    }

    fun eslingasNecesarias(a: Aparejo): Int =
        if (a.enganche == Enganche.CANASTA) (a.ramales + 1) / 2 else a.ramales
}
