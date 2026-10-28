package co.losmagnificos.izaje.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.losmagnificos.izaje.AppViewModel
import co.losmagnificos.izaje.Pantalla

@Composable
fun AppRaiz(vm: AppViewModel) {
    val voz = rememberControlVoz()
    val snack = remember { SnackbarHostState() }
    val pantalla = vm.pantalla

    BackHandler(enabled = pantalla !is Pantalla.Principal) { vm.volverAEditar() }

    LaunchedEffect(vm.aviso) {
        vm.aviso?.let { snack.showSnackbar(it); vm.aviso = null }
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }, containerColor = Paleta.Fondo) { margen ->
        Surface(Modifier.fillMaxSize().padding(margen), color = Paleta.Fondo) {
            when (pantalla) {
                is Pantalla.Principal -> PantallaPrincipal(vm, voz)
                is Pantalla.Revision -> PantallaRevision(vm) { vm.volverAEditar() }
                is Pantalla.Pdf -> PantallaPdf(vm, pantalla.archivo) { vm.volverAEditar() }
            }
        }
    }

    vm.dialogoPrevio?.let { a ->
        AlertDialog(
            onDismissRequest = { vm.dialogoPrevio = null },
            title = { Text(if (a.criticos.isNotEmpty()) "Faltan datos indispensables" else "Hay valores que exceden lo informado") },
            text = {
                Column {
                    a.alertas.forEach { Text("⚠ $it", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paleta.Rojo) }
                    a.criticos.take(8).forEach { Text("• ${it.texto}", fontSize = 15.sp) }
                    if (a.criticos.size > 8) Text("• y ${a.criticos.size - 8} más…", fontSize = 15.sp)
                    Text("\nSi continúas, el PDF saldrá marcado como «PRELIMINAR – BORRADOR».", fontSize = 14.sp)
                }
            },
            confirmButton = { TextButton(onClick = { vm.dialogoPrevio = null; vm.pantalla = Pantalla.Revision }) { Text("Completar datos", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { vm.generarPdf() }) { Text("Generar borrador preliminar") } },
        )
    }

    if (vm.generando) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Generando PDF…") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(color = Paleta.Naranja); Spacer(Modifier.width(16.dp)); Text("Un momento") } },
        )
    }
}
