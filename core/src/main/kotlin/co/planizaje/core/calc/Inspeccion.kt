package co.planizaje.core.calc

import co.planizaje.core.model.Accesorio
import co.planizaje.core.model.Enganche
import co.planizaje.core.model.EstadoInspeccion
import java.time.LocalDate

object Inspeccion {
    fun fecha(s: String): LocalDate? = runCatching { LocalDate.parse(s.trim()) }.getOrNull()

    fun vencida(a: Accesorio, hoy: LocalDate): Boolean {
        val p = fecha(a.proximaInspeccion) ?: return false
        return p.isBefore(hoy)
    }

    /** Problemas que impiden aprobar el uso de un accesorio, al margen de su capacidad. */
    fun problemas(a: Accesorio, hoy: LocalDate): List<String> = buildList {
        when (a.estado) {
            EstadoInspeccion.NO_APTO -> add("Marcado como NO APTO / fuera de servicio")
            EstadoInspeccion.PENDIENTE -> add("Inspección pendiente")
            EstadoInspeccion.APTO -> {}
        }
        if (vencida(a, hoy)) add("Inspección vencida (próxima: ${a.proximaInspeccion})")
        if (a.proximaInspeccion.isNotBlank() && fecha(a.proximaInspeccion) == null) add("Fecha de próxima inspección no válida (use AAAA-MM-DD)")
        if (a.identificacion.isBlank()) add("Sin identificación")
        if (a.numeroCertificado.isBlank() && a.certificado == null) add("Sin certificado registrado")
    }
}

enum class Aptitud(val etiqueta: String, val simbolo: String) {
    APTO("Compatible con los datos registrados", "✓"),
    FALTA_DATO("Falta información para decidir", "…"),
    NO_APTO("No compatible", "✕"),
}

data class Candidato(val accesorio: Accesorio, val aptitud: Aptitud, val wll: Double?, val motivos: List<String>)

object Candidatos {
    /**
     * Clasifica accesorios de la biblioteca frente a la carga requerida.
     * La capacidad requerida se compara SOLO con el WLL de la configuración de uso; nunca con la carga de rotura.
     */
    fun clasificar(
        lista: List<Accesorio>,
        filtro: (Accesorio) -> Boolean,
        requeridoKg: Double?,
        enganche: Enganche,
        hoy: LocalDate,
        longitudDeseada: Double? = null,
    ): List<Candidato> = lista.filter(filtro).map { a ->
        val wll = a.wll(enganche)
        val motivos = Inspeccion.problemas(a, hoy).toMutableList()
        val aptitud = when {
            a.estado == EstadoInspeccion.NO_APTO || Inspeccion.vencida(a, hoy) -> Aptitud.NO_APTO
            wll == null -> { motivos += "Sin WLL registrado para ${if (a.tipo.esEslinga) enganche.etiqueta.lowercase() else "uso directo"}"; Aptitud.FALTA_DATO }
            requeridoKg == null -> { motivos += "Aún no se conoce la carga requerida"; Aptitud.FALTA_DATO }
            wll < requeridoKg -> { motivos += "WLL insuficiente: ${"%.0f".format(wll)} kg < ${"%.0f".format(requeridoKg)} kg requeridos"; Aptitud.NO_APTO }
            a.estado == EstadoInspeccion.PENDIENTE -> Aptitud.FALTA_DATO
            else -> Aptitud.APTO
        }
        Candidato(a, aptitud, wll, motivos)
    }.sortedWith(
        compareBy<Candidato> { it.aptitud.ordinal }
            .thenBy { c -> if (longitudDeseada != null && c.accesorio.longitud != null) kotlin.math.abs(c.accesorio.longitud - longitudDeseada) else 0.0 }
            .thenBy { it.wll ?: Double.MAX_VALUE },
    )
}
