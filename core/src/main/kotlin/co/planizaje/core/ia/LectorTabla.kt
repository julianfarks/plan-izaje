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
            .replace(Regex("(?<=\\d)[lI](?=\\d|$)|^[lI](?=\\d)"), "1")
            .replace(Regex("(?<=\\d)[sS](?=\\d)"), "5")
            .replace(Regex("(?<=\\d)[bB](?=\\d)"), "8")
        return s to (s != orig)
    }

    /** Separa celdas que el OCR unió: «18.0011.9814.00» → 18.00, 11.98, 14.00; «25401880» → 2540, 1880. */
    fun separarUnidos(t: String): List<String>? {
        if (t.count { it == '.' || it == ',' } > 1) {
            val dos = Regex("\\d{1,2}[.,]\\d{2}").findAll(t).map { it.value }.toList()
            if (dos.joinToString("") == t) return dos
            val uno = Regex("\\d{1,2}[.,]\\d").findAll(t).map { it.value }.toList()
            if (uno.joinToString("") == t) return uno
            return null
        }
        if (Regex("^\\d{7,}$").matches(t)) {
            if (t.length % 4 == 0) return t.chunked(4)
            return null
        }
        return null
    }

    private fun numeros(frags: List<Fragmento>): List<Num> {
        val out = mutableListOf<Num>()
        for (f in frags) {
            // Fragmentos con palabras (títulos, modelos, notas) no aportan valores de la tabla.
            val sinUnidades = f.texto.replace(Regex("(?i)\\b(kg|kgs|m|t|lbs|ft|max\\.?)\\b"), "").replace(Regex("(?i)(?<=\\d)(kg|m)"), "")
            if (Regex("[A-Za-zÁÉÍÓÚáéíóúñÑ]{2,}").containsMatchIn(sinUnidades)) continue
            val baja = f.confianza != null && f.confianza < CONF_MIN
            val anchoChar = if (f.texto.isNotEmpty()) f.ancho / f.texto.length else f.ancho
            // «|» son bordes de celda: separan valores.
            val partes = Regex("[\\dOolIS][\\dOolISbB]*(?:[.,][\\dOolIS]+)*\\s*(?:kg|kgs|m|t|lbs|ft|'|\")?", RegexOption.IGNORE_CASE).findAll(f.texto).toList()
                .filter { it.value.any(Char::isDigit) }
            for (p in partes) {
                val bruto = p.value.trim()
                val unidad = Regex("(kg|kgs|m|t|lbs|ft|'|\")$", RegexOption.IGNORE_CASE).find(bruto)?.value?.lowercase() ?: ""
                val cuerpo = bruto.dropLast(unidad.length).trim()
                val (limpio, corregido) = limpiarNumero(cuerpo)
                val trozos = if (Regex("^\\d+(?:[.,]\\d+)?$").matches(limpio)) listOf(limpio) else separarUnidos(limpio) ?: continue
                val unido = trozos.size > 1
                val anchoTrozo = (bruto.length * anchoChar) / trozos.size
                trozos.forEachIndexed { k, tz ->
                    val v = tz.replace(',', '.').toDoubleOrNull() ?: return@forEachIndexed
                    if (tz.replace(Regex("[.,]"), "").length > 6) return@forEachIndexed
                    val motivo = when {
                        unido -> "Celdas unidas en la lectura («$cuerpo»)"
                        corregido -> "Corregido de «$cuerpo»"
                        baja -> "Lectura con poca confianza (${(f.confianza!! * 100).toInt()} %)"
                        else -> ""
                    }
                    val sub = if (partes.size > 1 || unido) f.copy(x = f.x + p.range.first * anchoChar + k * anchoTrozo, ancho = anchoTrozo) else f
                    out += Num(sub, tz, v, if (unidad == "kgs") "kg" else unidad, unido || corregido || baja, motivo)
                }
            }
        }
        return out
    }

    /** Agrupa en filas encadenando vecinos: tolera filas inclinadas por la perspectiva de la foto. */
    private fun filas(nums: List<Num>): List<List<Num>> {
        if (nums.isEmpty()) return emptyList()
        val altoMed = nums.map { it.f.alto }.sorted()[nums.size / 2].coerceAtLeast(1f)
        fun clase(n: Num) = when (n.unidad) { "m" -> 1; "kg", "t" -> 2; "lbs", "ft", "'", "\"" -> 3; else -> 0 }
        val grupos = mutableListOf<MutableList<Num>>()
        for (n in nums.sortedBy { it.cx }) {
            val g = grupos.filter { abs(it.last().cy - n.cy) < altoMed * 0.75f && n.cx > it.last().cx && clase(it.last()) == clase(n) }
                .minByOrNull { abs(it.last().cy - n.cy) }
            if (g != null) g += n else grupos += mutableListOf(n)
        }
        return grupos.sortedBy { g -> g.map { it.cy }.average() }
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

    /**
     * Empareja capacidades con radios. Estima el desplazamiento horizontal típico entre la etiqueta de radio
     * y su capacidad (en muchas placas el radio está a un lado de la casilla) y empareja uno a uno los que
     * encajan con ese desplazamiento. Los huecos (celdas ilegibles) quedan sin emparejar.
     */
    private fun alinear(cap: List<Num>, rad: List<Num>): Pair<List<Triple<Num, Num, Boolean>>, Pair<List<Num>, List<Num>>> {
        val col = if (rad.size > 1) rad.zipWithNext { a, b -> b.cx - a.cx }.sorted()[(rad.size - 1) / 2] else max(rad.firstOrNull()?.f?.ancho?.times(2) ?: 50f, 50f)
        val tol = col * 0.3f
        fun emparejar(off: Float): List<Triple<Int, Int, Float>> {
            val cand = cap.indices.flatMap { i -> rad.indices.map { j -> Triple(i, j, abs(rad[j].cx - cap[i].cx - off)) } }
                .filter { it.third < tol }.sortedBy { it.third }
            val ui = mutableSetOf<Int>(); val uj = mutableSetOf<Int>()
            val out = mutableListOf<Triple<Int, Int, Float>>()
            for (c in cand) if (c.first !in ui && c.second !in uj) { ui += c.first; uj += c.second; out += c }
            // conserva el orden izquierda→derecha: descarta cruces
            return out.sortedBy { it.first }.fold(mutableListOf()) { acc, c -> if (acc.isEmpty() || c.second > acc.last().second) acc += c; acc }
        }
        var mejor: List<Triple<Int, Int, Float>> = emptyList()
        var clave = Triple(-1, Double.MAX_VALUE, Float.MAX_VALUE)
        val offsets = cap.flatMap { c -> rad.map { r -> r.cx - c.cx } }.filter { abs(it) < col * 1.2f }
        for (off in offsets) {
            val m = emparejar(off)
            val dev = m.map { it.third.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0
            val k = Triple(m.size, dev, abs(off))
            val mejora = k.first > clave.first || (k.first == clave.first && (k.second < clave.second - 1e-6 || (abs(k.second - clave.second) < 1e-6 && k.third < clave.third)))
            if (mejora) { clave = k; mejor = m }
        }
        val ui = mejor.map { it.first }.toSet(); val uj = mejor.map { it.second }.toSet()
        val trios = mejor.map { (i, j, d) -> Triple(cap[i], rad[j], d > col * 0.15f) }
        return trios to (cap.indices.filter { it !in ui }.map { cap[it] } to rad.indices.filter { it !in uj }.map { rad[it] })
    }

    /** Agrupa fragmentos de texto en líneas (tolerando inclinación) y devuelve el texto de cada línea. */
    private fun lineasTexto(frags: List<Fragmento>): List<String> {
        if (frags.isEmpty()) return emptyList()
        val altoMed = frags.map { it.alto }.sorted()[frags.size / 2].coerceAtLeast(1f)
        val grupos = mutableListOf<MutableList<Fragmento>>()
        for (f in frags.sortedBy { it.cx }) {
            val g = grupos.filter { abs(it.last().cy - f.cy) < altoMed * 0.75f && f.cx > it.last().cx && f.x - (it.last().x + it.last().ancho) < altoMed * 4 }
                .minByOrNull { abs(it.last().cy - f.cy) }
            if (g != null) g += f else grupos += mutableListOf(f)
        }
        return grupos.map { g -> g.joinToString(" ") { it.texto } }
    }

    fun leer(frags: List<Fragmento>): LecturaTabla {
        val adv = mutableListOf<String>()
        val ordenados = frags.sortedWith(compareBy({ it.cy }, { it.cx }))
        val texto = ordenados.joinToString(" ") { it.texto }
        val tn = NumerosTexto.normalizar(texto)

        // Fabricante y modelo: lo que aparece en la misma línea, a la derecha del nombre del fabricante.
        val fragFab = frags.firstOrNull { f -> FABRICANTES.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(NumerosTexto.normalizar(f.texto)) } }
        val fabricante = fragFab?.let { f -> FABRICANTES.first { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(NumerosTexto.normalizar(f.texto)) } }
            ?.let { if (it == "pm") "PM" else it.replaceFirstChar { c -> c.uppercase() } } ?: ""
        val esUnidad = Regex("(?i)^\\|?\\d+(?:[.,]\\d+)?\\s*(kg|m|t|lbs|ft)\\|?$")
        val modeloLinea = fragFab?.let { ff ->
            frags.filter { abs(it.cy - ff.cy) < ff.alto * 0.6f && it.x > ff.x && it !== ff && !esUnidad.matches(it.texto.trim()) && it.texto.any(Char::isLetterOrDigit) }
                .sortedBy { it.x }.take(3).joinToString(" ") { it.texto }.trim()
        }.orEmpty()
        val reModelo = Regex("\\b(pk ?\\d{4,6}(?: ?[a-z]\\b)?|\\d{2,3} ?[a-z]-\\d{1,2}(?: ?[a-z]+)?|\\d{5} ?sp ?pt)")
        val modelo = modeloLinea.takeIf { it.any(Char::isDigit) }
            ?: lineasTexto(frags).firstNotNullOfOrNull { reModelo.find(NumerosTexto.normalizar(it))?.value?.trim() }
            ?: ""
            .let { it }
        val modeloFinal = modelo.uppercase()
        val referencia = Regex("\\b(eb ?\\d{3,5}|\\d{3} ?\\d{4}|\\d{3}\\.\\d{3})\\b").findAll(tn).map { it.value.uppercase() }.lastOrNull() ?: ""
        val unidades = buildList {
            if (Regex("\\bkg\\b|\\d\\s*kg").containsMatchIn(tn)) add("kg")
            if (Regex("\\blbs\\b").containsMatchIn(tn)) add("lbs")
            if (Regex("\\d\\s*m\\b|\\bm\\b").containsMatchIn(tn)) add("m")
            if (Regex("\\bft\\b|\\d+'").containsMatchIn(tn)) add("ft")
        }.joinToString(", ")
        if (!unidades.contains("kg")) adv += "No se detectó la unidad «kg»: confirma la unidad de las capacidades antes de importar."
        if (unidades.contains("lbs")) adv += "La tabla tiene también libras/pies: solo se importan los valores en kg y m."

        // Valores bajo una marca «max» no tienen radio impreso: no se emparejan.
        val marcasMax = frags.filter { Regex("(?i)^max\\.?$").matches(it.texto.trim()) }
        val todos = numeros(frags)
        val nums = todos.filter { n -> marcasMax.none { m -> n.cy > m.cy && n.cy - m.cy < n.f.alto * 3f && n.f.x < m.x + m.ancho && n.f.x + n.f.ancho > m.x } }
        val excluidosMax = todos - nums.toSet()
        if (excluidosMax.isNotEmpty()) adv += "Valores «max» sin radio impreso (${excluidosMax.joinToString { it.texto }} kg): no se importan; su radio debe salir del manual."

        val fs = filas(nums)
        val tipos = fs.map { f ->
            val y0 = f.minOf { it.f.y }; val y1 = f.maxOf { it.f.y + it.f.alto }
            val textoFila = frags.filter { it.cy in y0..y1 }.joinToString(" ") { it.texto }
            clasificar(f, textoFila)
        }.toMutableList()
        // Si hay filas con unidades explícitas, las filas sin unidad (teléfonos, códigos…) no se usan.
        fun conUnidad(i: Int, u: Set<String>) = fs[i].count { it.unidad in u } * 2 >= fs[i].size
        if (fs.indices.any { tipos[it] == TipoFila.CAPACIDAD && conUnidad(it, setOf("kg", "t")) })
            fs.indices.filter { tipos[it] == TipoFila.CAPACIDAD && !conUnidad(it, setOf("kg", "t")) }.forEach { tipos[it] = TipoFila.OTRO }
        if (fs.indices.any { tipos[it] == TipoFila.RADIO && conUnidad(it, setOf("m")) })
            fs.indices.filter { tipos[it] == TipoFila.RADIO && !conUnidad(it, setOf("m")) }.forEach { tipos[it] = TipoFila.OTRO }
        // Filas de capacidad con letra mucho más pequeña suelen ser anotaciones (p. ej. peso de las extensiones).
        val altCap = fs.indices.filter { tipos[it] == TipoFila.CAPACIDAD }.associateWith { i -> fs[i].map { it.f.alto }.sorted()[fs[i].size / 2] }
        val altMax = altCap.values.maxOrNull() ?: 0f
        altCap.forEach { (i, h) -> if (h < altMax * 0.72f) { tipos[i] = TipoFila.OTRO; adv += "Se ignoró una fila de valores pequeños (${fs[i].joinToString(" ") { it.texto }}): parecen anotaciones, no capacidades." } }

        val filasRadio = fs.indices.filter { tipos[it] == TipoFila.RADIO }
        val filasCap = fs.indices.filter { tipos[it] == TipoFila.CAPACIDAD }
        if (filasRadio.isEmpty()) adv += "No se encontró una fila de radios (m). Puede que la foto no muestre la tabla completa o que el formato sea un gráfico: introduce los radios a mano."
        if (filasCap.isEmpty()) adv += "No se encontró una fila de capacidades (kg)."

        val tablas = mutableListOf<TablaLeida>()
        for (ic in filasCap) {
            val cap = fs[ic]
            val yCap = cap.map { it.cy }.average()
            val ir = filasRadio.minByOrNull { abs(fs[it].map { n -> n.cy }.average() - yCap) }
            val primero = cap.first()
            val etiqueta = frags.filter { abs(it.cy - primero.cy) < primero.f.alto * 0.7f && it.x + it.ancho <= primero.f.x + 2 && it.texto.none(Char::isDigit) && !Regex("(?i)^max\\.?$").matches(it.texto.trim()) }
                .sortedBy { it.x }.joinToString(" ") { it.texto.trim('|', ' ') }.trim()
                .let { if (it.isBlank() || it.equals("kg", true)) "Fila ${tablas.size + 1}" else "Fila $it" }
            if (ir == null) { tablas += TablaLeida(etiqueta, emptyList(), cap.map { "${it.texto} (sin radio)" }); continue }
            val (pares, sueltos) = alinear(cap, fs[ir])
            val puntos = pares.map { (c, r, desplazado) ->
                PuntoTabla(r.valor, if (c.unidad == "t") c.valor * 1000 else c.valor, dudoso = c.dudoso || r.dudoso || desplazado)
            }.sortedBy { it.radio }.toMutableList()
            // Un salto de radio muy distinto a los demás suele ser una cifra mal leída (p. ej. 28,65 por 20,65).
            if (puntos.size >= 4) {
                val saltos = puntos.zipWithNext { x, y -> y.radio - x.radio }
                val med = saltos.sorted()[saltos.size / 2]
                saltos.forEachIndexed { k, d -> if (med > 0 && (d > med * 2.2 || d < med * 0.3)) puntos[k + 1] = puntos[k + 1].copy(dudoso = true) }
            }
            // Una capacidad que sube al aumentar el radio es sospechosa: se marca para revisar.
            for (k in 1 until puntos.size) if (puntos[k].capacidad > puntos[k - 1].capacidad) {
                puntos[k] = puntos[k].copy(dudoso = true); puntos[k - 1] = puntos[k - 1].copy(dudoso = true)
            }
            val sinPar = sueltos.first.map { "${it.texto} kg (sin radio)" } + sueltos.second.map { "${it.texto} m (sin capacidad)" }
            if (puntos.zipWithNext().any { (a, b) -> b.capacidad > a.capacidad }) adv += "«$etiqueta»: hay capacidades que aumentan con el radio; compruébalas con la foto."
            if (sinPar.isNotEmpty()) adv += "«$etiqueta»: ${sinPar.size} valor(es) sin emparejar; no se rellenan automáticamente."
            tablas += TablaLeida(etiqueta, puntos, sinPar)
        }
        val dud = tablas.sumOf { t -> t.puntos.count { it.dudoso } }
        if (dud > 0) adv += "$dud valor(es) leídos con poca seguridad quedan marcados como pendientes de verificar."
        if (fabricante.isEmpty() || modeloFinal.isEmpty()) adv += "No se pudo leer con seguridad el fabricante o el modelo en la imagen."
        return LecturaTabla(tablas, fabricante, modeloFinal, referencia, unidades, adv, texto)
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
