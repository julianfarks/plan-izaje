package co.losmagnificos.izaje.core

enum class Amarre(val etiqueta: String) {
    DIRECTO("Directo (ramales simples)"),
    CANASTA("En canasta"),
    AHORCADO("Ahorcado"),
}

/** Desde dónde se mide el ángulo de las eslingas. */
enum class RefAngulo(val etiqueta: String) {
    HORIZONTAL("Desde la horizontal"),
    VERTICAL("Desde la vertical"),
    SIN_CONFIRMAR("Sin confirmar"),
}

data class PuntoCapacidad(val radioM: Double, val capacidadKg: Double)

/** Claves de los campos editables (para saber qué ha corregido el usuario a mano). */
object Campo {
    const val EQUIPO = "equipo"
    const val NOMINAL = "nominal"
    const val CARGA = "carga"
    const val GANCHO = "gancho"
    const val ESLINGAS_KG = "eslingasKg"
    const val ESLINGA_UNIT_KG = "eslingaUnitKg"
    const val GRILLETES_KG = "grilletesKg"
    const val GRILLETE_UNIT_KG = "grilleteUnitKg"
    const val N_GRILLETES = "nGrilletes"
    const val BALANCIN = "balancin"
    const val OTROS = "otros"
    const val RADIO_INICIAL = "radioInicial"
    const val RADIO_FINAL = "radioFinal"
    const val RADIO_MAX = "radioMax"
    const val ALTURA = "altura"
    const val CAPACIDADES = "capacidades"
    const val N_ESLINGAS = "nEslingas"
    const val WLL = "wll"
    const val ANGULO = "angulo"
    const val REF_ANGULO = "refAngulo"
    const val RAMALES = "ramales"
    const val PORTANTES = "portantes"
    const val AMARRE = "amarre"
    const val SIN_ESLINGAS = "sinEslingas"
}

/**
 * Datos técnicos del izaje, extraídos de la descripción (y corregidos por el usuario).
 * Todos los pesos están en kg y las distancias en metros. Un valor `null` significa «no informado».
 */
data class DatosIzaje(
    val equipo: String? = null,
    val capacidadNominalKg: Double? = null,
    val cargaKg: Double? = null,
    val ganchoKg: Double? = null,
    val eslingasKg: Double? = null,
    val eslingaUnitKg: Double? = null,
    val grilletesKg: Double? = null,
    val grilleteUnitKg: Double? = null,
    val nGrilletes: Int? = null,
    val balancinKg: Double? = null,
    val otrosKg: Double? = null,
    val radioInicialM: Double? = null,
    val radioFinalM: Double? = null,
    val radioMaximoM: Double? = null,
    val alturaM: Double? = null,
    val capacidades: List<PuntoCapacidad> = emptyList(),
    val nEslingas: Int? = null,
    val wllEslingaKg: Double? = null,
    val anguloGrados: Double? = null,
    val refAngulo: RefAngulo = RefAngulo.SIN_CONFIRMAR,
    val ramales: Int? = null,
    val ramalesPortantes: Int? = null,
    val amarre: Amarre? = null,
    val sinEslingas: Boolean = false,
    /** Pesos que el operador dijo que ya están incluidos en otro valor (claves: gancho, eslingas, grilletes, balancin, otros). */
    val incluidos: Set<String> = emptySet(),
    /** Campos cuyo valor se dijo como aproximado («unos», «más o menos»…). */
    val aproximados: Set<String> = emptySet(),
    /** Campos que el usuario corrigió a mano; no se sobrescriben al volver a interpretar. */
    val editados: Set<String> = emptySet(),
    /** Frase original en la que se encontró cada dato. */
    val frases: Map<String, String> = emptyMap(),
    /** Otra información mencionada (centro de gravedad, puntos de sujeción, configuración, terreno…). */
    val notas: List<String> = emptyList(),
    val configuracion: List<String> = emptyList(),
)

data class PdfRegistro(val archivo: String, val generadoMs: Long, val estado: String, val version: Int)

data class Plan(
    val id: String,
    val creadoMs: Long,
    val modificadoMs: Long,
    val operador: String = "",
    val cedula: String = "",
    /** Fecha del trabajo en formato AAAA-MM-DD. */
    val fecha: String = "",
    val cliente: String = "",
    val sitio: String = "",
    val texto: String = "",
    val textoInterpretado: String? = null,
    val datos: DatosIzaje = DatosIzaje(),
    val observaciones: String = "",
    val pdfs: List<PdfRegistro> = emptyList(),
) {
    val tieneContenido: Boolean
        get() = operador.isNotBlank() || cedula.isNotBlank() || cliente.isNotBlank() || sitio.isNotBlank() || texto.isNotBlank()

    val estadoResumen: String get() = pdfs.lastOrNull()?.estado ?: "Sin PDF"
}
