package co.planizaje.app.ia

import android.content.Context
import android.util.Base64
import co.planizaje.core.ia.Ambito
import co.planizaje.core.ia.CampoExtraido
import co.planizaje.core.ia.Clave
import co.planizaje.core.ia.EstadoCampo
import co.planizaje.core.ia.Extraccion
import co.planizaje.core.ia.LecturaTabla
import co.planizaje.core.ia.OpcionPregunta
import co.planizaje.core.ia.Pregunta
import co.planizaje.core.ia.TablaLeida
import co.planizaje.core.ia.TipoValor
import co.planizaje.core.model.Grua
import co.planizaje.core.model.PuntoTabla
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente del servicio de IA OPCIONAL.
 *
 * La app nunca contiene claves de API. Habla con un servidor intermedio propio del usuario
 * (ver carpeta servidor/ del repositorio), que guarda la clave del proveedor de IA como secreto.
 * La app solo guarda la dirección del servidor y un código de acceso que el usuario configura.
 * Todo lo que devuelve la IA pasa por la misma pantalla de revisión y nunca se da por verificado.
 */
class ServicioIA(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("servicio_ia", Context.MODE_PRIVATE)

    var url: String
        get() = prefs.getString("url", "") ?: ""
        set(v) { prefs.edit().putString("url", v.trim().trimEnd('/')).apply() }
    var codigo: String
        get() = prefs.getString("codigo", "") ?: ""
        set(v) { prefs.edit().putString("codigo", v.trim()).apply() }

    val configurado: Boolean get() = url.startsWith("https://") && codigo.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Serializable private data class CampoDto(val clave: String = "", val valor: String = "", val estado: String = "APROXIMADO", val nota: String = "")
    @Serializable private data class OpcionDto(val etiqueta: String = "", val campos: List<CampoDto> = emptyList())
    @Serializable private data class PreguntaDto(val texto: String = "", val opciones: List<OpcionDto> = emptyList())
    @Serializable private data class ExtraccionDto(val campos: List<CampoDto> = emptyList(), val preguntas: List<PreguntaDto> = emptyList(), val avisos: List<String> = emptyList())
    @Serializable private data class PuntoDto(val radio: Double? = null, val capacidad: Double? = null, val legible: Boolean = false)
    @Serializable private data class TablaDto(val nombre: String = "", val puntos: List<PuntoDto> = emptyList())
    @Serializable private data class LecturaDto(
        val fabricante: String = "", val modelo: String = "", val referencia: String = "", val unidades: String = "",
        val tablas: List<TablaDto> = emptyList(), val advertencias: List<String> = emptyList(),
    )

    private fun post(ruta: String, cuerpo: String): String {
        val c = URL("$url/$ruta").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 90_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("Authorization", "Bearer $codigo")
            c.outputStream.use { it.write(cuerpo.toByteArray()) }
            val code = c.responseCode
            val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) throw IllegalStateException(
                when (code) { 401, 403 -> "Código de acceso rechazado por el servidor"; 429 -> "Demasiadas solicitudes; inténtalo en un momento"; else -> "Error del servidor ($code): ${txt.take(200)}" },
            )
            return txt
        } finally { c.disconnect() }
    }

    private fun aCampo(d: CampoDto, gruas: List<Grua>): CampoExtraido? {
        val clave = Clave.entries.firstOrNull { it.name == d.clave } ?: return null
        if (d.valor.isBlank()) return null
        var estado = runCatching { EstadoCampo.valueOf(d.estado) }.getOrDefault(EstadoCampo.APROXIMADO)
        if (estado == EstadoCampo.NO_PROPORCIONADO) return null
        // Los números que propone la IA nunca entran como confirmados: los confirma el usuario.
        if (clave.tipo != TipoValor.TEXTO) estado = EstadoCampo.APROXIMADO
        var ref: String? = null
        if (clave == Clave.GRUA) {
            val dig = Regex("\\d{3,6}").findAll(d.valor).map { it.value }.toSet()
            ref = gruas.firstOrNull { g -> Regex("\\d{3,6}").findAll(g.modelo).any { it.value in dig } }?.id ?: return null
        }
        return CampoExtraido(clave, d.valor, estado, (d.nota + " (propuesto por IA)").trim(), ref = ref, origen = "ia")
    }

    suspend fun extraer(texto: String, ambito: Ambito, gruas: List<Grua>): Extraccion = withContext(Dispatchers.IO) {
        val cuerpo = buildJsonObject {
            put("texto", texto)
            put("ambito", ambito.name)
            put("claves", buildJsonArray { ambito.claves.forEach { add(kotlinx.serialization.json.JsonPrimitive("${it.name}: ${it.etiqueta}${if (it.unidad.isNotBlank()) " (${it.unidad})" else ""}")) } })
            put("gruas", buildJsonArray { gruas.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.nombreCorto)) } })
        }.toString()
        val d = json.decodeFromString(ExtraccionDto.serializer(), post("extraer", cuerpo))
        Extraccion(
            texto,
            d.campos.mapNotNull { aCampo(it, gruas) }.distinctBy { it.clave },
            d.preguntas.mapIndexed { i, q -> Pregunta("ia$i", q.texto, q.opciones.map { o -> OpcionPregunta(o.etiqueta, o.campos.mapNotNull { aCampo(it, gruas) }) }) },
            d.avisos,
        )
    }

    suspend fun leerTabla(jpeg: ByteArray, grua: Grua?): LecturaTabla = withContext(Dispatchers.IO) {
        val cuerpo = buildJsonObject {
            put("imagen", Base64.encodeToString(jpeg, Base64.NO_WRAP))
            put("mime", "image/jpeg")
            put("grua", grua?.let { "${it.fabricante} ${it.modelo} ${it.variante}".trim() } ?: "")
        }.toString()
        val d = json.decodeFromString(LecturaDto.serializer(), post("tabla", cuerpo))
        LecturaTabla(
            tablas = d.tablas.map { t ->
                val pts = t.puntos.filter { it.radio != null && it.capacidad != null && it.radio > 0 && it.capacidad > 0 }
                TablaLeida(
                    t.nombre.ifBlank { "Tabla" },
                    pts.map { PuntoTabla(it.radio!!, it.capacidad!!, dudoso = !it.legible) },
                    t.puntos.filter { it.radio == null || it.capacidad == null }.map { "${it.radio ?: "?"} m → ${it.capacidad ?: "?"} kg (ilegible)" },
                )
            },
            fabricante = d.fabricante, modelo = d.modelo, referencia = d.referencia, unidades = d.unidades,
            advertencias = d.advertencias + "Lectura hecha por un servicio de IA: compárala celda por celda con la foto antes de importarla.",
            textoCompleto = "",
        )
    }

    suspend fun probar(): String = withContext(Dispatchers.IO) { post("ping", "{}") }
}
