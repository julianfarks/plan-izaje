package co.planizaje.app.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import co.planizaje.app.data.Almacen
import co.planizaje.app.data.nuevoId
import co.planizaje.app.ui.comp.Archivos
import co.planizaje.app.ui.croquis.Opciones
import co.planizaje.app.ui.croquis.Renderizador
import co.planizaje.app.ui.croquis.Vista
import co.planizaje.core.calc.EstadoDato
import co.planizaje.core.calc.EstadoDocumento
import co.planizaje.core.calc.Evaluacion
import co.planizaje.core.calc.Motor
import co.planizaje.core.calc.Resultado
import co.planizaje.core.calc.Veredicto
import co.planizaje.core.data.Serializacion
import co.planizaje.core.geo.Geometria
import co.planizaje.core.model.Accesorio
import co.planizaje.core.model.Adjunto
import co.planizaje.core.model.Grua
import co.planizaje.core.model.Proyecto
import co.planizaje.core.model.VersionPdf
import co.planizaje.core.util.Unidades
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Genera el plan de izaje en PDF con el motor nativo de Android (PdfDocument).
 * El croquis se dibuja en vectores, por lo que conserva la nitidez al ampliar o imprimir.
 */
class GeneradorPdf(private val almacen: Almacen, private val versionApp: String) {

    data class Salida(val archivo: File, val version: VersionPdf)

    fun generar(p: Proyecto, grua: Grua?, accesorios: List<Accesorio>): Salida {
        val ev = Motor.evaluar(p, grua, accesorios)
        val huella = Serializacion.huella(p, grua, accesorios)
        val ahora = System.currentTimeMillis()
        // 1ª pasada: contar páginas. 2ª pasada: dibujar con "Página x de N".
        val conteo = Escritor(null, p, ev.estadoDocumento, 0, huella, ahora, versionApp).also { contenido(it, p, grua, accesorios, ev) }.cerrar()
        val doc = PdfDocument()
        val esc = Escritor(doc, p, ev.estadoDocumento, conteo, huella, ahora, versionApp)
        contenido(esc, p, grua, accesorios, ev)
        esc.cerrar()
        val numero = (p.versionesPdf.filter { it.revision == p.revision }.maxOfOrNull { it.numero } ?: 0) + 1
        val nombreSeguro = p.titulo.replace(Regex("[^A-Za-z0-9áéíóúñÁÉÍÓÚÑ_-]+"), "_").take(40)
        val dir = File(almacen.dirPlanes, p.id).apply { mkdirs() }
        val f = File(dir, "Plan_${nombreSeguro}_R${p.revision}_v$numero${if (ev.estadoDocumento.esBorrador) "_BORRADOR" else ""}.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        val v = VersionPdf(nuevoId(), "${p.id}/${f.name}", ahora, p.revision, numero, ev.estadoDocumento.esBorrador, huella, ev.estadoDocumento.etiqueta)
        return Salida(f, v)
    }

    // ------------------------------------------------------------------ contenido

    private fun contenido(e: Escritor, p: Proyecto, g: Grua?, biblioteca: List<Accesorio>, ev: Evaluacion) {
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CO"))
        val usados = biblioteca.filter { it.id in p.aparejo.todosIds }
        val tabla = g?.tablas?.firstOrNull { it.id == p.maniobra.tablaId }

        // A. Portada --------------------------------------------------------
        e.nuevaPagina()
        e.espacio(40f)
        e.texto("PLAN DE IZAJE", 26f, negrita = true, color = AZUL)
        e.texto(p.titulo, 18f, negrita = true)
        e.espacio(8f)
        e.bandaEstado(ev.estadoDocumento)
        e.espacio(14f)
        e.kv(
            listOf(
                "Cliente" to p.cliente, "Empresa responsable" to p.empresa, "Ubicación" to p.ubicacion,
                "Fecha de la maniobra" to p.fecha, "Revisión" to p.revision.toString(),
                "Elaborado por" to p.elaboradoPor, "Responsable de revisión" to p.revisorPrevisto, "Responsable de aprobación" to p.aprobadorPrevisto,
                "Fecha de generación" to fmt.format(Date(e.ahora)),
            ),
        )
        e.espacio(8f)
        if (p.descripcion.isNotBlank()) { e.subtitulo("Descripción del trabajo"); e.parrafo(p.descripcion) }
        e.espacio(10f)
        e.subtitulo("Resumen técnico")
        e.kv(
            listOf(
                "Grúa" to (g?.nombreCorto ?: "sin seleccionar"),
                "Masa total suspendida" to Unidades.kgYt(ev.masaTotal),
                "Radio máximo" to Unidades.m(ev.radioGobernante),
                "Utilización máxima" to Unidades.pct(ev.utilizacionMax),
                "Puntos por resolver" to ev.puntosPorResolver.toString(),
            ),
        )
        e.espacio(10f)
        firmas(e, p)
        e.espacio(8f)
        e.parrafo(
            "Este documento es una herramienta de planificación. No sustituye el manual del fabricante, el diagrama de carga de la grúa ni el criterio de una persona competente. " +
                "Un porcentaje de utilización dentro del límite no significa que la maniobra sea segura.",
            8.5f, GRIS,
        )

        // Faltantes / advertencias
        if (ev.faltantes.isNotEmpty() || ev.resultados.any { it.bloquea }) {
            e.seccion("Pendientes y advertencias")
            if (ev.faltantes.isNotEmpty()) {
                e.subtitulo("Datos que faltan")
                ev.faltantes.forEach { e.vineta("Paso ${it.paso.numero} · ${it.texto}") }
            }
            val bloq = ev.resultados.filter { it.bloquea }
            if (bloq.isNotEmpty()) {
                e.subtitulo("Verificaciones no resueltas")
                bloq.forEach { r -> e.vineta("${r.estado.simbolo} ${r.titulo}: ${r.estado.etiqueta}${r.veredicto?.let { " · ${it.etiqueta}" } ?: ""}" + (r.mensajes.firstOrNull()?.let { " — $it" } ?: "")) }
            }
        }
        if (p.advertencias.isNotBlank()) { e.subtitulo("Advertencias del proyecto"); e.parrafo(p.advertencias) }

        // B. Carga ------------------------------------------------------------
        val c = p.carga
        e.seccion("B. Información de la carga")
        e.kv(
            listOf(
                "Descripción" to c.descripcion,
                "Peso de la carga" to Unidades.kgYt(c.pesoKg),
                "Origen del peso" to c.origenPeso.etiqueta,
                "Referencia" to c.referenciaPeso,
                "Dimensiones (L × A × H)" to "${Unidades.m(c.largo)} × ${Unidades.m(c.ancho)} × ${Unidades.m(c.alto)}",
                "Centro de gravedad" to if (c.cdgConocido) "X ${Unidades.m(c.cdgX)}, Y ${Unidades.m(c.cdgY)}, Z ${Unidades.m(c.cdgZ)}" else "No conocido: se supone centrado (hipótesis)",
                "Puntos de izaje" to listOfNotNull(c.numeroPuntosIzaje?.toString(), c.wllPuntoIzajeKg?.let { "WLL ${Unidades.kg(it)} c/u" }, c.descripcionPuntos.ifBlank { null }).joinToString(" · "),
                "Método de sujeción" to c.metodoSujecion,
                "Posición inicial" to c.posicionInicial,
                "Posición final" to c.posicionFinal,
            ),
        )
        if (c.observaciones.isNotBlank()) { e.subtitulo("Observaciones"); e.parrafo(c.observaciones) }
        fotos(e, c.fotos, "Fotografías de la carga")

        // C. Grúa ---------------------------------------------------------------
        e.seccion("C. Información de la grúa")
        if (g == null) e.parrafo("No se ha seleccionado grúa.", color = ROJO)
        else {
            val est = g.estabilizadores.firstOrNull { it.id == p.maniobra.estabilizadoresId }
            e.kv(
                listOf(
                    "Fabricante y modelo" to g.nombreCorto,
                    "Tipo" to g.tipo.etiqueta,
                    "Nº de serie / identificación" to listOf(g.numeroSerie, g.identificacionInterna).filter { it.isNotBlank() }.joinToString(" · "),
                    "Vehículo portador" to listOf(g.vehiculo, g.placaVehiculo).filter { it.isNotBlank() }.joinToString(" · placa "),
                    "Extensiones hidráulicas / manuales" to "${g.extensionesHidraulicas ?: "—"} / ${g.extensionesManuales ?: "—"}",
                    "Plumín (jib)" to g.plumin,
                    "Tabla de carga usada" to (tabla?.let { it.nombre.ifBlank { it.configuracionPluma } } ?: "sin tabla"),
                    "Configuración de pluma" to (tabla?.configuracionPluma ?: ""),
                    "Estabilizadores" to (est?.let { "${it.nombre}${it.anchoApoyos?.let { a -> " · ancho ${Unidades.m(a)}" } ?: ""}" } ?: "sin especificar"),
                    "Sector de trabajo" to (tabla?.sector ?: g.sectoresPermitidos),
                    "Viento máximo (manual)" to (g.vientoMaxMs?.let { "${Unidades.fmt(it, 1)} m/s" } ?: "sin dato"),
                    "Restricciones" to g.restricciones,
                ),
            )
            ev.resultados.filter { it.id.startsWith("capacidad_") || it.id.startsWith("utilizacion_") || it.id.startsWith("radio_") }.let { lr ->
                if (lr.isNotEmpty()) {
                    e.subtitulo("Radio, capacidad y utilización por fase")
                    e.tabla(listOf(0.45f, 0.25f, 0.30f), listOf("Concepto", "Valor", "Estado"), lr.map { listOf(it.titulo, it.valor, estadoTxt(it)) })
                }
            }
        }

        // D. Aparejos -------------------------------------------------------------
        e.seccion("D. Aparejos")
        val a = p.aparejo
        e.kv(
            listOf(
                "Ramales" to "${a.ramales}${a.ramalesPortantes?.let { " (portantes: $it)" } ?: ""}",
                "Enganche" to a.enganche.etiqueta,
                "Ángulo" to when {
                    a.ramales == 1 -> "Vertical"
                    a.metodoAngulo == co.planizaje.core.model.MetodoAngulo.GEOMETRIA -> "Por medidas: ramal ${Unidades.m(a.longitudRamal)}, separación ${Unidades.m(a.separacionPuntos)}"
                    else -> Unidades.grados(a.anguloVertical) + " respecto a la vertical"
                },
            ),
        )
        if (usados.isEmpty()) e.parrafo("No hay accesorios seleccionados.", color = ROJO)
        else e.tabla(
            listOf(0.28f, 0.17f, 0.15f, 0.12f, 0.28f),
            listOf("Elemento", "Identificación", "WLL usado", "Peso", "Certificado / inspección"),
            usados.map { ac ->
                listOf(
                    "${ac.tipo.etiqueta}${if (ac.fabricante.isNotBlank()) " · ${ac.fabricante}" else ""}",
                    ac.identificacion.ifBlank { "—" },
                    Unidades.kg(ac.wll(a.enganche)),
                    Unidades.kg(ac.pesoKg),
                    "${ac.numeroCertificado.ifBlank { "sin cert." }} · ${ac.estado.etiqueta}${if (ac.proximaInspeccion.isNotBlank()) " · próx. ${ac.proximaInspeccion}" else ""}",
                )
            },
        )
        ev.resultados.filter { it.grupo == "Aparejos" }.let { lr ->
            if (lr.isNotEmpty()) {
                e.subtitulo("Verificaciones de aparejos")
                e.tabla(listOf(0.40f, 0.30f, 0.30f), listOf("Verificación", "Valor", "Estado"), lr.map { listOf(it.titulo, it.valor, estadoTxt(it)) })
                lr.flatMap { r -> r.mensajes.map { "${r.titulo}: $it" } }.forEach { e.vineta(it, 8.5f) }
            }
        }
        if (a.notas.isNotBlank()) e.parrafo(a.notas)

        // E. Croquis ---------------------------------------------------------------
        e.seccion("E. Croquis del lugar de trabajo")
        e.parrafo("VISTA EN PLANTA — croquis esquemático. Las posiciones dibujadas son aproximadas salvo las marcadas como medidas verificadas; no constituye un levantamiento topográfico.", 8.5f, GRIS)
        e.dibujo(400f) { cv, r ->
            val v = Vista.ajustar(Geometria.cajaElementos(p.croquis.elementos)?.expandir(2.0), r.width(), r.height(), 14f)
            Renderizador.dibujar(cv, p, v, Opciones(u = 0.62f, cuadricula = p.croquis.cuadricula, paso = p.croquis.pasoCuadricula), r.width(), r.height())
            Renderizador.barraEscala(cv, v, 0.7f, 12f, r.height() - 12f, 110f)
        }
        e.subtitulo("Leyenda")
        Renderizador.leyenda(p).forEach { e.vineta("${it.simbolo}  ${it.texto}", 8.5f) }
        if (p.croquis.nota.isNotBlank()) { e.subtitulo("Notas del croquis"); e.parrafo(p.croquis.nota) }
        p.maniobra.fases.forEachIndexed { i, f ->
            val rf = Motor.radioFase(f, p.croquis)
            if (rf.radio != null) {
                e.subtitulo("Fase ${i + 1}${if (f.nombre.isNotBlank()) ": ${f.nombre}" else ""} — vista lateral esquemática")
                e.dibujo(210f) { cv, r -> Renderizador.elevacion(cv, p, f, RectF(0f, 0f, r.width(), r.height()), 0.7f) }
                if (f.descripcion.isNotBlank()) e.parrafo(f.descripcion, 8.5f)
            }
        }
        ev.resultados.firstOrNull { it.id == "interferencias" }?.let { r ->
            e.subtitulo("Análisis de interferencias en planta")
            e.parrafo(estadoTxt(r), 8.5f)
            r.mensajes.forEach { e.vineta(it, 8.5f) }
        }

        // F. Procedimiento ------------------------------------------------------------
        val pr = p.procedimiento
        e.seccion("F. Procedimiento de trabajo")
        e.subtitulo("Secuencia de la maniobra"); e.parrafo(pr.secuencia.ifBlank { "Sin definir." })
        e.subtitulo("Responsabilidades")
        e.kv(listOf("Operador de grúa" to pr.operador, "Señalero / rigger" to pr.senalero, "Aparejador" to pr.aparejador, "Supervisor" to pr.supervisor, "Otros" to pr.otrosResponsables))
        e.subtitulo("Comunicación entre operador y señalero"); e.parrafo(pr.comunicacion.ifBlank { "Sin definir." })
        if (pr.senalizacion.isNotBlank()) { e.subtitulo("Señalización"); e.parrafo(pr.senalizacion) }
        if (pr.delimitacion.isNotBlank()) { e.subtitulo("Delimitación del área"); e.parrafo(pr.delimitacion) }
        val pel = pr.peligros.filter { it.aplica }
        e.subtitulo("Peligros identificados y medidas preventivas")
        if (pel.isEmpty()) e.parrafo("No se marcaron peligros. Revise esta sección.", color = ROJO)
        else e.tabla(listOf(0.32f, 0.68f), listOf("Peligro", "Medidas preventivas"), pel.map { listOf(it.nombre, it.medidas) })
        e.subtitulo("Condiciones meteorológicas aplicables")
        e.parrafo(listOfNotNull(g?.vientoMaxMs?.let { "Viento máximo según ficha de la grúa: ${Unidades.fmt(it, 1)} m/s." }, pr.meteorologia.ifBlank { null }).joinToString(" ").ifBlank { "Sin definir." })
        e.subtitulo("Criterios de suspensión del trabajo"); e.parrafo(pr.criteriosSuspension)
        e.subtitulo("Lista de comprobación previa")
        pr.checklist.forEach { e.casilla(it.texto, it.marcado) }

        // G. Anexos ------------------------------------------------------------------------
        e.seccion("G. Anexos")
        e.subtitulo("G.1 Fuentes de las tablas de carga")
        if (tabla != null) {
            e.kv(listOf(
                "Documento" to tabla.fuente.texto(),
                "Verificación" to if (tabla.verificacion.valida) "Verificada por ${tabla.verificacion.porQuien} el ${tabla.verificacion.fecha}" else "SIN VERIFICAR contra el diagrama original",
                "Método entre radios" to tabla.interpolacion.etiqueta,
                "Descuenta gancho" to when (tabla.descuentaGancho) { true -> "Sí"; false -> "No"; null -> "No confirmado" },
                "Notas" to tabla.notas,
            ))
            e.tabla(listOf(0.33f, 0.33f, 0.34f), listOf("Radio", "Capacidad nominal", "Altura máx."), tabla.ordenados.map { listOf(Unidades.m(it.radio), Unidades.kg(it.capacidad), it.alturaMax?.let { h -> Unidades.m(h) } ?: "—") })
        } else e.parrafo("Sin tabla de carga: la capacidad de la grúa no es verificable.", color = ROJO)

        e.subtitulo("G.2 Memoria de cálculo")
        ev.resultados.forEach { r -> memoria(e, r) }

        e.subtitulo("G.3 Certificados disponibles")
        if (usados.isEmpty()) e.parrafo("—")
        usados.forEach { ac -> e.vineta("${ac.nombreCorto}: certificado ${ac.numeroCertificado.ifBlank { "no registrado" }}${if (ac.certificado != null) " (archivo adjunto en la app)" else ""}; inspección ${ac.fechaInspeccion.ifBlank { "sin fecha" }}, ${ac.estado.etiqueta}.") }

        e.subtitulo("G.4 Documentación técnica registrada")
        val docs = (g?.documentos ?: emptyList()) + p.carga.documentos + p.adjuntos.filter { !it.esImagen }
        if (docs.isEmpty()) e.parrafo("—") else docs.forEach { e.vineta("${it.nombre}${if (it.descripcion.isNotBlank()) " — ${it.descripcion}" else ""}") }
        fotos(e, p.adjuntos + (g?.documentos ?: emptyList()), "G.5 Fotografías y diagramas")

        e.subtitulo("G.6 Revisión humana registrada")
        val rh = p.revisionHumana
        e.kv(listOf(
            "Revisado por" to if (rh.confirmaRevision) "${rh.revisadoPor} (${rh.fechaRevision})" else "No registrada",
            "Aprobado por" to if (rh.confirmaAprobacion) "${rh.aprobadoPor} (${rh.fechaAprobacion})" else "No registrada",
            "Comentarios" to rh.comentarios,
        ))
        e.parrafo("La revisión y aprobación son declaraciones registradas por el usuario en la app; no son firmas.", 8f, GRIS)

        e.subtitulo("G.7 Historial de revisiones")
        val fh = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CO"))
        e.tabla(listOf(0.25f, 0.12f, 0.63f), listOf("Fecha", "Rev.", "Cambio"), p.historial.takeLast(40).map { listOf(fh.format(Date(it.fecha)), it.revision.toString(), it.descripcion) })
        if (p.versionesPdf.isNotEmpty()) {
            e.subtitulo("PDF generados anteriormente")
            p.versionesPdf.forEach { v -> e.vineta("Rev. ${v.revision} v${v.numero} · ${fh.format(Date(v.fecha))} · ${if (v.borrador) "borrador" else v.nota} · huella ${v.huellaDatos}", 8f) }
        }
    }

    private fun estadoTxt(r: Resultado) = "${r.estado.simbolo} ${r.estado.etiqueta}" + (r.veredicto?.let { "  ·  ${it.simbolo} ${it.etiqueta}" } ?: "")

    private fun memoria(e: Escritor, r: Resultado) {
        e.asegurar(60f)
        e.texto("${r.titulo} — ${r.valor}", 9.5f, negrita = true, color = if (r.veredicto == Veredicto.NO_CUMPLE || r.estado == EstadoDato.NO_VERIFICABLE) ROJO else TINTA)
        e.parrafo(estadoTxt(r), 8.5f, GRIS)
        if (r.formula.isNotBlank()) e.parrafo("Fórmula: ${r.formula}", 8.5f)
        r.datos.forEach { d -> e.parrafo("· ${d.nombre}: ${d.valor}${if (d.origen.isNotBlank()) " (${d.origen})" else ""}", 8.5f) }
        r.hipotesis.forEach { e.parrafo("Hipótesis: $it", 8.5f, GRIS) }
        if (r.fuente.isNotBlank()) e.parrafo("Fuente: ${r.fuente}", 8.5f, GRIS)
        r.mensajes.forEach { e.parrafo("→ $it", 8.5f) }
        e.espacio(6f)
    }

    private fun firmas(e: Escritor, p: Proyecto) {
        e.asegurar(90f)
        val cols = listOf("Elaborado" to p.elaboradoPor, "Revisado" to p.revisorPrevisto, "Aprobado" to p.aprobadorPrevisto)
        e.dibujo(80f, borde = false) { cv, r ->
            val w = r.width() / 3
            val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = TINTA }
            val tb = TextPaint(t).apply { typeface = Typeface.DEFAULT_BOLD }
            val linea = Paint().apply { color = GRIS; strokeWidth = 0.8f }
            cols.forEachIndexed { i, (rol, nombre) ->
                val x = i * w + 6
                cv.drawRect(x - 4, 0f, x + w - 10, r.height(), Paint().apply { style = Paint.Style.STROKE; color = 0xFFCCCCCC.toInt(); strokeWidth = 0.6f })
                cv.drawText(rol, x + 4, 14f, tb)
                cv.drawText(nombre.ifBlank { "Nombre: ____________" }, x + 4, 28f, t)
                cv.drawLine(x + 4, 62f, x + w - 20, 62f, linea)
                cv.drawText("Firma y fecha (a mano)", x + 4, 74f, t)
            }
        }
    }

    private fun fotos(e: Escritor, adj: List<Adjunto>, titulo: String) {
        val imgs = adj.filter { it.esImagen }.distinctBy { it.id }
        if (imgs.isEmpty()) return
        e.subtitulo(titulo)
        imgs.forEach { a ->
            val f = almacen.archivoAdjunto(a)
            val bmp = Archivos.miniatura(f, 900) ?: return@forEach
            e.imagen(bmp, 260f, a.nombre)
            bmp.recycle()
        }
    }

    companion object {
        const val TINTA = 0xFF1B1F24.toInt()
        const val GRIS = 0xFF5F6670.toInt()
        const val AZUL = 0xFF16263D.toInt()
        const val AMBAR = 0xFFF2A900.toInt()
        const val ROJO = 0xFFB3261E.toInt()
        const val VERDE = 0xFF1E7A46.toInt()
    }
}

/** Maquetador sencillo de páginas A4 con salto automático. Si [doc] es null solo mide (para contar páginas). */
class Escritor(
    private val doc: PdfDocument?,
    private val p: Proyecto,
    private val estado: EstadoDocumento,
    private val total: Int,
    private val huella: String,
    val ahora: Long,
    private val versionApp: String,
) {
    private val W = 595f
    private val H = 842f
    private val M = 40f
    private val TOP = 62f
    private val BOTTOM = 800f
    private val ancho = W - 2 * M
    private var pagina: PdfDocument.Page? = null
    private var cv: Canvas? = null
    private var numero = 0
    private var y = TOP

    fun nuevaPagina() {
        terminarPagina()
        numero++
        y = TOP
        if (doc != null) {
            val pg = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), numero).create())
            pagina = pg
            cv = pg.canvas
            marco(pg.canvas)
        }
    }

    private fun terminarPagina() {
        val pg = pagina ?: return
        doc?.finishPage(pg)
        pagina = null; cv = null
    }

    fun cerrar(): Int { terminarPagina(); return numero }

    private fun marco(c: Canvas) {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = GeneradorPdf.GRIS }
        val tb = TextPaint(tp).apply { typeface = Typeface.DEFAULT_BOLD; color = GeneradorPdf.AZUL }
        c.drawRect(M, 30f, M + 5f, 44f, Paint().apply { color = GeneradorPdf.AMBAR })
        c.drawText("PLAN DE IZAJE · ${p.titulo.take(60)} · Rev. ${p.revision}", M + 10, 41f, tb)
        val est = TextPaint(tp).apply { textAlign = Paint.Align.RIGHT; typeface = Typeface.DEFAULT_BOLD; color = if (estado.esBorrador) GeneradorPdf.ROJO else GeneradorPdf.VERDE }
        c.drawText(if (estado.esBorrador) "BORRADOR" else estado.etiqueta.take(40), W - M, 41f, est)
        c.drawLine(M, 48f, W - M, 48f, Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 0.8f })
        c.drawLine(M, 812f, W - M, 812f, Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 0.8f })
        val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CO")).format(Date(ahora))
        c.drawText("Generado $fecha · PlanIzaje $versionApp · huella de datos $huella", M, 824f, tp)
        c.drawText("Página $numero de ${if (total > 0) total else "?"}", W - M, 824f, TextPaint(tp).apply { textAlign = Paint.Align.RIGHT })
        estado.marcaAgua?.let { marca ->
            c.save()
            c.rotate(-35f, W / 2, H / 2)
            val wp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 44f; color = 0x22B3261E; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
            c.drawText(marca, W / 2, H / 2, wp)
            c.restore()
        }
    }

    fun asegurar(alto: Float) { if (pagina == null && doc != null || numero == 0 || y + alto > BOTTOM) nuevaPagina() }
    fun espacio(h: Float) { y += h; if (y > BOTTOM) nuevaPagina() }

    private fun layout(t: String, size: Float, negrita: Boolean, color: Int, w: Float): StaticLayout {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = if (negrita) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
        return StaticLayout.Builder.obtain(t, 0, t.length, tp, w.toInt().coerceAtLeast(10)).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(1.5f, 1f).build()
    }

    fun texto(t: String, size: Float, negrita: Boolean = false, color: Int = GeneradorPdf.TINTA, sangria: Float = 0f) {
        val l = layout(t, size, negrita, color, ancho - sangria)
        if (l.height > BOTTOM - TOP) {
            // Texto muy largo: se parte por líneas
            t.split("\n").forEach { texto(it, size, negrita, color, sangria) }
            return
        }
        asegurar(l.height.toFloat())
        cv?.let { c -> c.save(); c.translate(M + sangria, y); l.draw(c); c.restore() }
        y += l.height + 3f
    }

    fun parrafo(t: String, size: Float = 9.5f, color: Int = GeneradorPdf.TINTA) = texto(t, size, false, color)
    fun vineta(t: String, size: Float = 9f) = texto("•  $t", size, sangria = 6f)

    fun seccion(t: String) {
        asegurar(120f)
        espacio(6f)
        cv?.drawRect(M, y, M + ancho, y + 22f, Paint().apply { color = GeneradorPdf.AZUL })
        cv?.drawRect(M, y, M + 5f, y + 22f, Paint().apply { color = GeneradorPdf.AMBAR })
        cv?.drawText(t, M + 12f, y + 15f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.5f; color = 0xFFFFFFFF.toInt(); typeface = Typeface.DEFAULT_BOLD })
        y += 30f
    }

    fun subtitulo(t: String) { asegurar(40f); espacio(4f); texto(t, 10.5f, true, GeneradorPdf.AZUL) }

    fun bandaEstado(e: EstadoDocumento) {
        val col = if (e == EstadoDocumento.BORRADOR_NO_CUMPLE) GeneradorPdf.ROJO else if (e.esBorrador) 0xFF9A6200.toInt() else GeneradorPdf.VERDE
        asegurar(30f)
        cv?.drawRoundRect(RectF(M, y, M + ancho, y + 26f), 4f, 4f, Paint().apply { color = col })
        cv?.drawText("ESTADO: ${e.etiqueta}", M + 10f, y + 17f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = 0xFFFFFFFF.toInt(); typeface = Typeface.DEFAULT_BOLD })
        y += 30f
    }

    fun kv(filas: List<Pair<String, String>>) {
        val wk = ancho * 0.34f
        filas.forEach { (k, v) ->
            val lk = layout(k, 9f, true, GeneradorPdf.GRIS, wk - 6)
            val lv = layout(v.ifBlank { "—" }, 9.5f, false, GeneradorPdf.TINTA, ancho - wk)
            val h = maxOf(lk.height, lv.height) + 5f
            asegurar(h)
            cv?.let { c ->
                c.save(); c.translate(M, y); lk.draw(c); c.restore()
                c.save(); c.translate(M + wk, y); lv.draw(c); c.restore()
                c.drawLine(M, y + h - 2, M + ancho, y + h - 2, Paint().apply { color = 0xFFEEEEEE.toInt(); strokeWidth = 0.5f })
            }
            y += h
        }
    }

    fun tabla(proporciones: List<Float>, cabecera: List<String>, filas: List<List<String>>) {
        val ws = proporciones.map { it * ancho }
        fun fila(celdas: List<String>, negrita: Boolean, fondo: Int?) {
            val ls = celdas.mapIndexed { i, t -> layout(t.ifBlank { "—" }, 8.5f, negrita, if (negrita) 0xFFFFFFFF.toInt() else GeneradorPdf.TINTA, ws[i] - 8) }
            val h = (ls.maxOfOrNull { it.height } ?: 10) + 8f
            asegurar(h)
            cv?.let { c ->
                if (fondo != null) c.drawRect(M, y, M + ancho, y + h, Paint().apply { color = fondo })
                var x = M
                ls.forEachIndexed { i, l -> c.save(); c.translate(x + 4, y + 4); l.draw(c); c.restore(); x += ws[i] }
                c.drawLine(M, y + h, M + ancho, y + h, Paint().apply { color = 0xFFDDDDDD.toInt(); strokeWidth = 0.5f })
            }
            y += h
        }
        fila(cabecera, true, GeneradorPdf.AZUL)
        filas.forEachIndexed { i, f -> fila(f, false, if (i % 2 == 1) 0xFFF5F5F2.toInt() else null) }
        y += 6f
    }

    fun casilla(t: String, marcado: Boolean) {
        val l = layout(t, 9f, false, GeneradorPdf.TINTA, ancho - 22)
        asegurar(l.height + 4f)
        cv?.let { c ->
            c.drawRect(M, y + 1, M + 10, y + 11, Paint().apply { style = Paint.Style.STROKE; color = GeneradorPdf.TINTA; strokeWidth = 0.8f })
            if (marcado) c.drawText("✓", M + 1.5f, y + 10, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = GeneradorPdf.VERDE })
            c.save(); c.translate(M + 18, y); l.draw(c); c.restore()
        }
        y += l.height + 4f
    }

    fun dibujo(alto: Float, borde: Boolean = true, f: (Canvas, RectF) -> Unit) {
        asegurar(alto + 6)
        cv?.let { c ->
            c.save()
            c.translate(M, y)
            c.clipRect(0f, 0f, ancho, alto)
            f(c, RectF(0f, 0f, ancho, alto))
            c.restore()
            if (borde) c.drawRect(M, y, M + ancho, y + alto, Paint().apply { style = Paint.Style.STROKE; color = 0xFFBBBBBB.toInt(); strokeWidth = 0.6f })
        }
        y += alto + 6
    }

    fun imagen(b: Bitmap, maxAlto: Float, pie: String) {
        val esc = minOf(ancho / b.width, maxAlto / b.height)
        val w = b.width * esc; val h = b.height * esc
        asegurar(h + 16)
        cv?.drawBitmap(b, null, RectF(M, y, M + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
        y += h + 2
        texto(pie, 8f, color = GeneradorPdf.GRIS)
    }
}
