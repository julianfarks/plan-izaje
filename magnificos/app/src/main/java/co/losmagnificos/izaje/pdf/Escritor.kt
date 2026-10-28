package co.losmagnificos.izaje.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/** Colores corporativos tomados del logotipo: naranja y negro. */
object Colores {
    val NARANJA = Color.rgb(241, 86, 38)
    val NEGRO = Color.rgb(17, 17, 17)
    val GRIS = Color.rgb(90, 90, 90)
    val GRIS_CLARO = Color.rgb(244, 241, 238)
    val LINEA = Color.rgb(205, 200, 195)
    val ROJO = Color.rgb(176, 32, 24)
    val ROJO_FONDO = Color.rgb(252, 232, 230)
    val AMBAR_FONDO = Color.rgb(255, 243, 224)
    val VERDE = Color.rgb(30, 110, 60)
}

class Cabecera(
    val tituloDoc: String,
    val idDoc: String,
    val version: Int,
    val fechaTrabajo: String,
    val estado: String,
    val preliminar: Boolean,
    val logo: Bitmap?,
)

/**
 * Escribe un PDF A4 con PdfDocument de Android: cabecera con logo en cada página, pie con numeración,
 * tablas con salto de página por filas, párrafos que continúan en la página siguiente y marca de agua si es preliminar.
 * Todo se mide con StaticLayout, así que nada se corta ni se sale de los márgenes.
 */
class Escritor(private val doc: PdfDocument, private val cab: Cabecera, private val totalPaginas: Int) {

    companion object {
        const val W = 595
        const val H = 842
        const val MI = 40f
        const val MD = 40f
        const val CW = W - MI - MD            // ancho útil
        const val TOPE = 112f                 // donde empieza el contenido
        const val BASE = H - 54f              // límite inferior del contenido
    }

    var paginas = 0; private set
    private var pagina: PdfDocument.Page? = null
    private var c: Canvas = Canvas()
    private var y = TOPE

    // ------------------------------------------------------------------ pinceles
    private fun tp(size: Float, color: Int = Colores.NEGRO, negrita: Boolean = false, cursiva: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (negrita && cursiva) Typeface.BOLD_ITALIC else if (negrita) Typeface.BOLD else if (cursiva) Typeface.ITALIC else Typeface.NORMAL)
    }

    private fun relleno(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private fun trazo(color: Int, ancho: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = ancho }

    private fun diseno(texto: CharSequence, p: TextPaint, ancho: Float, alin: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(texto, 0, texto.length, p, ancho.toInt().coerceAtLeast(10))
            .setAlignment(alin).setLineSpacing(0f, 1.08f).setIncludePad(false).build()

    // ------------------------------------------------------------------ páginas
    fun nuevaPagina() {
        cerrarPagina()
        paginas++
        pagina = doc.startPage(PdfDocument.PageInfo.Builder(W, H, paginas).create())
        c = pagina!!.canvas
        dibujarCabecera()
        y = TOPE
    }

    private fun cerrarPagina() {
        val p = pagina ?: return
        dibujarPie()
        doc.finishPage(p)
        pagina = null
    }

    fun cerrar(): Int { cerrarPagina(); return paginas }

    private fun asegurar(alto: Float) {
        if (pagina == null || y + alto > BASE) nuevaPagina()
    }

    private fun dibujarCabecera() {
        // barra naranja superior
        c.drawRect(0f, 0f, W.toFloat(), 9f, relleno(Colores.NARANJA))
        // marca de agua
        if (cab.preliminar) {
            c.save()
            c.rotate(-38f, W / 2f, H / 2f)
            val p = tp(78f, Color.argb(34, 176, 32, 24), negrita = true)
            val txt = "PRELIMINAR"
            c.drawText(txt, (W - p.measureText(txt)) / 2f, H / 2f + 26f, p)
            c.restore()
        }
        // logotipo original, esquina superior derecha, con sus proporciones
        var anchoLogo = 0f
        cab.logo?.let { b ->
            val alto = 70f
            anchoLogo = alto * b.width / b.height
            c.drawBitmap(b, null, RectF(W - MD - anchoLogo, 18f, W - MD, 18f + alto), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        val maxAncho = CW - anchoLogo - 14f
        // título corporativo
        var tam = 18f
        val pt = tp(tam, Colores.NEGRO, negrita = true)
        while (pt.measureText(cab.tituloDoc) > maxAncho && tam > 11f) { tam -= 0.5f; pt.textSize = tam }
        c.drawText(cab.tituloDoc, MI, 46f, pt)
        // datos del documento
        val ps = tp(8.5f, Colores.GRIS)
        c.drawText("Documento: ${cab.idDoc}  ·  Versión ${cab.version}  ·  Fecha del trabajo: ${cab.fechaTrabajo}", MI, 62f, ps)
        // etiqueta de estado
        val pe = tp(8.5f, if (cab.preliminar) Colores.ROJO else Colores.NEGRO, negrita = true)
        val txt = cab.estado
        val ancho = pe.measureText(txt) + 14f
        val caja = RectF(MI, 70f, MI + ancho, 87f)
        c.drawRoundRect(caja, 4f, 4f, relleno(if (cab.preliminar) Colores.ROJO_FONDO else Colores.AMBAR_FONDO))
        c.drawRoundRect(caja, 4f, 4f, trazo(if (cab.preliminar) Colores.ROJO else Colores.NARANJA, 1f))
        c.drawText(txt, MI + 7f, 82f, pe)
        // regla naranja
        c.drawRect(MI, 95f, W - MD, 97.5f, relleno(Colores.NARANJA))
    }

    private fun dibujarPie() {
        c.drawLine(MI, H - 40f, W - MD, H - 40f, trazo(Colores.LINEA, 0.8f))
        val p = tp(7.5f, Colores.GRIS)
        c.drawText("${cab.tituloDoc}  ·  ${cab.idDoc}  ·  ${cab.estado}", MI, H - 27f, p)
        val num = if (totalPaginas > 0) "Página $paginas de $totalPaginas" else "Página $paginas"
        c.drawText(num, W - MD - p.measureText(num), H - 27f, p)
    }

    // ------------------------------------------------------------------ bloques
    fun espacio(h: Float) { y += h }

    fun seccion(texto: String) {
        asegurar(48f)
        y += 8f
        c.drawRect(MI, y, MI + 5f, y + 17f, relleno(Colores.NARANJA))
        c.drawText(texto, MI + 12f, y + 13f, tp(11.5f, Colores.NEGRO, negrita = true))
        y += 21f
        c.drawLine(MI, y, W - MD, y, trazo(Colores.LINEA, 0.8f))
        y += 6f
    }

    fun subtitulo(texto: String) {
        asegurar(24f)
        c.drawText(texto, MI, y + 10f, tp(9.5f, Colores.NEGRO, negrita = true))
        y += 16f
    }

    /** Párrafo que puede continuar en la página siguiente. */
    fun parrafo(texto: String, size: Float = 9.5f, color: Int = Colores.NEGRO, cursiva: Boolean = false, sangria: Float = 0f, negrita: Boolean = false) {
        if (texto.isBlank()) return
        val l = diseno(texto, tp(size, color, negrita, cursiva), CW - sangria)
        dibujarPorPartes(l, MI + sangria)
        y += 4f
    }

    private fun dibujarPorPartes(l: StaticLayout, x: Float) {
        var linea = 0
        while (linea < l.lineCount) {
            if (pagina == null) nuevaPagina()
            val disponible = BASE - y
            var fin = linea
            while (fin < l.lineCount && l.getLineBottom(fin) - l.getLineTop(linea) <= disponible) fin++
            if (fin == linea) {
                if (y > TOPE + 1f) { nuevaPagina(); continue }
                fin = linea + 1   // una línea más alta que la página: se fuerza
            }
            val top = l.getLineTop(linea).toFloat()
            val bottom = l.getLineBottom(fin - 1).toFloat()
            c.save()
            c.clipRect(x, y, x + l.width, y + (bottom - top))
            c.translate(x, y - top)
            l.draw(c)
            c.restore()
            y += bottom - top
            linea = fin
            if (linea < l.lineCount) nuevaPagina()
        }
    }

    fun vineta(texto: String, size: Float = 9.5f, color: Int = Colores.NEGRO) {
        val l = diseno(texto, tp(size, color), CW - 14f)
        asegurar(minOf(l.height.toFloat(), 40f))
        c.drawCircle(MI + 4f, y + size * 0.55f, 1.8f, relleno(Colores.NARANJA))
        dibujarPorPartes(l, MI + 14f)
        y += 2f
    }

    fun numerado(n: Int, texto: String) {
        val l = diseno(texto, tp(9.5f), CW - 20f)
        asegurar(minOf(l.height.toFloat(), 40f))
        c.drawText("$n.", MI + 2f, y + 9.5f, tp(9.5f, Colores.NARANJA, negrita = true))
        dibujarPorPartes(l, MI + 20f)
        y += 2f
    }

    fun casilla(texto: String) {
        val l = diseno(texto, tp(9.5f), CW - 20f)
        asegurar(l.height.toFloat() + 4f)
        c.drawRect(MI + 1f, y + 1f, MI + 10f, y + 10f, trazo(Colores.NEGRO, 1f))
        dibujarPorPartes(l, MI + 20f)
        y += 3f
    }

    /** Recuadro de aviso con texto que se ajusta al ancho. */
    fun caja(texto: String, fondo: Int, borde: Int, colorTexto: Int = Colores.NEGRO, negrita: Boolean = false, size: Float = 9f) {
        val l = diseno(texto, tp(size, colorTexto, negrita), CW - 20f)
        val alto = l.height + 16f
        asegurar(minOf(alto, BASE - TOPE))
        val r = RectF(MI, y, W - MD, y + alto)
        c.drawRoundRect(r, 5f, 5f, relleno(fondo))
        c.drawRoundRect(r, 5f, 5f, trazo(borde, 1.2f))
        c.save()
        c.translate(MI + 10f, y + 8f)
        l.draw(c)
        c.restore()
        y += alto + 6f
    }

    /** Tabla de dos columnas etiqueta / valor. */
    fun clave(pares: List<Pair<String, String>>, anchoEtiqueta: Float = 0.34f) {
        val wl = CW * anchoEtiqueta
        val wv = CW - wl
        for ((et, vl) in pares) {
            val le = diseno(et, tp(9f, Colores.GRIS, negrita = true), wl - 12f)
            val lv = diseno(vl.ifBlank { "—" }, tp(9.5f), wv - 12f)
            val alto = maxOf(le.height, lv.height) + 10f
            asegurar(alto)
            c.drawRect(MI, y, MI + wl, y + alto, relleno(Colores.GRIS_CLARO))
            c.drawRect(MI, y, W - MD, y + alto, trazo(Colores.LINEA, 0.6f))
            c.drawLine(MI + wl, y, MI + wl, y + alto, trazo(Colores.LINEA, 0.6f))
            c.save(); c.translate(MI + 6f, y + 5f); le.draw(c); c.restore()
            c.save(); c.translate(MI + wl + 6f, y + 5f); lv.draw(c); c.restore()
            y += alto
        }
        y += 6f
    }

    /**
     * Tabla con cabecera que se repite en cada página. [pesos] = anchos relativos; [derecha] = columnas alineadas a la derecha.
     * Las filas cuyo primer texto empieza por «»» se resaltan como total.
     */
    fun tabla(pesos: List<Float>, cabecera: List<String>, filas: List<List<String>>, derecha: Set<Int> = emptySet(), resaltarUltima: Boolean = false) {
        val suma = pesos.sum()
        val ws = pesos.map { CW * it / suma }
        fun altoFila(celdas: List<String>, negrita: Boolean): Float {
            var m = 0
            celdas.forEachIndexed { i, t -> m = maxOf(m, diseno(t, tp(8.8f, negrita = negrita), ws[i] - 8f).height) }
            return m + 9f
        }
        fun dibujarCabecera() {
            val alto = altoFila(cabecera, true)
            c.drawRect(MI, y, W - MD, y + alto, relleno(Colores.NEGRO))
            var x = MI
            cabecera.forEachIndexed { i, t ->
                val l = diseno(t, tp(8.8f, Color.WHITE, negrita = true), ws[i] - 8f, if (i in derecha) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
                c.save(); c.translate(x + 4f, y + 4.5f); l.draw(c); c.restore()
                x += ws[i]
            }
            y += alto
        }
        asegurar(altoFila(cabecera, true) + (filas.firstOrNull()?.let { altoFila(it, false) } ?: 0f))
        dibujarCabecera()
        filas.forEachIndexed { idx, fila ->
            val ultimaResaltada = resaltarUltima && idx == filas.lastIndex
            val alto = altoFila(fila, ultimaResaltada)
            if (y + alto > BASE) { nuevaPagina(); dibujarCabecera() }
            val fondo = when {
                ultimaResaltada -> Colores.AMBAR_FONDO
                idx % 2 == 1 -> Colores.GRIS_CLARO
                else -> Color.WHITE
            }
            c.drawRect(MI, y, W - MD, y + alto, relleno(fondo))
            var x = MI
            fila.forEachIndexed { i, t ->
                val l = diseno(t, tp(8.8f, negrita = ultimaResaltada), ws[i] - 8f, if (i in derecha) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
                c.save(); c.translate(x + 4f, y + 4.5f); l.draw(c); c.restore()
                x += ws[i]
            }
            c.drawLine(MI, y + alto, W - MD, y + alto, trazo(Colores.LINEA, 0.5f))
            y += alto
        }
        y += 8f
    }

    /** Tres recuadros de firma: elaboró, revisó y aprobó. */
    fun firmas(elaboro: Pair<String, String>) {
        val alto = 118f
        asegurar(alto + 6f)
        val gap = 10f
        val w = (CW - 2 * gap) / 3f
        val titulos = listOf("ELABORÓ", "REVISÓ", "APROBÓ")
        for (i in 0 until 3) {
            val x = MI + i * (w + gap)
            val r = RectF(x, y, x + w, y + alto)
            c.drawRoundRect(r, 4f, 4f, trazo(Colores.NEGRO, 1f))
            c.drawRect(x, y, x + w, y + 16f, relleno(Colores.NEGRO))
            c.drawText(titulos[i], x + 8f, y + 11.5f, tp(8.5f, Color.WHITE, negrita = true))
            val p = tp(8f, Colores.GRIS)
            val nombre = if (i == 0) elaboro.first else ""
            val ced = if (i == 0) elaboro.second else ""
            c.drawText("Nombre:", x + 8f, y + 32f, p)
            if (nombre.isNotBlank()) c.drawText(recortar(nombre, tp(8.5f), w - 52f), x + 46f, y + 32f, tp(8.5f))
            c.drawLine(x + 44f, y + 34f, x + w - 8f, y + 34f, trazo(Colores.LINEA, 0.7f))
            c.drawText("Cédula:", x + 8f, y + 50f, p)
            if (ced.isNotBlank()) c.drawText(recortar(ced, tp(8.5f), w - 52f), x + 46f, y + 50f, tp(8.5f))
            c.drawLine(x + 44f, y + 52f, x + w - 8f, y + 52f, trazo(Colores.LINEA, 0.7f))
            c.drawText("Cargo:", x + 8f, y + 68f, p)
            c.drawLine(x + 44f, y + 70f, x + w - 8f, y + 70f, trazo(Colores.LINEA, 0.7f))
            c.drawText("Fecha:", x + 8f, y + 86f, p)
            c.drawLine(x + 44f, y + 88f, x + w - 8f, y + 88f, trazo(Colores.LINEA, 0.7f))
            c.drawText("Firma:", x + 8f, y + 108f, p)
            c.drawLine(x + 44f, y + 110f, x + w - 8f, y + 110f, trazo(Colores.LINEA, 0.7f))
        }
        y += alto + 6f
    }

    private fun recortar(t: String, p: TextPaint, ancho: Float): String {
        if (p.measureText(t) <= ancho) return t
        var s = t
        while (s.length > 1 && p.measureText("$s…") > ancho) s = s.dropLast(1)
        return "$s…"
    }
}
