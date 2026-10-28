package co.losmagnificos.izaje.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import co.losmagnificos.izaje.voz.Dictado

/** Pide el permiso del micrófono la primera vez que se pulsa un micrófono y arranca el dictado. */
class ControlVoz(val dictado: Dictado, private val ctx: Context, private val lanzarPermiso: () -> Unit) {

    private class Pendiente(val id: String, val continuo: Boolean, val alTexto: (String) -> Unit)
    private var pendiente: Pendiente? = null

    fun dictar(id: String, continuo: Boolean, alTexto: (String) -> Unit) {
        if (dictado.escuchando && dictado.destino == id) { dictado.detener(); return }
        if (dictado.escuchando) dictado.detener()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            dictado.iniciar(id, continuo, alTexto)
        } else {
            pendiente = Pendiente(id, continuo, alTexto)
            lanzarPermiso()
        }
    }

    fun resultadoPermiso(concedido: Boolean) {
        val p = pendiente
        pendiente = null
        if (concedido && p != null) dictado.iniciar(p.id, p.continuo, p.alTexto)
        else dictado.fallo("Sin permiso del micrófono. Puedes escribir los datos con el teclado.")
    }
}

@Composable
fun rememberControlVoz(): ControlVoz {
    val ctx = LocalContext.current
    val dictado = remember { Dictado(ctx.applicationContext) }
    val propietario = LocalLifecycleOwner.current
    DisposableEffect(propietario) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) dictado.detener() }
        propietario.lifecycle.addObserver(obs)
        onDispose { propietario.lifecycle.removeObserver(obs); dictado.liberar() }
    }
    val soporte = remember { arrayOfNulls<ControlVoz>(1) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> soporte[0]?.resultadoPermiso(ok) }
    return remember {
        ControlVoz(dictado, ctx) { lanzador.launch(Manifest.permission.RECORD_AUDIO) }.also { soporte[0] = it }
    }
}
