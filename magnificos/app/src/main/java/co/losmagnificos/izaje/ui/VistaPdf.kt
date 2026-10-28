package co.losmagnificos.izaje.ui

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.losmagnificos.izaje.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PantallaPdf(vm: AppViewModel, nombre: String, onVolver: () -> Unit) {
    val ctx = LocalContext.current
    val archivo = remember(nombre) { vm.almacen.archivoPdf(nombre) }
    var paginas by remember(nombre) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var fallo by remember(nombre) { mutableStateOf(false) }

    LaunchedEffect(nombre) {
        try {
            paginas = withContext(Dispatchers.IO) {
                val pfd = ParcelFileDescriptor.open(archivo, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(pfd).use { r ->
                    (0 until r.pageCount).map { i ->
                        r.openPage(i).use { p ->
                            val w = 1100
                            val h = (w.toFloat() * p.height / p.width).toInt()
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(AColor.WHITE)
                            p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }
        } catch (e: Throwable) { fallo = true }
    }

    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.aviso = if (Archivos.copiarA(ctx, archivo, uri)) "PDF guardado." else "No se pudo guardar el PDF."
    }
    val asunto = "Plan de Izaje Los Magníficos SAS – ${vm.plan.cliente}".trimEnd(' ', '–')

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver a editar") }
            Text("PDF generado", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonPrimario("Compartir (WhatsApp, correo…)", { Archivos.compartir(ctx, archivo, asunto) }, icono = Icons.Filled.Share)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonContorno("Abrir", {
                    if (!Archivos.abrir(ctx, archivo)) vm.aviso = "Este teléfono no tiene una app para ver PDF."
                }, Modifier.weight(1f), Icons.AutoMirrored.Filled.OpenInNew)
                BotonContorno("Guardar copia", { guardar.launch(nombre) }, Modifier.weight(1f), Icons.Filled.Save)
            }
            BotonContorno("Volver a editar", onVolver, Modifier.fillMaxWidth())
        }
        when {
            fallo -> Text("No se pudo mostrar la vista previa. Usa «Abrir» o «Compartir».", Modifier.padding(16.dp), fontSize = 16.sp)
            paginas.isEmpty() -> Text("Cargando vista previa…", Modifier.padding(16.dp), fontSize = 16.sp)
            else -> LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(paginas) { i, b ->
                    Image(b.asImageBitmap(), "Página ${i + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                }
            }
        }
    }
}
