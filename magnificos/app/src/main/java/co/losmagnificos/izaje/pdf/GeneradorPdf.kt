package co.losmagnificos.izaje.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import co.losmagnificos.izaje.R
import co.losmagnificos.izaje.core.Amarre
import co.losmagnificos.izaje.core.Analisis
import co.losmagnificos.izaje.core.Analizador
import co.losmagnificos.izaje.core.EstadoEslingas
import co.losmagnificos.izaje.core.EstadoFila
import co.losmagnificos.izaje.core.EstadoPlan
import co.losmagnificos.izaje.core.Formato
import co.losmagnificos.izaje.core.PdfRegistro
import co.losmagnificos.izaje.core.Plan
import co.losmagnificos.izaje.core.RefAngulo
import java.io.File
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Genera el plan de izaje en PDF (A4) con el motor nativo de Android.
 * El encabezado «PLAN DE IZAJE LOS MAGNÍFICOS SAS» y el logotipo original aparecen en todas las páginas.
 */
class GeneradorPdf(private val ctx: Context, private val dirPdf: File) {

    data class Resultado(val archivo: File, val registro: PdfRegistro, val estado: EstadoPlan, val analisis: Analisis)

    companion object {
        const val TITULO = "PLAN DE IZAJE LOS MAGNÍFICOS SAS"

        fun fechaLegible(iso: String): String = try {
            LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } catch (e: Exception) { iso.ifBlank { "sin fecha" } }

        /** Parte de «Cliente S.A.S.» → «Cliente_S_A_S» (sin tildes ni caracteres raros). */
        fun limpiarParaArchivo(s: String): String {
            val sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            return sinTildes.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(30)
        }

        fun nombreArchivo(plan: Plan, preliminar: Boolean, version: Int): String {
            val fecha = plan.fecha.ifBlank { LocalDate.now().toString() }
            val cliente = limpiarParaArchivo(plan.cliente).ifBlank { "SinCliente" }
            return "Plan_Izaje_Los_Magnificos_${fecha}_$cliente" +
                (if (preliminar) "_PRELIMINAR" else "") + (if (version > 1) "_v$version" else "") + ".pdf"
        }

        fun idDocumento(plan: Plan): String {
            val f = plan.fecha.replace("-", "").ifBlank { "SINFECHA" }
            return "PI-$f-${plan.id.take(4).uppercase()}"
        }
    }

    fun generar(plan: Plan): Resultado {
        val a = Analizador.analizar(plan)
        val version = plan.pdfs.size + 1
        val ahora = System.currentTimeMillis()
        val logo = BitmapFactory.decodeResource(ctx.resources, R.drawable.logo_magnificos, BitmapFactory.Options().apply { inScaled = false })
        val preliminar = a.estado == EstadoPlan.PRELIMINAR
        val cab = Cabecera(TITULO, idDocumento(plan), version, fechaLegible(plan.fecha), a.estado.etiqueta, preliminar, logo)

        // 1.ª pasada: contar páginas. 2.ª pasada: dibujar con «Página x de N».
        val total = PdfDocument().let { d ->
            val e = Escritor(d, cab, 0)
            contenido(e, plan, a, ahora)
            val n = e.cerrar()
            d.close()
            n
        }
        val nombre = nombreArchivo(plan, preliminar, version)
        val archivo = File(dirPdf, nombre).apply { parentFile?.mkdirs() }
        val doc = PdfDocument()
        try {
            val e = Escritor(doc, cab, total)
            contenido(e, plan, a, ahora)
            e.cerrar()
            archivo.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return Resultado(archivo, PdfRegistro(nombre, ahora, a.estado.resumen, version), a.estado, a)
    }

    // ------------------------------------------------------------------ contenido

    private fun contenido(e: Escritor, plan: Plan, a: Analisis, ahora: Long) {
        val d = plan.datos
        e.nuevaPagina()

        // Estado del documento
        if (a.estado == EstadoPlan.PRELIMINAR) {
            e.caja(
                "DOCUMENTO PRELIMINAR – BORRADOR. Faltan datos indispensables o hay verificaciones que no se cumplen (ver sección 14). " +
                    "No es un plan aprobado y no debe usarse para ejecutar la maniobra.",
                Colores.ROJO_FONDO, Colores.ROJO, Colores.ROJO, negrita = true,
            )
        } else {
            e.caja(
                "PARA REVISIÓN – NO APROBADO. Los datos indispensables fueron informados, pero el plan debe ser revisado y aprobado por el " +
                    "responsable competente antes de ejecutar la maniobra.",
                Colores.AMBAR_FONDO, Colores.NARANJA, Colores.NEGRO, negrita = true,
            )
        }
        a.alertas.forEach { e.caja("ATENCIÓN: $it", Colores.ROJO_FONDO, Colores.ROJO, Colores.ROJO, negrita = true) }
        e.parrafo(
            "Herramienta de apoyo a la planificación. Los cálculos son estimaciones con los datos informados por el operador; no constituyen " +
                "una certificación de seguridad ni sustituyen la tabla de cargas oficial de la grúa, el manual del fabricante ni el criterio de una persona competente.",
            8f, Colores.GRIS,
        )

        // 1. Operador y cliente
        e.seccion("1. Datos del operador y del cliente")
        e.clave(listOf(
            "Operador de grúa" to plan.operador.ifBlank { "PENDIENTE" },
            "Cédula del operador" to plan.cedula.ifBlank { "PENDIENTE" },
            "Cliente" to plan.cliente.ifBlank { "PENDIENTE" },
        ))

        // 2. Fecha y lugar
        e.seccion("2. Fecha y lugar del trabajo")
        e.clave(listOf(
            "Fecha del trabajo" to fechaLegible(plan.fecha),
            "Sitio o ubicación" to plan.sitio.ifBlank { "PENDIENTE" },
        ))

        // 3. Descripción de la maniobra
        e.seccion("3. Descripción de la maniobra")
        e.parrafo(resumenManiobra(plan, a))
        if (plan.texto.isNotBlank()) {
            e.subtitulo("Texto original dictado o escrito por el operador")
            e.parrafo(plan.texto.trim(), 8.8f, Colores.GRIS, cursiva = true)
        }

        // 4. Equipo
        e.seccion("4. Identificación del equipo")
        e.clave(listOf(
            "Equipo de izaje (marca y modelo)" to (d.equipo ?: "PENDIENTE DE VERIFICACIÓN"),
            "Capacidad máxima nominal (solo referencia)" to
                (d.capacidadNominalKg?.let { Formato.kgYt(it) } ?: "No informada"),
        ))
        e.parrafo(
            "La capacidad máxima nominal no es la capacidad disponible: para evaluar la maniobra se usa la capacidad permitida en el radio y la " +
                "configuración reales de trabajo, según la tabla de cargas oficial de la grúa.",
            8.5f, Colores.GRIS,
        )

        // 5. Carga
        e.seccion("5. Características de la carga")
        val notasCarga = d.notas.filter { it.startsWith("Dimensión") || it.startsWith("Centro de gravedad") }
        e.clave(listOf(
            "Peso de la carga" to (d.cargaKg?.let { Formato.kgYt(it) + if (aproximado(d.aproximados, "carga")) "  (≈ aproximado)" else "" } ?: "PENDIENTE"),
            "Origen del dato" to "Informado por el operador (no verificado con báscula ni documento)",
        ))
        notasCarga.forEach { e.vineta(it.replace("«", "").replace("»", ""), 9f) }

        // 6. Peso total
        e.seccion("6. Peso total suspendido")
        val p = a.peso
        if (p.lineas.isEmpty()) {
            e.parrafo("No se informó ningún peso. Peso total: PENDIENTE.", 9.5f, Colores.ROJO, negrita = true)
        } else {
            val filas = p.lineas.map { listOf(it.concepto, Formato.num(it.kg, 2), it.nota ?: "") }.toMutableList()
            filas += listOf("PESO TOTAL SUSPENDIDO", Formato.num(p.totalKg ?: p.lineas.sumOf { it.kg }, 2), if (p.totalKg == null) "Incompleto: falta el peso de la carga" else "")
            e.tabla(listOf(3.2f, 1.6f, 3.2f), listOf("Concepto", "Peso (kg)", "Observación"), filas, derecha = setOf(1), resaltarUltima = true)
        }
        if (p.incluidosEnOtro.isNotEmpty()) e.vineta("No se sumaron por estar incluidos en otro valor, según el operador: ${p.incluidosEnOtro.joinToString(", ")}.", 9f)
        if (p.sinDato.isNotEmpty()) e.vineta("Sin peso informado (no se sumaron ni se estimaron): ${p.sinDato.joinToString(", ")}.", 9f, Colores.ROJO)

        // 7. Accesorios
        e.seccion("7. Accesorios de izaje")
        val acc = mutableListOf<Pair<String, String>>()
        if (d.sinEslingas) acc += "Eslingas" to "El operador indicó que no se usan eslingas."
        else {
            acc += "Cantidad de eslingas" to (d.nEslingas?.toString() ?: "PENDIENTE")
            acc += "Capacidad de cada eslinga (informada)" to (d.wllEslingaKg?.let { Formato.kgYt(it) + " — verificar contra la etiqueta" } ?: "PENDIENTE")
            acc += "Tipo de amarre" to (d.amarre?.etiqueta ?: "No informado (se calculó como ramales directos)")
            val pesoEsl = d.eslingasKg ?: d.eslingaUnitKg?.let { u -> d.nEslingas?.let { n -> u * n } }
            acc += "Peso de las eslingas" to (pesoEsl?.let { Formato.kg(it) } ?: "No informado")
        }
        acc += "Gancho" to (d.ganchoKg?.let { Formato.kg(it) } ?: "Peso no informado")
        val grillete = d.grilletesKg ?: d.grilleteUnitKg?.let { u -> d.nGrilletes?.let { n -> u * n } }
        if (grillete != null || d.nGrilletes != null) acc += "Grilletes" to ((d.nGrilletes?.let { "$it · " } ?: "") + (grillete?.let { Formato.kg(it) } ?: "peso no informado"))
        d.balancinKg?.let { acc += "Balancín / barra separadora" to Formato.kg(it) }
        d.otrosKg?.let { acc += "Otros accesorios" to Formato.kg(it) }
        e.clave(acc)

        // 8. Configuración y radios
        e.seccion("8. Configuración y radios de trabajo")
        e.clave(listOf(
            "Radio horizontal inicial (recogida)" to (d.radioInicialM?.let { Formato.m(it) } ?: "No informado"),
            "Radio horizontal final (colocación)" to (d.radioFinalM?.let { Formato.m(it) } ?: "No informado"),
            "Radio máximo de trabajo" to (d.radioMaximoM?.let { Formato.m(it) } ?: "No informado"),
            "Altura de elevación (distinta del radio)" to (d.alturaM?.let { Formato.m(it) } ?: "No informada"),
            "Longitud y extensiones de pluma, estabilizadores y sector de trabajo" to
                (if (d.configuracion.isEmpty()) "PENDIENTE DE VERIFICACIÓN" else d.configuracion.joinToString(" / ") + "  (informado por el operador; verificar)"),
        ), anchoEtiqueta = 0.42f)

        // 9. Capacidades y utilización
        e.seccion("9. Capacidades y porcentajes de utilización")
        if (a.filas.isEmpty()) {
            e.parrafo("No se informó ningún radio de trabajo: no se puede evaluar la capacidad. PENDIENTE.", 9.5f, Colores.ROJO, negrita = true)
        } else {
            val filas = a.filas.map { f ->
                listOf(
                    Formato.num(f.radioM, 2) + if (f.critico) " ◄" else "",
                    f.etiqueta,
                    f.capacidadKg?.let { Formato.num(it, 2) } ?: "Pendiente",
                    f.pesoKg?.let { Formato.num(it, 2) } ?: "—",
                    f.utilizacionPct?.let { Formato.pct(it) } ?: "—",
                    f.margenKg?.let { Formato.num(it, 2) } ?: "—",
                    when (f.estado) {
                        EstadoFila.DENTRO -> "Dentro de la capacidad informada"
                        EstadoFila.EXCEDE -> "EXCEDE"
                        EstadoFila.SIN_CAPACIDAD -> "Pendiente de validación con la tabla oficial"
                        EstadoFila.SIN_PESO -> "Falta el peso"
                    },
                )
            }
            e.tabla(
                listOf(1.0f, 1.7f, 1.4f, 1.3f, 1.2f, 1.4f, 2.4f),
                listOf("Radio (m)", "Posición", "Capacidad informada (kg)", "Peso total (kg)", "Utilización", "Margen (kg)", "Estado"),
                filas, derecha = setOf(0, 2, 3, 4, 5),
            )
            e.parrafo("Utilización = peso total suspendido ÷ capacidad permitida en el radio × 100.  ◄ = radio con la menor capacidad informada.", 8.5f, Colores.GRIS)
        }
        e.parrafo(
            "No se interpolan ni se inventan capacidades: un radio sin capacidad informada queda pendiente de validación. Un porcentaje inferior al 100 % " +
                "no demuestra que la maniobra sea segura; la evaluación definitiva debe hacerse con la tabla oficial de cargas de la configuración real del equipo.",
            8.5f, Colores.GRIS,
        )

        // 10. Eslingas
        e.seccion("10. Cálculo de tensiones en las eslingas")
        val r = a.eslingas
        when (r.estado) {
            EstadoEslingas.NO_CALCULABLE -> {
                e.parrafo(r.notas.firstOrNull() ?: "No calculable con los datos informados.", 9.5f, if (d.sinEslingas) Colores.NEGRO else Colores.ROJO, negrita = !d.sinEslingas)
            }
            else -> {
                val filas = mutableListOf<Pair<String, String>>()
                filas += "Peso total suspendido" to Formato.kgYt(a.peso.totalKg)
                filas += "Ramales que soportan la carga" to r.ramalesPortantes.toString()
                if (r.estado == EstadoEslingas.CALCULADA || r.tensionRamalKg != null) {
                    val ref = when (d.refAngulo) {
                        RefAngulo.HORIZONTAL -> "medido desde la horizontal"
                        RefAngulo.VERTICAL -> "medido desde la vertical (${Formato.grados(r.anguloHorizontal)} con la horizontal)"
                        RefAngulo.SIN_CONFIRMAR -> "referencia sin confirmar; se usó el caso más desfavorable (${Formato.grados(r.anguloHorizontal)} con la horizontal)"
                    }
                    if (d.anguloGrados != null && r.ramalesPortantes > 1) filas += "Ángulo informado" to "${Formato.grados(d.anguloGrados)} — $ref"
                    filas += "Tensión estimada por ramal" to
                        ((r.tensionRamalKg?.let { Formato.kgYt(it) } ?: "—") + if (r.referenciaPendiente) "  (estimación condicionada)" else "")
                    if (r.ramalesPortantes > 1 && r.anguloHorizontal != null)
                        filas += "Fórmula aplicada" to "T = W ÷ (n × sen α) = ${Formato.num(a.peso.totalKg, 2)} ÷ (${r.ramalesPortantes} × sen ${Formato.num(r.anguloHorizontal, 1)}°)"
                    if (d.amarre == Amarre.CANASTA) filas += "Carga por eslinga (2 ramales en canasta)" to (r.cargaPorEslingaKg?.let { Formato.kg(it) } ?: "—")
                } else {
                    filas += "Ángulo informado" to "NO INFORMADO: no se puede dar una tensión definitiva"
                    filas += "Tensión mínima posible (ramales verticales)" to (r.tensionMinimaKg?.let { Formato.kg(it) } ?: "—")
                    r.anguloMinimoGrados?.let { filas += "Ángulo mínimo con la horizontal para no superar la capacidad indicada" to Formato.grados(it) }
                }
                filas += "Capacidad indicada por eslinga" to (r.capacidadIndicadaKg?.let { Formato.kg(it) + " (cada una, sin sumar)" } ?: "PENDIENTE")
                r.utilizacionPct?.let { filas += "Utilización de la eslinga" to Formato.pct(it) + if (r.excede) "  — EXCEDE" else "" }
                e.clave(filas, anchoEtiqueta = 0.42f)
                if (r.estado == EstadoEslingas.CONDICIONADA && r.referencias.isNotEmpty()) {
                    e.subtitulo("Valores de referencia según el ángulo con la horizontal (no son datos del trabajo)")
                    e.tabla(
                        listOf(1.5f, 2f, 2.4f), listOf("Ángulo", "Tensión por ramal (kg)", "Utilización de la eslinga"),
                        r.referencias.map { (ang, t) ->
                            val carga = if (d.amarre == Amarre.CANASTA) t * 2 else t
                            listOf(Formato.grados(ang), Formato.num(t, 1), r.capacidadIndicadaKg?.let { Formato.pct(carga / it * 100.0) } ?: "—")
                        },
                        derecha = setOf(1, 2),
                    )
                }
                r.notas.forEach { e.vineta(it, 8.8f, Colores.GRIS) }
            }
        }

        // 11. Secuencia
        e.seccion("11. Secuencia de ejecución")
        e.parrafo("Secuencia general propuesta; el responsable de la maniobra debe ajustarla a las condiciones reales del sitio.", 8.5f, Colores.GRIS)
        val rad = when {
            d.radioInicialM != null && d.radioFinalM != null -> "desde el radio de ${Formato.m(d.radioInicialM)} hasta el radio de ${Formato.m(d.radioFinalM)}"
            else -> "dentro de los radios evaluados"
        }
        listOf(
            "Delimitar y señalizar la zona de trabajo; confirmar que no haya personas bajo la carga ni líneas eléctricas dentro del radio de acción.",
            "Ubicar el camión grúa en terreno firme y nivelado, y desplegar estabilizadores y extensiones según la configuración de la tabla de cargas usada.",
            "Verificar el peso de la carga y la inspección y etiquetas de eslingas, grilletes y gancho antes de armar el aparejo.",
            "Armar el aparejo con el ángulo previsto y comprobar que el gancho quede sobre el centro de gravedad de la carga.",
            "Izar unos centímetros, detener y comprobar estabilidad, amarre y respuesta de la grúa antes de continuar.",
            "Trasladar la carga $rad, con movimientos suaves y guiado por un señalero, sin superar las capacidades de la tabla oficial.",
            "Posicionar y asentar la carga; mantener el aparejo tensionado hasta comprobar que está estable antes de soltarla.",
            "Retirar el aparejo, recoger la pluma, plegar los estabilizadores y retirar la señalización.",
        ).forEachIndexed { i, t -> e.numerado(i + 1, t) }

        // 12. Verificaciones
        e.seccion("12. Verificaciones de seguridad")
        e.parrafo("Marcar en campo antes de iniciar la maniobra.", 8.5f, Colores.GRIS)
        listOf(
            "Capacidades confirmadas con la tabla de cargas oficial para el radio y la configuración reales.",
            "Configuración de estabilizadores, extensiones y sector de trabajo verificada.",
            "Peso de la carga verificado (báscula, placa o documento).",
            "Eslingas, grilletes y gancho inspeccionados, con etiqueta legible y capacidad suficiente.",
            "Ángulo de las eslingas medido en campo y compatible con su capacidad.",
            "Terreno firme y nivelado; estabilizadores bien apoyados.",
            "Distancia a líneas eléctricas y obstáculos verificada.",
            "Zona delimitada y personal fuera del radio de acción de la carga.",
            "Comunicación acordada entre operador y señalero.",
            "Condiciones climáticas aceptables (viento, lluvia, visibilidad).",
        ).forEach { e.casilla(it) }

        // 13. Riesgos
        e.seccion("13. Riesgos identificados y medidas preventivas")
        e.parrafo("Riesgos generales de referencia; complementar con el análisis de riesgos específico del sitio.", 8.5f, Colores.GRIS)
        e.tabla(
            listOf(2.2f, 5f), listOf("Riesgo", "Medida preventiva"),
            listOf(
                listOf("Caída o desprendimiento de la carga", "Inspeccionar el aparejo, respetar el ángulo y la capacidad de las eslingas y no permanecer bajo la carga."),
                listOf("Vuelco del equipo", "No superar la capacidad de la tabla oficial en el radio de trabajo; apoyar bien los estabilizadores en terreno firme."),
                listOf("Contacto con líneas eléctricas", "Identificar las líneas y mantener la distancia de seguridad antes de mover la pluma."),
                listOf("Golpes o atrapamiento del personal", "Delimitar la zona, usar señalero y mantener al personal fuera del radio de acción."),
                listOf("Rotura de aparejos", "Verificar etiquetas y estado; evitar ángulos pequeños con la horizontal, aristas vivas y sobrecargas."),
                listOf("Viento o clima adverso", "Suspender la maniobra si el viento o la visibilidad comprometen el control de la carga."),
            ),
        )

        // 14. Pendientes
        e.seccion("14. Datos pendientes de confirmación")
        if (a.faltantes.isEmpty()) {
            e.parrafo("Sin pendientes.", 9.5f)
        } else {
            e.tabla(
                listOf(1.3f, 6f), listOf("Tipo", "Dato pendiente"),
                a.faltantes.sortedByDescending { it.critico }.map { listOf(if (it.critico) "INDISPENSABLE" else "Confirmar", it.texto) },
            )
        }

        // 15. Observaciones
        e.seccion("15. Observaciones")
        val otras = d.notas.filterNot { it.startsWith("Dimensión") || it.startsWith("Centro de gravedad") }
        if (plan.observaciones.isBlank() && otras.isEmpty()) e.parrafo("Sin observaciones.", 9.5f, Colores.GRIS)
        if (plan.observaciones.isNotBlank()) { e.subtitulo("Observaciones del operador"); e.parrafo(plan.observaciones.trim()) }
        if (otras.isNotEmpty()) {
            e.subtitulo("Otra información mencionada en el dictado (no usada en los cálculos)")
            otras.forEach { e.vineta(it.replace("«", "“").replace("»", "”"), 9f) }
        }

        // 16. Firmas
        e.seccion("16. Elaboración, revisión y aprobación")
        e.firmas(plan.operador to plan.cedula)
        e.parrafo(
            "Generado el ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("es", "CO")).format(java.util.Date(ahora))} por la aplicación Plan de Izaje Los Magníficos SAS.",
            8f, Colores.GRIS,
        )
    }

    private fun aproximado(aprox: Set<String>, clave: String) = clave in aprox

    private fun resumenManiobra(plan: Plan, a: Analisis): String {
        val d = plan.datos
        val partes = mutableListOf<String>()
        val total = a.peso.totalKg
        partes += if (d.cargaKg != null)
            "Se planea izar una carga de ${Formato.kg(d.cargaKg)}" + (total?.let { " (peso total suspendido: ${Formato.kg(it)})" } ?: "")
        else "Se planea una maniobra de izaje cuyo peso de carga está pendiente de informar"
        d.equipo?.let { partes[0] += " con la grúa $it" }
        partes[0] += "."
        when {
            d.radioInicialM != null && d.radioFinalM != null ->
                partes += "La carga se recoge a un radio de ${Formato.m(d.radioInicialM)} y se coloca a un radio de ${Formato.m(d.radioFinalM)}."
            d.radioInicialM != null -> partes += "El radio de recogida es de ${Formato.m(d.radioInicialM)}."
            d.radioFinalM != null -> partes += "El radio de colocación es de ${Formato.m(d.radioFinalM)}."
        }
        d.radioMaximoM?.let { partes += "El radio máximo de trabajo informado es de ${Formato.m(it)}." }
        d.alturaM?.let { partes += "La altura de elevación es de ${Formato.m(it)}." }
        if (d.sinEslingas) partes += "No se usan eslingas."
        else d.nEslingas?.let { n ->
            partes += "Se emplean $n ${if (n == 1) "eslinga" else "eslingas"}" +
                (d.wllEslingaKg?.let { " de ${Formato.kg(it)} de capacidad indicada cada una" } ?: "") +
                (d.amarre?.let { ", amarre ${it.etiqueta.lowercase()}" } ?: "") + "."
        }
        val crit = a.filas.firstOrNull { it.critico }
        if (crit?.utilizacionPct != null)
            partes += "En el radio de menor capacidad informada (${Formato.m(crit.radioM)}) la utilización calculada es de ${Formato.pct(crit.utilizacionPct)}, " +
                "valor que debe confirmarse con la tabla de cargas oficial."
        return partes.joinToString(" ")
    }
}
