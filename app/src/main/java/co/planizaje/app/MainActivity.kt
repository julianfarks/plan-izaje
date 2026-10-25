package co.planizaje.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import co.planizaje.app.ui.Navegacion
import co.planizaje.app.ui.comp.LocalAlmacen
import co.planizaje.app.ui.theme.TemaPlanIzaje

class MainActivity : ComponentActivity() {
    private val almacen get() = (application as PlanIzajeApp).almacen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalAlmacen provides almacen) {
                TemaPlanIzaje { Navegacion() }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Garantiza que todo lo editado quede escrito en disco al salir o cambiar de app.
        Thread { almacen.volcar() }.start()
    }
}
