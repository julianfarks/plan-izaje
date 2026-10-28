package co.losmagnificos.izaje

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.losmagnificos.izaje.core.Analisis
import co.losmagnificos.izaje.core.Analizador
import co.losmagnificos.izaje.core.DatosIzaje
import co.losmagnificos.izaje.core.Interprete
import co.losmagnificos.izaje.core.Plan
import co.losmagnificos.izaje.pdf.GeneradorPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

sealed interface Pantalla {
    data object Principal : Pantalla
    data object Revision : Pantalla
    data class Pdf(val archivo: String) : Pantalla
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val almacen = (app as PlanIzajeApp).almacen
    val planes = almacen.planes

    var pantalla by mutableStateOf<Pantalla>(Pantalla.Principal)
    var plan by mutableStateOf(nuevoPlan())
        private set

    /** Cambia cada vez que se vuelve a interpretar o se abre un plan: los campos de la revisión se reinician con él. */
    var versionDatos by mutableIntStateOf(0)
        private set

    var generando by mutableStateOf(false)
        private set
    var aviso by mutableStateOf<String?>(null)
    /** Resultado del análisis que se muestra en el diálogo «faltan datos» antes de generar el PDF. */
    var dialogoPrevio by mutableStateOf<Analisis?>(null)

    private fun nuevoPlan(): Plan {
        val ahora = System.currentTimeMillis()
        return Plan(
            id = UUID.randomUUID().toString().replace("-", "").take(12),
            creadoMs = ahora, modificadoMs = ahora,
            operador = almacen.ultimoOperador, cedula = almacen.ultimaCedula,
            fecha = LocalDate.now().toString(),
        )
    }

    // ------------------------------------------------------------------ edición

    fun cambiar(f: (Plan) -> Plan) {
        plan = f(plan).copy(modificadoMs = System.currentTimeMillis())
        if (plan.tieneContenido) almacen.guardar(plan)
    }

    fun cambiarDatos(f: (DatosIzaje) -> DatosIzaje) = cambiar { it.copy(datos = f(it.datos)) }

    fun guardarAhora() { if (plan.tieneContenido) almacen.guardar(plan) }

    fun nuevo() {
        guardarAhora()
        plan = nuevoPlan()
        versionDatos++
        pantalla = Pantalla.Principal
    }

    fun abrir(id: String) {
        guardarAhora()
        almacen.plan(id)?.let { plan = it; versionDatos++; pantalla = Pantalla.Principal }
    }

    fun eliminar(id: String) {
        val eraElActual = plan.id == id
        almacen.eliminar(id)
        if (eraElActual) { plan = nuevoPlan(); versionDatos++ }
    }

    // ------------------------------------------------------------------ interpretación y análisis

    /** Interpreta la descripción si cambió desde la última vez, respetando lo que el usuario corrigió a mano. */
    fun interpretarSiHaceFalta() {
        if (plan.textoInterpretado == plan.texto) return
        val nuevos = Interprete.interpretar(plan.texto, plan.datos)
        cambiar { it.copy(datos = nuevos, textoInterpretado = it.texto) }
        versionDatos++
    }

    fun analisis(): Analisis = Analizador.analizar(plan)

    fun revisar() {
        interpretarSiHaceFalta()
        pantalla = Pantalla.Revision
    }

    /** «Generar PDF» desde la pantalla principal: si faltan datos indispensables o algo se excede, antes pregunta. */
    fun pedirPdf() {
        interpretarSiHaceFalta()
        val a = analisis()
        if (a.criticos.isNotEmpty() || a.hayExcedencias) dialogoPrevio = a else generarPdf()
    }

    fun generarPdf() {
        dialogoPrevio = null
        if (generando) return
        interpretarSiHaceFalta()
        generando = true
        val copia = plan
        viewModelScope.launch {
            try {
                val res = withContext(Dispatchers.IO) { GeneradorPdf(getApplication(), almacen.dirPdf).generar(copia) }
                cambiar { it.copy(pdfs = it.pdfs + res.registro) }
                pantalla = Pantalla.Pdf(res.registro.archivo)
            } catch (e: Throwable) {
                aviso = "No se pudo generar el PDF: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                generando = false
            }
        }
    }

    fun verPdf(p: Plan) {
        val ultimo = p.pdfs.lastOrNull() ?: return
        if (plan.id != p.id) abrir(p.id)
        pantalla = Pantalla.Pdf(ultimo.archivo)
    }

    fun volverAEditar() { pantalla = Pantalla.Principal }
}
