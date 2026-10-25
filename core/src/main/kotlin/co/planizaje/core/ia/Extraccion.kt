package co.planizaje.core.ia

import co.planizaje.core.model.Paso
import kotlinx.serialization.Serializable

/** Estado de un dato propuesto, tal como lo ve el usuario en la pantalla de revisión. */
@Serializable
enum class EstadoCampo(val etiqueta: String, val simbolo: String) {
    CONFIRMADO("Confirmado por el usuario", "✓"),
    APROXIMADO("Aproximado o pendiente de verificar", "≈"),
    NO_PROPORCIONADO("No proporcionado", "—"),
}

enum class TipoValor { TEXTO, NUMERO, ENTERO, PESO, OPCION }

/** Cómo se escribe en el proyecto: sustituye el valor anterior o se añade a un texto existente. */
enum class Escritura { REEMPLAZA, ANADE }

/** Campos del proyecto/grúa que el asistente sabe rellenar. */
@Serializable
enum class Clave(
    val etiqueta: String,
    val paso: Paso?,
    val tipo: TipoValor = TipoValor.TEXTO,
    val escritura: Escritura = Escritura.REEMPLAZA,
    val unidad: String = "",
    val grupo: String,
    /** Clave de enfoque del formulario (para llevar al usuario al campo). */
    val campo: String? = null,
) {
    // Proyecto
    NOMBRE("Nombre del proyecto", Paso.INFO, grupo = "Proyecto", campo = "nombre"),
    UBICACION("Ubicación de la obra", Paso.INFO, grupo = "Proyecto", campo = "ubicacion"),
    CLIENTE("Cliente", Paso.INFO, grupo = "Proyecto", campo = "cliente"),
    EMPRESA("Empresa responsable", Paso.INFO, grupo = "Proyecto", campo = "empresa"),
    DESCRIPCION_TRABAJO("Descripción del trabajo", Paso.INFO, escritura = Escritura.ANADE, grupo = "Proyecto", campo = "descripcion"),

    // Carga
    CARGA_DESCRIPCION("Carga", Paso.CARGA, grupo = "Carga", campo = "carga.descripcion"),
    CARGA_PESO("Peso de la carga", Paso.CARGA, TipoValor.PESO, unidad = "kg", grupo = "Carga", campo = "carga.peso"),
    CARGA_LARGO("Largo de la carga", Paso.CARGA, TipoValor.NUMERO, unidad = "m", grupo = "Carga", campo = "carga.largo"),
    CARGA_ANCHO("Ancho de la carga", Paso.CARGA, TipoValor.NUMERO, unidad = "m", grupo = "Carga", campo = "carga.ancho"),
    CARGA_ALTO("Alto de la carga", Paso.CARGA, TipoValor.NUMERO, unidad = "m", grupo = "Carga", campo = "carga.alto"),
    CARGA_CDG("Centro de gravedad", Paso.CARGA, escritura = Escritura.ANADE, grupo = "Carga"),
    CARGA_PUNTOS("Puntos de izaje", Paso.CARGA, TipoValor.ENTERO, grupo = "Carga", campo = "carga.puntos"),
    CARGA_SUJECION("Método de sujeción", Paso.CARGA, grupo = "Carga", campo = "carga.sujecion"),
    CARGA_POS_INICIAL("Punto inicial (recogida)", Paso.CARGA, grupo = "Carga", campo = "carga.posInicial"),
    CARGA_POS_FINAL("Punto final (descarga)", Paso.CARGA, grupo = "Carga", campo = "carga.posFinal"),

    // Maniobra y emplazamiento
    ALTURA_RECOGIDA("Altura de recogida", Paso.MANIOBRA, TipoValor.NUMERO, unidad = "m", grupo = "Maniobra"),
    ALTURA_DESCARGA("Altura de descarga", Paso.MANIOBRA, TipoValor.NUMERO, unidad = "m", grupo = "Maniobra"),
    RADIO("Radio de trabajo", Paso.MANIOBRA, TipoValor.NUMERO, unidad = "m", grupo = "Maniobra", campo = "fase.radio"),
    DISTANCIA_REFERENCIA("Distancia indicada", Paso.MANIOBRA, escritura = Escritura.ANADE, grupo = "Maniobra", campo = "maniobra.emplazamiento"),
    POSICION_GRUA("Posición de la grúa", Paso.CROQUIS, escritura = Escritura.ANADE, grupo = "Maniobra", campo = "maniobra.emplazamiento"),
    OBSTACULOS("Obstáculos y entorno", Paso.CROQUIS, escritura = Escritura.ANADE, grupo = "Maniobra"),
    GRUA("Grúa", Paso.GRUA, TipoValor.OPCION, grupo = "Grúa", campo = "grua"),
    CONFIG_EXTENSIONES("Extensiones en uso", Paso.GRUA, TipoValor.ENTERO, grupo = "Grúa", campo = "tabla"),
    ESTABILIZADORES("Estabilizadores", Paso.GRUA, escritura = Escritura.ANADE, grupo = "Grúa", campo = "maniobra.emplazamiento"),
    APAREJO_RAMALES("Número de ramales", Paso.MANIOBRA, TipoValor.ENTERO, grupo = "Aparejos", campo = "aparejo"),
    APAREJO_ENGANCHE("Tipo de enganche", Paso.MANIOBRA, grupo = "Aparejos", campo = "aparejo"),
    APAREJO_DESCRIPCION("Aparejos", Paso.MANIOBRA, escritura = Escritura.ANADE, grupo = "Aparejos", campo = "aparejo"),
    OPERADOR("Operador", Paso.MANIOBRA, grupo = "Procedimiento", campo = "operador"),
    SENALERO("Señalero / rigger", Paso.MANIOBRA, grupo = "Procedimiento", campo = "senalero"),
    COMUNICACION("Comunicación", Paso.MANIOBRA, grupo = "Procedimiento", campo = "comunicacion"),
    OBSERVACIONES("Observaciones y precauciones", Paso.INFO, escritura = Escritura.ANADE, grupo = "Observaciones"),
    PENDIENTES("Pendiente de confirmar", Paso.VERIFICACION, escritura = Escritura.ANADE, grupo = "Observaciones"),

    // Ficha de la grúa
    GRUA_FABRICANTE("Fabricante", null, grupo = "Ficha de la grúa"),
    GRUA_MODELO("Modelo", null, grupo = "Ficha de la grúa"),
    GRUA_VARIANTE("Variante", null, grupo = "Ficha de la grúa"),
    GRUA_SERIE("Número de serie", null, grupo = "Ficha de la grúa"),
    GRUA_PLACA("Placa del vehículo", null, grupo = "Ficha de la grúa"),
    GRUA_VEHICULO("Vehículo portador", null, grupo = "Ficha de la grúa"),
    GRUA_EXTENSIONES("Extensiones hidráulicas", null, TipoValor.ENTERO, grupo = "Ficha de la grúa"),
    GRUA_EXT_MANUALES("Extensiones manuales", null, TipoValor.ENTERO, grupo = "Ficha de la grúa"),
    ;

    val esDeGrua: Boolean get() = name.startsWith("GRUA_")
}

/** Ámbito desde el que se dicta: decide qué campos «no proporcionados» se muestran. */
enum class Ambito(val titulo: String, val claves: List<Clave>) {
    GENERAL("Todo el izaje", Clave.entries.filter { !it.esDeGrua && it != Clave.PENDIENTES }),
    INFO("Proyecto", listOf(Clave.NOMBRE, Clave.UBICACION, Clave.CLIENTE, Clave.EMPRESA, Clave.DESCRIPCION_TRABAJO, Clave.OBSERVACIONES)),
    GRUA_PASO("Grúa", listOf(Clave.GRUA, Clave.CONFIG_EXTENSIONES, Clave.ESTABILIZADORES)),
    CARGA("Carga", listOf(Clave.CARGA_DESCRIPCION, Clave.CARGA_PESO, Clave.CARGA_LARGO, Clave.CARGA_ANCHO, Clave.CARGA_ALTO, Clave.CARGA_CDG, Clave.CARGA_PUNTOS, Clave.CARGA_SUJECION, Clave.CARGA_POS_INICIAL, Clave.CARGA_POS_FINAL)),
    CROQUIS("Lugar de trabajo", listOf(Clave.POSICION_GRUA, Clave.OBSTACULOS, Clave.CARGA_POS_INICIAL, Clave.CARGA_POS_FINAL, Clave.DISTANCIA_REFERENCIA, Clave.ALTURA_RECOGIDA, Clave.ALTURA_DESCARGA)),
    MANIOBRA("Maniobra", listOf(Clave.APAREJO_RAMALES, Clave.APAREJO_ENGANCHE, Clave.APAREJO_DESCRIPCION, Clave.RADIO, Clave.ALTURA_RECOGIDA, Clave.ALTURA_DESCARGA, Clave.OPERADOR, Clave.SENALERO, Clave.COMUNICACION)),
    FICHA_GRUA("Ficha de la grúa", Clave.entries.filter { it.esDeGrua }),
}

@Serializable
data class CampoExtraido(
    val clave: Clave,
    /** Valor legible y editable (p. ej. "850 kg", "Equipo eléctrico"). */
    val valor: String,
    val estado: EstadoCampo,
    val nota: String = "",
    /** Frase original de donde salió. */
    val frase: String = "",
    /** Referencia interna (p. ej. id de la grúa) cuando el valor es una opción. */
    val ref: String? = null,
    /** Origen: "voz/texto" (reglas locales) o "ia". */
    val origen: String = "local",
)

@Serializable
data class OpcionPregunta(val etiqueta: String, val campos: List<CampoExtraido> = emptyList())

@Serializable
data class Pregunta(val id: String, val texto: String, val opciones: List<OpcionPregunta>)

@Serializable
data class Extraccion(
    val texto: String,
    val campos: List<CampoExtraido>,
    val preguntas: List<Pregunta> = emptyList(),
    val avisos: List<String> = emptyList(),
) {
    fun campo(c: Clave) = campos.firstOrNull { it.clave == c }
}
