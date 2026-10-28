package co.losmagnificos.izaje.ui

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fact
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.losmagnificos.izaje.AppViewModel
import co.losmagnificos.izaje.R
import co.losmagnificos.izaje.core.Plan
import co.losmagnificos.izaje.pdf.GeneradorPdf
import java.time.LocalDate

private fun mayusculasPalabras(t: String): String =
    t.trim().split(Regex("\\s+")).joinToString(" ") { w -> w.lowercase().replaceFirstChar { c -> c.uppercase() } }

@Composable
fun PantallaPrincipal(vm: AppViewModel, voz: ControlVoz) {
    val ctx = LocalContext.current
    val planes by vm.planes.collectAsState()
    val plan = vm.plan
    val dic = voz.dictado
    var menu by remember { mutableStateOf(false) }
    var aEliminar by remember { mutableStateOf<Plan?>(null) }

    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.almacen.exportar().toByteArray()) } }.isSuccess
            vm.aviso = if (ok) "Respaldo guardado." else "No se pudo guardar el respaldo."
        }
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.aviso = try {
                val texto = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val n = vm.almacen.importar(texto)
                "Se incorporaron $n planes del respaldo."
            } catch (e: Exception) { "El archivo no es un respaldo válido." }
        }
    }

    fun elegirFecha() {
        val f = runCatching { LocalDate.parse(plan.fecha) }.getOrElse { LocalDate.now() }
        DatePickerDialog(ctx, { _, a, m, d -> vm.cambiar { it.copy(fecha = LocalDate.of(a, m + 1, d).toString()) } }, f.year, f.monthValue - 1, f.dayOfMonth).show()
    }

    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Cabecera con el logotipo
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.logo_magnificos), "Logotipo de Los Magníficos SAS",
                    modifier = Modifier.size(68.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Plan de Izaje", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Los Magníficos SAS", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más opciones") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Exportar respaldo") }, leadingIcon = { Icon(Icons.Filled.FileUpload, null) },
                            onClick = { menu = false; exportar.launch("Respaldo_Plan_Izaje_${LocalDate.now()}.json") },
                        )
                        DropdownMenuItem(
                            text = { Text("Importar respaldo") }, leadingIcon = { Icon(Icons.Filled.FileDownload, null) },
                            onClick = { menu = false; importar.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        )
                    }
                }
            }
        }

        item { BotonPrimario("Nuevo plan", { vm.nuevo() }, icono = Icons.Filled.AddCircle) }

        // Datos del trabajo: solo cinco
        item {
            Tarjeta("Datos del trabajo") {
                Campo(
                    plan.operador, { v -> vm.cambiar { it.copy(operador = v) } }, "Nombre completo del operador",
                    mayusculas = KeyboardCapitalization.Words,
                    onMic = { voz.dictar("operador", false) { t -> vm.cambiar { it.copy(operador = mayusculasPalabras(t)) } } },
                    micActivo = dic.destino == "operador",
                )
                Campo(
                    plan.cedula, { v -> vm.cambiar { it.copy(cedula = v) } }, "Cédula del operador", teclado = KeyboardType.Number,
                    onMic = { voz.dictar("cedula", false) { t -> vm.cambiar { it.copy(cedula = t.filter { c -> c.isDigit() }) } } },
                    micActivo = dic.destino == "cedula",
                )
                Box {
                    Campo(
                        GeneradorPdf.fechaLegible(plan.fecha), {}, "Fecha del trabajo",
                        apoyo = "Toca para cambiarla",
                    )
                    Box(Modifier.matchParentSize().clickable { elegirFecha() })
                    Icon(
                        Icons.Filled.CalendarMonth, "Elegir fecha",
                        Modifier.align(Alignment.CenterEnd).padding(end = 14.dp, bottom = 18.dp).size(26.dp),
                    )
                }
                Campo(
                    plan.cliente, { v -> vm.cambiar { it.copy(cliente = v) } }, "Nombre del cliente",
                    mayusculas = KeyboardCapitalization.Words,
                    onMic = { voz.dictar("cliente", false) { t -> vm.cambiar { it.copy(cliente = t.trim()) } } },
                    micActivo = dic.destino == "cliente",
                )
                Campo(
                    plan.sitio, { v -> vm.cambiar { it.copy(sitio = v) } }, "Sitio o ubicación del trabajo",
                    mayusculas = KeyboardCapitalization.Sentences,
                    onMic = { voz.dictar("sitio", false) { t -> vm.cambiar { it.copy(sitio = t.trim()) } } },
                    micActivo = dic.destino == "sitio",
                )
            }
        }

        // Descripción del izaje
        item {
            Tarjeta {
                OutlinedTextField(
                    value = plan.texto,
                    onValueChange = { v -> vm.cambiar { it.copy(texto = v) } },
                    label = { Text("Describe o dicta los datos del izaje") },
                    placeholder = {
                        Text(
                            "Ej.: Voy a levantar una carga de 3 toneladas, la recojo a 2 metros de radio y la coloco a 10 metros. " +
                                "La grúa a 2 metros levanta 18 toneladas y a 10 metros, 5 toneladas…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 230.dp),
                    textStyle = TextStyle(fontSize = 18.sp),
                    minLines = 8,
                    shape = RoundedCornerShape(12.dp),
                )
                val escuchandoTexto = dic.escuchando && dic.destino == "texto"
                Button(
                    onClick = {
                        voz.dictar("texto", true) { t ->
                            vm.cambiar { it.copy(texto = (it.texto.trimEnd() + " " + t.trim()).trim()) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (escuchandoTexto) Paleta.Rojo else Paleta.Negro, contentColor = Color.White,
                    ),
                ) {
                    Icon(if (escuchandoTexto) Icons.Filled.Stop else Icons.Filled.Mic, null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (escuchandoTexto) "Detener dictado" else "Dictar maniobra", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                if (escuchandoTexto) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(progress = { dic.nivel }, modifier = Modifier.fillMaxWidth(), color = Paleta.Naranja)
                        Text(
                            if (dic.parcial.isBlank()) "Escuchando… habla con calma; puedes hacer pausas." else dic.parcial,
                            fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        "Puedes dictar varias veces o escribir: lo nuevo se añade al final. Antes de generar el PDF puedes corregir el texto y revisar los datos que la app entendió.",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                dic.error?.let { Text(it, fontSize = 15.sp, color = Paleta.Rojo, fontWeight = FontWeight.SemiBold) }
            }
        }

        item { BotonPrimario("Revisar datos", { vm.revisar() }, icono = Icons.Filled.Fact) }
        item { BotonContorno("Generar PDF", { vm.pedirPdf() }, Modifier.fillMaxWidth(), Icons.Filled.PictureAsPdf) }

        // Historial
        item {
            Text("Planes anteriores", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        }
        if (planes.isEmpty()) {
            item { Text("Aún no hay planes guardados. Los planes se guardan solos mientras los escribes.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp) }
        }
        items(planes, key = { it.id }) { p ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { vm.abrir(p.id) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (p.id == plan.id) Paleta.NaranjaClaro else Color.White),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(GeneradorPdf.fechaLegible(p.fecha), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        EtiquetaEstado(p.estadoResumen)
                    }
                    Text(p.cliente.ifBlank { "Sin cliente" }, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(p.sitio.ifBlank { "Sin sitio" }, fontSize = 15.sp)
                    Text("Operador: " + p.operador.ifBlank { "—" }, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (p.pdfs.isNotEmpty()) {
                            TextButton(onClick = { vm.verPdf(p) }) {
                                Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(6.dp))
                                Text("Ver PDF (${p.pdfs.size})", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { aEliminar = p }) { Icon(Icons.Filled.Delete, "Eliminar plan", tint = Paleta.Gris) }
                    }
                }
            }
        }
        item { Spacer(Modifier.size(24.dp)) }
    }

    aEliminar?.let { p ->
        AlertDialog(
            onDismissRequest = { aEliminar = null },
            title = { Text("¿Eliminar este plan?") },
            text = { Text("Se borrarán también sus PDF guardados en la app. Esto no se puede deshacer.") },
            confirmButton = { TextButton(onClick = { vm.eliminar(p.id); aEliminar = null }) { Text("Eliminar", color = Paleta.Rojo, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { aEliminar = null }) { Text("Cancelar") } },
        )
    }
}
