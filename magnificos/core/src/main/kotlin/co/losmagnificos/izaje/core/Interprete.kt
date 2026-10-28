package co.losmagnificos.izaje.core

/**
 * Convierte la descripción del izaje (dictada o escrita) en datos estructurados.
 * Funciona sin internet, con reglas deterministas en español. No inventa nada: lo que no se dice queda en `null`.
 */
object Interprete {

    private const val NUM = "(\\d+(?:[.,]\\d+)?)"
    private const val PESO_U = "(toneladas?|tons?|t|kilogramos?|kilos?|kgs?)"
    private const val LONG_U = "(metros?|mts?|cms?|centimetros?|m)"

    private val reW = Regex("$NUM\\s*$PESO_U\\b")
    private val reL = Regex("$NUM\\s*$LONG_U\\b")

    private const val CUE = "(?:capacidad(?:\\s+de)?|puede\\s+levantar|levanta|levantan|soporta|aguanta|permite\\s+levantar|grua\\s+tiene)"

    // Radio primero: «a 2 metros de radio tiene una capacidad de 18 toneladas»
    private val reCapRadioPrimero = Regex(
        "\\b(?:a|en|con|hasta)\\s+$NUM\\s*$LONG_U\\b(?:\\s+de\\s+(?:radio|alcance|distancia|volado|extension))?" +
            "[^.;\\d]{0,60}?\\b$CUE\\s*(?:de\\s+)?$NUM\\s*$PESO_U\\b",
    )

    // Capacidad primero: «levanta 5 toneladas a 10 metros»
    private val reCapCapacidadPrimero = Regex(
        "\\b$CUE\\s*(?:de\\s+)?$NUM\\s*$PESO_U\\s+(?:a|en)\\s+$NUM\\s*$LONG_U\\b(?:\\s+de\\s+(?:radio|alcance))?",
    )

    // «la capacidad (de la grúa) a 2 metros es de 18 toneladas»
    private val reCapAntesRadio = Regex(
        "\\bcapacidad(?:\\s+de\\s+la\\s+grua)?\\s+(?:a|en)\\s+$NUM\\s*$LONG_U\\b(?:\\s+de\\s+(?:radio|alcance))?\\s*(?:es|de|=|:)?\\s*(?:de\\s+)?$NUM\\s*$PESO_U\\b",
    )

    // Listas encadenadas tras una capacidad
    private val reCadenaCapacidadPrimero = Regex("\\s*(?:,\\s*|y\\s+)$NUM\\s*$PESO_U\\s+(?:a|en)\\s+$NUM\\s*$LONG_U\\b(?:\\s+de\\s+(?:radio|alcance))?")
    private val reCadenaRadioPrimero = Regex(
        "\\s*(?:,\\s*|y\\s+)(?:a|en)\\s+$NUM\\s*$LONG_U\\b(?:\\s+de\\s+(?:radio|alcance))?\\s*(?:es|de|=|:)?\\s*(?:de\\s+)?$NUM\\s*$PESO_U\\b",
    )

    // Tras «…18 toneladas» viene otro «a 2 metros»: la capacidad pertenece a ese radio (capacidad primero), no al anterior.
    private val reRadioSiguiente = Regex("^\\s*(?:de\\s+(?:radio|alcance)\\s+)?(?:a|en)\\s+$NUM\\s*$LONG_U\\b")
    private val reCueDespues = Regex("^(?:\\s+de\\s+(?:radio|alcance))?[^.;\\d]{0,60}?\\b$CUE")

    private val reNominal = Regex(
        "\\b(?:capacidad\\s+(?:maxima\\s+)?nominal|capacidad\\s+maxima|carga\\s+maxima\\s+nominal|nominal)\\b[^.;\\d]{0,30}?$NUM\\s*$PESO_U\\b",
    )

    private val reAltura1 = Regex(
        "\\balturas?(?:\\s+de\\s+(?:trabajo|elevacion|izaje|levantamiento|gancho|maniobra|carga))?[^.;\\d]{0,25}?$NUM\\s*$LONG_U\\b",
    )
    private val reAltura2 = Regex("$NUM\\s*$LONG_U\\s+de\\s+altura\\b")

    private val marcas = "hiab|palfinger|pm|fassi|effer|atlas|liebherr|grove|tadano|manitex|terex|kato|xcmg|sany|zoomlion|manitowoc|demag|maeda|unic|cormach|copma|hyva|amco|stellar|iowa|ramsey|jekko|ormet|cargotec"
    private val reMarca = Regex("\\b($marcas)\\b")
    private val reToken = Regex("\\s+([a-z0-9][a-z0-9\\-/]*)")
    private val paradaEquipo = setOf(
        "tiene", "con", "de", "que", "y", "la", "el", "para", "a", "en", "es", "un", "una", "voy", "capacidad", "pluma",
        "puede", "levanta", "esta", "tipo", "como", "segun", "marca", "modelo", "del", "al", "su", "se", "lo", "los", "las",
    )

    private val reSep = Regex("([.;\\n]+)|(,\\s+)|(\\s+y\\s+)|(\\s+pero\\s+)|(\\s+luego\\s+)|(\\s+despues\\s+)")
    private val reAprox = Regex("\\b(?:aproximadamente|aprox|unos|unas|mas o menos|cerca de|casi|creo|supongo|estimad\\w*|alrededor de|como de)\\b")
    private val rePeso = Regex("\\bpes(?:a|an|ando|o)\\b|\\bmasa\\b|\\bcombinad\\w*|\\bde peso\\b")
    private val reCada = Regex("\\bcada\\b|\\bc/u\\b|\\bpor (?:eslinga|grillete|unidad)\\b|\\bunitari\\w*")

    private enum class Suj(val re: Regex) {
        GANCHO(Regex("\\bgancho")),
        ESLINGA(Regex("\\b(?:eslinga|estrobo|aparejo de cable)")),
        GRILLETE(Regex("\\bgrillete")),
        BALANCIN(Regex("\\b(?:balancin|barra separadora|viga separadora|ecualizador)")),
        ACCESORIO(Regex("\\b(?:accesorios?|aparejos?)\\b")),
        CARGA(Regex("\\bcarga\\b")),
    }

    private enum class TipoRadio { INICIAL, FINAL, MAXIMO, GENERICO }

    private val reIni = Regex("\\b(?:recoj\\w*|recog\\w*|levant\\w*|inicial|recogida|origen|salida|desde|tomo|tomar|engancho|enganchar|izar|izo|alz\\w*)")
    private val reFin = Regex("\\b(?:coloc\\w*|deposit\\w*|descarg\\w*|final|destino|dejo|dejar\\w*|bajo|bajar\\w*|ubic\\w*|pongo|poner\\w*|llevo|llevar\\w*|traslad\\w*|instal\\w*|hasta|llegada)")
    private val reMax = Regex("\\b(?:maxim\\w*|mayor)")

    // ------------------------------------------------------------------ API

    fun interpretar(texto: String, previo: DatosIzaje? = null): DatosIzaje {
        val nuevo = Lector(Texto.cifras(texto)).leer()
        return if (previo == null) nuevo else fusionar(nuevo, previo)
    }

    /** Al volver a interpretar se respetan los campos que el usuario corrigió a mano. */
    fun fusionar(nuevo: DatosIzaje, previo: DatosIzaje): DatosIzaje {
        var r = nuevo
        for (k in previo.editados) r = Edicion.copiarCampo(r, previo, k)
        if (Campo.CAPACIDADES in previo.editados) {
            // lo dictado de nuevo se suma a lo que el usuario corrigió; si coincide el radio, gana lo corregido
            val extra = nuevo.capacidades.filter { n -> previo.capacidades.none { kotlin.math.abs(it.radioM - n.radioM) < 1e-6 } }
            r = r.copy(capacidades = (previo.capacidades + extra).sortedBy { it.radioM })
        }
        return r.copy(editados = previo.editados, aproximados = r.aproximados - previo.editados)
    }

    // ------------------------------------------------------------------ lector

    private class Lector(val c: String) {
        val n: String = Texto.normalizar(c)
        val r = StringBuilder(n)

        var equipo: String? = null
        var nominal: Double? = null
        var carga: Double? = null
        var gancho: Double? = null
        var eslingasKg: Double? = null
        var eslingaUnit: Double? = null
        var grilletesKg: Double? = null
        var grilleteUnit: Double? = null
        var nGrilletes: Int? = null
        var balancin: Double? = null
        var otros: Double? = null
        var rIni: Double? = null
        var rFin: Double? = null
        var rMax: Double? = null
        var rGen: Double? = null
        var altura: Double? = null
        val caps = mutableListOf<PuntoCapacidad>()
        var nEsl: Int? = null
        var wll: Double? = null
        var angulo: Double? = null
        var refAng = RefAngulo.SIN_CONFIRMAR
        var ramales: Int? = null
        var portantes: Int? = null
        var amarre: Amarre? = null
        var sinEslingas = false
        val incluidos = mutableSetOf<String>()
        val aprox = mutableSetOf<String>()
        val frases = linkedMapOf<String, String>()
        val notas = mutableListOf<String>()
        val config = mutableListOf<String>()

        fun kg(num: String, u: String): Double? = Texto.numero(num)?.let { if (u.startsWith("t")) it * 1000.0 else it }
        fun mt(num: String, u: String): Double? = Texto.numero(num)?.let { if (u.startsWith("c")) it / 100.0 else it }
        fun tapar(rango: IntRange) { for (i in rango) r.setCharAt(i, ' ') }
        fun snippet(rango: IntRange): String = c.substring(rango.first, rango.last + 1).trim()
        fun poner(clave: String, frase: String, esAprox: Boolean) {
            if (clave !in frases) frases[clave] = frase
            if (esAprox) aprox += clave
        }

        fun leer(): DatosIzaje {
            capacidades()
            equipo()
            alturas()
            incluidos()
            amarreYEslingas()
            clausulas()
            if (rMax == null && rGen != null && rGen != rIni && rGen != rFin) rMax = rGen
            val unico = caps.groupBy { it.radioM }.map { (_, v) -> v.last() }.sortedBy { it.radioM }
            return DatosIzaje(
                equipo = equipo, capacidadNominalKg = nominal, cargaKg = carga, ganchoKg = gancho,
                eslingasKg = eslingasKg, eslingaUnitKg = eslingaUnit, grilletesKg = grilletesKg, grilleteUnitKg = grilleteUnit,
                nGrilletes = nGrilletes, balancinKg = balancin, otrosKg = otros,
                radioInicialM = rIni, radioFinalM = rFin, radioMaximoM = rMax, alturaM = altura,
                capacidades = unico, nEslingas = nEsl, wllEslingaKg = wll, anguloGrados = angulo, refAngulo = refAng,
                ramales = ramales, ramalesPortantes = portantes, amarre = amarre, sinEslingas = sinEslingas,
                incluidos = incluidos, aproximados = aprox, frases = frases, notas = notas.distinct(), configuracion = config.distinct(),
            )
        }

        // --- capacidades de la grúa (nunca se confunden con el peso de la carga) ---
        private fun tapado(rango: IntRange): Boolean = r.substring(rango.first, rango.last + 1) != n.substring(rango.first, rango.last + 1)

        private fun registrar(radio: Double?, cap: Double?, rango: IntRange) {
            if (radio != null && cap != null) {
                caps += PuntoCapacidad(radio, cap)
                frases["capacidad:${Formato.editable(radio)}"] = snippet(rango)
            }
            tapar(rango)
        }

        private fun encadenar(desde: Int, re: Regex, radioPrimero: Boolean) {
            var fin = desde
            while (true) {
                val sig = re.find(r.toString(), fin)?.takeIf { it.range.first == fin } ?: break
                val g = sig.groupValues
                if (radioPrimero) registrar(mt(g[1], g[2]), kg(g[3], g[4]), sig.range)
                else registrar(mt(g[3], g[4]), kg(g[1], g[2]), sig.range)
                fin = sig.range.last + 1
            }
        }

        fun capacidades() {
            for (m in reCapAntesRadio.findAll(r.toString()).toList()) {
                if (tapado(m.range)) continue
                val g = m.groupValues
                registrar(mt(g[1], g[2]), kg(g[3], g[4]), m.range)
                encadenar(m.range.last + 1, reCadenaRadioPrimero, radioPrimero = true)
            }
            for (m in reCapRadioPrimero.findAll(r.toString()).toList()) {
                if (tapado(m.range)) continue
                val tail = r.substring(m.range.last + 1)
                val sig = reRadioSiguiente.find(tail)
                if (sig != null && !reCueDespues.containsMatchIn(tail.substring(sig.range.last + 1))) continue
                val g = m.groupValues
                registrar(mt(g[1], g[2]), kg(g[3], g[4]), m.range)
            }
            for (m in reCapCapacidadPrimero.findAll(r.toString()).toList()) {
                if (tapado(m.range)) continue
                val g = m.groupValues
                registrar(mt(g[3], g[4]), kg(g[1], g[2]), m.range)
                encadenar(m.range.last + 1, reCadenaCapacidadPrimero, radioPrimero = false)
            }
            reNominal.find(r.toString())?.let { m ->
                nominal = kg(m.groupValues[1], m.groupValues[2])
                if (nominal != null) frases[Campo.NOMINAL] = snippet(m.range)
                tapar(m.range)
            }
        }

        // --- equipo: marca + modelo ---
        fun equipo() {
            for (m in reMarca.findAll(n)) {
                var pos = m.range.last + 1
                var fin = m.range.last
                var k = 0
                var conDigito = false
                while (k < 4) {
                    val t = reToken.find(n, pos)?.takeIf { it.range.first == pos } ?: break
                    val w = t.groupValues[1]
                    if (w in paradaEquipo) break
                    if (w.any { it.isDigit() }) conDigito = true
                    fin = t.range.last; pos = fin + 1; k++
                }
                if (!conDigito) continue
                val nombre = c.substring(m.range.first, fin + 1).split(Regex("\\s+")).joinToString(" ") { w ->
                    if (w.any { it.isDigit() } || w.length <= 2) w.uppercase() else w.lowercase().replaceFirstChar { it.uppercase() }
                }
                equipo = nombre
                frases[Campo.EQUIPO] = c.substring(m.range.first, fin + 1)
                tapar(m.range.first..fin)
                break
            }
        }

        // --- altura de elevación (no se confunde con el radio) ---
        fun alturas() {
            for (re in listOf(reAltura1, reAltura2)) {
                val m = re.find(r.toString()) ?: continue
                val v = mt(m.groupValues[1], m.groupValues[2])
                if (v != null && altura == null) {
                    altura = v
                    poner(Campo.ALTURA, snippet(m.range), reAprox.containsMatchIn(frasePrevia(m.range)))
                }
                tapar(m.range)
            }
        }

        fun frasePrevia(rango: IntRange): String {
            val ini = maxOf(0, rango.first - 30)
            return n.substring(ini, rango.last + 1)
        }

        // --- «ya incluido en la carga» ---
        fun incluidos() {
            var ini = 0
            val txt = r.toString()
            val cortes = Regex("[.;\\n]+").findAll(txt).map { it.range.first }.toList() + txt.length
            for (fin in cortes) {
                val s = txt.substring(ini, fin)
                ini = fin + 1
                if (!Regex("\\bincluy\\w*|\\bincluid\\w*").containsMatchIn(s)) continue
                if (Regex("sin incluir|no incluy\\w*|no (?:esta|estan) incluid\\w*|no incluid\\w*").containsMatchIn(s)) continue
                if (Regex("\\bgancho").containsMatchIn(s)) incluidos += "gancho"
                if (Suj.ESLINGA.re.containsMatchIn(s)) incluidos += "eslingas"
                if (Suj.GRILLETE.re.containsMatchIn(s)) incluidos += "grilletes"
                if (Suj.BALANCIN.re.containsMatchIn(s)) incluidos += "balancin"
                if (Regex("\\baccesorios?\\b").containsMatchIn(s)) incluidos += "otros"
            }
        }

        // --- tipo de amarre y «sin eslingas» (sobre todo el texto) ---
        fun amarreYEslingas() {
            val t = r.toString()
            amarre = when {
                Regex("\\bcanasta\\b").containsMatchIn(t) -> Amarre.CANASTA
                Regex("\\bahorcad\\w*|\\bestrangul\\w*|\\bchoker\\b|\\bnudo corredizo").containsMatchIn(t) -> Amarre.AHORCADO
                Regex("(?:amarre|enganche|eslingado|eslingas?)[^.;]{0,25}\\bdirect[oa]s?\\b|\\bdirect[oa]s?\\b[^.;]{0,15}(?:amarre|enganche|eslingas?)").containsMatchIn(t) -> Amarre.DIRECTO
                else -> null
            }
            if (amarre != null) {
                val m = Regex("\\bcanasta\\b|\\bahorcad\\w*|\\bestrangul\\w*|\\bchoker\\b|\\bdirect[oa]s?\\b").find(t)
                m?.let { frases[Campo.AMARRE] = c.substring(maxOf(0, it.range.first - 20), minOf(c.length, it.range.last + 21)).trim() }
            }
            if (Regex("\\bsin\\s+(?:usar\\s+)?(?:eslingas|aparejos|estrobos)|\\bno\\s+(?:uso|usare|voy\\s+a\\s+usar|voy\\s+a\\s+utilizar|utilizo|utilizare)\\s+(?:eslingas|aparejos|estrobos)").containsMatchIn(t)) {
                sinEslingas = true
            }
        }

        // --- cláusula por cláusula ---
        private class Cl(val rango: IntRange, val duro: Boolean)

        private fun cortar(): List<Cl> {
            val t = r.toString()
            val out = mutableListOf<Cl>()
            var ini = 0
            var duro = true
            for (m in reSep.findAll(t)) {
                if (m.range.first > ini) out += Cl(ini until m.range.first, duro)
                ini = m.range.last + 1
                duro = m.groups[1] != null
            }
            if (ini < t.length) out += Cl(ini until t.length, duro)
            return out
        }

        fun sujetoDe(x: String): Suj? =
            Suj.values().mapNotNull { s -> s.re.find(x)?.let { s to it.range.first } }.minByOrNull { it.second }?.first

        private fun clausulas() {
            var arrastre: Suj? = null
            for (cl in cortar()) {
                if (cl.duro) arrastre = null
                val x = r.substring(cl.rango.first, cl.rango.last + 1)
                if (x.isBlank()) continue
                val fr = snippet(cl.rango)
                val esAprox = reAprox.containsMatchIn(x)
                val propio = sujetoDe(x)
                if (propio != null) arrastre = propio
                val suj = propio ?: arrastre

                // cantidades
                Regex("$NUM\\s+(?:eslingas?|estrobos?)\\b").find(x)?.let { m ->
                    if (nEsl == null) { nEsl = Texto.numero(m.groupValues[1])?.toInt(); poner(Campo.N_ESLINGAS, fr, false) }
                }
                Regex("$NUM\\s+ramales\\b").find(x)?.let { m -> if (ramales == null) ramales = Texto.numero(m.groupValues[1])?.toInt() }
                (Regex("$NUM\\s+(?:ramales\\s+)?portantes?\\b").find(x) ?: Regex("portantes?\\s+$NUM").find(x))?.let { m ->
                    if (portantes == null) portantes = Texto.numero(m.groupValues[1])?.toInt()
                }
                Regex("$NUM\\s+grilletes?\\b").find(x)?.let { m -> if (nGrilletes == null) nGrilletes = Texto.numero(m.groupValues[1])?.toInt() }

                // ángulo de las eslingas
                Regex("$NUM\\s*(?:grados?|°|º)").find(x)?.let { m ->
                    val ini = maxOf(0, cl.rango.first - 60)
                    val ventana = r.substring(ini, minOf(r.length, cl.rango.last + 1 + 60))
                    if (Regex("eslinga|ramal|angul|abertura|apertura|aparejo|estrobo").containsMatchIn(ventana) && angulo == null) {
                        angulo = Texto.numero(m.groupValues[1])
                        poner(Campo.ANGULO, fr, esAprox)
                        val h = Regex("horizontal").find(ventana)
                        val v = Regex("vertical").find(ventana)
                        val pos = (cl.rango.first - ini) + m.range.first
                        refAng = when {
                            h != null && v != null ->
                                if (kotlin.math.abs(h.range.first - pos) <= kotlin.math.abs(v.range.first - pos)) RefAngulo.HORIZONTAL else RefAngulo.VERTICAL
                            h != null -> RefAngulo.HORIZONTAL
                            v != null -> RefAngulo.VERTICAL
                            else -> RefAngulo.SIN_CONFIRMAR
                        }
                    }
                }

                // porcentajes mencionados (pendiente del terreno, viento, etc.): se conservan como información
                Regex("$NUM\\s*(?:%|por ciento)").find(x)?.let { notas += "Porcentaje mencionado: «$fr»" }

                // pesos
                for (w in reW.findAll(x)) {
                    val kgv = kg(w.groupValues[1], w.groupValues[2]) ?: continue
                    val antes = x.substring(maxOf(0, w.range.first - 28), w.range.first)
                    val esPeso = rePeso.containsMatchIn(antes)
                    // En kg (y sin decir «capacidad») un accesorio se entiende como peso; en toneladas, como capacidad (WLL).
                    val esPesoAcc = esPeso || !w.groupValues[2].startsWith("t")
                    val cada = reCada.containsMatchIn(x)
                    when (suj) {
                        Suj.GANCHO -> if (esPesoAcc) { gancho = gancho ?: kgv; poner(Campo.GANCHO, fr, esAprox) } else notas += "Capacidad del gancho mencionada: «$fr»"
                        Suj.ESLINGA -> if (esPeso) {
                            if (cada) { eslingaUnit = eslingaUnit ?: kgv; poner(Campo.ESLINGA_UNIT_KG, fr, esAprox) }
                            else { eslingasKg = eslingasKg ?: kgv; poner(Campo.ESLINGAS_KG, fr, esAprox) }
                        } else { wll = wll ?: kgv; poner(Campo.WLL, fr, esAprox) }
                        Suj.GRILLETE -> if (esPesoAcc) {
                            if (cada) { grilleteUnit = grilleteUnit ?: kgv; poner(Campo.GRILLETE_UNIT_KG, fr, esAprox) }
                            else { grilletesKg = grilletesKg ?: kgv; poner(Campo.GRILLETES_KG, fr, esAprox) }
                        } else notas += "Capacidad de grilletes mencionada: «$fr»"
                        Suj.BALANCIN -> if (esPesoAcc) { balancin = balancin ?: kgv; poner(Campo.BALANCIN, fr, esAprox) } else notas += "Capacidad del balancín mencionada: «$fr»"
                        Suj.ACCESORIO -> if (esPesoAcc) { otros = otros ?: kgv; poner(Campo.OTROS, fr, esAprox) } else notas += "Capacidad de accesorios mencionada: «$fr»"
                        else -> {
                            if (Regex("\\b(?:grua|camion|vehiculo|equipo de izaje)\\b").containsMatchIn(x) && esPeso) {
                                notas += "Peso mencionado que no se usó (no es de la carga): «$fr»"
                            } else if (carga == null) {
                                carga = kgv; poner(Campo.CARGA, fr, esAprox)
                            } else {
                                notas += "Otro peso mencionado que no se usó: «$fr»"
                            }
                        }
                    }
                }

                // radios (con la distancia mencionada, sin confundir dimensiones ni distancias a obstáculos)
                for (m in reL.findAll(x)) {
                    val v = mt(m.groupValues[1], m.groupValues[2]) ?: continue
                    val antes = x.substring(0, m.range.first)
                    val despues = x.substring(m.range.last + 1)
                    val tieneRadio = Regex("\\bradio|\\balcance").containsMatchIn(x)
                    if (Regex("^\\s*(?:de\\s+)?(?:largo|ancho|alto|diametro|longitud|espesor|profundidad)").containsMatchIn(despues) ||
                        Regex("(?:largo|ancho|alto|diametro|longitud|espesor|profundidad)\\s*(?:de|es|mide)?\\s*$").containsMatchIn(antes)
                    ) { notas += "Dimensión mencionada: «$fr»"; continue }
                    if (suj == Suj.ESLINGA && !tieneRadio) { notas += "Longitud o distancia de eslinga mencionada: «$fr»"; continue }
                    if (!tieneRadio && Regex("\\b(?:camion|vehiculo|zanja|poste|linea|cable|edificio|pared|muro|arbol|borde|techo)\\b").containsMatchIn(x)) {
                        notas += "Distancia mencionada (no se tomó como radio): «$fr»"; continue
                    }
                    var mejor: Pair<Int, TipoRadio>? = null
                    for ((tipo, re) in listOf(TipoRadio.INICIAL to reIni, TipoRadio.FINAL to reFin, TipoRadio.MAXIMO to reMax)) {
                        for (k in re.findAll(antes)) if (mejor == null || k.range.first > mejor.first) mejor = k.range.first to tipo
                    }
                    if (mejor == null) {
                        for ((tipo, re) in listOf(TipoRadio.INICIAL to reIni, TipoRadio.FINAL to reFin, TipoRadio.MAXIMO to reMax)) {
                            if (mejor == null && re.containsMatchIn(despues)) mejor = 0 to tipo
                        }
                    }
                    val tipo = mejor?.second ?: if (tieneRadio) TipoRadio.GENERICO else null
                    when (tipo) {
                        TipoRadio.INICIAL -> if (rIni == null) { rIni = v; poner(Campo.RADIO_INICIAL, fr, esAprox) }
                        TipoRadio.FINAL -> if (rFin == null) { rFin = v; poner(Campo.RADIO_FINAL, fr, esAprox) }
                        TipoRadio.MAXIMO -> if (rMax == null) { rMax = v; poner(Campo.RADIO_MAX, fr, esAprox) }
                        TipoRadio.GENERICO -> if (rGen == null) { rGen = v; poner(Campo.RADIO_MAX, fr, esAprox) }
                        null -> {}
                    }
                }

                // centro de gravedad, puntos de sujeción, configuración de la grúa y otra información útil
                var clasificada = false
                if (Regex("centro de gravedad|\\bcdg\\b|puntos? de (?:sujecion|izaje|amarre|enganche|agarre)|cancamos?|argollas?|orejas? de izaje").containsMatchIn(x)) {
                    notas += "Centro de gravedad / puntos de sujeción: «$fr»"; clasificada = true
                }
                if (Regex("estabilizador|extension|pluma|\\bsector\\b|plumin|\\bjib\\b|\\bapoyos\\b|\\bgatos\\b").containsMatchIn(x)) {
                    config += fr; clasificada = true
                }
                if (!clasificada && Regex("viento|lluvia|terreno|suelo|pendiente|talud|nivelad|calzas|electric|senalero|banderillero|personal|obstaculo|transito|peaton|zanja|edificio|techo").containsMatchIn(x)) {
                    notas += "Información dictada: «$fr»"
                }
            }
        }
    }
}

/** Edición manual de un dato desde la pantalla de revisión. */
object Edicion {

    /** Escribe en `d` el valor de texto `valor` para el campo `clave` (vacío = quitar el dato) y lo marca como corregido por el usuario. */
    fun establecer(d: DatosIzaje, clave: String, valor: String): DatosIzaje {
        val v = valor.trim()
        fun kg() = if (v.isEmpty()) null else Texto.pesoKg(v)
        fun m() = if (v.isEmpty()) null else Texto.longitudM(v)
        fun entero() = if (v.isEmpty()) null else Texto.simple(v)?.toInt()
        val r = when (clave) {
            Campo.EQUIPO -> d.copy(equipo = v.ifEmpty { null })
            Campo.NOMINAL -> d.copy(capacidadNominalKg = kg())
            Campo.CARGA -> d.copy(cargaKg = kg())
            Campo.GANCHO -> d.copy(ganchoKg = kg())
            Campo.ESLINGAS_KG -> d.copy(eslingasKg = kg())
            Campo.ESLINGA_UNIT_KG -> d.copy(eslingaUnitKg = kg())
            Campo.GRILLETES_KG -> d.copy(grilletesKg = kg())
            Campo.GRILLETE_UNIT_KG -> d.copy(grilleteUnitKg = kg())
            Campo.N_GRILLETES -> d.copy(nGrilletes = entero())
            Campo.BALANCIN -> d.copy(balancinKg = kg())
            Campo.OTROS -> d.copy(otrosKg = kg())
            Campo.RADIO_INICIAL -> d.copy(radioInicialM = m())
            Campo.RADIO_FINAL -> d.copy(radioFinalM = m())
            Campo.RADIO_MAX -> d.copy(radioMaximoM = m())
            Campo.ALTURA -> d.copy(alturaM = m())
            Campo.N_ESLINGAS -> d.copy(nEslingas = entero())
            Campo.WLL -> d.copy(wllEslingaKg = kg())
            Campo.ANGULO -> d.copy(anguloGrados = if (v.isEmpty()) null else Texto.simple(v))
            Campo.RAMALES -> d.copy(ramales = entero())
            Campo.PORTANTES -> d.copy(ramalesPortantes = entero())
            else -> d
        }
        return r.copy(editados = r.editados + clave, aproximados = r.aproximados - clave)
    }

    fun conCapacidades(d: DatosIzaje, lista: List<PuntoCapacidad>): DatosIzaje =
        d.copy(capacidades = lista.sortedBy { it.radioM }, editados = d.editados + Campo.CAPACIDADES)

    /** Añade o reemplaza la capacidad informada para un radio. */
    fun ponerCapacidad(d: DatosIzaje, radioM: Double, capacidadKg: Double): DatosIzaje =
        conCapacidades(d, d.capacidades.filterNot { kotlin.math.abs(it.radioM - radioM) < 1e-6 } + PuntoCapacidad(radioM, capacidadKg))

    fun conAmarre(d: DatosIzaje, a: Amarre?): DatosIzaje = d.copy(amarre = a, editados = d.editados + Campo.AMARRE)
    fun conRefAngulo(d: DatosIzaje, ref: RefAngulo): DatosIzaje = d.copy(refAngulo = ref, editados = d.editados + Campo.REF_ANGULO)
    fun conSinEslingas(d: DatosIzaje, v: Boolean): DatosIzaje = d.copy(sinEslingas = v, editados = d.editados + Campo.SIN_ESLINGAS)

    /** Copia de `origen` a `dest` el valor del campo `clave`. */
    fun copiarCampo(dest: DatosIzaje, origen: DatosIzaje, clave: String): DatosIzaje = when (clave) {
        Campo.EQUIPO -> dest.copy(equipo = origen.equipo)
        Campo.NOMINAL -> dest.copy(capacidadNominalKg = origen.capacidadNominalKg)
        Campo.CARGA -> dest.copy(cargaKg = origen.cargaKg)
        Campo.GANCHO -> dest.copy(ganchoKg = origen.ganchoKg)
        Campo.ESLINGAS_KG -> dest.copy(eslingasKg = origen.eslingasKg)
        Campo.ESLINGA_UNIT_KG -> dest.copy(eslingaUnitKg = origen.eslingaUnitKg)
        Campo.GRILLETES_KG -> dest.copy(grilletesKg = origen.grilletesKg)
        Campo.GRILLETE_UNIT_KG -> dest.copy(grilleteUnitKg = origen.grilleteUnitKg)
        Campo.N_GRILLETES -> dest.copy(nGrilletes = origen.nGrilletes)
        Campo.BALANCIN -> dest.copy(balancinKg = origen.balancinKg)
        Campo.OTROS -> dest.copy(otrosKg = origen.otrosKg)
        Campo.RADIO_INICIAL -> dest.copy(radioInicialM = origen.radioInicialM)
        Campo.RADIO_FINAL -> dest.copy(radioFinalM = origen.radioFinalM)
        Campo.RADIO_MAX -> dest.copy(radioMaximoM = origen.radioMaximoM)
        Campo.ALTURA -> dest.copy(alturaM = origen.alturaM)
        Campo.CAPACIDADES -> dest.copy(capacidades = origen.capacidades)
        Campo.N_ESLINGAS -> dest.copy(nEslingas = origen.nEslingas)
        Campo.WLL -> dest.copy(wllEslingaKg = origen.wllEslingaKg)
        Campo.ANGULO -> dest.copy(anguloGrados = origen.anguloGrados)
        Campo.REF_ANGULO -> dest.copy(refAngulo = origen.refAngulo)
        Campo.RAMALES -> dest.copy(ramales = origen.ramales)
        Campo.PORTANTES -> dest.copy(ramalesPortantes = origen.ramalesPortantes)
        Campo.AMARRE -> dest.copy(amarre = origen.amarre)
        Campo.SIN_ESLINGAS -> dest.copy(sinEslingas = origen.sinEslingas)
        else -> dest
    }
}
