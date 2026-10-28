package co.losmagnificos.izaje.data

import co.losmagnificos.izaje.core.Amarre
import co.losmagnificos.izaje.core.DatosIzaje
import co.losmagnificos.izaje.core.PdfRegistro
import co.losmagnificos.izaje.core.Plan
import co.losmagnificos.izaje.core.PuntoCapacidad
import co.losmagnificos.izaje.core.RefAngulo
import org.json.JSONArray
import org.json.JSONObject

/** Guardado de los planes como JSON (org.json de Android, sin dependencias externas). */
object PlanJson {

    private fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun JSONObject.ent(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null
    private fun JSONObject.txt(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONArray.textos(): List<String> = (0 until length()).map { getString(it) }
    private fun List<String>.json(): JSONArray = JSONArray().also { a -> forEach { a.put(it) } }

    fun aJson(p: Plan): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("creadoMs", p.creadoMs)
        put("modificadoMs", p.modificadoMs)
        put("operador", p.operador)
        put("cedula", p.cedula)
        put("fecha", p.fecha)
        put("cliente", p.cliente)
        put("sitio", p.sitio)
        put("texto", p.texto)
        putOpt("textoInterpretado", p.textoInterpretado)
        put("datos", datosAJson(p.datos))
        put("observaciones", p.observaciones)
        put("pdfs", JSONArray().also { a ->
            p.pdfs.forEach { r ->
                a.put(JSONObject().put("archivo", r.archivo).put("generadoMs", r.generadoMs).put("estado", r.estado).put("version", r.version))
            }
        })
    }

    fun deJson(o: JSONObject): Plan = Plan(
        id = o.getString("id"),
        creadoMs = o.optLong("creadoMs"),
        modificadoMs = o.optLong("modificadoMs"),
        operador = o.optString("operador"),
        cedula = o.optString("cedula"),
        fecha = o.optString("fecha"),
        cliente = o.optString("cliente"),
        sitio = o.optString("sitio"),
        texto = o.optString("texto"),
        textoInterpretado = o.txt("textoInterpretado"),
        datos = o.optJSONObject("datos")?.let { datosDeJson(it) } ?: DatosIzaje(),
        observaciones = o.optString("observaciones"),
        pdfs = o.optJSONArray("pdfs")?.let { a ->
            (0 until a.length()).map { i ->
                val r = a.getJSONObject(i)
                PdfRegistro(r.getString("archivo"), r.optLong("generadoMs"), r.optString("estado"), r.optInt("version", 1))
            }
        } ?: emptyList(),
    )

    private fun datosAJson(d: DatosIzaje): JSONObject = JSONObject().apply {
        putOpt("equipo", d.equipo)
        putOpt("capacidadNominalKg", d.capacidadNominalKg)
        putOpt("cargaKg", d.cargaKg)
        putOpt("ganchoKg", d.ganchoKg)
        putOpt("eslingasKg", d.eslingasKg)
        putOpt("eslingaUnitKg", d.eslingaUnitKg)
        putOpt("grilletesKg", d.grilletesKg)
        putOpt("grilleteUnitKg", d.grilleteUnitKg)
        putOpt("nGrilletes", d.nGrilletes)
        putOpt("balancinKg", d.balancinKg)
        putOpt("otrosKg", d.otrosKg)
        putOpt("radioInicialM", d.radioInicialM)
        putOpt("radioFinalM", d.radioFinalM)
        putOpt("radioMaximoM", d.radioMaximoM)
        putOpt("alturaM", d.alturaM)
        put("capacidades", JSONArray().also { a -> d.capacidades.forEach { a.put(JSONObject().put("r", it.radioM).put("c", it.capacidadKg)) } })
        putOpt("nEslingas", d.nEslingas)
        putOpt("wllEslingaKg", d.wllEslingaKg)
        putOpt("anguloGrados", d.anguloGrados)
        put("refAngulo", d.refAngulo.name)
        putOpt("ramales", d.ramales)
        putOpt("ramalesPortantes", d.ramalesPortantes)
        putOpt("amarre", d.amarre?.name)
        put("sinEslingas", d.sinEslingas)
        put("incluidos", d.incluidos.toList().json())
        put("aproximados", d.aproximados.toList().json())
        put("editados", d.editados.toList().json())
        put("frases", JSONObject().also { f -> d.frases.forEach { (k, v) -> f.put(k, v) } })
        put("notas", d.notas.json())
        put("configuracion", d.configuracion.json())
    }

    private fun datosDeJson(o: JSONObject): DatosIzaje = DatosIzaje(
        equipo = o.txt("equipo"),
        capacidadNominalKg = o.dbl("capacidadNominalKg"),
        cargaKg = o.dbl("cargaKg"),
        ganchoKg = o.dbl("ganchoKg"),
        eslingasKg = o.dbl("eslingasKg"),
        eslingaUnitKg = o.dbl("eslingaUnitKg"),
        grilletesKg = o.dbl("grilletesKg"),
        grilleteUnitKg = o.dbl("grilleteUnitKg"),
        nGrilletes = o.ent("nGrilletes"),
        balancinKg = o.dbl("balancinKg"),
        otrosKg = o.dbl("otrosKg"),
        radioInicialM = o.dbl("radioInicialM"),
        radioFinalM = o.dbl("radioFinalM"),
        radioMaximoM = o.dbl("radioMaximoM"),
        alturaM = o.dbl("alturaM"),
        capacidades = o.optJSONArray("capacidades")?.let { a ->
            (0 until a.length()).map { i -> a.getJSONObject(i).let { PuntoCapacidad(it.getDouble("r"), it.getDouble("c")) } }
        } ?: emptyList(),
        nEslingas = o.ent("nEslingas"),
        wllEslingaKg = o.dbl("wllEslingaKg"),
        anguloGrados = o.dbl("anguloGrados"),
        refAngulo = o.txt("refAngulo")?.let { n -> RefAngulo.values().firstOrNull { it.name == n } } ?: RefAngulo.SIN_CONFIRMAR,
        ramales = o.ent("ramales"),
        ramalesPortantes = o.ent("ramalesPortantes"),
        amarre = o.txt("amarre")?.let { n -> Amarre.values().firstOrNull { it.name == n } },
        sinEslingas = o.optBoolean("sinEslingas"),
        incluidos = o.optJSONArray("incluidos")?.textos()?.toSet() ?: emptySet(),
        aproximados = o.optJSONArray("aproximados")?.textos()?.toSet() ?: emptySet(),
        editados = o.optJSONArray("editados")?.textos()?.toSet() ?: emptySet(),
        frases = o.optJSONObject("frases")?.let { f -> f.keys().asSequence().associateWith { f.getString(it) } } ?: emptyMap(),
        notas = o.optJSONArray("notas")?.textos() ?: emptyList(),
        configuracion = o.optJSONArray("configuracion")?.textos() ?: emptyList(),
    )
}
