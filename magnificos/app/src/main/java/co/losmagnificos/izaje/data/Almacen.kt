package co.losmagnificos.izaje.data

import android.content.Context
import co.losmagnificos.izaje.core.Plan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Almacenamiento local: un archivo JSON por plan en la memoria interna de la app (sin internet ni cuentas).
 * La escritura es atómica (archivo temporal + renombrado) para no perder un plan si se corta la batería.
 */
class Almacen(private val ctx: Context) {

    private val dirPlanes = File(ctx.filesDir, "planes").apply { mkdirs() }
    val dirPdf = File(ctx.filesDir, "pdf").apply { mkdirs() }
    private val io = Executors.newSingleThreadExecutor()
    private val prefs = ctx.getSharedPreferences("izaje", Context.MODE_PRIVATE)

    private val _planes = MutableStateFlow<List<Plan>>(emptyList())
    val planes: StateFlow<List<Plan>> = _planes

    init {
        val leidos = (dirPlanes.listFiles { f -> f.extension == "json" } ?: emptyArray()).mapNotNull { f ->
            runCatching { PlanJson.deJson(JSONObject(f.readText())) }.getOrNull()
        }
        _planes.value = leidos.sortedByDescending { it.modificadoMs }
    }

    var ultimoOperador: String
        get() = prefs.getString("operador", "") ?: ""
        set(v) { prefs.edit().putString("operador", v).apply() }

    var ultimaCedula: String
        get() = prefs.getString("cedula", "") ?: ""
        set(v) { prefs.edit().putString("cedula", v).apply() }

    fun plan(id: String): Plan? = _planes.value.firstOrNull { it.id == id }

    fun guardar(p: Plan) {
        _planes.value = (_planes.value.filterNot { it.id == p.id } + p).sortedByDescending { it.modificadoMs }
        if (p.operador.isNotBlank()) ultimoOperador = p.operador
        if (p.cedula.isNotBlank()) ultimaCedula = p.cedula
        val json = PlanJson.aJson(p).toString()
        io.execute { escribir(p.id, json) }
    }

    private fun escribir(id: String, json: String) {
        runCatching {
            val destino = File(dirPlanes, "$id.json")
            val tmp = File(dirPlanes, "$id.json.tmp")
            tmp.writeText(json)
            if (!tmp.renameTo(destino)) { destino.delete(); tmp.renameTo(destino) }
        }
    }

    fun eliminar(id: String) {
        val p = plan(id)
        _planes.value = _planes.value.filterNot { it.id == id }
        io.execute {
            File(dirPlanes, "$id.json").delete()
            p?.pdfs?.forEach { File(dirPdf, it.archivo).delete() }
        }
    }

    fun archivoPdf(nombre: String): File = File(dirPdf, nombre)

    // ------------------------------------------------------------------ respaldo

    /** Todos los planes en un solo JSON (los PDF se pueden volver a generar desde los datos). */
    fun exportar(): String = JSONObject().apply {
        put("formato", "plan-izaje-los-magnificos")
        put("version", 1)
        put("planes", JSONArray().also { a -> _planes.value.forEach { a.put(PlanJson.aJson(it)) } })
    }.toString(2)

    /** Combina un respaldo con los planes actuales; si un plan existe en ambos, gana el más reciente. Devuelve cuántos se incorporaron. */
    fun importar(texto: String): Int {
        val o = JSONObject(texto)
        val a = o.getJSONArray("planes")
        var n = 0
        for (i in 0 until a.length()) {
            val p = PlanJson.deJson(a.getJSONObject(i))
            val actual = plan(p.id)
            if (actual == null || p.modificadoMs > actual.modificadoMs) {
                // los PDF del respaldo no viajan: se quitan las referencias que no existan en este teléfono
                val existentes = p.pdfs.filter { archivoPdf(it.archivo).exists() }
                guardar(p.copy(pdfs = existentes))
                n++
            }
        }
        return n
    }
}
