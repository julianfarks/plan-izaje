package co.planizaje.app.voz

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import co.planizaje.app.ia.ServicioIA
import co.planizaje.app.ui.comp.Aviso
import co.planizaje.app.ui.comp.Ayuda
import co.planizaje.app.ui.comp.LocalAlmacen
import co.planizaje.app.ui.comp.Selector
import co.planizaje.app.ui.theme.Paleta
import co.planizaje.core.ia.Ambito
import co.planizaje.core.ia.Aplicador
import co.planizaje.core.ia.CampoExtraido
import co.planizaje.core.ia.Clave
import co.planizaje.core.ia.Conflicto
import co.planizaje.core.ia.EstadoCampo
import co.planizaje.core.ia.Extraccion
import co.planizaje.core.ia.Extractor
import co.planizaje.core.ia.TipoValor
import kotlinx.coroutines.launch

private val EJEMPLOS = mapOf(
    Ambito.GENERAL to "Ej.: «Vamos a levantar un equipo eléctrico de unos 850 kilos que está al lado del edificio y hay que subirlo a una plataforma a unos 12 metros del camión. Usaremos dos eslingas con sus grilletes.»",
    Ambito.INFO to "Ej.: «El proyecto se llama montaje de chiller. La obra está en la calle 10 con carrera 43. El cliente es Constructora Andina.»",
    Ambito.GRUA_PASO to "Ej.: «Vamos con la Palfinger 23500 con 6 extensiones y los estabilizadores totalmente extendidos.»",
    Ambito.CARGA to "Ej.: «Es un transformador de 1,2 toneladas según la placa, mide 2 por 1,5 por 1,8 metros y tiene 4 orejas de izaje.»",
    Ambito.CROQUIS to "Ej.: «La grúa se parquea en la calle frente al edificio. Hay cables de luz al lado del andén y un árbol junto a la entrada.»",
    Ambito.MANIOBRA to "Ej.: «Usamos 2 eslingas de cadena en canasta y 2 grilletes. El operador es Carlos y el señalero es Andrés, por radio.»",
    Ambito.FICHA_GRUA to "Ej.: «Es una Palfinger PK 23500, número de serie 12345, placa TTK 456, con 6 extensiones hidráulicas, montada en un Kenworth T800.»",
)

/**
 * Hoja completa para dictar o escribir y revisar lo interpretado antes de aplicarlo.
 * @param proyectoId proyecto destino (null si se dicta para la ficha de una grúa)
 * @param gruaId grúa destino cuando ambito = FICHA_GRUA
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HojaVoz(ambito: Ambito, proyectoId: String?, gruaId: String?, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val almacen = LocalAlmacen.current
    val gruas by almacen.gruas.collectAsState()
    val proyectos by almacen.proyectos.collectAsState()
    val proyecto = proyectos.firstOrNull { it.id == proyectoId }
    val gruaDestino = gruas.firstOrNull { it.id == gruaId }
    val scope = rememberCoroutineScope()
    val ia = remember { ServicioIA(ctx) }

    var texto by remember { mutableStateOf("") }
    var revisando by remember { mutableStateOf(false) }
    var extraccion by remember { mutableStateOf<Extraccion?>(null) }
    val campos = remember { mutableStateListOf<CampoExtraido>() }
    val faltantes = remember { mutableStateMapOf<Clave, String>() }
    val respuestas = remember { mutableStateMapOf<String, Int>() }
    val reemplazar = remember { mutableStateMapOf<Clave, Boolean>() }
    var trabajandoIA by remember { mutableStateOf(false) }
    var errorIA by remember { mutableStateOf<String?>(null) }
    var resultado by remember { mutableStateOf<List<String>?>(null) }

    val dictado = remember { Dictado(ctx) { t -> texto = (texto.trimEnd() + " " + t.trim()).trim() + if (t.trimEnd().endsWith(".")) "" else "." } }
    DisposableEffect(Unit) { onDispose { dictado.liberar() } }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) dictado.iniciar()
    }

    fun interpretar(e: Extraccion) {
        extraccion = e
        campos.clear(); campos.addAll(e.campos)
        faltantes.clear(); respuestas.clear(); reemplazar.clear()
        revisando = true
    }

    fun finales(): List<CampoExtraido> {
        val base = campos.filter { it.estado != EstadoCampo.NO_PROPORCIONADO && it.valor.isNotBlank() }
        val llenados = faltantes.filterValues { it.isNotBlank() }.map { (k, v) -> CampoExtraido(k, v, EstadoCampo.CONFIRMADO, "Escrito por el usuario") }
        val deRespuestas = extraccion?.preguntas?.flatMap { q -> respuestas[q.id]?.let { q.opciones.getOrNull(it)?.campos } ?: emptyList() } ?: emptyList()
        val clavesResp = deRespuestas.map { it.clave }.toSet()
        return base.filter { it.clave !in clavesResp } + deRespuestas + llenados
    }

    val conflictos: List<Conflicto> = remember(campos.toList(), faltantes.toMap(), respuestas.toMap(), proyecto, gruaDestino) {
        val f = finales()
        when {
            ambito == Ambito.FICHA_GRUA && gruaDestino != null -> Aplicador.conflictosGrua(gruaDestino, f)
            proyecto != null -> Aplicador.conflictos(proyecto, gruas, f)
            else -> emptyList()
        }
    }

    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Scaffold(
            topBar = {
                Surface(tonalElevation = 2.dp) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (revisando) revisando = false else onCerrar() }) {
                            Icon(if (revisando) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Close, if (revisando) "Volver a dictar" else "Cerrar")
                        }
                        Column(Modifier.weight(1f)) {
                            Text(if (revisando) "Revisa los datos" else "Hablar del izaje", style = MaterialTheme.typography.titleMedium)
                            Text(ambito.titulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            bottomBar = {
                Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!revisando) {
                            Button(
                                onClick = { dictado.detener(); interpretar(Extractor.extraer(texto, Extractor.Contexto(gruas, gruaDestino ?: gruas.firstOrNull { it.id == proyecto?.gruaId }), ambito)) },
                                enabled = texto.isNotBlank() && !trabajandoIA, modifier = Modifier.weight(1f).height(54.dp),
                            ) { Text("Interpretar") }
                            if (ia.configurado) OutlinedButton(
                                onClick = {
                                    dictado.detener(); trabajandoIA = true; errorIA = null
                                    scope.launch {
                                        runCatching { ia.extraer(texto, ambito, gruas) }
                                            .onSuccess { interpretar(it) }
                                            .onFailure { errorIA = "Servicio de IA no disponible (${it.message}). Se usa la interpretación sin conexión."; interpretar(Extractor.extraer(texto, Extractor.Contexto(gruas), ambito)) }
                                        trabajandoIA = false
                                    }
                                },
                                enabled = texto.isNotBlank() && !trabajandoIA, modifier = Modifier.height(54.dp),
                            ) { if (trabajandoIA) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.AutoAwesome, null); Text(" Con IA") }
                        } else {
                            OutlinedButton(onClick = { revisando = false }, modifier = Modifier.height(54.dp)) { Text("Seguir dictando") }
                            Button(onClick = {
                                val f = finales()
                                val rem = reemplazar.filterValues { it }.keys
                                val msgs = mutableListOf<String>()
                                if (ambito == Ambito.FICHA_GRUA && gruaDestino != null) {
                                    val r = Aplicador.aplicarGrua(gruaDestino, f, rem)
                                    almacen.guardarGrua(r.valor)
                                    msgs += "Se aplicaron ${r.aplicados.size} dato(s) a la ficha de la grúa."
                                    if (r.omitidos.isNotEmpty()) msgs += "Se conservaron los datos anteriores en: ${r.omitidos.joinToString { it.etiqueta }}."
                                } else if (proyecto != null) {
                                    var aplicados = 0
                                    almacen.actualizarProyecto(proyecto.id, "Datos aplicados desde ${if (extraccion?.campos?.any { it.origen == "ia" } == true) "IA" else "voz/texto"}") { actual ->
                                        val r = Aplicador.aplicar(actual, gruas, f, rem)
                                        aplicados = r.aplicados.size
                                        if (r.omitidos.isNotEmpty()) msgs += "No se cambiaron: ${r.omitidos.distinct().joinToString { it.etiqueta }}."
                                        msgs += r.avisos
                                        r.valor
                                    }
                                    msgs.add(0, "Se aplicaron $aplicados dato(s) al proyecto.")
                                    if (f.any { it.estado == EstadoCampo.APROXIMADO }) msgs += "Los datos aproximados quedan como pendientes de confirmar en la verificación y en el PDF."
                                }
                                resultado = msgs
                            }, enabled = finales().isNotEmpty(), modifier = Modifier.weight(1f).height(54.dp)) { Text("Aplicar datos") }
                        }
                    }
                }
            },
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!revisando) PantallaDictado(ambito, texto, { texto = it }, dictado, errorIA, onMic = {
                    if (dictado.escuchando) dictado.detener()
                    else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) dictado.iniciar()
                    else permiso.launch(Manifest.permission.RECORD_AUDIO)
                })
                else {
                    errorIA?.let { Aviso(it) }
                    val e = extraccion
                    if (e != null) {
                        Ayuda("Comprueba cada dato. Puedes cambiar el texto o el estado. Nada se guarda hasta que pulses «Aplicar datos», y lo que ya tenías no se reemplaza sin tu permiso.")
                        e.avisos.forEach { Aviso(it) }
                        e.preguntas.forEach { q ->
                            Card(colors = CardDefaults.cardColors(containerColor = Paleta.RevisarFondo)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("? ${q.texto}", fontWeight = FontWeight.Bold, color = Color(0xFF1B1F24))
                                    q.opciones.forEachIndexed { i, o ->
                                        Row(Modifier.fillMaxWidth().clickable { respuestas[q.id] = i }, verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(respuestas[q.id] == i, { respuestas[q.id] = i })
                                            Text(o.etiqueta, color = Color(0xFF1B1F24))
                                        }
                                    }
                                }
                            }
                        }
                        if (campos.isEmpty() && e.preguntas.isEmpty()) Aviso("No se encontraron datos en la descripción. Puedes completarlos abajo o volver a dictar con más detalle.", Paleta.Pendiente, Paleta.PendienteFondo, "…")
                        campos.groupBy { it.clave.grupo }.forEach { (grupo, lista) ->
                            Text(grupo, style = MaterialTheme.typography.titleMedium)
                            lista.forEach { c ->
                                val idx = campos.indexOf(c)
                                FilaCampo(
                                    c, conflictos.firstOrNull { it.clave == c.clave }, reemplazar[c.clave] == true, gruas,
                                    onCambio = { n -> if (idx >= 0) campos[idx] = n },
                                    onReemplazar = { b -> reemplazar[c.clave] = b },
                                )
                            }
                        }
                        val vistos = campos.map { it.clave }.toSet() + (e.preguntas.flatMap { q -> q.opciones.flatMap { o -> o.campos.map { it.clave } } })
                        val sinDato = ambito.claves.filter { it !in vistos && it != Clave.PENDIENTES && it.tipo != TipoValor.OPCION }
                        if (sinDato.isNotEmpty()) {
                            HorizontalDivider()
                            Text("No proporcionado", style = MaterialTheme.typography.titleMedium)
                            Text("Estos datos no aparecieron. Puedes escribirlos ahora o dejarlos para después.", style = MaterialTheme.typography.bodySmall)
                            sinDato.forEach { k ->
                                OutlinedTextField(
                                    faltantes[k] ?: "", { faltantes[k] = it }, Modifier.fillMaxWidth(),
                                    label = { Text("— ${k.etiqueta}") }, suffix = if (k.unidad.isNotBlank()) { { Text(k.unidad) } } else null, singleLine = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    resultado?.let { msgs ->
        AlertDialog(
            onDismissRequest = { resultado = null; onCerrar() },
            title = { Text("Datos aplicados") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { msgs.forEach { Text("• $it") } } },
            confirmButton = { TextButton(onClick = { resultado = null; onCerrar() }) { Text("Listo") } },
        )
    }
}

@Composable
private fun PantallaDictado(ambito: Ambito, texto: String, onTexto: (String) -> Unit, dictado: Dictado, errorIA: String?, onMic: () -> Unit) {
    val escala by animateFloatAsState(if (dictado.escuchando) 1f + dictado.nivel * 0.25f else 1f, label = "mic")
    Text(
        if (dictado.escuchando) "Te escucho… habla con calma. Pulsa para terminar." else "Pulsa el micrófono y explica el trabajo con tus palabras, o escríbelo.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size((96 * escala).dp).background(if (dictado.escuchando) Paleta.Mal else Paleta.Ambar, CircleShape)
                .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape).clickable(onClick = onMic),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (dictado.escuchando) Icons.Filled.Stop else Icons.Filled.Mic, if (dictado.escuchando) "Detener" else "Hablar", Modifier.size(44.dp), tint = Paleta.Azul)
        }
    }
    if (dictado.parcial.isNotBlank()) Text("«${dictado.parcial}»", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
    dictado.error?.let { Aviso(it) }
    errorIA?.let { Aviso(it) }
    OutlinedTextField(
        texto, onTexto, Modifier.fillMaxWidth(), minLines = 6,
        label = { Text("Lo que se va a hacer") },
        placeholder = { Text(EJEMPLOS[ambito] ?: "") },
    )
    if (texto.isNotBlank()) TextButton(onClick = { onTexto("") }) { Text("Borrar texto") }
    Ayuda("Puedes aportar información poco a poco: dicta ahora lo que sepas y vuelve a dictar más tarde desde cualquier paso. El reconocimiento de voz lo hace el propio teléfono; según el modelo puede necesitar internet.")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaCampo(
    c: CampoExtraido, conflicto: Conflicto?, reemplaza: Boolean, gruas: List<co.planizaje.core.model.Grua>,
    onCambio: (CampoExtraido) -> Unit, onReemplazar: (Boolean) -> Unit,
) {
    val (col, fondo) = when (c.estado) {
        EstadoCampo.CONFIRMADO -> Paleta.Ok to Paleta.OkFondo
        EstadoCampo.APROXIMADO -> Paleta.Revisar to Paleta.RevisarFondo
        EstadoCampo.NO_PROPORCIONADO -> Paleta.Pendiente to Paleta.PendienteFondo
    }
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, col.copy(alpha = 0.4f), RoundedCornerShape(14.dp))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.estado.simbolo, color = col, fontWeight = FontWeight.Bold, modifier = Modifier.background(fondo, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp))
                Spacer(Modifier.width(8.dp))
                Text(c.clave.etiqueta, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (c.origen == "ia") Text("IA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (c.clave == Clave.GRUA) {
                Selector("Grúa de tu biblioteca", gruas, gruas.firstOrNull { it.id == c.ref }, { it.nombreCorto },
                    { g -> onCambio(c.copy(valor = g.nombreCorto, ref = g.id, estado = EstadoCampo.CONFIRMADO)) })
            } else {
                OutlinedTextField(
                    c.valor, { v -> onCambio(c.copy(valor = v, estado = if (v.isBlank()) EstadoCampo.NO_PROPORCIONADO else EstadoCampo.CONFIRMADO)) },
                    Modifier.fillMaxWidth(), singleLine = c.clave.tipo != TipoValor.TEXTO,
                    suffix = if (c.clave.unidad.isNotBlank() && c.clave.tipo != TipoValor.PESO) { { Text(c.clave.unidad) } } else null,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EstadoCampo.entries.forEach { e ->
                    FilterChip(c.estado == e, { onCambio(c.copy(estado = e)) }, { Text("${e.simbolo} ${e.etiqueta}", style = MaterialTheme.typography.labelSmall) })
                }
            }
            if (c.nota.isNotBlank()) Text(c.nota, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (c.frase.isNotBlank() && c.frase != c.valor) Text("De: «${c.frase.take(140)}»", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.outline)
            if (conflicto != null && c.estado != EstadoCampo.NO_PROPORCIONADO) {
                Column(Modifier.fillMaxWidth().background(Paleta.MalFondo, RoundedCornerShape(10.dp)).padding(10.dp)) {
                    Text("Ya estaba guardado: «${conflicto.actual}»", color = Paleta.Mal, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(!reemplaza, { onReemplazar(false) }); Text("Conservar el anterior", color = Color(0xFF1B1F24))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(reemplaza, { onReemplazar(true) }); Text("Reemplazar por «${c.valor.take(40)}»", color = Color(0xFF1B1F24))
                    }
                }
            }
        }
    }
}
