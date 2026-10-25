package co.planizaje.core.data

import co.planizaje.core.model.Accesorio
import co.planizaje.core.model.ESQUEMA_VERSION
import co.planizaje.core.model.Grua
import co.planizaje.core.model.Proyecto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

/** Envoltorio versionado que se guarda en disco. Permite migrar datos de versiones anteriores sin perderlos. */
@Serializable
data class Sobre<T>(val esquema: Int = ESQUEMA_VERSION, val dato: T)

@Serializable
data class Manifiesto(
    val app: String = "PlanIzaje",
    val esquema: Int = ESQUEMA_VERSION,
    val versionApp: String = "",
    val fecha: Long = 0L,
    val proyectos: Int = 0,
    val gruas: Int = 0,
    val accesorios: Int = 0,
    val adjuntos: Int = 0,
)

object Serializacion {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = false
        coerceInputValues = true
    }

    /**
     * Migra el JSON de un sobre a la versión actual del esquema.
     * Versión 1 es la primera; aquí se añadirán los pasos futuros (1→2, 2→3...).
     * Si el archivo es de una versión MÁS NUEVA que la app, se rechaza para no destruir datos.
     */
    fun migrar(texto: String): JsonObject {
        val obj = json.parseToJsonElement(texto).jsonObject
        val v = obj["esquema"]?.jsonPrimitive?.int ?: 1
        require(v <= ESQUEMA_VERSION) {
            "Los datos son de una versión más nueva de la app (esquema $v). Actualice la app para abrirlos."
        }
        var actual = obj
        // when (v) { 1 -> actual = migrar1a2(actual) }  // ejemplo para futuras versiones
        if (v != ESQUEMA_VERSION) actual = JsonObject(actual + ("esquema" to JsonPrimitive(ESQUEMA_VERSION)))
        return actual
    }

    fun proyectoAJson(p: Proyecto) = json.encodeToString(Sobre.serializer(Proyecto.serializer()), Sobre(dato = p))
    fun gruaAJson(g: Grua) = json.encodeToString(Sobre.serializer(Grua.serializer()), Sobre(dato = g))
    fun accesorioAJson(a: Accesorio) = json.encodeToString(Sobre.serializer(Accesorio.serializer()), Sobre(dato = a))

    fun proyectoDesde(t: String): Proyecto = json.decodeFromJsonElement(Sobre.serializer(Proyecto.serializer()), migrar(t)).dato
    fun gruaDesde(t: String): Grua = json.decodeFromJsonElement(Sobre.serializer(Grua.serializer()), migrar(t)).dato
    fun accesorioDesde(t: String): Accesorio = json.decodeFromJsonElement(Sobre.serializer(Accesorio.serializer()), migrar(t)).dato

    /**
     * Huella de los datos que influyen en el plan. Si cambia después de generar un PDF,
     * ese PDF queda marcado como desactualizado.
     */
    /** Huella sin la revisión humana: es la que se guarda al registrar una revisión. */
    fun huellaRevision(p: Proyecto, g: Grua?, accesorios: List<Accesorio>): String =
        huella(p.copy(revisionHumana = co.planizaje.core.model.RevisionHumana()), g, accesorios)

    fun huella(p: Proyecto, g: Grua?, accesorios: List<Accesorio>): String {
        val limpio = p.copy(
            historial = emptyList(), versionesPdf = emptyList(), modificado = 0L,
            pasosVistos = emptySet(), archivado = false,
        )
        val usados = accesorios.filter { it.id in p.aparejo.todosIds }.sortedBy { it.id }.map { it.copy(modificado = 0L) }
        val texto = buildString {
            append(json.encodeToString(Proyecto.serializer(), limpio))
            append('|')
            append(g?.let { json.encodeToString(Grua.serializer(), it.copy(modificado = 0L)) } ?: "-")
            usados.forEach { append('|').append(json.encodeToString(Accesorio.serializer(), it)) }
        }
        val d = MessageDigest.getInstance("SHA-256").digest(texto.toByteArray())
        return d.take(10).joinToString("") { "%02x".format(it) }
    }
}
