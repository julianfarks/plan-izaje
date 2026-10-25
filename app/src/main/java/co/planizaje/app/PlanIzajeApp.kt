package co.planizaje.app

import android.app.Application
import co.planizaje.app.data.Almacen

class PlanIzajeApp : Application() {
    lateinit var almacen: Almacen
        private set

    override fun onCreate() {
        super.onCreate()
        almacen = Almacen(this)
    }
}
