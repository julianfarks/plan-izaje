package co.losmagnificos.izaje.core

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.util.Locale

/** Formato de números al estilo colombiano: punto para miles y coma para decimales. */
object Formato {
    private fun df(maxDec: Int): DecimalFormat {
        val s = DecimalFormatSymbols(Locale.ROOT).apply { decimalSeparator = ','; groupingSeparator = '.' }
        val patron = "#,##0" + if (maxDec > 0) "." + "#".repeat(maxDec) else ""
        return DecimalFormat(patron, s).apply { roundingMode = RoundingMode.HALF_UP }
    }

    fun num(v: Double?, dec: Int = 2): String = if (v == null || !v.isFinite()) "—" else df(dec).format(v)
    fun kg(v: Double?): String = if (v == null) "sin dato" else "${num(v, 2)} kg"
    fun kgYt(v: Double?): String = if (v == null) "sin dato" else "${num(v, 2)} kg (${num(v / 1000.0, 3)} t)"
    fun m(v: Double?): String = if (v == null) "sin dato" else "${num(v, 2)} m"
    fun pct(v: Double?): String = if (v == null) "—" else "${num(v, 2)} %"
    fun grados(v: Double?): String = if (v == null) "sin dato" else "${num(v, 1)}°"

    /** Número para un campo editable: coma decimal, sin separador de miles y sin ceros sobrantes. */
    fun editable(v: Double?): String {
        if (v == null || !v.isFinite()) return ""
        return df(4).apply { isGroupingUsed = false }.format(v)
    }
}

/** Utilidades de texto en español: números escritos con letras, unidades y normalización. */
object Texto {

    private val unidades = mapOf(
        "cero" to 0, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7,
        "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14,
        "quince" to 15, "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19,
        "veinte" to 20, "veintiuno" to 21, "veintiun" to 21, "veintiuna" to 21, "veintidos" to 22,
        "veintitres" to 23, "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26,
        "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29, "treinta" to 30, "cuarenta" to 40,
        "cincuenta" to 50, "sesenta" to 60, "setenta" to 70, "ochenta" to 80, "noventa" to 90,
        "cien" to 100, "ciento" to 100, "doscientos" to 200, "doscientas" to 200, "trescientos" to 300,
        "trescientas" to 300, "cuatrocientos" to 400, "cuatrocientas" to 400, "quinientos" to 500,
        "quinientas" to 500, "seiscientos" to 600, "seiscientas" to 600, "setecientos" to 700,
        "setecientas" to 700, "ochocientos" to 800, "ochocientas" to 800, "novecientos" to 900,
        "novecientas" to 900,
    )
    private val uno = setOf("un", "uno", "una")

    /** Minúsculas y sin tildes, conservando la longitud del texto (los índices coinciden con el original). */
    fun normalizar(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val d = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD).firstOrNull() ?: ch
            sb.append(d.lowercaseChar())
        }
        return sb.toString()
    }

    private fun valor(palabraNormalizada: String): Int? = unidades[palabraNormalizada]

    private fun puedeSumar(actual: Int, v: Int, tras_y: Boolean): Boolean {
        val resto = actual % 100
        return when {
            v >= 100 -> actual == 0
            v >= 10 -> resto == 0
            else -> resto == 0 || (tras_y && resto in 30..90 && resto % 10 == 0)
        }
    }

    /**
     * Sustituye números escritos con letras por cifras: «ochocientos cincuenta» → 850,
     * «dos mil quinientos» → 2500, «doce coma cinco» → 12,5, «tonelada y media» → 1,5 toneladas.
     * «un/una» solo se convierten delante de «mil» o de unidades y aparejos (una eslinga → 1 eslinga).
     */
    fun cifras(texto: String): String {
        var t = texto
        t = Regex("(?i)\\bmedia tonelada\\b").replace(t, "0,5 toneladas")
        t = Regex("(?i)\\bmedio metro\\b").replace(t, "0,5 metros")
        t = Regex("(?i)\\b(?:una|un|1) tonelada y media\\b").replace(t, "1,5 toneladas")
        val tk = Regex("\\p{L}+|\\d+(?:[.,]\\d+)?|[^\\p{L}\\d]+").findAll(t).map { it.value }.toList()
        val norm = tk.map { normalizar(it) }
        fun siguientePalabra(desde: Int): String? {
            var k = desde + 1
            while (k < tk.size) { if (norm[k].isNotBlank()) return norm[k]; k++ }
            return null
        }
        val out = StringBuilder()
        var i = 0
        while (i < tk.size) {
            val n = norm[i]
            val empieza = valor(n) != null || n == "mil" || (n in uno && siguientePalabra(i) == "mil")
            if (!empieza) { out.append(tk[i]); i++; continue }
            var total = 0
            var actual = 0
            var ultimo = i
            var j = i
            var hayY = false
            var primero = true
            while (j < tk.size) {
                val p = norm[j]
                if (p.isBlank()) { j++; continue }
                if (p == "y" && !primero) {
                    val sig = siguientePalabra(j)
                    val sv = sig?.let { valor(it) ?: if (it in uno) 1 else null }
                    if (sv != null && sv in 1..9 && (actual % 100) in 30..90 && (actual % 10) == 0) { hayY = true; j++; continue }
                    break
                }
                if (p == "mil") {
                    if (total % 1_000_000 >= 1000 && actual == 0) break
                    total += (if (actual == 0) 1 else actual) * 1000
                    actual = 0; ultimo = j; j++; primero = false; hayY = false; continue
                }
                val v = valor(p) ?: if (p in uno && (primero || hayY)) 1 else null
                if (v == null) break
                if (!puedeSumar(actual, v, hayY)) break
                actual += v; ultimo = j; j++; primero = false; hayY = false
            }
            out.append(total + actual)
            i = ultimo + 1
        }
        var r = out.toString()
        r = Regex("(\\d+) (?:coma|punto) (\\d+)").replace(r) { "${it.groupValues[1]},${it.groupValues[2]}" }
        r = Regex("(?i)\\b(un|una|uno) (eslingas?|estrobos?|grilletes?|ganchos?|balancin|balancín|metros?|kilos?|kilogramos?|toneladas?|extensi[oó]n|ramal|ramales|grados?)\\b")
            .replace(r) { "1 ${it.groupValues[2]}" }
        r = Regex("(?i)(\\d+) (metros?|toneladas?|kilos?|kilogramos?) y medi[oa]\\b").replace(r) { "${it.groupValues[1]},5 ${it.groupValues[2]}" }
        return r
    }

    /** Número escrito con coma o punto decimal. «1.250» (miles con punto) → 1250; «2,5» y «2.5» → 2,5. */
    fun numero(s: String): Double? {
        var t = s.trim().replace(" ", "").replace(" ", "")
        if (t.isEmpty() || t == "-" || t == "," || t == ".") return null
        if (Regex("^\\d{1,3}(\\.\\d{3})+$").matches(t)) return t.replace(".", "").toDoubleOrNull()
        if (t.contains('.') && t.contains(',')) {
            t = if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replace(',', '.') else t.replace(",", "")
        } else if (t.count { it == ',' } == 1) {
            t = t.replace(',', '.')
        } else if (t.count { it == ',' } > 1) {
            return null
        }
        return t.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private val reUnidadPeso = Regex("^(\\d[\\d.,]*)\\s*(toneladas?|tons?|t|kilogramos?|kilos?|kgs?)?$")

    /** Convierte «3 t», «3000 kg», «2,5 toneladas» o «3000» (kg por defecto) a kilogramos. */
    fun pesoKg(texto: String, tonPorDefecto: Boolean = false): Double? {
        val n = normalizar(texto.trim())
        val m = reUnidadPeso.matchEntire(n) ?: return null
        val v = numero(m.groupValues[1]) ?: return null
        val u = m.groupValues[2]
        return when {
            u.isEmpty() -> if (tonPorDefecto) v * 1000.0 else v
            u.startsWith("t") -> v * 1000.0
            else -> v
        }
    }

    private val reUnidadLong = Regex("^(\\d[\\d.,]*)\\s*(metros?|mts?|m|centimetros?|cms?)?$")

    /** Convierte «2,5 m», «250 cm» o «2.5» (metros por defecto) a metros. */
    fun longitudM(texto: String): Double? {
        val n = normalizar(texto.trim())
        val m = reUnidadLong.matchEntire(n) ?: return null
        val v = numero(m.groupValues[1]) ?: return null
        return if (m.groupValues[2].startsWith("c")) v / 100.0 else v
    }

    /** Un número escrito por el usuario (sin unidad), p. ej. ángulo o cantidad. */
    fun simple(texto: String): Double? = numero(normalizar(texto.trim()).removeSuffix("°").removeSuffix("grados").trim())
}
