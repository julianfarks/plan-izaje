package co.losmagnificos.izaje.core

enum class EstadoPlan(val etiqueta: String, val resumen: String) {
    PRELIMINAR("PRELIMINAR – BORRADOR", "Preliminar"),
    PARA_REVISION("PARA REVISIÓN – NO APROBADO", "Para revisión"),
}

/**
 * Un dato que falta o que debe confirmarse.
 * [critico] = indispensable para evaluar la maniobra: mientras falte, el plan sale como PRELIMINAR.
 * [clave] identifica el campo para poder pedirlo en la pantalla de revisión.
 */
data class Faltante(val clave: String, val texto: String, val critico: Boolean)

data class Analisis(
    val peso: PesoTotal,
    val filas: List<FilaRadio>,
    val eslingas: ResultadoEslingas,
    val faltantes: List<Faltante>,
    /** Condiciones que exceden lo informado (capacidad de la grúa o de las eslingas). */
    val alertas: List<String>,
    val estado: EstadoPlan,
) {
    val criticos: List<Faltante> get() = faltantes.filter { it.critico }
    val hayExcedencias: Boolean get() = alertas.isNotEmpty()
}

object ClavesFaltante {
    const val OPERADOR = "operador"
    const val CEDULA = "cedula"
    const val CLIENTE = "cliente"
    const val SITIO = "sitio"
    const val CARGA = "carga"
    const val EQUIPO = "equipo"
    const val RADIO = "radio"
    const val CAPACIDAD = "capacidad:"       // seguido del radio
    const val ESLINGAS = "eslingas"
    const val WLL = "wll"
    const val ANGULO = "angulo"
    const val REF_ANGULO = "refAngulo"
    const val GANCHO = "gancho"
    const val PESO_ESLINGAS = "pesoEslingas"
    const val AMARRE = "amarre"
    const val TABLA = "tablaOficial"
    const val CONFIGURACION = "configuracion"
    const val APROX = "aprox:"
}

object Analizador {

    fun analizar(plan: Plan): Analisis {
        val d = plan.datos
        val peso = Calculos.pesoTotal(d)
        val filas = Calculos.filasRadios(d, peso.totalKg)
        val esl = Calculos.eslingas(d, peso.totalKg)
        val falt = mutableListOf<Faltante>()
        val alertas = mutableListOf<String>()

        // Datos del trabajo
        if (plan.operador.isBlank()) falt += Faltante(ClavesFaltante.OPERADOR, "Nombre del operador", true)
        if (plan.cedula.isBlank()) falt += Faltante(ClavesFaltante.CEDULA, "Cédula del operador", true)
        if (plan.cliente.isBlank()) falt += Faltante(ClavesFaltante.CLIENTE, "Nombre del cliente", true)
        if (plan.sitio.isBlank()) falt += Faltante(ClavesFaltante.SITIO, "Sitio o ubicación del trabajo", true)

        // Maniobra
        if (d.cargaKg == null) falt += Faltante(ClavesFaltante.CARGA, "Peso de la carga", true)
        if (d.equipo.isNullOrBlank()) falt += Faltante(ClavesFaltante.EQUIPO, "Grúa que se va a usar (marca y modelo)", true)
        if (filas.isEmpty()) falt += Faltante(ClavesFaltante.RADIO, "Radio de trabajo (dónde recoges y dónde colocas la carga)", true)
        filas.filter { it.capacidadKg == null }.forEach {
            falt += Faltante(
                ClavesFaltante.CAPACIDAD + Formato.editable(it.radioM),
                "Capacidad de la grúa a ${Formato.num(it.radioM, 2)} m de radio (según su tabla de cargas)", true,
            )
        }

        // Eslingas
        if (!d.sinEslingas) {
            if (d.nEslingas == null) falt += Faltante(ClavesFaltante.ESLINGAS, "Número de eslingas (o indica que no usas)", true)
            if (d.wllEslingaKg == null) falt += Faltante(ClavesFaltante.WLL, "Capacidad de cada eslinga (según su etiqueta)", true)
            val portantes = Calculos.ramalesPortantes(d)?.first
            val necesitaAngulo = portantes != null && portantes >= 2
            if (necesitaAngulo && d.anguloGrados == null) falt += Faltante(ClavesFaltante.ANGULO, "Ángulo de las eslingas (en grados)", true)
            if (d.anguloGrados != null && d.refAngulo == RefAngulo.SIN_CONFIRMAR && necesitaAngulo)
                falt += Faltante(ClavesFaltante.REF_ANGULO, "¿El ángulo se mide desde la horizontal o desde la vertical?", true)
            if (d.nEslingas != null && d.amarre == null) falt += Faltante(ClavesFaltante.AMARRE, "Tipo de amarre (directo, canasta o ahorcado): se calculó como ramales directos", false)
        }

        // Datos que no bloquean pero deben quedar visibles
        if (d.ganchoKg == null && "gancho" !in d.incluidos) falt += Faltante(ClavesFaltante.GANCHO, "Peso del gancho: no se sumó al peso total", false)
        if (!d.sinEslingas && d.nEslingas != null && "eslingas" !in d.incluidos &&
            d.eslingasKg == null && (d.eslingaUnitKg == null)
        ) falt += Faltante(ClavesFaltante.PESO_ESLINGAS, "Peso de las eslingas: no se sumó al peso total", false)
        if (d.configuracion.isEmpty()) falt += Faltante(ClavesFaltante.CONFIGURACION, "Configuración de la grúa: estabilizadores, extensiones de pluma y sector de trabajo", false)
        falt += Faltante(ClavesFaltante.TABLA, "Confirmar las capacidades con la tabla de cargas oficial de la grúa y su configuración real", false)
        d.aproximados.sorted().forEach { falt += Faltante(ClavesFaltante.APROX + it, "Dato dicho como aproximado: ${nombreCampo(it)}", false) }

        // Excedencias
        filas.filter { it.estado == EstadoFila.EXCEDE }.forEach {
            alertas += "EXCEDE la capacidad informada a ${Formato.num(it.radioM, 2)} m: utilización ${Formato.pct(it.utilizacionPct)}."
        }
        if (esl.excede) alertas += "La carga por eslinga EXCEDE la capacidad indicada de las eslingas."

        val estado = if (falt.any { it.critico } || alertas.isNotEmpty()) EstadoPlan.PRELIMINAR else EstadoPlan.PARA_REVISION
        return Analisis(peso, filas, esl, falt, alertas, estado)
    }

    fun nombreCampo(clave: String): String = when (clave) {
        Campo.CARGA -> "peso de la carga"
        Campo.GANCHO -> "peso del gancho"
        Campo.ESLINGAS_KG, Campo.ESLINGA_UNIT_KG -> "peso de las eslingas"
        Campo.GRILLETES_KG, Campo.GRILLETE_UNIT_KG -> "peso de los grilletes"
        Campo.BALANCIN -> "peso del balancín"
        Campo.OTROS -> "peso de otros accesorios"
        Campo.RADIO_INICIAL -> "radio inicial"
        Campo.RADIO_FINAL -> "radio final"
        Campo.RADIO_MAX -> "radio máximo"
        Campo.ALTURA -> "altura"
        Campo.WLL -> "capacidad de las eslingas"
        Campo.N_ESLINGAS -> "número de eslingas"
        Campo.ANGULO -> "ángulo de las eslingas"
        Campo.NOMINAL -> "capacidad nominal"
        Campo.CAPACIDADES -> "capacidad de la grúa por radio"
        else -> clave
    }
}
