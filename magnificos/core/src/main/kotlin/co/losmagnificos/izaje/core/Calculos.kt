package co.losmagnificos.izaje.core

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class LineaPeso(val concepto: String, val kg: Double, val nota: String? = null)

data class PesoTotal(
    val lineas: List<LineaPeso>,
    /** Suma de todo lo suspendido; `null` si falta el peso de la carga. */
    val totalKg: Double?,
    /** Conceptos que no se sumaron porque el operador dijo que ya estaban incluidos en otro valor. */
    val incluidosEnOtro: List<String>,
    /** Conceptos sin peso informado (no se sumaron ni se inventaron). */
    val sinDato: List<String>,
)

enum class EstadoFila { DENTRO, EXCEDE, SIN_CAPACIDAD, SIN_PESO }

data class FilaRadio(
    val radioM: Double,
    val etiqueta: String,
    val capacidadKg: Double?,
    val pesoKg: Double?,
    val utilizacionPct: Double?,
    val margenKg: Double?,
    val estado: EstadoFila,
    val critico: Boolean = false,
)

enum class EstadoEslingas { CALCULADA, CONDICIONADA, NO_CALCULABLE }

data class ResultadoEslingas(
    val estado: EstadoEslingas,
    val ramalesPortantes: Int = 0,
    /** Ángulo con la horizontal usado en el cálculo (el más desfavorable si la referencia no está confirmada). */
    val anguloHorizontal: Double? = null,
    /** Tensión estimada en cada ramal (kg-fuerza). */
    val tensionRamalKg: Double? = null,
    /** Carga que debe resistir cada eslinga (1 ramal en directo; 2 ramales en canasta). */
    val cargaPorEslingaKg: Double? = null,
    val capacidadIndicadaKg: Double? = null,
    val utilizacionPct: Double? = null,
    val excede: Boolean = false,
    /** Tensión mínima posible (ramales verticales). */
    val tensionMinimaKg: Double? = null,
    /** Ángulo mínimo con la horizontal para no superar la capacidad indicada. */
    val anguloMinimoGrados: Double? = null,
    /** Pares (ángulo con la horizontal, tensión por ramal) a modo de referencia cuando falta el ángulo. */
    val referencias: List<Pair<Double, Double>> = emptyList(),
    val referenciaPendiente: Boolean = false,
    val notas: List<String> = emptyList(),
)

object Calculos {

    private const val EPS = 1e-6

    // ------------------------------------------------------------------ peso total

    fun pesoTotal(d: DatosIzaje): PesoTotal {
        val lineas = mutableListOf<LineaPeso>()
        val incl = mutableListOf<String>()
        val sin = mutableListOf<String>()

        fun anadir(clave: String, concepto: String, kg: Double?, nota: String? = null) {
            when {
                clave in d.incluidos -> incl += concepto
                kg == null -> sin += concepto
                else -> lineas += LineaPeso(concepto, kg, nota)
            }
        }

        d.cargaKg?.let { lineas += LineaPeso("Carga", it) }
        anadir("gancho", "Gancho", d.ganchoKg)
        val eslingas = d.eslingasKg ?: if (d.eslingaUnitKg != null && d.nEslingas != null) d.eslingaUnitKg * d.nEslingas else null
        val notaEsl = if (d.eslingasKg == null && eslingas != null) "${Formato.num(d.eslingaUnitKg, 3)} kg × ${d.nEslingas}" else null
        anadir("eslingas", "Eslingas", eslingas, notaEsl)
        val grilletes = d.grilletesKg ?: if (d.grilleteUnitKg != null && d.nGrilletes != null) d.grilleteUnitKg * d.nGrilletes else null
        // Los grilletes, el balancín y otros accesorios solo cuentan como «sin dato» si se mencionaron.
        val mencionaGrilletes = d.grilletesKg != null || d.grilleteUnitKg != null || d.nGrilletes != null
        if (mencionaGrilletes) anadir("grilletes", "Grilletes", grilletes)
        if (d.balancinKg != null) anadir("balancin", "Balancín / barra separadora", d.balancinKg)
        if (d.otrosKg != null) anadir("otros", "Otros accesorios", d.otrosKg)

        val total = if (d.cargaKg == null) null else lineas.sumOf { it.kg }
        return PesoTotal(lineas, total, incl, sin)
    }

    // ------------------------------------------------------------------ utilización

    fun utilizacionPct(pesoKg: Double, capacidadKg: Double): Double = pesoKg / capacidadKg * 100.0

    /** Capacidad informada exactamente para ese radio (sin interpolar ni inventar valores). */
    fun capacidadEn(d: DatosIzaje, radioM: Double): Double? =
        d.capacidades.lastOrNull { abs(it.radioM - radioM) < EPS }?.capacidadKg

    /**
     * Radios a evaluar: inicial, final y máximo, más los radios con capacidad informada que queden entre ellos.
     * La capacidad se toma solo de los valores informados para ese radio exacto.
     */
    fun filasRadios(d: DatosIzaje, pesoKg: Double?): List<FilaRadio> {
        val etiquetas = linkedMapOf<Double, MutableList<String>>()
        fun poner(r: Double?, e: String) {
            if (r == null) return
            val clave = etiquetas.keys.firstOrNull { abs(it - r) < EPS } ?: r
            etiquetas.getOrPut(clave) { mutableListOf() }.add(e)
        }
        poner(d.radioInicialM, "inicial (recogida)")
        poner(d.radioFinalM, "final (colocación)")
        poner(d.radioMaximoM, "máximo")
        val extremos = etiquetas.keys.toList()
        if (extremos.size >= 2) {
            val lo = extremos.min()
            val hi = extremos.max()
            d.capacidades.map { it.radioM }.filter { it > lo + EPS && it < hi - EPS }.forEach { poner(it, "intermedio (tabla)") }
        }
        val radios = etiquetas.keys.sorted()
        val filas = radios.map { r ->
            val cap = capacidadEn(d, r)
            val et = etiquetas.getValue(r).joinToString(" / ")
            when {
                cap == null -> FilaRadio(r, et, null, pesoKg, null, null, EstadoFila.SIN_CAPACIDAD)
                pesoKg == null -> FilaRadio(r, et, cap, null, null, null, EstadoFila.SIN_PESO)
                else -> {
                    val u = utilizacionPct(pesoKg, cap)
                    FilaRadio(r, et, cap, pesoKg, u, cap - pesoKg, if (u > 100.0 + EPS) EstadoFila.EXCEDE else EstadoFila.DENTRO)
                }
            }
        }
        val minCap = filas.mapNotNull { it.capacidadKg }.minOrNull()
        return filas.map { it.copy(critico = minCap != null && it.capacidadKg != null && abs(it.capacidadKg - minCap) < EPS) }
    }

    // ------------------------------------------------------------------ eslingas

    private fun grados(rad: Double) = Math.toDegrees(rad)

    /** Tensión por ramal con ángulo `alfa` medido desde la horizontal: T = W / (n · sen α). */
    fun tensionRamal(pesoKg: Double, ramales: Int, anguloHorizontalGrados: Double): Double =
        pesoKg / (ramales * sin(Math.toRadians(anguloHorizontalGrados)))

    /** Ramales totales y portantes según eslingas, tipo de amarre y lo indicado por el usuario. `null` si falta el número de eslingas. */
    fun ramalesPortantes(d: DatosIzaje): Pair<Int, String?>? {
        val n = d.nEslingas ?: return null
        if (n < 1) return null
        val canasta = d.amarre == Amarre.CANASTA
        val totales = d.ramales ?: if (canasta) 2 * n else n
        return when {
            d.ramalesPortantes != null -> d.ramalesPortantes.coerceIn(1, totales) to null
            totales > 2 -> 2 to "Con $totales ramales se consideran solo 2 portantes, porque el reparto entre más ramales no está garantizado. Si hay un balancín o ecualizador que lo asegure, indícalo."
            else -> totales to null
        }
    }

    fun eslingas(d: DatosIzaje, pesoKg: Double?): ResultadoEslingas {
        val notas = mutableListOf<String>()
        if (d.sinEslingas) return ResultadoEslingas(EstadoEslingas.NO_CALCULABLE, notas = listOf("El operador indicó que no se usan eslingas."))
        if (pesoKg == null) return ResultadoEslingas(EstadoEslingas.NO_CALCULABLE, notas = listOf("Falta el peso de la carga."))
        val n = d.nEslingas ?: return ResultadoEslingas(EstadoEslingas.NO_CALCULABLE, notas = listOf("Falta el número de eslingas."))
        if (n < 1) return ResultadoEslingas(EstadoEslingas.NO_CALCULABLE, notas = listOf("El número de eslingas no es válido."))

        val canasta = d.amarre == Amarre.CANASTA
        val (portantes, notaPortantes) = ramalesPortantes(d) ?: return ResultadoEslingas(EstadoEslingas.NO_CALCULABLE, notas = listOf("Falta el número de eslingas."))
        notaPortantes?.let { notas += it }
        val ramalesPorEslinga = if (canasta) 2 else 1
        val wll = d.wllEslingaKg
        notas += "Modelo ideal: ramales simétricos que se reparten la carga por igual y gancho sobre el centro de gravedad. Si la geometría o el centro de gravedad no lo cumplen, las tensiones reales pueden ser mayores."
        notas += "La capacidad de cada eslinga se compara por separado con la carga que le corresponde; las capacidades no se suman. Verifica la etiqueta, el tipo de amarre y el ángulo."
        if (d.amarre == Amarre.AHORCADO) notas += "En ahorcado la capacidad se reduce: usa el valor de ahorcado de la etiqueta de la eslinga."
        if (canasta) notas += "En canasta cada eslinga trabaja con dos ramales: se compara con su capacidad en canasta de la etiqueta."

        // Un solo ramal portante: vertical, el ángulo no interviene.
        if (portantes == 1) {
            val t = pesoKg
            val carga = t * ramalesPorEslinga
            val util = wll?.let { carga / it * 100.0 }
            return ResultadoEslingas(
                EstadoEslingas.CALCULADA, 1, 90.0, t, carga, wll, util, util != null && util > 100.0 + EPS,
                notas = notas + "Un solo ramal portante: la tensión es igual al peso suspendido.",
            )
        }

        val ang = d.anguloGrados
        if (ang == null) {
            val minima = pesoKg / portantes
            val cargaMin = minima * ramalesPorEslinga
            val refs = listOf(60.0, 45.0, 30.0).map { it to tensionRamal(pesoKg, portantes, it) }
            var angMin: Double? = null
            var excede = false
            if (wll != null) {
                val ratio = pesoKg * ramalesPorEslinga / (portantes * wll)
                if (ratio > 1.0 + EPS) {
                    excede = true
                    notas += "Aun con los ramales verticales la carga por eslinga supera la capacidad indicada."
                } else {
                    angMin = grados(asin(min(1.0, ratio)))
                }
            }
            notas += "Falta el ángulo de las eslingas: no se puede dar una tensión definitiva. Se muestra la tensión mínima posible y valores de referencia."
            return ResultadoEslingas(
                EstadoEslingas.CONDICIONADA, portantes, null, null, null, wll, null, excede, minima, angMin, refs, false,
                notas + (if (wll == null) "Falta la capacidad de las eslingas." else ""),
            ).let { it.copy(utilizacionPct = wll?.let { w -> cargaMin / w * 100.0 }) }
        }

        // Ángulo informado: se necesita saber desde dónde se mide.
        val candidatos: List<Double> = when (d.refAngulo) {
            RefAngulo.HORIZONTAL -> listOf(ang)
            RefAngulo.VERTICAL -> listOf(90.0 - ang)
            RefAngulo.SIN_CONFIRMAR -> listOf(ang, 90.0 - ang)
        }
        if (candidatos.any { it <= 0.0 || it > 90.0 + EPS }) {
            return ResultadoEslingas(EstadoEslingas.NO_CALCULABLE, portantes, notas = notas + "El ángulo informado no es válido (debe estar entre 0° y 90°).")
        }
        val alfa = candidatos.minOrNull()!!   // el más desfavorable: mayor tensión
        val pendiente = d.refAngulo == RefAngulo.SIN_CONFIRMAR && abs(candidatos[0] - candidatos[1]) > EPS
        val t = tensionRamal(pesoKg, portantes, alfa)
        val carga = t * ramalesPorEslinga
        val util = wll?.let { carga / it * 100.0 }
        if (pendiente) notas += "No se confirmó si el ángulo de ${Formato.num(ang, 1)}° se mide desde la horizontal o desde la vertical: se usó la interpretación más desfavorable (${Formato.num(alfa, 1)}° con la horizontal)."
        if (alfa < 30.0) notas += "Ángulo menor de 30° con la horizontal: la tensión es mucho mayor que el peso. Consulta la etiqueta y el manual del aparejo; normalmente no se admite."
        else if (alfa < 45.0) notas += "Ángulo menor de 45° con la horizontal: la tensión aumenta mucho. Verifica que el aparejo lo admita."
        if (wll == null) notas += "Falta la capacidad de las eslingas para compararla con la tensión."
        return ResultadoEslingas(
            if (pendiente) EstadoEslingas.CONDICIONADA else EstadoEslingas.CALCULADA,
            portantes, alfa, t, carga, wll, util, util != null && util > 100.0 + EPS,
            referenciaPendiente = pendiente, notas = notas,
        )
    }

    /** Mayor entre dos valores opcionales (utilidad para resúmenes). */
    fun mayor(a: Double?, b: Double?): Double? = if (a == null) b else if (b == null) a else max(a, b)
}
