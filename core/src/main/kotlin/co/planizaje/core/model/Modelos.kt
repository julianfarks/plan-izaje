package co.planizaje.core.model

import kotlinx.serialization.Serializable

/**
 * Modelos de datos de PlanIzaje.
 *
 * Reglas de diseño:
 *  - Todas las magnitudes se guardan en unidades SI base de la app: metros (m) y kilogramos (kg).
 *  - Un valor desconocido es `null`, nunca 0 ni una estimación silenciosa.
 *  - Los datos técnicos de grúas y accesorios llevan su fuente documental.
 */

const val ESQUEMA_VERSION = 1

@Serializable
data class Adjunto(
    val id: String,
    val nombre: String,
    /** Ruta relativa dentro del almacenamiento interno de la app (carpeta adjuntos/). */
    val archivo: String,
    val mime: String = "application/octet-stream",
    val descripcion: String = "",
    val fecha: Long = 0L,
) {
    val esImagen: Boolean get() = mime.startsWith("image/")
    val esPdf: Boolean get() = mime == "application/pdf"
}

/** Referencia a un documento técnico (manual, diagrama de carga, certificado). */
@Serializable
data class FuenteTecnica(
    val documento: String = "",
    val paginaOFigura: String = "",
    val revisionDocumento: String = "",
    val adjuntoId: String? = null,
) {
    val completa: Boolean get() = documento.isNotBlank() && paginaOFigura.isNotBlank()
    fun texto(): String = buildString {
        append(documento.ifBlank { "Documento sin identificar" })
        if (paginaOFigura.isNotBlank()) append(", ").append(paginaOFigura)
        if (revisionDocumento.isNotBlank()) append(" (rev. ").append(revisionDocumento).append(")")
    }
}

/** Constancia de que una persona contrastó un dato con su documento original. */
@Serializable
data class Verificacion(
    val verificado: Boolean = false,
    val porQuien: String = "",
    val fecha: String = "",
) {
    val valida: Boolean get() = verificado && porQuien.isNotBlank()
}

// ---------------------------------------------------------------- GRÚAS

@Serializable
enum class TipoGrua(val etiqueta: String) {
    ARTICULADA("Grúa articulada sobre camión (brazo articulado)"),
    TELESCOPICA_CAMION("Grúa telescópica sobre camión"),
    TODO_TERRENO("Grúa móvil todo terreno"),
    ORUGAS("Grúa sobre orugas"),
    OTRA("Otro tipo"),
}

@Serializable
data class ConfigEstabilizadores(
    val id: String,
    val nombre: String = "",
    /** Distancia transversal total entre apoyos (m), según manual. */
    val anchoApoyos: Double? = null,
    /** Distancia longitudinal entre apoyos delanteros y traseros (m), según manual. */
    val largoApoyos: Double? = null,
    /** Fuerza máxima por apoyo documentada por el fabricante (kN). */
    val fuerzaMaxApoyoKn: Double? = null,
    val fuente: FuenteTecnica = FuenteTecnica(),
)

@Serializable
enum class Interpolacion(val etiqueta: String) {
    RADIO_SUPERIOR("Usar el radio tabulado inmediatamente superior (conservador)"),
    LINEAL("Interpolación lineal (solo si el fabricante la permite)"),
}

@Serializable
data class PuntoTabla(
    /** Radio de trabajo (m). */
    val radio: Double,
    /** Capacidad nominal (kg) en ese radio. */
    val capacidad: Double,
    /** Altura máxima de gancho en ese radio (m), opcional. */
    val alturaMax: Double? = null,
)

@Serializable
data class TablaCarga(
    val id: String,
    val nombre: String = "",
    /** Descripción de la configuración de pluma: p. ej. "6 extensiones hidráulicas, sin plumín". */
    val configuracionPluma: String = "",
    val extensiones: Int? = null,
    val conPlumin: Boolean = false,
    val estabilizadoresId: String? = null,
    val sector: String = "",
    val puntos: List<PuntoTabla> = emptyList(),
    val interpolacion: Interpolacion = Interpolacion.RADIO_SUPERIOR,
    /**
     * Indica si la capacidad tabulada YA descuenta gancho/accesorios del fabricante.
     * null = no se sabe (debe revisarse en el diagrama).
     */
    val descuentaGancho: Boolean? = null,
    val fuente: FuenteTecnica = FuenteTecnica(),
    val verificacion: Verificacion = Verificacion(),
    val notas: String = "",
) {
    val ordenados: List<PuntoTabla> get() = puntos.sortedBy { it.radio }
}

@Serializable
data class Grua(
    val id: String,
    val fabricante: String = "",
    val modelo: String = "",
    val variante: String = "",
    val numeroSerie: String = "",
    val identificacionInterna: String = "",
    val tipo: TipoGrua = TipoGrua.ARTICULADA,
    val vehiculo: String = "",
    val placaVehiculo: String = "",
    val extensionesHidraulicas: Int? = null,
    val extensionesManuales: Int? = null,
    val plumin: String = "",
    val contrapeso: String = "",
    val longitudesPluma: String = "",
    val sectoresPermitidos: String = "",
    val estabilizadores: List<ConfigEstabilizadores> = emptyList(),
    val tablas: List<TablaCarga> = emptyList(),
    /** Peso del gancho/bloque propio de la grúa (kg), si consta en documentación. */
    val pesoGanchoKg: Double? = null,
    /** Velocidad de viento máxima de trabajo según fabricante (m/s). */
    val vientoMaxMs: Double? = null,
    val restricciones: String = "",
    val documentos: List<Adjunto> = emptyList(),
    val notas: String = "",
    val creado: Long = 0L,
    val modificado: Long = 0L,
) {
    val nombreCorto: String
        get() = listOf(fabricante, modelo, variante).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { "Grúa sin nombre" }
    val identificada: Boolean
        get() = fabricante.isNotBlank() && modelo.isNotBlank() &&
            (numeroSerie.isNotBlank() || identificacionInterna.isNotBlank())
}

// ---------------------------------------------------------------- ACCESORIOS

@Serializable
enum class TipoAccesorio(val etiqueta: String, val esEslinga: Boolean = false) {
    ESLINGA_TEXTIL("Eslinga textil", true),
    ESLINGA_CABLE("Eslinga de cable", true),
    ESLINGA_CADENA("Eslinga de cadena", true),
    GRILLETE("Grillete"),
    GANCHO("Gancho"),
    ESLABON_MAESTRO("Eslabón maestro / argolla"),
    BALANCIN("Balancín"),
    VIGA_IZAJE("Viga de izaje"),
    OTRO("Otro accesorio certificado"),
}

@Serializable
enum class EstadoInspeccion(val etiqueta: String) {
    APTO("Apto"),
    NO_APTO("No apto / fuera de servicio"),
    PENDIENTE("Inspección pendiente"),
}

@Serializable
enum class Enganche(val etiqueta: String) {
    VERTICAL("Vertical (directo)"),
    AHORCADO("Ahorcado (estrangulado)"),
    CANASTA("Canasta (cesta)"),
}

@Serializable
data class Accesorio(
    val id: String,
    val tipo: TipoAccesorio = TipoAccesorio.ESLINGA_TEXTIL,
    val fabricante: String = "",
    val modelo: String = "",
    val identificacion: String = "",
    val longitud: Double? = null,
    val pesoKg: Double? = null,
    /** WLL (carga de trabajo límite) en kg para enganche vertical / uso directo, según etiqueta o certificado. */
    val wllVertical: Double? = null,
    val wllAhorcado: Double? = null,
    val wllCanasta: Double? = null,
    /** Carga de rotura mínima (kg). SOLO informativa: nunca se usa como capacidad. */
    val cargaRotura: Double? = null,
    val configuracionPermitida: String = "",
    val numeroCertificado: String = "",
    val fechaInspeccion: String = "",
    val proximaInspeccion: String = "",
    val estado: EstadoInspeccion = EstadoInspeccion.PENDIENTE,
    val fotos: List<Adjunto> = emptyList(),
    val certificado: Adjunto? = null,
    val observaciones: String = "",
    val creado: Long = 0L,
    val modificado: Long = 0L,
) {
    fun wll(enganche: Enganche): Double? = when {
        !tipo.esEslinga -> wllVertical
        enganche == Enganche.VERTICAL -> wllVertical
        enganche == Enganche.AHORCADO -> wllAhorcado
        else -> wllCanasta
    }
    val nombreCorto: String
        get() = buildString {
            append(tipo.etiqueta)
            if (identificacion.isNotBlank()) append(" · ").append(identificacion)
        }
}

// ---------------------------------------------------------------- CARGA

@Serializable
enum class OrigenPeso(val etiqueta: String, val documentado: Boolean) {
    DOCUMENTADO("Dato del fabricante / placa / plano", true),
    PESADO("Pesado en báscula certificada", true),
    CALCULADO("Calculado por volumen y densidad", false),
    ESTIMADO("Estimado", false),
    DESCONOCIDO("Desconocido", false),
}

@Serializable
data class Carga(
    val descripcion: String = "",
    val pesoKg: Double? = null,
    val origenPeso: OrigenPeso = OrigenPeso.DESCONOCIDO,
    val referenciaPeso: String = "",
    val largo: Double? = null,
    val ancho: Double? = null,
    val alto: Double? = null,
    val cdgConocido: Boolean = false,
    /** Centro de gravedad respecto al centro geométrico (m). */
    val cdgX: Double? = null,
    val cdgY: Double? = null,
    val cdgZ: Double? = null,
    val numeroPuntosIzaje: Int? = null,
    val wllPuntoIzajeKg: Double? = null,
    val descripcionPuntos: String = "",
    val metodoSujecion: String = "",
    val posicionInicial: String = "",
    val posicionFinal: String = "",
    val fotos: List<Adjunto> = emptyList(),
    val documentos: List<Adjunto> = emptyList(),
    val observaciones: String = "",
)

// ---------------------------------------------------------------- CROQUIS

@Serializable
data class Pt(val x: Double, val y: Double)

@Serializable
enum class TipoElemento(
    val etiqueta: String,
    val categoria: String,
    val forma: Forma,
    val anchoDef: Double,
    val largoDef: Double,
) {
    EDIFICIO("Edificio", "Estructuras", Forma.RECT, 10.0, 15.0),
    MURO("Muro", "Estructuras", Forma.RECT, 0.3, 8.0),
    COLUMNA("Columna", "Estructuras", Forma.RECT, 0.5, 0.5),
    CUBIERTA("Cubierta", "Estructuras", Forma.RECT, 8.0, 10.0),
    ESTRUCTURA_METALICA("Estructura metálica", "Estructuras", Forma.RECT, 6.0, 12.0),
    CALLE("Calle", "Entorno", Forma.RECT, 7.0, 30.0),
    ANDEN("Andén / acera", "Entorno", Forma.RECT, 2.0, 30.0),
    CAMION("Camión", "Vehículos", Forma.RECT, 2.5, 9.0),
    GRUA("Grúa (camión grúa)", "Vehículos", Forma.GRUA, 2.5, 10.0),
    ESTABILIZADOR("Estabilizador / placa de apoyo", "Vehículos", Forma.RECT, 0.6, 0.6),
    CARGA("Carga", "Maniobra", Forma.RECT, 2.0, 3.0),
    RECOGIDA("Punto de recogida", "Maniobra", Forma.PUNTO, 1.0, 1.0),
    DESCARGA("Punto de descarga", "Maniobra", Forma.PUNTO, 1.0, 1.0),
    OBSTACULO("Obstáculo", "Entorno", Forma.RECT, 2.0, 2.0),
    ARBOL("Árbol", "Entorno", Forma.CIRCULO, 4.0, 4.0),
    POSTE("Poste", "Entorno", Forma.CIRCULO, 0.4, 0.4),
    LINEA_ELECTRICA("Línea eléctrica", "Riesgos", Forma.LINEA, 0.0, 20.0),
    ZONA_EXCLUSION("Zona de exclusión", "Riesgos", Forma.POLIGONO, 6.0, 6.0),
    ESLINGA("Eslinga", "Aparejos", Forma.LINEA, 0.0, 3.0),
    GRILLETE("Grillete", "Aparejos", Forma.SIMBOLO, 0.5, 0.5),
    GANCHO("Gancho", "Aparejos", Forma.SIMBOLO, 0.6, 0.6),
    BALANCIN("Balancín", "Aparejos", Forma.RECT, 0.3, 3.0),
    TEXTO("Anotación", "Dibujo", Forma.TEXTO, 0.0, 0.0),
    COTA("Cota / medida", "Dibujo", Forma.COTA, 0.0, 5.0),
    LINEA("Línea", "Dibujo", Forma.LINEA, 0.0, 5.0),
    FLECHA("Flecha", "Dibujo", Forma.FLECHA, 0.0, 5.0),
    POLIGONO("Polígono libre", "Dibujo", Forma.POLIGONO, 4.0, 4.0),
}

@Serializable
enum class Forma { RECT, CIRCULO, PUNTO, LINEA, FLECHA, COTA, POLIGONO, TEXTO, SIMBOLO, GRUA }

@Serializable
data class Capa(
    val id: String,
    val nombre: String,
    val visible: Boolean = true,
    val bloqueada: Boolean = false,
)

@Serializable
data class Elemento(
    val id: String,
    val tipo: TipoElemento,
    val capaId: String = CAPA_GENERAL,
    /** Centro del elemento en metros (plano). Para líneas/polígonos es el origen de [puntos]. */
    val x: Double = 0.0,
    val y: Double = 0.0,
    val ancho: Double = tipo.anchoDef,
    val largo: Double = tipo.largoDef,
    val alto: Double? = null,
    /** Rotación en grados, sentido horario. */
    val rotacion: Double = 0.0,
    val etiqueta: String = "",
    val bloqueado: Boolean = false,
    /** true si las dimensiones fueron introducidas como medidas verificadas en campo. */
    val medidaExacta: Boolean = false,
    /** Puntos relativos a (x, y) para líneas, flechas, cotas y polígonos. */
    val puntos: List<Pt> = emptyList(),
    /** Tensión nominal (kV) para líneas eléctricas. */
    val tensionKv: Double? = null,
    /** Distancia mínima de seguridad exigida (m) para líneas eléctricas/zonas. */
    val distanciaMinima: Double? = null,
    /** Ancho total con estabilizadores extendidos (m), solo para grúas. */
    val anchoEstabilizado: Double? = null,
) {
    companion object {
        const val CAPA_GENERAL = "general"
        const val CAPA_MANIOBRA = "maniobra"
        const val CAPA_RIESGOS = "riesgos"
        const val CAPA_ANOTACIONES = "anotaciones"
    }
}

@Serializable
data class Croquis(
    val elementos: List<Elemento> = emptyList(),
    val capas: List<Capa> = capasPorDefecto(),
    val cuadricula: Boolean = true,
    val ajusteCuadricula: Boolean = false,
    val pasoCuadricula: Double = 1.0,
    val mostrarLeyenda: Boolean = true,
    val nota: String = "",
) {
    fun elemento(id: String?): Elemento? = elementos.firstOrNull { it.id == id }
    val grua: Elemento? get() = elementos.firstOrNull { it.tipo == TipoElemento.GRUA }

    companion object {
        fun capasPorDefecto() = listOf(
            Capa(Elemento.CAPA_GENERAL, "Entorno y estructuras"),
            Capa(Elemento.CAPA_MANIOBRA, "Grúa y maniobra"),
            Capa(Elemento.CAPA_RIESGOS, "Riesgos y exclusión"),
            Capa(Elemento.CAPA_ANOTACIONES, "Anotaciones y cotas"),
        )
    }
}

// ---------------------------------------------------------------- MANIOBRA

@Serializable
data class Fase(
    val id: String,
    val nombre: String = "",
    val origenId: String? = null,
    val destinoId: String? = null,
    /** Radio medido/verificado (m). Si es null se usa el radio aproximado del croquis. */
    val radioMedido: Double? = null,
    val alturaGancho: Double? = null,
    /** Puntos intermedios del recorrido de la carga, en coordenadas absolutas del plano (m). */
    val recorrido: List<Pt> = emptyList(),
    val descripcion: String = "",
)

@Serializable
enum class MetodoAngulo { ANGULO_DIRECTO, GEOMETRIA }

@Serializable
data class Aparejo(
    val ramales: Int = 2,
    val enganche: Enganche = Enganche.VERTICAL,
    val metodoAngulo: MetodoAngulo = MetodoAngulo.ANGULO_DIRECTO,
    /** Ángulo de cada ramal medido desde la vertical (grados). */
    val anguloVertical: Double? = null,
    /** Longitud de cada ramal (m) para calcular el ángulo. */
    val longitudRamal: Double? = null,
    /** Distancia horizontal entre puntos de izaje opuestos (m). */
    val separacionPuntos: Double? = null,
    /** Ramales que se consideran portantes cuando hay 3 o 4 (criterio conservador). */
    val ramalesPortantes: Int? = null,
    val eslingaIds: List<String> = emptyList(),
    val grilleteIds: List<String> = emptyList(),
    val ganchoIds: List<String> = emptyList(),
    val otrosIds: List<String> = emptyList(),
    val notas: String = "",
) {
    val todosIds: List<String> get() = eslingaIds + grilleteIds + ganchoIds + otrosIds
}

@Serializable
data class Maniobra(
    val fases: List<Fase> = emptyList(),
    val tablaId: String? = null,
    val estabilizadoresId: String? = null,
    /** Criterio de utilización de la empresa (%) si existe, p. ej. 75. null = solo límite del fabricante. */
    val limiteUtilizacionEmpresa: Double? = null,
    /** Dimensiones de la placa/almohadilla de apoyo (m) y presión admisible del terreno (kPa). */
    val placaAncho: Double? = null,
    val placaLargo: Double? = null,
    val presionAdmisibleKpa: Double? = null,
    val fuentePresionAdmisible: String = "",
    val alturaPluma: Double? = null,
)

// ---------------------------------------------------------------- PROCEDIMIENTO

@Serializable
data class ItemCheck(val texto: String, val marcado: Boolean = false)

@Serializable
data class Peligro(val nombre: String, val medidas: String, val aplica: Boolean = false)

@Serializable
data class Procedimiento(
    val secuencia: String = "",
    val operador: String = "",
    val senalero: String = "",
    val supervisor: String = "",
    val aparejador: String = "",
    val otrosResponsables: String = "",
    val comunicacion: String = "",
    val senalizacion: String = "",
    val delimitacion: String = "",
    val peligros: List<Peligro> = Plantillas.peligros(),
    val meteorologia: String = "",
    val criteriosSuspension: String = Plantillas.CRITERIOS_SUSPENSION,
    val checklist: List<ItemCheck> = Plantillas.checklist(),
)

// ---------------------------------------------------------------- PROYECTO

@Serializable
enum class Paso(val numero: Int, val titulo: String, val opcional: Boolean) {
    INFO(1, "Información del proyecto", false),
    GRUA(2, "Grúa", false),
    CARGA(3, "Carga", false),
    CROQUIS(4, "Lugar de trabajo", false),
    MANIOBRA(5, "Maniobra", false),
    VERIFICACION(6, "Verificación técnica", false),
    PDF(7, "Plan PDF", false),
}

@Serializable
data class Cambio(
    val fecha: Long,
    val descripcion: String,
    val revision: Int,
)

@Serializable
data class VersionPdf(
    val id: String,
    val archivo: String,
    val fecha: Long,
    val revision: Int,
    val numero: Int,
    val borrador: Boolean,
    val huellaDatos: String,
    val nota: String = "",
)

@Serializable
data class RevisionHumana(
    val revisadoPor: String = "",
    val fechaRevision: String = "",
    val confirmaRevision: Boolean = false,
    val aprobadoPor: String = "",
    val fechaAprobacion: String = "",
    val confirmaAprobacion: Boolean = false,
    val comentarios: String = "",
    /** Huella de los datos en el momento de registrar la revisión; si cambian, la revisión deja de valer. */
    val huellaDatos: String = "",
)

@Serializable
data class Proyecto(
    val id: String,
    val nombre: String = "",
    val cliente: String = "",
    val empresa: String = "",
    val ubicacion: String = "",
    val fecha: String = "",
    val revision: Int = 0,
    val descripcion: String = "",
    val elaboradoPor: String = "",
    val revisorPrevisto: String = "",
    val aprobadorPrevisto: String = "",
    val gruaId: String? = null,
    val carga: Carga = Carga(),
    val croquis: Croquis = Croquis(),
    val maniobra: Maniobra = Maniobra(),
    val aparejo: Aparejo = Aparejo(),
    val procedimiento: Procedimiento = Procedimiento(),
    val revisionHumana: RevisionHumana = RevisionHumana(),
    val observaciones: String = "",
    val advertencias: String = "",
    val adjuntos: List<Adjunto> = emptyList(),
    val historial: List<Cambio> = emptyList(),
    val versionesPdf: List<VersionPdf> = emptyList(),
    val pasosVistos: Set<Paso> = emptySet(),
    val archivado: Boolean = false,
    val creado: Long = 0L,
    val modificado: Long = 0L,
) {
    val titulo: String get() = nombre.ifBlank { "Proyecto sin nombre" }
}
