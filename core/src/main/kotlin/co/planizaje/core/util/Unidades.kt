package co.planizaje.core.util

import java.util.Locale
import kotlin.math.abs

enum class UnidadPeso(val simbolo: String, val factorKg: Double) {
    KG("kg", 1.0),
    T("t", 1000.0),
}

object Unidades {
    private val LOCALE: Locale = Locale.forLanguageTag("es-CO")

    const val G = 9.80665

    fun aKg(valor: Double, unidad: UnidadPeso): Double = valor * unidad.factorKg
    fun desdeKg(kg: Double, unidad: UnidadPeso): Double = kg / unidad.factorKg

    /** Fuerza en kN que ejerce una masa en kg. */
    fun kgAkN(kg: Double): Double = kg * G / 1000.0
    fun kNaKg(kn: Double): Double = kn * 1000.0 / G

    /**
     * Convierte texto escrito por el usuario a número. Acepta coma o punto decimal.
     * "1.250,5" → 1250.5 ; "2,5" → 2.5 ; "2.5" → 2.5 ; "" → null ; "abc" → null.
     */
    fun parse(texto: String): Double? {
        var t = texto.trim().replace(" ", "").replace(" ", "")
        if (t.isEmpty() || t == "-" || t == "," || t == ".") return null
        if (t.contains('.') && t.contains(',')) {
            t = if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replace(',', '.')
            else t.replace(",", "")
        } else if (t.count { it == ',' } == 1) {
            t = t.replace(',', '.')
        } else if (t.count { it == ',' } > 1) {
            return null
        }
        return t.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** Formatea con coma decimal y separador de miles locales; elimina ceros sobrantes. */
    fun fmt(v: Double?, decimales: Int = 2): String {
        if (v == null) return "—"
        if (!v.isFinite()) return "—"
        val s = String.format(LOCALE, "%,.${decimales}f", v)
        if (decimales == 0) return s
        val dec = java.text.DecimalFormatSymbols.getInstance(LOCALE).decimalSeparator
        if (!s.contains(dec)) return s
        return s.trimEnd('0').trimEnd(dec).ifEmpty { "0" }
    }

    fun fmtEditable(v: Double?): String {
        if (v == null) return ""
        val r = if (abs(v - Math.round(v)) < 1e-9) Math.round(v).toString()
        else String.format(Locale.ROOT, "%.4f", v).trimEnd('0').trimEnd('.')
        return r.replace('.', ',')
    }

    fun kg(v: Double?): String = if (v == null) "— kg" else "${fmt(v, 1)} kg"
    fun t(v: Double?): String = if (v == null) "— t" else "${fmt(v?.div(1000.0), 3)} t"
    fun kgYt(v: Double?): String = if (v == null) "sin dato" else "${fmt(v, 1)} kg (${fmt(v / 1000.0, 3)} t)"
    fun m(v: Double?): String = if (v == null) "— m" else "${fmt(v, 2)} m"
    fun pct(v: Double?): String = if (v == null) "— %" else "${fmt(v, 1)} %"
    fun grados(v: Double?): String = if (v == null) "— °" else "${fmt(v, 1)}°"
}
