package co.losmagnificos.izaje

import android.app.Application
import co.losmagnificos.izaje.data.Almacen

class PlanIzajeApp : Application() {
    lateinit var almacen: Almacen
        private set

    override fun onCreate() {
        super.onCreate()
        almacen = Almacen(this)
    }
}
