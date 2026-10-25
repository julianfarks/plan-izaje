package co.planizaje.app.tabla

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import co.planizaje.app.data.nuevoId
import co.planizaje.app.ia.ServicioIA
import co.planizaje.app.ui.comp.Archivos
import co.planizaje.app.ui.comp.Aviso
import co.planizaje.app.ui.comp.Ayuda
import co.planizaje.app.ui.comp.BarraSuperior
import co.planizaje.app.ui.comp.CampoEntero
import co.planizaje.app.ui.comp.CampoNumero
import co.planizaje.app.ui.comp.CampoTexto
import co.planizaje.app.ui.comp.LocalAlmacen
import co.planizaje.app.ui.comp.avisar
import co.planizaje.app.ui.theme.Paleta
import co.planizaje.core.ia.LecturaTabla
import co.planizaje.core.ia.LectorTabla
import co.planizaje.core.model.FuenteTecnica
import co.planizaje.core.model.OrigenTabla
import co.planizaje.core.model.PuntoTabla
import co.planizaje.core.model.TablaCarga
import co.planizaje.core.util.Unidades
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.min

private enum class Fase { FUENTE, AJUSTAR, ANALIZANDO, REVISAR }

/** Fila editable de la revisión. [leida] guarda lo que leyó la máquina para registrar correcciones. */
private data class Fila(val radio: Double?, val capacidad: Double?, val dudoso: Boolean, val leida: PuntoTabla?)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaImportarTabla(gruaId: String, onAtras: () -> Unit, onImportada: (String) -> Unit) {
    val ctx = LocalContext.current
    val almacen = LocalAlmacen.current
    val gruas by almacen.gruas.collectAsState()
    val grua = gruas.firstOrNull { it.id == gruaId }
    val scope = rememberCoroutineScope()
    val ia = remember { ServicioIA(ctx) }

    var fase by remember { mutableStateOf(Fase.FUENTE) }
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var trabajo by remember { mutableStateOf<Bitmap?>(null) }       // tras girar
    var procesada by remember { mutableStateOf<Bitmap?>(null) }     // tras recortar/mejorar
    var esquinas by remember { mutableStateOf(listOf(PointF(0f, 0f), PointF(1f, 0f), PointF(1f, 1f), PointF(0f, 1f))) } // normalizadas
    var mejorar by remember { mutableStateOf(true) }
    var pdf by remember { mutableStateOf<File?>(null) }
    var paginas by remember { mutableIntStateOf(0) }
    var pagina by remember { mutableIntStateOf(0) }
    var lectura by remember { mutableStateOf<LecturaTabla?>(null) }
    var porIA by remember { mutableStateOf(false) }
    var tablaSel by remember { mutableIntStateOf(0) }
    val filas = remember { mutableStateListOf<Fila>() }
    var nombre by remember { mutableStateOf("") }
    var configuracion by remember { mutableStateOf("") }
    var extensiones by remember { mutableStateOf<Int?>(null) }
    var referencia by remember { mutableStateOf("") }
    var confirmaGrua by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fotoTmp = remember { File(ctx.cacheDir, "tabla-foto.jpg") }

    if (grua == null) { Scaffold(topBar = { BarraSuperior("Importar tabla", onAtras) }) { p -> Text("Grúa no encontrada", Modifier.padding(p).padding(16.dp)) }; return }

    fun cargarBitmap(b: Bitmap?) {
        if (b == null) { error = "No se pudo abrir la imagen."; return }
        original = b; trabajo = b; error = null
        esquinas = listOf(PointF(0.03f, 0.03f), PointF(0.97f, 0.03f), PointF(0.97f, 0.97f), PointF(0.03f, 0.97f))
        fase = Fase.AJUSTAR
    }

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { cargarBitmap(withContext(Dispatchers.IO) { Imagenes.cargar(ctx, Archivos.uri(ctx, fotoTmp)) }) }
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { cargarBitmap(withContext(Dispatchers.IO) { Imagenes.cargar(ctx, uri) }) }
    }
    val abrirPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val f = File(ctx.cacheDir, "tabla-fabricante.pdf")
                    ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
                    val n = Imagenes.paginasPdf(f)
                    Triple(f, n, Imagenes.renderPdf(f, 0))
                }
            }
            r.onSuccess { (f, n, b) -> pdf = f; paginas = n; pagina = 0; cargarBitmap(b) }
                .onFailure { error = "No se pudo abrir el PDF (puede estar protegido o dañado): ${it.message}" }
        }
    }

    fun recortar(): Bitmap? {
        val b = trabajo ?: return null
        val q = esquinas.map { PointF(it.x * b.width, it.y * b.height) }
        val c = Imagenes.corregir(b, q)
        return if (mejorar) Imagenes.mejorar(c) else c
    }

    fun cargarLectura(l: LecturaTabla, ia: Boolean) {
        lectura = l; porIA = ia; tablaSel = 0
        filas.clear()
        l.tablas.firstOrNull()?.puntos?.sortedBy { it.radio }?.forEach { filas += Fila(it.radio, it.capacidad, it.dudoso, it) }
        nombre = l.tablas.firstOrNull()?.nombre?.takeIf { !it.startsWith("Fila") } ?: "Tabla importada"
        referencia = l.referencia
        val coincide = LectorTabla.coincideModelo(l.modelo, l.fabricante, grua.modelo, grua.fabricante)
        confirmaGrua = false
        if (coincide == false) error = "El modelo leído en la imagen («${l.fabricante} ${l.modelo}») no coincide con esta grúa (${grua.nombreCorto})."
        fase = Fase.REVISAR
    }

    fun analizar(conIA: Boolean) {
        val b = recortar() ?: return
        procesada = b
        fase = Fase.ANALIZANDO
        error = null
        scope.launch {
            val r = runCatching {
                if (conIA) ia.leerTabla(Imagenes.jpeg(b), grua)
                else withContext(Dispatchers.Default) { LectorTabla.leer(Imagenes.ocr(b)) }
            }
            r.onSuccess { cargarLectura(it, conIA) }
                .onFailure { error = (if (conIA) "El servicio de IA no respondió: " else "No se pudo leer el texto: ") + it.message; fase = Fase.AJUSTAR }
        }
    }

    fun elegirTabla(i: Int) {
        val l = lectura ?: return
        tablaSel = i
        filas.clear()
        l.tablas[i].puntos.sortedBy { it.radio }.forEach { filas += Fila(it.radio, it.capacidad, it.dudoso, it) }
        nombre = l.tablas[i].nombre.takeIf { !it.startsWith("Fila") } ?: "Tabla importada"
    }

    fun importar() {
        val l = lectura ?: return
        val validas = filas.filter { it.radio != null && it.capacidad != null && it.radio > 0 && it.capacidad > 0 }
        if (validas.isEmpty()) { error = "No hay filas completas para importar."; return }
        scope.launch {
            val adjs = withContext(Dispatchers.IO) {
                buildList {
                    original?.let { add(almacen.guardarImagen(it, "Foto original del diagrama (${java.time.LocalDate.now()})", "Imagen original importada")) }
                    procesada?.let { add(almacen.guardarImagen(it, "Tabla recortada y corregida", "Región analizada")) }
                    pdf?.let { add(almacen.guardarArchivo(it, "Documento del fabricante (PDF)", "application/pdf", "Página ${pagina + 1} analizada")) }
                }
            }
            val correcciones = mutableListOf<String>()
            val leidas = l.tablas.getOrNull(tablaSel)?.puntos ?: emptyList()
            filas.forEachIndexed { i, f ->
                val o = f.leida
                if (o == null) correcciones += "Fila ${i + 1} añadida manualmente: ${Unidades.fmt(f.radio)} m → ${Unidades.fmt(f.capacidad, 0)} kg"
                else {
                    if (o.radio != f.radio) correcciones += "Fila ${i + 1}: radio corregido ${Unidades.fmt(o.radio)} → ${Unidades.fmt(f.radio)} m"
                    if (o.capacidad != f.capacidad) correcciones += "Fila ${i + 1}: capacidad corregida ${Unidades.fmt(o.capacidad, 0)} → ${Unidades.fmt(f.capacidad, 0)} kg"
                    if (o.dudoso && !f.dudoso) correcciones += "Fila ${i + 1}: comprobada por el usuario"
                }
            }
            leidas.filter { p -> filas.none { it.leida == p } }.forEach { correcciones += "Fila eliminada: ${Unidades.fmt(it.radio)} m → ${Unidades.fmt(it.capacidad, 0)} kg" }
            val fuentePrincipal = adjs.firstOrNull { it.esPdf } ?: adjs.firstOrNull()
            val t = TablaCarga(
                id = nuevoId(),
                nombre = nombre.ifBlank { "Tabla importada" },
                configuracionPluma = configuracion,
                extensiones = extensiones,
                puntos = validas.map { PuntoTabla(it.radio!!, it.capacidad!!, dudoso = it.dudoso) },
                fuente = FuenteTecnica(
                    if (pdf != null) "Documento PDF del fabricante importado" else "Foto del diagrama de carga importada",
                    listOfNotNull(referencia.ifBlank { null }, if (pdf != null) "pág. ${pagina + 1}" else null).joinToString(", ").ifBlank { "imagen importada" },
                    adjuntoId = fuentePrincipal?.id,
                ),
                origen = if (porIA) OrigenTabla.IMPORTADA_IA else OrigenTabla.IMPORTADA_OCR,
                fechaImportacion = System.currentTimeMillis(),
                modeloDetectado = listOf(l.fabricante, l.modelo).filter { it.isNotBlank() }.joinToString(" "),
                unidades = l.unidades,
                identificacionPendiente = !confirmaGrua,
                correcciones = correcciones,
                advertenciasImportacion = l.advertencias + filas.filter { it.radio == null || it.capacidad == null }.map { "Fila incompleta no importada: ${it.radio ?: "?"} m / ${it.capacidad ?: "?"} kg" } +
                    (l.tablas.getOrNull(tablaSel)?.sinEmparejar ?: emptyList()).map { "Sin emparejar en la lectura: $it" },
            )
            val g = almacen.grua(gruaId) ?: return@launch
            almacen.guardarGrua(g.copy(tablas = g.tablas + t, documentos = g.documentos + adjs))
            avisar(ctx, if (t.identificacionPendiente) "Tabla guardada como pendiente de identificación" else "Tabla importada: revísala y verifícala", true)
            onImportada(t.id)
        }
    }

    Scaffold(
        topBar = { BarraSuperior("Importar tabla de carga", onAtras, subtitulo = grua.nombreCorto) },
        bottomBar = {
            if (fase == Fase.AJUSTAR || fase == Fase.REVISAR) Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (fase == Fase.AJUSTAR) {
                        OutlinedButton(onClick = { fase = Fase.FUENTE }, modifier = Modifier.height(54.dp)) { Text("Otra imagen") }
                        Button(onClick = { analizar(false) }, modifier = Modifier.weight(1f).height(54.dp)) { Text("Leer tabla") }
                        if (ia.configurado) OutlinedButton(onClick = { analizar(true) }, modifier = Modifier.height(54.dp)) { Icon(Icons.Filled.AutoAwesome, "Leer con IA") }
                    } else {
                        OutlinedButton(onClick = { fase = Fase.FUENTE }, modifier = Modifier.height(54.dp)) { Text("Volver a fotografiar") }
                        Button(onClick = { importar() }, modifier = Modifier.weight(1f).height(54.dp)) { Text("Confirmar e importar") }
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { Aviso(it, Paleta.Mal, Paleta.MalFondo, "✕") }
            when (fase) {
                Fase.FUENTE -> {
                    Ayuda("Fotografía el diagrama de carga de cabina o importa el PDF del fabricante. La app leerá radios y capacidades para que los revises; nada se usa en cálculos hasta que lo confirmes y verifiques.")
                    Ayuda("Consejos: foto de frente, con buena luz, sin reflejos, que se vea el modelo de la grúa y la tabla completa.")
                    Button(onClick = { camara.launch(Archivos.uri(ctx, fotoTmp)) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Filled.PhotoCamera, null); Text("  Tomar foto") }
                    FilledTonalButton(onClick = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Filled.PhotoLibrary, null); Text("  Elegir de la galería") }
                    FilledTonalButton(onClick = { abrirPdf.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Icon(Icons.Filled.PictureAsPdf, null); Text("  Importar PDF del fabricante") }
                    Text("La lectura se hace en el teléfono, sin internet." + if (ia.configurado) " También puedes usar el servicio de IA configurado." else "", style = MaterialTheme.typography.bodySmall)
                }
                Fase.AJUSTAR -> {
                    if (pdf != null && paginas > 1) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Página ${pagina + 1} de $paginas", Modifier.weight(1f))
                            OutlinedButton(onClick = { if (pagina > 0) { pagina--; scope.launch { cargarBitmap(withContext(Dispatchers.IO) { Imagenes.renderPdf(pdf!!, pagina) }) } } }, enabled = pagina > 0) { Text("Anterior") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { if (pagina < paginas - 1) { pagina++; scope.launch { cargarBitmap(withContext(Dispatchers.IO) { Imagenes.renderPdf(pdf!!, pagina) }) } } }, enabled = pagina < paginas - 1) { Text("Siguiente") }
                        }
                    }
                    Text("Arrastra las 4 esquinas sobre los bordes de la tabla. Así se recorta la región y se corrige la perspectiva.", style = MaterialTheme.typography.bodyMedium)
                    trabajo?.let { b -> EditorEsquinas(b, esquinas) { esquinas = it } }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { trabajo = trabajo?.let { Imagenes.rotar(it, 90f) } }) { Icon(Icons.AutoMirrored.Filled.RotateRight, null); Text(" Girar") }
                        FilledTonalButton(onClick = { esquinas = listOf(PointF(0f, 0f), PointF(1f, 0f), PointF(1f, 1f), PointF(0f, 1f)) }) { Icon(Icons.Filled.CropFree, null); Text(" Toda la imagen") }
                        FilterChip(mejorar, { mejorar = !mejorar }, { Text("Mejorar legibilidad") }, leadingIcon = { Icon(Icons.Filled.Contrast, null) })
                    }
                }
                Fase.ANALIZANDO -> {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("Leyendo la tabla…")
                        }
                    }
                }
                Fase.REVISAR -> {
                    val l = lectura ?: return@Column
                    Text("Compara cada valor con la imagen", style = MaterialTheme.typography.titleMedium)
                    procesada?.let { b -> Image(b.asImageBitmap(), "Imagen analizada", Modifier.fillMaxWidth().heightIn(max = 320.dp).background(Color.White, RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit) }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Leído en la imagen${if (porIA) " (servicio de IA)" else " (reconocimiento en el teléfono)"}", fontWeight = FontWeight.Bold)
                            Text("Fabricante: ${l.fabricante.ifBlank { "no legible" }} · Modelo: ${l.modelo.ifBlank { "no legible" }}")
                            Text("Referencia: ${l.referencia.ifBlank { "no legible" }} · Unidades: ${l.unidades.ifBlank { "no detectadas" }}")
                        }
                    }
                    l.advertencias.forEach { Aviso(it) }
                    if (l.tablas.size > 1) {
                        Text("Se detectaron ${l.tablas.size} filas de capacidad (configuraciones distintas). Importa una cada vez:", style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            l.tablas.forEachIndexed { i, t -> FilterChip(tablaSel == i, { elegirTabla(i) }, { Text("${t.nombre} (${t.puntos.size})") }) }
                        }
                    }
                    Row { Text("Radio (m)", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text("Capacidad (kg)", Modifier.weight(1.2f), fontWeight = FontWeight.Bold); Text("Ok", Modifier.width(48.dp), fontWeight = FontWeight.Bold); Spacer(Modifier.width(40.dp)) }
                    filas.forEachIndexed { i, f ->
                        androidx.compose.runtime.key(i, filas.size) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.background(if (f.dudoso) Paleta.RevisarFondo else Color.Transparent, RoundedCornerShape(8.dp))) {
                                CampoNumero(f.radio, { v -> filas[i] = filas[i].copy(radio = v) }, "", "m", Modifier.weight(1f))
                                CampoNumero(f.capacidad, { v -> filas[i] = filas[i].copy(capacidad = v) }, "", "kg", Modifier.weight(1.2f))
                                Checkbox(!f.dudoso, { ok -> filas[i] = filas[i].copy(dudoso = !ok) }, modifier = Modifier.width(48.dp))
                                IconButton(onClick = { filas.removeAt(i) }) { Icon(Icons.Filled.Delete, "Quitar fila") }
                            }
                        }
                    }
                    Text("Filas en amarillo: lectura dudosa. Marca «Ok» solo después de comprobar el valor en la imagen. Las que dejes sin marcar se guardan como pendientes de verificar.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { filas += Fila(null, null, false, null) }) { Icon(Icons.Filled.Add, null); Text(" Añadir fila") }
                    l.tablas.getOrNull(tablaSel)?.sinEmparejar?.takeIf { it.isNotEmpty() }?.let { s ->
                        Aviso("No se pudieron emparejar: ${s.joinToString(", ")}. Si son valores de la tabla, añádelos a mano comprobando la imagen.")
                    }
                    CampoTexto(nombre, { nombre = it }, "Nombre de la tabla", marcador = "Ej. 6 extensiones (fila E)")
                    CampoTexto(configuracion, { configuracion = it }, "Configuración a la que corresponde", obligatorio = true, marcador = "Extensiones, plumín, estabilizadores…")
                    CampoEntero(extensiones, { extensiones = it }, "Número de extensiones", max = 20)
                    CampoTexto(referencia, { referencia = it }, "Referencia / versión del documento")
                    Card(colors = CardDefaults.cardColors(containerColor = if (confirmaGrua) Paleta.OkFondo else Paleta.RevisarFondo)) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(confirmaGrua, { confirmaGrua = it })
                            Text("Confirmo que esta tabla es de ${grua.nombreCorto}${if (grua.numeroSerie.isNotBlank()) " (serie ${grua.numeroSerie})" else ""} y de la configuración indicada.", color = Color(0xFF1B1F24))
                        }
                    }
                    if (!confirmaGrua) Text("Sin esta confirmación la tabla se guarda como «pendiente de identificación»: queda en la ficha como documento, pero no se usa en los cálculos.", style = MaterialTheme.typography.bodySmall, color = Paleta.Revisar)
                    Text("Después de importar, la tabla queda «sin verificar» hasta que compruebes cada valor contra el original en la ficha de la grúa.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Imagen con 4 esquinas arrastrables (coordenadas normalizadas 0..1). */
@Composable
private fun EditorEsquinas(b: Bitmap, esquinas: List<PointF>, onCambio: (List<PointF>) -> Unit) {
    val img = remember(b) { b.asImageBitmap() }
    var tam by remember { mutableStateOf(IntSize(1, 1)) }
    var activa by remember { mutableIntStateOf(-1) }
    val ratio = b.width.toFloat() / b.height
    Box(Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.4f, 2.5f)).background(Color.Black)) {
        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(b) {
                    detectDragGestures(
                        onDragStart = { o ->
                            val (ox, oy, w, h) = area(size.width.toFloat(), size.height.toFloat(), ratio)
                            activa = esquinas.indices.minByOrNull { i -> hypot(ox + esquinas[i].x * w - o.x, oy + esquinas[i].y * h - o.y) } ?: -1
                        },
                        onDragEnd = { activa = -1 },
                    ) { ch, d ->
                        ch.consume()
                        if (activa < 0) return@detectDragGestures
                        val (_, _, w, h) = area(size.width.toFloat(), size.height.toFloat(), ratio)
                        val n = esquinas.toMutableList()
                        n[activa] = PointF((n[activa].x + d.x / w).coerceIn(0f, 1f), (n[activa].y + d.y / h).coerceIn(0f, 1f))
                        onCambio(n)
                    }
                },
        ) {
            val (ox, oy, w, h) = area(size.width, size.height, ratio)
            val pts = esquinas.map { Offset(ox + it.x * w, oy + it.y * h) }
            val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            drawPath(path, Color(0x33F2A900))
            drawPath(path, Paleta.Ambar, style = Stroke(3.dp.toPx()))
            pts.forEach { drawCircle(Color.White, 14.dp.toPx(), it); drawCircle(Paleta.Azul, 14.dp.toPx(), it, style = Stroke(3.dp.toPx())) }
        }
    }
}

private data class Area(val ox: Float, val oy: Float, val w: Float, val h: Float)

private fun area(cw: Float, ch: Float, ratio: Float): Area {
    val w = min(cw, ch * ratio)
    val h = w / ratio
    return Area((cw - w) / 2, (ch - h) / 2, w, h)
}
