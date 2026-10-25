package co.planizaje.app.ui.comp

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.planizaje.app.data.Almacen
import co.planizaje.app.ui.theme.Paleta
import co.planizaje.core.calc.EstadoDato
import co.planizaje.core.calc.Veredicto
import co.planizaje.core.util.UnidadPeso
import co.planizaje.core.util.Unidades
import java.time.LocalDate

val LocalAlmacen = staticCompositionLocalOf<Almacen> { error("Almacen no disponible") }

/** Campo al que el asistente quiere llevar al usuario. */
class Objetivo { var clave by mutableStateOf<String?>(null) }
val LocalObjetivo = staticCompositionLocalOf { Objetivo() }

/** Marca un campo con una clave: si el asistente la pide, se desplaza hasta él y le da el foco. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.objetivo(clave: String?): Modifier {
    if (clave == null) return this
    val obj = LocalObjetivo.current
    val traer = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val foco = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(obj.clave) {
        if (obj.clave == clave) {
            kotlinx.coroutines.delay(300)
            runCatching { traer.bringIntoView() }
            runCatching { foco.requestFocus() }
            obj.clave = null
        }
    }
    return this.bringIntoViewRequester(traer).focusRequester(foco)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperior(
    titulo: String,
    onAtras: (() -> Unit)? = null,
    subtitulo: String? = null,
    acciones: @Composable () -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                if (subtitulo != null) Text(subtitulo, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        navigationIcon = {
            if (onAtras != null) IconButton(onClick = onAtras) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
        },
        actions = { acciones() },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun CampoTexto(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    ayuda: String? = null,
    obligatorio: Boolean = false,
    multilinea: Boolean = false,
    teclado: KeyboardType = KeyboardType.Text,
    marcador: String? = null,
    clave: String? = null,
) {
    val falta = obligatorio && valor.isBlank()
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(if (obligatorio) "$etiqueta *" else etiqueta) },
        placeholder = marcador?.let { { Text(it) } },
        supportingText = when {
            falta -> { { Text("Dato necesario para el plan") } }
            ayuda != null -> { { Text(ayuda) } }
            else -> null
        },
        singleLine = !multilinea,
        minLines = if (multilinea) 3 else 1,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            keyboardType = teclado,
            imeAction = if (multilinea) ImeAction.Default else ImeAction.Next,
        ),
        modifier = modifier.fillMaxWidth().objetivo(clave),
    )
}

/**
 * Campo numérico con unidad visible. Acepta coma o punto decimal.
 * Mantiene el texto escrito mientras se edita para no "saltar" al escribir "2,".
 */
@Composable
fun CampoNumero(
    valor: Double?,
    onCambio: (Double?) -> Unit,
    etiqueta: String,
    unidad: String,
    modifier: Modifier = Modifier,
    ayuda: String? = null,
    obligatorio: Boolean = false,
    min: Double? = 0.0,
    max: Double? = null,
    entero: Boolean = false,
    clave: String? = null,
) {
    var texto by remember { mutableStateOf(Unidades.fmtEditable(valor)) }
    LaunchedEffect(valor) {
        if (Unidades.parse(texto) != valor) texto = Unidades.fmtEditable(valor)
    }
    val num = Unidades.parse(texto)
    val error = when {
        texto.isNotBlank() && num == null -> "Escriba un número válido (ej. 2,5)"
        num != null && min != null && num < min -> "Debe ser mayor o igual que ${Unidades.fmt(min)}"
        num != null && max != null && num > max -> "Debe ser menor o igual que ${Unidades.fmt(max)}"
        num != null && entero && num % 1.0 != 0.0 -> "Debe ser un número entero"
        obligatorio && texto.isBlank() -> "Dato necesario para el plan"
        else -> null
    }
    OutlinedTextField(
        value = texto,
        onValueChange = { t ->
            texto = t
            val n = Unidades.parse(t)
            val valido = n != null && (min == null || n >= min) && (max == null || n <= max) && (!entero || n % 1.0 == 0.0)
            if (t.isBlank()) onCambio(null) else if (valido) onCambio(n)
        },
        label = { Text(if (obligatorio) "$etiqueta *" else etiqueta) },
        suffix = { Text(unidad, fontWeight = FontWeight.SemiBold) },
        isError = error != null && !(obligatorio && texto.isBlank()),
        supportingText = (error ?: ayuda)?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (entero) KeyboardType.Number else KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth().objetivo(clave),
    )
}

@Composable
fun CampoEntero(valor: Int?, onCambio: (Int?) -> Unit, etiqueta: String, modifier: Modifier = Modifier, ayuda: String? = null, max: Int? = null) {
    CampoNumero(valor?.toDouble(), { onCambio(it?.toInt()) }, etiqueta, "", modifier, ayuda, entero = true, max = max?.toDouble())
}

/** Peso con selector kg / t. Siempre se guarda en kg. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoPeso(valorKg: Double?, onCambio: (Double?) -> Unit, etiqueta: String, ayuda: String? = null, obligatorio: Boolean = false, clave: String? = null) {
    var unidad by rememberSaveable { mutableStateOf(UnidadPeso.KG) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Unidad:", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            SingleChoiceSegmentedButtonRow {
                UnidadPeso.entries.forEachIndexed { i, u ->
                    SegmentedButton(
                        selected = unidad == u, onClick = { unidad = u },
                        shape = SegmentedButtonDefaults.itemShape(i, UnidadPeso.entries.size),
                    ) { Text(if (u == UnidadPeso.KG) "kg" else "toneladas") }
                }
            }
        }
        // clave por unidad: al cambiar de unidad el campo se reconstruye mostrando el valor convertido
        androidx.compose.runtime.key(unidad) {
            CampoNumero(
                valor = valorKg?.let { Unidades.desdeKg(it, unidad) },
                onCambio = { v -> onCambio(v?.let { Unidades.aKg(it, unidad) }) },
                etiqueta = etiqueta, unidad = unidad.simbolo, obligatorio = obligatorio, clave = clave,
                ayuda = (ayuda?.let { "$it · " } ?: "") + if (valorKg != null) "Equivale a ${Unidades.kgYt(valorKg)}" else "",
            )
        }
    }
}

@Composable
fun CampoFecha(valor: String, onCambio: (String) -> Unit, etiqueta: String, obligatorio: Boolean = false, clave: String? = null) {
    val ctx = LocalContext.current
    val valida = valor.isBlank() || runCatching { LocalDate.parse(valor) }.isSuccess
    OutlinedTextField(
        value = valor, onValueChange = onCambio,
        label = { Text(if (obligatorio) "$etiqueta *" else etiqueta) },
        placeholder = { Text("AAAA-MM-DD") },
        isError = !valida,
        supportingText = if (!valida) { { Text("Use el formato AAAA-MM-DD o el calendario") } } else null,
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = {
                val d = runCatching { LocalDate.parse(valor) }.getOrDefault(LocalDate.now())
                DatePickerDialog(ctx, { _, y, m, day -> onCambio(LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
            }) { Icon(Icons.Filled.CalendarMonth, "Elegir fecha") }
        },
        modifier = Modifier.fillMaxWidth().objetivo(clave),
    )
}

@Composable
fun Seccion(
    titulo: String,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    subtitulo: String? = null,
    desplegable: Boolean = false,
    inicialAbierta: Boolean = true,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    var abierta by rememberSaveable(titulo) { mutableStateOf(inicialAbierta || !desplegable) }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (desplegable) Modifier.clickable { abierta = !abierta } else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icono != null) {
                Icon(icono, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                if (subtitulo != null) Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (desplegable) Icon(if (abierta) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (abierta) "Cerrar" else "Abrir")
        }
        AnimatedVisibility(abierta) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = contenido)
        }
    }
}

@Composable
fun Ayuda(texto: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), RoundedCornerShape(10.dp)).padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Aviso(texto: String, color: Color = Paleta.Revisar, fondo: Color = Paleta.RevisarFondo, simbolo: String = "!") {
    Row(
        Modifier.fillMaxWidth().background(fondo, RoundedCornerShape(10.dp)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(simbolo, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF1B1F24))
    }
}

fun coloresEstado(e: EstadoDato): Pair<Color, Color> = when (e) {
    EstadoDato.DOCUMENTADO -> Paleta.Ok to Paleta.OkFondo
    EstadoDato.REVISAR -> Paleta.Revisar to Paleta.RevisarFondo
    EstadoDato.PENDIENTE -> Paleta.Pendiente to Paleta.PendienteFondo
    EstadoDato.NO_VERIFICABLE -> Paleta.Mal to Paleta.MalFondo
}

@Composable
fun Etiqueta(simbolo: String, texto: String, color: Color, fondo: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.background(fondo, RoundedCornerShape(50)).border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(simbolo, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(texto, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun ChipEstado(e: EstadoDato, modifier: Modifier = Modifier) {
    val (c, f) = coloresEstado(e)
    Etiqueta(e.simbolo, e.etiqueta, c, f, modifier)
}

@Composable
fun ChipVeredicto(v: Veredicto, modifier: Modifier = Modifier) {
    if (v == Veredicto.CUMPLE) Etiqueta(v.simbolo, v.etiqueta, Paleta.Ok, Paleta.OkFondo, modifier)
    else Etiqueta(v.simbolo, v.etiqueta, Paleta.Mal, Paleta.MalFondo, modifier)
}

@Composable
fun <T> Selector(
    etiqueta: String,
    opciones: List<T>,
    seleccion: T?,
    texto: (T) -> String,
    onSeleccion: (T) -> Unit,
    modifier: Modifier = Modifier,
    vacio: String = "Seleccionar…",
    clave: String? = null,
) {
    var abierto by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().objetivo(clave)) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { abierto = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(10.dp)) {
                Text(seleccion?.let(texto) ?: vacio, modifier = Modifier.weight(1f), maxLines = 2)
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                opciones.forEach { o ->
                    DropdownMenuItem(text = { Text(texto(o)) }, onClick = { onSeleccion(o); abierto = false })
                }
            }
        }
    }
}

@Composable
fun FilaInterruptor(texto: String, marcado: Boolean, onCambio: (Boolean) -> Unit, descripcion: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable { onCambio(!marcado) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(texto, style = MaterialTheme.typography.bodyLarge)
            if (descripcion != null) Text(descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = marcado, onCheckedChange = onCambio)
    }
}

@Composable
fun Confirmar(
    titulo: String,
    texto: String,
    confirmar: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
    peligroso: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = {
            TextButton(onClick = onConfirmar) {
                Text(confirmar, color = if (peligroso) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

@Composable
fun Vacio(icono: ImageVector, titulo: String, texto: String, accion: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icono, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        accion?.invoke()
    }
}

@Composable
fun FilaDato(nombre: String, valor: String, origen: String = "") {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(nombre, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (origen.isNotBlank()) Text(origen, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
