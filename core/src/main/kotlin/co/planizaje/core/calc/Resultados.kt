package co.planizaje.core.calc

import co.planizaje.core.model.Paso

/** Calidad del dato/resultado. Nunca se comunica solo con color: cada estado tiene símbolo y texto. */
enum class EstadoDato(val etiqueta: String, val simbolo: String, val rango: Int) {
    DOCUMENTADO("Calculado con datos documentados", "✓", 0),
    REVISAR("Requiere revisión", "!", 1),
    PENDIENTE("Pendiente: faltan datos", "…", 2),
    NO_VERIFICABLE("No verificable", "✕", 3),
    ;

    companion object {
        fun peor(vararg e: EstadoDato?): EstadoDato = peor(e.filterNotNull())
        fun peor(e: Collection<EstadoDato>): EstadoDato = e.maxByOrNull { it.rango } ?: DOCUMENTADO
    }
}

/** Resultado de la comparación técnica. Es independiente de la calidad del dato. */
enum class Veredicto(val etiqueta: String, val simbolo: String) {
    CUMPLE("Dentro del límite", "✓"),
    NO_CUMPLE("Excede el límite", "✕"),
}

data class Dato(val nombre: String, val valor: String, val origen: String = "")

data class Resultado(
    val id: String,
    val grupo: String,
    val titulo: String,
    val estado: EstadoDato,
    val valor: String = "—",
    val valorNumerico: Double? = null,
    val veredicto: Veredicto? = null,
    val formula: String = "",
    val datos: List<Dato> = emptyList(),
    val hipotesis: List<String> = emptyList(),
    val fuente: String = "",
    val mensajes: List<String> = emptyList(),
    val paso: Paso = Paso.VERIFICACION,
    /**
     * false para comprobaciones orientativas (p. ej. interferencias en planta): su «requiere revisión»
     * se traslada al revisor humano y no impide marcar el plan como listo para revisión.
     * Un NO_CUMPLE o un dato PENDIENTE siempre bloquean.
     */
    val bloqueante: Boolean = true,
    /** Campo del formulario donde se corrige (para llevar al usuario directamente). */
    val campo: String? = null,
) {
    val bloquea: Boolean get() = veredicto == Veredicto.NO_CUMPLE || estado == EstadoDato.PENDIENTE || estado == EstadoDato.NO_VERIFICABLE ||
        (bloqueante && estado != EstadoDato.DOCUMENTADO)
}

data class Faltante(val paso: Paso, val texto: String, val campo: String? = null)

enum class EstadoDocumento(val etiqueta: String, val marcaAgua: String?) {
    BORRADOR_NO_CUMPLE("BORRADOR — UNA VERIFICACIÓN EXCEDE LÍMITES", "BORRADOR · NO EJECUTAR"),
    BORRADOR("BORRADOR — FALTAN DATOS O VERIFICACIONES", "BORRADOR · NO APTO PARA EJECUCIÓN"),
    LISTO_PARA_REVISION("LISTO PARA REVISIÓN — SIN REVISIÓN HUMANA REGISTRADA", "PENDIENTE DE REVISIÓN"),
    REVISADO("REVISADO — PENDIENTE DE APROBACIÓN", "PENDIENTE DE APROBACIÓN"),
    APROBACION_REGISTRADA("APROBACIÓN REGISTRADA POR EL USUARIO", null),
    ;

    val esBorrador: Boolean get() = this == BORRADOR || this == BORRADOR_NO_CUMPLE
}

data class Evaluacion(
    val resultados: List<Resultado>,
    val faltantes: List<Faltante>,
    val estadoDocumento: EstadoDocumento,
    val radioGobernante: Double?,
    val utilizacionMax: Double?,
    val masaTotal: Double?,
) {
    val puntosPorResolver: Int get() = faltantes.size + resultados.count { it.bloquea }
    fun faltantesDe(paso: Paso) = faltantes.filter { it.paso == paso }
    fun resultadosDe(paso: Paso) = resultados.filter { it.paso == paso }
}
