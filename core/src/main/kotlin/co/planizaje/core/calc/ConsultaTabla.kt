package co.planizaje.core.calc

import co.planizaje.core.model.Interpolacion
import co.planizaje.core.model.PuntoTabla
import co.planizaje.core.model.TablaCarga

enum class TipoConsulta { EN_TABLA, FUERA_MAYOR, FUERA_MENOR, SIN_DATOS, RADIO_INVALIDO }

data class Consulta(
    val tipo: TipoConsulta,
    val capacidad: Double? = null,
    val radioInferior: Double? = null,
    val radioSuperior: Double? = null,
    val alturaMax: Double? = null,
    val explicacion: String = "",
)

object ConsultaTabla {
    private const val EPS = 1e-6

    /** Elimina puntos inválidos y, en radios repetidos, conserva la menor capacidad. */
    fun normalizar(puntos: List<PuntoTabla>): List<PuntoTabla> =
        puntos.filter { it.radio.isFinite() && it.capacidad.isFinite() && it.radio >= 0 && it.capacidad >= 0 }
            .groupBy { it.radio }
            .map { (_, g) -> g.minBy { it.capacidad } }
            .sortedBy { it.radio }

    fun capacidad(tabla: TablaCarga, radio: Double): Consulta {
        if (!radio.isFinite() || radio < 0) return Consulta(TipoConsulta.RADIO_INVALIDO, explicacion = "Radio no válido")
        val pts = normalizar(tabla.puntos)
        if (pts.isEmpty()) return Consulta(TipoConsulta.SIN_DATOS, explicacion = "La tabla no tiene valores")
        val primero = pts.first()
        val ultimo = pts.last()
        if (radio > ultimo.radio + EPS) {
            return Consulta(
                TipoConsulta.FUERA_MAYOR, radioInferior = ultimo.radio,
                explicacion = "El radio supera el máximo tabulado (${ultimo.radio} m): la tabla no autoriza este radio.",
            )
        }
        if (radio < primero.radio - EPS) {
            return Consulta(
                TipoConsulta.FUERA_MENOR, radioSuperior = primero.radio,
                explicacion = "El radio es menor que el mínimo tabulado (${primero.radio} m): consulte el diagrama del fabricante.",
            )
        }
        val exacto = pts.firstOrNull { kotlin.math.abs(it.radio - radio) <= EPS }
        if (exacto != null) {
            return Consulta(
                TipoConsulta.EN_TABLA, exacto.capacidad, exacto.radio, exacto.radio, exacto.alturaMax,
                "Valor tabulado exacto para ${exacto.radio} m.",
            )
        }
        val sup = pts.first { it.radio > radio }
        val inf = pts.last { it.radio < radio }
        val alt = listOfNotNull(inf.alturaMax, sup.alturaMax).minOrNull()
        return when (tabla.interpolacion) {
            Interpolacion.RADIO_SUPERIOR -> {
                val cap = minOf(inf.capacidad, sup.capacidad)
                Consulta(
                    TipoConsulta.EN_TABLA, cap, inf.radio, sup.radio, alt,
                    "Sin interpolar: se toma el menor valor entre los radios tabulados ${inf.radio} m y ${sup.radio} m " +
                        "(equivale a usar el radio superior cuando la capacidad disminuye con el radio).",
                )
            }
            Interpolacion.LINEAL -> {
                val t = (radio - inf.radio) / (sup.radio - inf.radio)
                val cap = inf.capacidad + t * (sup.capacidad - inf.capacidad)
                Consulta(
                    TipoConsulta.EN_TABLA, cap, inf.radio, sup.radio, alt,
                    "Interpolación lineal entre ${inf.radio} m y ${sup.radio} m (configurada como permitida por el fabricante).",
                )
            }
        }
    }
}
