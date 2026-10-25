package co.planizaje.app.tabla

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import co.planizaje.core.ia.Fragmento
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Utilidades de imagen para leer tablas de carga. Todo se ejecuta en el teléfono, sin internet. */
object Imagenes {

    const val MAX_LADO = 2600

    /** Carga una imagen respetando la orientación EXIF y limitando el tamaño. */
    fun cargar(ctx: Context, uri: Uri): Bitmap? = runCatching {
        val cr = ctx.contentResolver
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (max(o.outWidth, o.outHeight) / s > MAX_LADO * 1.5) s *= 2
        val b = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return null
        val rot = cr.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val r = if (rot != 0) rotar(b, rot.toFloat()) else b
        limitar(r)
    }.getOrNull()

    fun limitar(b: Bitmap, lado: Int = MAX_LADO): Bitmap {
        val m = max(b.width, b.height)
        if (m <= lado) return b
        val k = lado.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * k).roundToInt(), (b.height * k).roundToInt(), true)
    }

    fun rotar(b: Bitmap, grados: Float): Bitmap =
        Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(grados) }, true)

    /** Páginas de un PDF del fabricante. */
    fun paginasPdf(f: File): Int = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }

    fun renderPdf(f: File, pagina: Int): Bitmap =
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                r.openPage(pagina.coerceIn(0, r.pageCount - 1)).use { p ->
                    val k = min(3f, MAX_LADO.toFloat() / max(p.width, p.height))
                    val bmp = Bitmap.createBitmap((p.width * k).roundToInt(), (p.height * k).roundToInt(), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }

    /**
     * Corrige la perspectiva: lleva el cuadrilátero [q] (arriba-izq, arriba-der, abajo-der, abajo-izq; en px de [src])
     * a un rectángulo. También sirve para recortar la región de la tabla.
     */
    fun corregir(src: Bitmap, q: List<PointF>): Bitmap {
        val (tl, tr, br, bl) = q
        var w = max(hypot(tr.x - tl.x, tr.y - tl.y), hypot(br.x - bl.x, br.y - bl.y))
        var h = max(hypot(bl.x - tl.x, bl.y - tl.y), hypot(br.x - tr.x, br.y - tr.y))
        val k = min(1f, MAX_LADO / max(w, h))
        w = (w * k).coerceAtLeast(10f); h = (h * k).coerceAtLeast(10f)
        val m = Matrix()
        m.setPolyToPoly(floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y), 0, floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, 4)
        val out = Bitmap.createBitmap(w.roundToInt(), h.roundToInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(android.graphics.Color.WHITE)
        c.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** Mejora la legibilidad: escala de grises y más contraste. */
    fun mejorar(b: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix().apply { setSaturation(0f) }
        val contraste = 1.6f
        val t = (-0.5f * contraste + 0.5f) * 255f
        cm.postConcat(ColorMatrix(floatArrayOf(contraste, 0f, 0f, 0f, t, 0f, contraste, 0f, 0f, t, 0f, 0f, contraste, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        Canvas(out).drawBitmap(b, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    /** Reconocimiento de texto en el teléfono (ML Kit, modelo latino incluido en la app, sin internet). */
    fun ocr(b: Bitmap): List<Fragmento> {
        // El OCR funciona mejor con textos de cierto tamaño: amplía imágenes pequeñas.
        val img = if (max(b.width, b.height) < 1400) Bitmap.createScaledBitmap(b, b.width * 2, b.height * 2, true) else b
        val k = b.width.toFloat() / img.width
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val res = Tasks.await(rec.process(InputImage.fromBitmap(img, 0)))
            return res.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { e ->
                val r = e.boundingBox ?: return@mapNotNull null
                val conf = runCatching { e.javaClass.getMethod("getConfidence").invoke(e) as? Float }.getOrNull()?.takeIf { it > 0f }
                Fragmento(e.text, r.left * k, r.top * k, r.width() * k, r.height() * k, conf)
            }
        } finally { rec.close() }
    }

    fun jpeg(b: Bitmap, calidad: Int = 88): ByteArray =
        java.io.ByteArrayOutputStream().use { b.compress(Bitmap.CompressFormat.JPEG, calidad, it); it.toByteArray() }
}
