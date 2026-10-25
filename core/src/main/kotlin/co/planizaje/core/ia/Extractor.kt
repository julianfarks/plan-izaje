package co.planizaje.core.ia

import co.planizaje.core.model.Grua
import co.planizaje.core.util.Unidades

/**
 * Interpreta una explicación en español (dictada o escrita) y propone valores para los formularios.
 *
 * Funciona sin internet, con reglas deterministas. Principios:
 *  - Solo propone lo que aparece en el texto; nunca rellena lo que no se dijo.
 *  - Lo dicho con dudas («unos», «creo», «más o menos») queda APROXIMADO.
 *  - Lo deducido (p. ej. 2 eslingas → 2 ramales) queda APROXIMADO con una nota.
 *  - Una distancia «al camión» no se toma como radio de trabajo: se pregunta.
 *  - Si hay dos interpretaciones (dos pesos distintos, dos grúas posibles) se pregunta.
 */
object Extractor {

    data class Contexto(val gruas: List<Grua> = emptyList(), val gruaActual: Grua? = null)

    private const val NUM = "(\\d+(?:[.,]\\d+)?)"
    private val APROX = Regex("\\b(unos|unas|aprox\\w*|alrededor|cerca de|mas o menos|masomenos|maso|como|creo|calculo|calculamos|estimo|estimamos|parece|por ahi|casi|mas de|menos de|entre|supongo|debe pesar|deberia|tal vez|quizas|quiza|posiblemente|probablemente)\\b")
    private val DOC = Regex("\\bsegun (?:la |el |los |las )?(placa|plano|ficha|certificado|fabricante|factura|documento|catalogo|manual|datos)")
    private val PESADO = Regex("\\b(bascula|pesad[oa]|pesamos|pesaron|peso real)\\b")
    private val NO_CARGA = Regex("\\b(eslinga|eslingas|grillete|grilletes|gancho|capacidad|wll|grua|aguanta|soporta|resiste|balancin|cable|cadena)\\b")

    private data class Frase(val original: String, val norm: String)

    fun extraer(textoEntrada: String, ctx: Contexto = Contexto(), ambito: Ambito = Ambito.GENERAL): Extraccion {
        val texto = NumerosTexto.cifras(textoEntrada.trim())
        if (texto.isBlank()) return Extraccion(textoEntrada, emptyList())
        val frases = texto.split(Regex("(?<=[.!?;\\n])\\s+|\\n+")).map { it.trim() }.filter { it.isNotBlank() }
            .map { Frase(it, NumerosTexto.normalizar(it)) }
        val campos = mutableListOf<CampoExtraido>()
        val preguntas = mutableListOf<Pregunta>()
        val avisos = mutableListOf<String>()

        fun add(c: Clave, valor: String, estado: EstadoCampo, frase: String, nota: String = "", ref: String? = null) {
            val v = valor.trim().trimEnd(',', '.', ';').trim()
            if (v.isEmpty()) return
            val existente = campos.indexOfFirst { it.clave == c }
            if (existente >= 0) {
                if (c.escritura == Escritura.ANADE && !campos[existente].valor.contains(v, true)) {
                    campos[existente] = campos[existente].copy(valor = campos[existente].valor + "; " + v)
                }
                return
            }
            campos += CampoExtraido(c, v.replaceFirstChar { it.uppercase() }, estado, nota, frase, ref)
        }

        if (ambito == Ambito.FICHA_GRUA) {
            extraerFichaGrua(frases, ::add)
            return Extraccion(textoEntrada, campos, preguntas, avisos)
        }

        // ------------------------------------------------------------ PESO
        data class PesoVisto(val kg: Double, val aprox: Boolean, val doc: String?, val frase: String)
        val pesos = mutableListOf<PesoVisto>()
        for (f in frases) {
            val r = Regex("$NUM\\s*(kilogramos|kilos|kilo|kgs|kg|toneladas|tonelada|tons|ton|tn|t)\\b")
            for (m in r.findAll(f.norm)) {
                val antes = f.norm.substring(maxOf(0, m.range.first - 40), m.range.first)
                if (NO_CARGA.containsMatchIn(antes.takeLast(25))) continue
                val n = NumerosTexto.numero(m.groupValues[1]) ?: continue
                val u = m.groupValues[2]
                val kg = if (u.startsWith("t")) n * 1000 else n
                if (kg <= 0) continue
                val despues = f.norm.substring(m.range.last + 1, minOf(f.norm.length, m.range.last + 25))
                val aprox = APROX.containsMatchIn(antes.takeLast(30)) || Regex("^\\s*(mas o menos|aprox|aproximadamente|o algo asi|mas o menos)").containsMatchIn(despues)
                val doc = DOC.find(f.norm)?.groupValues?.get(1)?.let { "según $it" } ?: if (PESADO.containsMatchIn(f.norm)) "pesado en báscula" else null
                pesos += PesoVisto(kg, aprox, doc, f.original)
            }
        }
        val distintos = pesos.distinctBy { it.kg }
        if (distintos.size == 1) {
            val p = distintos.first()
            val estado = if (p.aprox) EstadoCampo.APROXIMADO else EstadoCampo.CONFIRMADO
            val nota = buildString {
                if (p.aprox) append("Dicho como aproximado: debe confirmarse con placa, plano o báscula.")
                else if (p.doc != null) append("Origen indicado: ${p.doc}.")
                else append("Indique de dónde sale el peso (placa, plano, báscula) para darlo por documentado.")
            }
            add(Clave.CARGA_PESO, "${Unidades.fmt(p.kg, 1)} kg", estado, p.frase, nota, ref = p.doc)
        } else if (distintos.size > 1) {
            preguntas += Pregunta(
                "peso", "Se mencionaron varios pesos. ¿Cuál es el peso de la carga?",
                distintos.map { p ->
                    OpcionPregunta(
                        "${Unidades.fmt(p.kg, 1)} kg${if (p.aprox) " (aproximado)" else ""}",
                        listOf(CampoExtraido(Clave.CARGA_PESO, "${Unidades.fmt(p.kg, 1)} kg", if (p.aprox) EstadoCampo.APROXIMADO else EstadoCampo.CONFIRMADO, frase = p.frase, ref = p.doc)),
                    )
                } + OpcionPregunta("Ninguno de esos"),
            )
        }

        // ------------------------------------------------------------ QUÉ SE LEVANTA
        val verbos = "levantar|levantamos|izar|izamos|subir|subimos|bajar|bajamos|mover|movemos|trasladar|montar|desmontar|cargar|descargar|colocar|instalar|retirar|meter|sacar"
        val corte = "(?= que | de unos| de unas| de \\d| con | desde | hasta | hacia | a \\d| a unos| sobre | en el | en la | en una | en un | para | y lo | y la | al lado| a la | a el | al | a los | a las | en |,|\\.|;|$)"
        val reCarga = Regex("\\b(?:$verbos) (?:un |una |el |la |los |las |unos |unas )?([a-z0-9][^,.;]{2,70}?)$corte")
        val reCarga2 = Regex("\\bla carga es (?:un |una |el |la )?([^,.;]{3,70}?)$corte")
        run {
            for (f in frases) {
                val m = reCarga2.find(f.norm) ?: reCarga.find(f.norm) ?: continue
                val g = m.groups[1] ?: continue
                val cand = f.original.substring(g.range.first, g.range.last + 1).trim()
                val nc = NumerosTexto.normalizar(cand)
                if (nc.startsWith("lo ") || nc.startsWith("la ") || nc in setOf("lo", "la", "eso", "esto", "la carga") || NO_CARGA.containsMatchIn(nc.take(12))) continue
                add(Clave.CARGA_DESCRIPCION, cand, EstadoCampo.CONFIRMADO, f.original)
                break
            }
        }

        // ------------------------------------------------------------ DIMENSIONES
        for (f in frases) {
            val m = Regex("$NUM (?:metros |m )?(?:por|x) $NUM(?: (?:metros |m )?(?:por|x) $NUM)?(?: ?(?:metros|m))?\\b").find(f.norm)
            if (m != null && !Regex("\\b(plataforma|cubierta|terraza|patio|lote|losa)\\b").containsMatchIn(f.norm.substring(0, m.range.first).takeLast(30))) {
                val est = if (APROX.containsMatchIn(f.norm.substring(maxOf(0, m.range.first - 30), m.range.first))) EstadoCampo.APROXIMADO else EstadoCampo.CONFIRMADO
                NumerosTexto.numero(m.groupValues[1])?.let { add(Clave.CARGA_LARGO, Unidades.fmtEditable(it), est, f.original) }
                NumerosTexto.numero(m.groupValues[2])?.let { add(Clave.CARGA_ANCHO, Unidades.fmtEditable(it), est, f.original) }
                if (m.groupValues[3].isNotBlank()) NumerosTexto.numero(m.groupValues[3])?.let { add(Clave.CARGA_ALTO, Unidades.fmtEditable(it), est, f.original) }
            }
            for (d in Regex("$NUM (?:metros |m |mts )?de (largo|ancho|alto|longitud)\\b").findAll(f.norm)) {
                val antes = f.norm.substring(0, d.range.first)
                if (d.groupValues[2] == "alto" && Regex("\\b(plataforma|cubierta|terraza|techo|piso|edificio|muro|altura de descarga)\\b").containsMatchIn(antes.takeLast(40))) continue
                val est = if (APROX.containsMatchIn(antes.takeLast(30))) EstadoCampo.APROXIMADO else EstadoCampo.CONFIRMADO
                val v = NumerosTexto.numero(d.groupValues[1]) ?: continue
                val c = when (d.groupValues[2]) { "largo", "longitud" -> Clave.CARGA_LARGO; "ancho" -> Clave.CARGA_ANCHO; else -> Clave.CARGA_ALTO }
                add(c, Unidades.fmtEditable(v), est, f.original)
            }
        }

        // ------------------------------------------------------------ DISTANCIAS Y RADIO
        for (f in frases) {
            val rr = Regex("\\bradio(?: de trabajo)?(?: es| sera| va a ser| queda| de)?(?: de)?(?: unos| unas| aproximadamente| como)? $NUM ?(?:metros|m)?\\b").find(f.norm)
            if (rr != null) {
                val v = NumerosTexto.numero(rr.groupValues[1])
                if (v != null) {
                    val aprox = APROX.containsMatchIn(f.norm.substring(maxOf(0, rr.range.first - 20), minOf(f.norm.length, rr.range.last + 1))) && !Regex("\\bmedid[oa]\\b").containsMatchIn(f.norm)
                    add(
                        Clave.RADIO, Unidades.fmtEditable(v), if (aprox) EstadoCampo.APROXIMADO else EstadoCampo.CONFIRMADO, f.original,
                        if (aprox) "Radio aproximado: mídalo desde el eje de giro de la grúa antes de darlo por bueno." else "Radio indicado por el usuario, medido desde el eje de giro.",
                    )
                }
            }
            val rd = Regex("\\b(?:a|unos|unas|queda a|esta a)? ?(?:unos |unas |aproximadamente |como )?$NUM ?(?:metros|m|mts)(?: de distancia)? (?:del|de la|desde el|desde la|respecto al|respecto a la|al) (camion|grua|vehiculo|carro|eje|columna|truck)\\b")
            for (m in rd.findAll(f.norm)) {
                if (rr != null) continue
                val v = NumerosTexto.numero(m.groupValues[1]) ?: continue
                val aprox = APROX.containsMatchIn(f.norm.substring(maxOf(0, m.range.first - 15), m.range.last + 1))
                val ref = when (m.groupValues[2]) { "camion", "vehiculo", "carro", "truck" -> "el camión"; "grua" -> "la grúa"; else -> "el eje de giro" }
                val txt = "${Unidades.fmt(v, 2)} m respecto a $ref${if (aprox) " (aproximado)" else ""}; referencia pendiente de precisar"
                add(Clave.DISTANCIA_REFERENCIA, txt, EstadoCampo.APROXIMADO, f.original, "Una distancia al camión no es necesariamente el radio de trabajo: el radio se mide desde el eje de giro hasta la vertical del gancho.")
                preguntas += Pregunta(
                    "radio_${preguntas.size}", "Dijiste «${Unidades.fmt(v, 2)} m respecto a $ref». ¿Qué es esa distancia?",
                    listOf(
                        OpcionPregunta("Una referencia del sitio (no es el radio)"),
                        OpcionPregunta(
                            "Es el radio de trabajo, medido desde el eje de giro",
                            listOf(CampoExtraido(Clave.RADIO, Unidades.fmtEditable(v), if (aprox) EstadoCampo.APROXIMADO else EstadoCampo.CONFIRMADO, "Confirmado por el usuario como radio de trabajo.", f.original)),
                        ),
                    ),
                )
            }
        }

        // ------------------------------------------------------------ ALTURAS
        val reAlt = Regex("(?:a|de|hasta|en)? ?(?:unos |unas |aproximadamente |como )?$NUM ?(?:metros|m|mts) de (?:altura|alto)\\b|\\baltura de (?:unos |unas |aproximadamente )?$NUM ?(?:metros|m)?\\b")
        val palDescarga = Regex("\\b(descarg\\w*|dejar|dejarlo|dejarla|colocar|colocarlo|colocarla|poner|subir|subirlo|subirla|plataforma|cubierta|terraza|techo|azotea|destino|montar|instalar|encima)\\b")
        val palRecogida = Regex("\\b(recog\\w*|levantar del|desde el suelo|desde la cama|origen|esta en|esta ubicad\\w*)\\b")
        for (f in frases) {
            for (m in reAlt.findAll(f.norm)) {
                val num = m.groupValues[1].ifBlank { m.groupValues[2] }
                val v = NumerosTexto.numero(num) ?: continue
                val antes = f.norm.substring(0, m.range.first)
                if (Regex("\\b(mide|carga|equipo|pieza|tanque)\\b[^,]*$").containsMatchIn(antes) && !palDescarga.containsMatchIn(antes)) continue
                val aprox = APROX.containsMatchIn(f.norm.substring(maxOf(0, m.range.first - 15), m.range.last + 1))
                val est = if (aprox) EstadoCampo.APROXIMADO else EstadoCampo.CONFIRMADO
                when {
                    palDescarga.containsMatchIn(f.norm) && !palRecogida.containsMatchIn(antes.takeLast(30)) -> add(Clave.ALTURA_DESCARGA, Unidades.fmtEditable(v), est, f.original)
                    palRecogida.containsMatchIn(f.norm) -> add(Clave.ALTURA_RECOGIDA, Unidades.fmtEditable(v), est, f.original)
                    else -> avisos += "Se mencionó una altura de ${Unidades.fmt(v, 2)} m sin decir si es de recogida o de descarga. Indícalo en el paso de maniobra."
                }
            }
        }

        // ------------------------------------------------------------ POSICIONES INICIAL Y FINAL
        for (f in frases) {
            val ini = Regex("\\brecogida(?: se hace| es| sera| va a ser)? (?:desde|en|sobre) (?:el |la |los |las )?([^,.;]{2,50}?)(?= y | que |,|\\.|;|$)").find(f.norm)
                ?: Regex("\\b(?:esta|se encuentra|queda|ubicad[oa]|esta ubicad[oa]|se ubica)(?: ubicad[oa])? ((?:al lado de|junto a|cerca de|frente a|detras de|en|sobre|dentro de|encima de) [^,.;]{2,50}?)(?= y | que | a unos | a \\d|,|\\.|;|$)").find(f.norm)
            if (ini != null && !Regex("\\b(grua|camion)\\b").containsMatchIn(f.norm.substring(0, ini.range.first).takeLast(20))) {
                val g = ini.groups[1]!!
                add(Clave.CARGA_POS_INICIAL, f.original.substring(g.range.first, g.range.last + 1), EstadoCampo.CONFIRMADO, f.original)
            }
            val fin = Regex("\\bdescarga(?: se hace| es| sera| va a ser)? (?:sobre|en|a|hasta) (?:el |la |una |un |los |las )?([^,.;]{2,50}?)(?= y | que | a unos| a \\d|,|\\.|;|$)").find(f.norm)
                ?: Regex("\\b(?:colocar|dejar|poner|subir|llevar|montar|instalar|ubicar|descargar)(?:lo|la|los|las)? (?:en|sobre|encima de|hasta|a) (?:el |la |una |un |los |las )?([^,.;]{2,50}?)(?= y | que | a unos| a \\d| de \\d|,|\\.|;|$)").find(f.norm)
            if (fin != null) {
                val g = fin.groups[1]!!
                val v = f.original.substring(g.range.first, g.range.last + 1)
                if (!Regex("^\\d").containsMatchIn(v)) add(Clave.CARGA_POS_FINAL, v, EstadoCampo.CONFIRMADO, f.original)
            }
        }

        // ------------------------------------------------------------ PROYECTO
        for (f in frases) {
            Regex("\\b(?:el proyecto|la obra|el trabajo)(?: se llama| es el| es la| lo llamamos)? ?(?:se llama|llamad[oa]) ([^,.;]{3,60})").find(f.norm)?.let { m ->
                val g = m.groups[1]!!; add(Clave.NOMBRE, f.original.substring(g.range.first, g.range.last + 1), EstadoCampo.CONFIRMADO, f.original)
            }
            Regex("\\b(?:la obra|el trabajo|el sitio|el izaje|la maniobra)(?: esta| queda| es| sera)? en ((?:la |el )?[^,.;]{3,70}?)(?=,|\\.|;| para | con |$)").find(f.norm)
                ?.let { m -> val g = m.groups[1]!!; add(Clave.UBICACION, f.original.substring(g.range.first, g.range.last + 1), EstadoCampo.CONFIRMADO, f.original) }
                ?: Regex("\\b(?:en|sobre) (?:la )?((?:calle|carrera|avenida|diagonal|transversal|autopista|via|vereda|barrio|municipio de|conjunto|urbanizacion|zona industrial) [^,.;]{1,60}?)(?=,|\\.|;| para | con |$)").find(f.norm)
                    ?.let { m -> val g = m.groups[1]!!; add(Clave.UBICACION, f.original.substring(g.range.first, g.range.last + 1), EstadoCampo.CONFIRMADO, f.original) }
            Regex("\\b(?:el cliente es|cliente:|para el cliente|para la empresa|para la constructora|el trabajo es para|es para la empresa) ([^,.;]{2,60}?)(?=,|\\.|;| que | en |$)").find(f.norm)?.let { m ->
                val g = m.groups[1]!!; add(Clave.CLIENTE, f.original.substring(g.range.first, g.range.last + 1), EstadoCampo.CONFIRMADO, f.original)
            }
            Regex("\\b(?:trabajamos para|somos de|mi empresa es|nuestra empresa es|la empresa responsable es|trabajo en|trabajo para) ([^,.;]{2,60}?)(?=,|\\.|;| y | que |$)").find(f.norm)?.let { m ->
                val g = m.groups[1]!!; add(Clave.EMPRESA, f.original.substring(g.range.first, g.range.last + 1), EstadoCampo.CONFIRMADO, f.original)
            }
            Regex("\\b(?:el )?operador(?: de la grua)? (?:es|sera|va a ser) ([a-z]+(?: [a-z]+)?)").find(f.norm)?.let { m ->
                val g = m.groups[1]!!; add(Clave.OPERADOR, f.original.substring(g.range.first, g.range.last + 1), EstadoCampo.CONFIRMADO, f.original)
            }
            Regex("\\b(?:el )?(?:senalero|rigger|aparejador|banderero)(?: es| sera| va a ser) ([a-z]+(?: [a-z]+)?)").find(f.norm)?.let { m ->
                val g = m.groups[1]!!; add(Clave.SENALERO, f.original.substring(g.range.first, g.range.last + 1), EstadoCampo.CONFIRMADO, f.original)
            }
            if (Regex("\\b(por radio|con radios?|radio de comunicacion|radioteléfono|walkie|avantel)\\b").containsMatchIn(f.norm)) add(Clave.COMUNICACION, "Radio", EstadoCampo.CONFIRMADO, f.original)
            if (Regex("\\b(senas|senales manuales|por senas|con senales)\\b").containsMatchIn(f.norm)) add(Clave.COMUNICACION, "Señales manuales", EstadoCampo.CONFIRMADO, f.original)
        }

        // ------------------------------------------------------------ GRÚA Y CONFIGURACIÓN
        val textoNorm = frases.joinToString(" ") { it.norm }
        val candidatas = ctx.gruas.filter { g ->
            val cods = Regex("\\d{3,6}").findAll(NumerosTexto.normalizar(g.modelo)).map { it.value }.toList()
            cods.any { Regex("\\b$it\\b").containsMatchIn(textoNorm) } ||
                (g.numeroSerie.length >= 4 && textoNorm.contains(NumerosTexto.normalizar(g.numeroSerie)))
        }
        val fraseGrua = frases.firstOrNull { f -> candidatas.any { g -> Regex("\\d{3,6}").findAll(g.modelo).any { f.norm.contains(it.value) } } }?.original ?: ""
        when {
            candidatas.size == 1 -> add(Clave.GRUA, candidatas[0].nombreCorto, EstadoCampo.APROXIMADO, fraseGrua, "Coincide con una grúa de tu biblioteca. Confírmala.", candidatas[0].id)
            candidatas.size > 1 -> preguntas += Pregunta("grua", "La descripción coincide con varias grúas de tu biblioteca. ¿Cuál se usará?",
                candidatas.map { OpcionPregunta(it.nombreCorto, listOf(CampoExtraido(Clave.GRUA, it.nombreCorto, EstadoCampo.CONFIRMADO, ref = it.id))) } + OpcionPregunta("Ninguna"))
            else -> Regex("\\b(palfinger|hiab|pm|fassi|effer|hmf|ferrari|cormach|copma|atlas|tadano|unic|maxilift|xcmg)\\b ?(pk ?)?(\\d{3,6})?").find(textoNorm)?.let { m ->
                avisos += "Mencionaste una grúa ${m.value.trim()} que no está en «Mis grúas». Regístrala para poder usar su tabla de carga."
            }
        }
        for (f in frases) {
            Regex("\\b(\\d{1,2}) extensiones?(?! manual)").find(f.norm)?.let { m ->
                add(Clave.CONFIG_EXTENSIONES, m.groupValues[1], EstadoCampo.CONFIRMADO, f.original, "Se seleccionará la tabla de carga de esa configuración si existe en la ficha de la grúa.")
            }
            Regex("\\bestabilizadores(?: \\w+){0,3}? (desplegados|extendidos|abiertos|totalmente extendidos|completamente extendidos|a medias|parcialmente extendidos|parcialmente|recogidos|cerrados|al maximo|completos|de un solo lado|de un lado)").find(f.norm)?.let { m ->
                add(Clave.ESTABILIZADORES, "Estabilizadores ${m.groupValues[1]} (según el operador)", EstadoCampo.CONFIRMADO, f.original,
                    "Selecciona en el paso 2 la configuración de estabilizadores que corresponde a la tabla de carga.")
            }
            Regex("\\b(?:la grua|el camion|la grua del camion)(?: va| se| queda| estara| va a estar)?(?: a)? (?:ubicad[oa] |parquead[oa] |estacionad[oa] |colocad[oa] |ubicar |parquear )?(?:en|frente a|al lado de|junto a|delante de|detras de|cerca de|sobre) ((?:la |el |una |un )?[^,.;]{3,60}?)(?=,|\\.|;| con | y | para |$)").find(f.norm)?.let { m ->
                val g = m.groups[1]!!
                val v = f.original.substring(g.range.first, g.range.last + 1)
                if (!Regex("^(el |la )?(camion|grua)$").matches(NumerosTexto.normalizar(v).trim())) add(Clave.POSICION_GRUA, v, EstadoCampo.CONFIRMADO, f.original)
            }
        }

        // ------------------------------------------------------------ APAREJOS
        for (f in frases) {
            Regex("\\b(\\d) eslingas?(?: (de cadena|de cable|de acero|de poliester|de nylon|de nilon|de tela|de faja|textil(?:es)?|planas?|redondas?|sinfin))?").find(f.norm)?.let { m ->
                val n = m.groupValues[1].toInt()
                add(Clave.APAREJO_DESCRIPCION, f.original.substring(m.range.first, m.range.last + 1), EstadoCampo.CONFIRMADO, f.original)
                if (n in 1..4) add(Clave.APAREJO_RAMALES, n.toString(), EstadoCampo.APROXIMADO, f.original,
                    "Deducido de «$n eslinga${if (n > 1) "s" else ""}»: confirma cuántos ramales tendrá el eslingado.")
            }
            Regex("\\b(?:(\\d) |sus |los |con |y )?(grilletes?|balancin|viga de izaje|eslabon maestro|argolla maestra|ganchos?)\\b").findAll(f.norm).forEach { m ->
                add(Clave.APAREJO_DESCRIPCION, f.original.substring(m.range.first, m.range.last + 1).trim(), EstadoCampo.CONFIRMADO, f.original)
            }
            if (Regex("\\b(canasta|cesta)\\b").containsMatchIn(f.norm)) add(Clave.APAREJO_ENGANCHE, "Canasta", EstadoCampo.CONFIRMADO, f.original)
            else if (Regex("\\b(ahorcad\\w*|estrangulad\\w*|lazo)\\b").containsMatchIn(f.norm)) add(Clave.APAREJO_ENGANCHE, "Ahorcado", EstadoCampo.CONFIRMADO, f.original)
            Regex("\\b(\\d) (orejas|cancamos|puntos de (?:izaje|izado|amarre|anclaje|sujecion)|argollas|ojales)\\b").find(f.norm)?.let { m ->
                add(Clave.CARGA_PUNTOS, m.groupValues[1], EstadoCampo.CONFIRMADO, f.original)
            }
        }

        // ------------------------------------------------------------ ENTORNO, PRECAUCIONES Y PENDIENTES
        val obstaculos = linkedMapOf(
            "lineas? electricas?|cables? (?:de luz|electricos?|de energia|de alta)|redes? electricas?|cableado|alta tension" to "líneas eléctricas",
            "arbol(?:es)?" to "árboles", "postes?" to "postes", "muros?" to "muro", "techos?|aleros?|marquesinas?" to "techos o aleros",
            "trafico|carros|vehiculos" to "tráfico", "peatones|personas|gente" to "personas en la zona",
            "andenes?|aceras?" to "andén", "cubierta" to "cubierta", "terreno blando|barro|relleno|tierra suelta|alcantarilla|tapa de" to "terreno dudoso",
            "viento|brisa" to "viento", "espacio reducido|poco espacio|estrecho|angosto" to "espacio reducido",
        )
        val vistos = linkedSetOf<String>()
        var fraseObs = ""
        for (f in frases) for ((re, nombre) in obstaculos) if (Regex("\\b(?:$re)\\b").containsMatchIn(f.norm)) {
            if (nombre == "cubierta" && Regex("\\b(colocar|dejar|subir|descarg|poner)").containsMatchIn(f.norm)) continue
            vistos += nombre; if (fraseObs.isEmpty()) fraseObs = f.original
        }
        if (vistos.isNotEmpty()) {
            add(Clave.OBSTACULOS, vistos.joinToString(", "), EstadoCampo.CONFIRMADO, fraseObs, "Dibújalos en el croquis con su posición y medidas para analizar el recorrido.")
            if ("líneas eléctricas" in vistos) avisos += "Se mencionaron líneas eléctricas: dibújalas en el croquis e indica la distancia mínima exigida para su tensión."
        }
        for (f in frases) {
            if (Regex("\\b(cuidado|precaucion|ojo con|atencion|despacio|peligro|riesgo|no se puede|prohibido|hay que tener en cuenta|ten en cuenta|importante)\\b").containsMatchIn(f.norm))
                add(Clave.OBSERVACIONES, f.original, EstadoCampo.CONFIRMADO, f.original)
            if (Regex("\\bcentro de gravedad\\b").containsMatchIn(f.norm))
                add(Clave.CARGA_CDG, f.original, if (APROX.containsMatchIn(f.norm) || f.norm.contains("no se")) EstadoCampo.APROXIMADO else EstadoCampo.CONFIRMADO, f.original)
            if (Regex("\\b(tengo que (?:comprobar|verificar|confirmar|revisar|mirar)|hay que (?:comprobar|verificar|confirmar|revisar)|falta (?:confirmar|comprobar|verificar|saber)|pendiente|por confirmar|todavia (?:no|tengo|falta|hay)|no se (?:cuanto|si|el|la)|no sabemos|sin confirmar|no estoy seguro)\\b").containsMatchIn(f.norm))
                add(Clave.PENDIENTES, f.original, EstadoCampo.APROXIMADO, f.original)
        }

        // Descripción general del trabajo: las propias palabras del usuario.
        if (ambito == Ambito.GENERAL || ambito == Ambito.INFO) {
            val resumen = texto.trim().let { if (it.length > 600) it.take(600).substringBeforeLast(' ') + "…" else it }
            add(Clave.DESCRIPCION_TRABAJO, resumen, EstadoCampo.CONFIRMADO, "", "Tus propias palabras; puedes resumirlas.")
        }

        return Extraccion(textoEntrada, campos, preguntas, avisos)
    }

    private fun extraerFichaGrua(frases: List<Frase>, add: (Clave, String, EstadoCampo, String, String, String?) -> Unit) {
        val fabricantes = mapOf(
            "palfinger" to "Palfinger", "hiab" to "Hiab", "pm" to "PM", "fassi" to "Fassi", "effer" to "Effer", "hmf" to "HMF",
            "ferrari" to "Ferrari", "cormach" to "Cormach", "copma" to "Copma", "atlas" to "Atlas", "tadano" to "Tadano",
            "unic" to "Unic", "maxilift" to "Maxilift", "xcmg" to "XCMG", "amco veba" to "Amco Veba", "bonfiglioli" to "Bonfiglioli",
        )
        for (f in frases) {
            val n = f.norm
            val fab = fabricantes.entries.firstOrNull { Regex("\\b${it.key}\\b").containsMatchIn(n) }
            if (fab != null) {
                add(Clave.GRUA_FABRICANTE, fab.value, EstadoCampo.CONFIRMADO, f.original, "", null)
                Regex("\\b${fab.key}\\b(?: modelo)? ((?:pk ?)?\\d{2,6}(?: ?[a-z]{1,2}-?\\d{1,2})?)").find(n)?.let { m ->
                    val g = m.groups[1]!!; add(Clave.GRUA_MODELO, f.original.substring(g.range.first, g.range.last + 1).uppercase(), EstadoCampo.CONFIRMADO, f.original, "", null)
                }
            }
            if (fab == null) Regex("\\bmodelo (?:es )?([a-z]{0,3} ?\\d{2,6}(?: ?[a-z]{1,2}-?\\d{1,2})?)").find(n)?.let { m ->
                val g = m.groups[1]!!; add(Clave.GRUA_MODELO, f.original.substring(g.range.first, g.range.last + 1).uppercase(), EstadoCampo.CONFIRMADO, f.original, "", null)
            }
            Regex("\\b(?:variante|version)(?: es)? ([a-z0-9-]{1,15})").find(n)?.let { m ->
                val g = m.groups[1]!!; add(Clave.GRUA_VARIANTE, f.original.substring(g.range.first, g.range.last + 1).uppercase(), EstadoCampo.CONFIRMADO, f.original, "", null)
            }
            Regex("\\b(?:numero de serie|n(?:o|umero)? de serie|serial|serie)(?: es| numero)?:? ([a-z0-9][a-z0-9 -]{2,20}?)(?=,|\\.|;| y | la | el |$)").find(n)?.let { m ->
                val g = m.groups[1]!!; add(Clave.GRUA_SERIE, f.original.substring(g.range.first, g.range.last + 1).uppercase().replace(" ", ""), EstadoCampo.CONFIRMADO, f.original, "", null)
            }
            Regex("\\bplaca(?: del camion| del vehiculo)?(?: es)?:? ([a-z]{3} ?-?\\d{2,3}[a-z]?)").find(n)?.let { m ->
                add(Clave.GRUA_PLACA, m.groupValues[1].uppercase().replace(" ", "").replace("-", ""), EstadoCampo.CONFIRMADO, f.original, "", null)
            }
            Regex("\\b(\\d{1,2}) extensiones? manual(?:es)?").find(n)?.let { add(Clave.GRUA_EXT_MANUALES, it.groupValues[1], EstadoCampo.CONFIRMADO, f.original, "", null) }
            Regex("\\b(\\d{1,2}) extensiones?(?: hidraulicas?)?\\b(?! manual)").find(n)?.let { add(Clave.GRUA_EXTENSIONES, it.groupValues[1], EstadoCampo.CONFIRMADO, f.original, "", null) }
            Regex("\\b(?:camion|vehiculo|chasis)(?: es)? (?:un |una )?((?:kenworth|international|chevrolet|hino|isuzu|volvo|scania|mercedes|freightliner|foton|jac|hyundai|mack|renault|man|iveco|dongfeng|daf|ford|nissan)[^,.;]{0,30})").find(n)?.let { m ->
                val g = m.groups[1]!!; add(Clave.GRUA_VEHICULO, f.original.substring(g.range.first, g.range.last + 1), EstadoCampo.CONFIRMADO, f.original, "", null)
            }
        }
    }
}
