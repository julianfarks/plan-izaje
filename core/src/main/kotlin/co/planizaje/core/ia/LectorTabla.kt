package co.planizaje.core.ia

import co.planizaje.core.model.PuntoTabla
import kotlin.math.abs
import kotlin.math.max

/** Fragmento de texto reconocido en la imagen, con su caja (px) y confianza 0..1 (null si el motor no la da). */
data class Fragmento(
    val texto: String,
    val x: Float, val y: Float, val ancho: Float, val alto: Float,
    val confianza: Float? = null,
) {
    val cx get() = x + ancho / 2
    val cy get() = y + alto / 2
}

data class Celda(val texto: String, val valor: Double?, val dudosa: Boolean, val motivo: String = "")

data class TablaLeida(
    val nombre: String,
    val puntos: List<PuntoTabla>,
    /** Celdas que no se pudieron emparejar o leer: se muestran para revisión, nunca se rellenan. */
    val sinEmparejar: List<String>,
)

data class LecturaTabla(
    val tablas: List<TablaLeida>,
    val fabricante: String,
    val modelo: String,
    val referencia: String,
    val unidades: String,
    val advertencias: List<String>,
    val textoCompleto: String,
) {
    val hayDudosos: Boolean get() = tablas.any { t -> t.puntos.any { it.dudoso } || t.sinEmparejar.isNotEmpty() }
}

/**
 * Convierte el texto reconocido de una foto de diagrama de carga en filas radio → capacidad.
 *
 * No supone un formato fijo: agrupa números por filas según su posición vertical, clasifica cada fila
 * como «radios» (decimales pequeños, crecientes, con «m») o «capacidades» (enteros grandes, con «kg»),
 * y empareja cada capacidad con el radio de su misma columna. Lo que no encaja queda sin emparejar o dudoso.
 * El resultado SIEMPRE requiere revisión humana antes de guardarse.
 */
object LectorTabla {

    private val FABRICANTES = listOf("palfinger", "hiab", "fassi", "effer", "hmf", "ferrari", "cormach", "copma", "atlas", "tadano", "unic", "maxilift", "xcmg", "pm", "amco", "bonfiglioli", "pesci", "f.lli ferrari")

    private data class Num(val f: Fragmento, val texto: String, val valor: Double, val unidad: String, val dudoso: Boolean, val motivo: String) {
        val cx get() = f.cx
        val cy get() = f.cy
    }

    private val CONF_MIN = 0.70f

    /** Corrige confusiones típicas del OCR dentro de un número; devuelve el texto corregido y si hubo corrección. */
    fun limpiarNumero(t: String): Pair<String, Boolean> {
        var s = t.trim()
        val orig = s
        s = s.replace(Regex("(?<=\\d)[oO](?=\\d|$)|^[oO](?=[.,]?\\d)"), "0")
            .replace(Regex("(?<=\\d)[lI|](?=\\d|$)|^[lI|](?=\\d)"), "1")
            .replace(Regex("(?<=\\d)[sS](?=\\d)"), "5")
            .replace(Regex("(?<=\\d)[bB](?=\\d)"), "8")
        return s to (s != orig)
    }

    private fun numeros(frags: List<Fragmento>): List<Num> {
        val out = mutableListOf<Num>()
        for (f in frags) {
            // Fragmentos con palabras (títulos, modelos, notas) no aportan valores de la tabla.
            val sinUnidades = f.texto.replace(Regex("(?i)\\b(kg|kgs|m|t|lbs|ft|max\\.?)\\b"), "")
            if (Regex("[A-Za-zÁÉÍÓÚáéíóúñÑ]{2,}").containsMatchIn(sinUnidades.replace(Regex("(?i)(?<=\\d)(kg|m)"), ""))) continue
            // un fragmento puede contener varios números ("4520kg 3180kg")
            val partes = Regex("[\\dOolIS|][\\dOolISbB|]*(?:[.,][\\dOolIS]+)?\\s*(?:kg|kgs|m|t|lbs|ft|'|\")?", RegexOption.IGNORE_CASE).findAll(f.texto).toList()
                .filter { it.value.any(Char::isDigit) }
            val anchoChar = if (f.texto.isNotEmpty()) f.ancho / f.texto.length else f.ancho
            for (p in partes) {
                val unidad = Regex("(kg|kgs|m|t|lbs|ft|'|\")$", RegexOption.IGNORE_CASE).find(p.value.trim())?.value?.lowercase() ?: ""
                val cuerpo = p.value.trim().removeSuffix(unidad).removeSuffix(unidad.uppercase()).trim()
                val (limpio, corregido) = limpiarNumero(cuerpo)
                if (!Regex("^\\d+(?:[.,]\\d+)?$").matches(limpio)) continue
                val v = limpio.replace(',', '.').toDoubleOrNull() ?: continue
                val baja = f.confianza != null && f.confianza < CONF_MIN
                val motivo = when { corregido -> "Corregido de «$cuerpo»"; baja -> "Lectura con poca confianza (${(f.confianza!! * 100).toInt()} %)"; else -> "" }
                // Sub-caja aproximada del número dentro del fragmento
                val sub = if (partes.size > 1) f.copy(x = f.x + p.range.first * anchoChar, ancho = (p.value.length * anchoChar)) else f
                out += Num(sub, p.value.trim(), v, if (unidad == "kgs") "kg" else unidad, corregido || baja, motivo)
            }
        }
        return out
    }

    private fun filas(nums: List<Num>): List<List<Num>> {
        if (nums.isEmpty()) return emptyList()
        val altoMed = nums.map { it.f.alto }.sorted()[nums.size / 2].coerceAtLeast(1f)
        val ordenados = nums.sortedBy { it.cy }
        val grupos = mutableListOf<MutableList<Num>>()
        for (n in ordenados) {
            val g = grupos.lastOrNull()
            if (g != null && abs(g.map { it.cy }.average().toFloat() - n.cy) < altoMed * 0.6f) g += n else grupos += mutableListOf(n)
        }
        return grupos.map { it.sortedBy { n -> n.cx } }
    }

    private enum class TipoFila { RADIO, CAPACIDAD, IMPERIAL, OTRO }

    private fun clasificar(f: List<Num>, textoFila: String): TipoFila {
        if (f.size < 3) return TipoFila.OTRO
        val tn = NumerosTexto.normalizar(textoFila)
        if (f.count { it.unidad == "lbs" || it.unidad == "ft" || it.unidad == "'" || it.unidad == "\"" } * 2 >= f.size || Regex("\\b(lbs|ft)\\b").containsMatchIn(tn)) return TipoFila.IMPERIAL
        val conM = f.count { it.unidad == "m" }
        val conKg = f.count { it.unidad == "kg" || it.unidad == "t" }
        val decimales = f.count { it.texto.contains('.') || it.texto.contains(',') }
        val pequenos = f.count { it.valor in 0.5..60.0 }
        val grandes = f.count { it.valor >= 60.0 }
        return when {
            conKg * 2 >= f.size -> TipoFila.CAPACIDAD
            conM * 2 >= f.size -> TipoFila.RADIO
            pequenos == f.size && (decimales * 2 >= f.size) -> TipoFila.RADIO
            grandes * 2 > f.size -> TipoFila.CAPACIDAD
            pequenos == f.size -> TipoFila.RADIO
            else -> TipoFila.OTRO
        }
    }

    fun leer(frags: List<Fragmento>): LecturaTabla {
        val adv = mutableListOf<String>()
        val texto = frags.sortedWith(compareBy({ it.cy }, { it.cx })).joinToString(" ") { it.texto }
        val tn = NumerosTexto.normalizar(texto)

        val fabricante = FABRICANTES.firstOrNull { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(tn) }
            ?.let { if (it == "pm") "PM" else it.replaceFirstChar { c -> c.uppercase() } } ?: ""
        val modelo = Regex("\\b(pk ?\\d{4,6} ?[a-z]?|\\d{2,3} ?[a-z]-\\d{1,2}(?: ?[a-z]+)?|\\d{5} ?sp ?pt|\\d{3,5} ?[a-z]{1,3}\\b(?: ?\\w+)?)").find(tn)?.value?.uppercase()?.trim() ?: ""
        val referencia = Regex("\\b(eb ?\\d{3,5}|\\d{3} ?\\d{4}|\\d{3}\\.\\d{3})\\b").findAll(tn).map { it.value.uppercase() }.lastOrNull() ?: ""
        val unidades = buildList {
            if (Regex("\\bkg\\b|\\d\\s*kg").containsMatchIn(tn)) add("kg")
            if (Regex("\\blbs\\b").containsMatchIn(tn)) add("lbs")
            if (Regex("\\d\\s*m\\b|\\bm\\b").containsMatchIn(tn)) add("m")
            if (Regex("\\bft\\b|\\d+'").containsMatchIn(tn)) add("ft")
        }.joinToString(", ")
        if (!unidades.contains("kg")) adv += "No se detectó la unidad «kg»: confirma la unidad de las capacidades antes de importar."
        if (unidades.contains("lbs")) adv += "La tabla tiene también libras/pies: solo se importan los valores en kg y m."

        val nums = numeros(frags)
        val fs = filas(nums)
        val tipos = fs.map { f ->
            val y0 = f.minOf { it.f.y }; val y1 = f.maxOf { it.f.y + it.f.alto }
            val textoFila = frags.filter { it.cy in y0..y1 }.joinToString(" ") { it.texto }
            clasificar(f, textoFila) to textoFila
        }
        val filasRadio = fs.indices.filter { tipos[it].first == TipoFila.RADIO }
        val filasCap = fs.indices.filter { tipos[it].first == TipoFila.CAPACIDAD }
        if (filasRadio.isEmpty()) adv += "No se encontró una fila de radios (m). Puede que la foto no muestre la tabla completa o que el formato sea un gráfico: introduce los radios a mano."
        if (filasCap.isEmpty()) adv += "No se encontró una fila de capacidades (kg)."

        val tablas = mutableListOf<TablaLeida>()
        for (ic in filasCap) {
            val cap = fs[ic]
            // fila de radios más cercana en vertical
            val ir = filasRadio.minByOrNull { abs(fs[it].map { n -> n.cy }.average() - cap.map { n -> n.cy }.average()) }
            val etiqueta = tipos[ic].second.split(Regex("\\s+")).filter { w -> w.none(Char::isDigit) && w.length in 1..6 && !w.equals("kg", true) }
                .take(2).joinToString(" ").ifBlank { "Fila ${tablas.size + 1}" }
            if (ir == null) {
                tablas += TablaLeida(etiqueta, emptyList(), cap.map { "${it.texto} (sin radio)" }); continue
            }
            val rad = fs[ir]
            val anchoCol = if (rad.size > 1) rad.zipWithNext { a, b -> b.cx - a.cx }.sorted()[(rad.size - 1) / 2] else max(rad[0].f.ancho * 2, 50f)
            val usados = mutableSetOf<Int>()
            val puntos = mutableListOf<PuntoTabla>()
            val sueltos = mutableListOf<String>()
            for (c in cap) {
                val j = rad.indices.filter { it !in usados }.minByOrNull { abs(rad[it].cx - c.cx) }
                if (j == null) { sueltos += "${c.texto} kg (sin radio)"; continue }
                val dx = abs(rad[j].cx - c.cx)
                if (dx > anchoCol * 0.75f) { sueltos += "${c.texto} kg (columna sin radio claro)"; continue }
                usados += j
                val r = rad[j]
                val kg = if (c.unidad == "t") c.valor * 1000 else c.valor
                val dudoso = c.dudoso || r.dudoso || dx > anchoCol * 0.4f
                puntos += PuntoTabla(r.valor, kg, dudoso = dudoso)
            }
            rad.indices.filter { it !in usados }.forEach { sueltos += "${rad[it].texto} m (sin capacidad)" }
            val ord = puntos.sortedBy { it.radio }
            if (ord.zipWithNext().any { (a, b) -> b.capacidad > a.capacidad }) adv += "«$etiqueta»: hay capacidades que aumentan con el radio; compruébalas con la foto."
            if (sueltos.isNotEmpty()) adv += "«$etiqueta»: ${sueltos.size} valor(es) sin emparejar; no se rellenan automáticamente."
            tablas += TablaLeida(etiqueta, puntos, sueltos)
        }
        val dud = tablas.sumOf { t -> t.puntos.count { it.dudoso } }
        if (dud > 0) adv += "$dud valor(es) leídos con poca seguridad quedan marcados como pendientes de verificar."
        if (fabricante.isEmpty() || modelo.isEmpty()) adv += "No se pudo leer con seguridad el fabricante o el modelo en la imagen."
        return LecturaTabla(tablas, fabricante, modelo, referencia, unidades, adv, texto)
    }

    /**
     * ¿La tabla leída corresponde a la grúa? null = no se puede saber (no se leyó modelo).
     * Compara los grupos de cifras del modelo (p. ej. «23500», «288»).
     */
    fun coincideModelo(modeloLeido: String, fabricanteLeido: String, modeloGrua: String, fabricanteGrua: String): Boolean? {
        val cifrasLeidas = Regex("\\d{3,6}").findAll(modeloLeido).map { it.value }.toSet()
        val cifrasGrua = Regex("\\d{3,6}").findAll(modeloGrua).map { it.value }.toSet()
        if (cifrasLeidas.isEmpty() || cifrasGrua.isEmpty()) return null
        val fabOk = fabricanteLeido.isBlank() || fabricanteGrua.isBlank() || NumerosTexto.normalizar(fabricanteLeido) == NumerosTexto.normalizar(fabricanteGrua)
        return fabOk && cifrasLeidas.intersect(cifrasGrua).isNotEmpty()
    }
}
