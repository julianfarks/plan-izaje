package co.planizaje.app.ui.comp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import co.planizaje.core.model.Adjunto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Archivos {
    fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.archivos", f)

    fun abrir(ctx: Context, f: File, mime: String) {
        if (!f.exists()) { Toast.makeText(ctx, "El archivo no existe", Toast.LENGTH_SHORT).show(); return }
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri(ctx, f), mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { ctx.startActivity(i) } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No hay una app instalada para abrir este archivo", Toast.LENGTH_LONG).show()
        }
    }

    fun compartir(ctx: Context, f: File, mime: String, titulo: String = "Compartir") {
        val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri(ctx, f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, titulo))
    }

    fun miniatura(f: File, max: Int): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / s > max * 2 || o.outHeight / s > max * 2) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }.getOrNull()
}

/** Lista de fotos y documentos con botones para añadir desde cámara, galería o archivos. */
@Composable
fun GaleriaAdjuntos(
    titulo: String,
    adjuntos: List<Adjunto>,
    onCambio: (List<Adjunto>) -> Unit,
    fotos: Boolean = true,
    documentos: Boolean = true,
) {
    val ctx = LocalContext.current
    val almacen = LocalAlmacen.current
    var fotoPendiente by remember { mutableStateOf<Adjunto?>(null) }

    val scope = rememberCoroutineScope()
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        scope.launch {
            val nuevos = withContext(Dispatchers.IO) { uris.mapNotNull { almacen.importarAdjunto(it) } }
            if (nuevos.isNotEmpty()) onCambio(adjuntos + nuevos)
        }
    }
    val docs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) { almacen.importarAdjunto(uri) }
            if (n != null) onCambio(adjuntos + n) else Toast.makeText(ctx, "No se pudo copiar el archivo", Toast.LENGTH_SHORT).show()
        }
    }
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val a = fotoPendiente
        if (ok && a != null) onCambio(adjuntos + a) else a?.let { almacen.archivoAdjunto(it).delete() }
        fotoPendiente = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo, style = MaterialTheme.typography.labelLarge)
        if (adjuntos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(adjuntos, key = { it.id }) { a ->
                    TarjetaAdjunto(a, onAbrir = { Archivos.abrir(ctx, almacen.archivoAdjunto(a), a.mime) }, onQuitar = { onCambio(adjuntos - a) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (fotos) {
                FilledTonalButton(onClick = {
                    val (a, f) = almacen.nuevaFoto()
                    fotoPendiente = a
                    camara.launch(Archivos.uri(ctx, f))
                }) { Icon(Icons.Filled.PhotoCamera, null); Text(" Cámara") }
                FilledTonalButton(onClick = { galeria.launch("image/*") }) { Icon(Icons.Filled.PhotoLibrary, null); Text(" Galería") }
            }
            if (documentos) {
                FilledTonalButton(onClick = { docs.launch(arrayOf("application/pdf", "image/*")) }) { Icon(Icons.Filled.AttachFile, null); Text(" PDF") }
            }
        }
    }
}

@Composable
private fun TarjetaAdjunto(a: Adjunto, onAbrir: () -> Unit, onQuitar: () -> Unit) {
    val almacen = LocalAlmacen.current
    val bmp by produceState<Bitmap?>(null, a.id) {
        value = if (a.esImagen) withContext(Dispatchers.IO) { Archivos.miniatura(almacen.archivoAdjunto(a), 240) } else null
    }
    Box(Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onAbrir)) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), a.nombre, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(if (a.esPdf) Icons.Filled.PictureAsPdf else Icons.Filled.Description, null, Modifier.size(32.dp))
                Text(a.nombre, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onQuitar, modifier = Modifier.align(Alignment.TopEnd).size(32.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(50))) {
            Icon(Icons.Filled.Close, "Quitar ${a.nombre}", Modifier.size(18.dp))
        }
    }
}

@Composable
fun FilaArchivoSimple(texto: String, modifier: Modifier = Modifier) {
    Text(texto, modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall)
}
