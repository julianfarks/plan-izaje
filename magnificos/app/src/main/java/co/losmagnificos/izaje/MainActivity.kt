package co.losmagnificos.izaje

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import co.losmagnificos.izaje.ui.AppRaiz
import co.losmagnificos.izaje.ui.Tema

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Tema { AppRaiz(vm) } }
    }

    override fun onStop() {
        super.onStop()
        vm.guardarAhora()   // todo lo escrito queda guardado al salir o cambiar de app
    }
}
