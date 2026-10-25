package co.planizaje.app.ia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import co.planizaje.app.ui.comp.Aviso
import co.planizaje.app.ui.comp.Ayuda
import co.planizaje.app.ui.comp.BarraSuperior
import co.planizaje.app.ui.theme.Paleta
import kotlinx.coroutines.launch

@Composable
fun PantallaAjustesIA(onAtras: () -> Unit) {
    val ctx = LocalContext.current
    val ia = remember { ServicioIA(ctx) }
    var url by remember { mutableStateOf(ia.url) }
    var codigo by remember { mutableStateOf(ia.codigo) }
    var estado by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { BarraSuperior("Servicio de IA (opcional)", onAtras) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Ayuda("La app funciona completa sin esto: la voz se interpreta con reglas en el teléfono y las tablas se leen con reconocimiento de texto local, todo sin internet.")
            Text("¿Para qué sirve?", style = MaterialTheme.typography.titleMedium)
            Text("Un modelo de IA puede interpretar explicaciones más libres y leer tablas o gráficos difíciles. Lo que proponga pasa por la misma revisión y nunca se da por verificado.", style = MaterialTheme.typography.bodyMedium)
            Text("Seguridad", style = MaterialTheme.typography.titleMedium)
            Text("La app NO lleva claves de API. Necesitas tu propio servidor intermedio (en el repositorio, carpeta «servidor», hay uno listo para Cloudflare Workers con instrucciones). La clave del proveedor de IA se guarda allí como secreto; en el teléfono solo va la dirección y un código de acceso que tú defines. Usarlo requiere internet y puede tener costo según tu proveedor.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Dirección del servidor (https://…)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(codigo, { codigo = it }, Modifier.fillMaxWidth(), label = { Text("Código de acceso") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation())
            Button(onClick = { ia.url = url; ia.codigo = codigo; estado = (ia.configurado to if (ia.configurado) "Guardado." else "Revisa la dirección (debe empezar por https://) y el código.") }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
            OutlinedButton(onClick = {
                ia.url = url; ia.codigo = codigo
                scope.launch { estado = runCatching { ia.probar() }.fold({ true to "Conexión correcta." }, { false to "No se pudo conectar: ${it.message}" }) }
            }, enabled = url.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Probar conexión") }
            OutlinedButton(onClick = { url = ""; codigo = ""; ia.url = ""; ia.codigo = ""; estado = true to "Servicio desactivado." }, modifier = Modifier.fillMaxWidth()) { Text("Desactivar") }
            estado?.let { (ok, m) -> if (ok) Aviso(m, Paleta.Ok, Paleta.OkFondo, "✓") else Aviso(m, Paleta.Mal, Paleta.MalFondo, "✕") }
        }
    }
}
