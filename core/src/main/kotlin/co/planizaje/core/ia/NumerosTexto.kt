package co.planizaje.core.ia

import java.text.Normalizer

/** Utilidades de texto en español: números escritos con letras y normalización. */
object NumerosTexto {

    private val unidades = mapOf(
        "cero" to 0, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7,
        "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14,
        "quince" to 15, "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19,
        "veinte" to 20, "veintiuno" to 21, "veintiun" to 21, "veintiuna" to 21, "veintidos" to 22, "veintitres" to 23,
        "veinticuatro" to 24, "veinticinco" to 25, "veintiseis" to 26, "veintisiete" to 27, "veintiocho" to 28,
        "veintinueve" to 29, "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60,
        "setenta" to 70, "ochenta" to 80, "noventa" to 90, "cien" to 100, "ciento" to 100,
        "doscientos" to 200, "doscientas" to 200, "trescientos" to 300, "trescientas" to 300,
        "cuatrocientos" to 400, "cuatrocientas" to 400, "quinientos" to 500, "quinientas" to 500,
        "seiscientos" to 600, "seiscientas" to 600, "setecientos" to 700, "setecientas" to 700,
        "ochocientos" to 800, "ochocientas" to 800, "novecientos" to 900, "novecientas" to 900,
    )
    private val unoPalabras = setOf("un", "uno", "una")

    /** Quita tildes y pasa a minúsculas conservando la longitud del texto (útil para indexar). */
    fun normalizar(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val d = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD).firstOrNull() ?: ch
            sb.append(d.lowercaseChar())
        }
        return sb.toString()
    }

    private fun valor(tok: String): Int? = unidades[normalizar(tok)]

    /**
     * Sustituye números escritos con letras por cifras: «ochocientos cincuenta» → 850,
     * «doce coma cinco» → 12,5, «tonelada y media» → 1,5 toneladas.
     * «un/una» solo se convierten delante de «mil» o de unidades de medida/aparejos.
     */
    fun cifras(texto: String): String {
        var t = texto
        t = Regex("(?i)\\bmedia tonelada\\b").replace(t, "0,5 toneladas")
        t = Regex("(?i)\\b(una|1) tonelada y media\\b").replace(t, "1,5 toneladas")
        val palabras = Regex("[\\p{L}]+|\\d+(?:[.,]\\d+)?|[^\\p{L}\\d]+").findAll(t).map { it.value }.toList()
        val out = StringBuilder()
        var i = 0
        while (i < palabras.size) {
            val w = palabras[i]
            val nw = normalizar(w)
            val esUno = nw in unoPalabras
            val siguienteMil = esUno && palabras.getOrNull(i + 2)?.let { normalizar(it) } == "mil"
            if (valor(w) != null || nw == "mil" || siguienteMil) {
                var total = 0
                var actual = 0
                var j = i
                var consumido = i
                while (j < palabras.size) {
                    val p = normalizar(palabras[j])
                    when {
                        p.isBlank() || p == " " -> { j++; continue }
                        p.trim().isEmpty() -> { j++; continue }
                        p == "y" && j > i -> { j++; continue }
                        p == "mil" -> { total += (if (actual == 0) 1 else actual) * 1000; actual = 0; consumido = j; j++ }
                        p in unoPalabras && j == i -> { actual += 1; consumido = j; j++ }
                        valor(palabras[j]) != null -> { actual += valor(palabras[j])!!; consumido = j; j++ }
                        else -> break
                    }
                }
                val n = total + actual
                out.append(n)
                // conserva el separador posterior a la última palabra numérica
                i = consumido + 1
                continue
            }
            out.append(w)
            i++
        }
        var r = out.toString()
        r = Regex("(\\d+) (?:coma|punto) (\\d+)").replace(r) { "${it.groupValues[1]},${it.groupValues[2]}" }
        r = Regex("(?i)\\b(un|una|uno) (eslingas?|grilletes?|ganchos?|balancin|balancín|metros?|kilos?|toneladas?|extensi[oó]n|ramal|ramales)\\b")
            .replace(r) { "1 ${it.groupValues[2]}" }
        return r
    }

    /** Número escrito con coma o punto decimal; «1.250» (miles con punto) → 1250. */
    fun numero(s: String): Double? {
        val t = s.trim()
        if (Regex("^\\d{1,3}(\\.\\d{3})+$").matches(t)) return t.replace(".", "").toDoubleOrNull()
        return co.planizaje.core.util.Unidades.parse(t)
    }
}
