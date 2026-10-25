package co.planizaje.app

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.planizaje.app.data.nuevoId
import co.planizaje.app.pdf.GeneradorPdf
import co.planizaje.app.ui.croquis.exportarPng
import co.planizaje.core.calc.Motor
import co.planizaje.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Genera un plan completo con todos los tipos de elementos y comprueba el PDF y el PNG resultantes. */
@RunWith(AndroidJUnit4::class)
class GeneradorPdfTest {

    private val almacen get() = (ApplicationProvider.getApplicationContext<PlanIzajeApp>()).almacen

    private fun proyectoMuestra(grua: Grua, acc: List<Accesorio>): Proyecto {
        val elems = TipoElemento.entries.mapIndexed { i, t ->
            Elemento(nuevoId(), t, Elemento.CAPA_GENERAL, x = (i % 6) * 6.0, y = (i / 6) * 6.0, alto = 4.0, etiqueta = t.etiqueta, tensionKv = 13.2, distanciaMinima = 3.0, anchoEstabilizado = 5.0)
        }
        val r = elems.first { it.tipo == TipoElemento.RECOGIDA }
        val d = elems.first { it.tipo == TipoElemento.DESCARGA }
        return Proyecto(
            id = nuevoId(), nombre = "Prueba PDF", cliente = "Cliente", ubicacion = "Bogotá", fecha = "2026-10-25",
            elaboradoPor = "Operador", descripcion = "Plan de prueba automática", gruaId = grua.id,
            carga = Carga("Tanque", 900.0, OrigenPeso.DOCUMENTADO, "Plano 1", 2.0, 1.5, 1.8, wllPuntoIzajeKg = 2000.0),
            croquis = Croquis(elementos = elems),
            maniobra = Maniobra(fases = listOf(Fase(nuevoId(), "Fase única", r.id, d.id, radioMedido = 8.0, alturaGancho = 7.0, recorrido = listOf(Pt(3.0, 3.0)))), tablaId = grua.tablas.first().id),
            aparejo = Aparejo(ramales = 2, anguloVertical = 30.0, eslingaIds = acc.map { it.id }),
            procedimiento = Procedimiento(secuencia = "1. Izar", operador = "A", senalero = "B", comunicacion = "Radio",
                peligros = Plantillas.peligros().map { it.copy(aplica = true) }),
            historial = listOf(Cambio(System.currentTimeMillis(), "Creado en prueba", 0)),
        )
    }

    @Test
    fun generaPdfConVariasPaginasYPngDelCroquis() {
        val grua = almacen.crearGruaDesdePlantilla(PlantillasGruas.todas.first { it.clave == "hiab111" })
        val acc = listOf(
            Accesorio(nuevoId(), TipoAccesorio.ESLINGA_TEXTIL, identificacion = "E1", wllVertical = 2000.0, pesoKg = 2.0, estado = EstadoInspeccion.APTO, numeroCertificado = "C1"),
            Accesorio(nuevoId(), TipoAccesorio.ESLINGA_TEXTIL, identificacion = "E2", wllVertical = 2000.0, pesoKg = 2.0, estado = EstadoInspeccion.APTO, numeroCertificado = "C2"),
        )
        val p = proyectoMuestra(grua, acc)
        val ev = Motor.evaluar(p, grua, acc)
        assertTrue("La tabla sin verificar debe dejar el plan en borrador", ev.estadoDocumento.esBorrador)

        val salida = GeneradorPdf(almacen, "test").generar(p, grua, acc)
        assertTrue(salida.archivo.exists())
        assertTrue("PDF demasiado pequeño", salida.archivo.length() > 20_000)
        ParcelFileDescriptor.open(salida.archivo, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r -> assertTrue("Se esperaban al menos 6 páginas, hay ${r.pageCount}", r.pageCount >= 6) }
        }
        assertEquals(true, salida.version.borrador)

        val png = exportarPng(ApplicationProvider.getApplicationContext<PlanIzajeApp>().cacheDir, p)
        assertTrue(png.exists() && png.length() > 10_000)
    }

    @Test
    fun guardaYRecuperaProyectoDesdeDisco() {
        val p = almacen.crearProyecto()
        almacen.actualizarProyecto(p.id, "prueba") { it.copy(nombre = "Persistencia", croquis = Croquis(elementos = listOf(Elemento("e1", TipoElemento.EDIFICIO, x = 3.0, y = 4.0, alto = 9.0)))) }
        almacen.volcar()
        almacen.cargarTodo()
        val leido = almacen.proyecto(p.id)!!
        assertEquals("Persistencia", leido.nombre)
        assertEquals(9.0, leido.croquis.elementos.single().alto!!, 1e-9)
        almacen.eliminarProyecto(p.id)
    }

    @Test
    fun copiaDeSeguridadIdaYVuelta() {
        val p = almacen.crearProyecto()
        almacen.actualizarProyecto(p.id) { it.copy(nombre = "Para copia") }
        val f = java.io.File(ApplicationProvider.getApplicationContext<PlanIzajeApp>().cacheDir, "copia-test.zip")
        f.outputStream().use { almacen.exportarCopia(it, "test") }
        almacen.eliminarProyecto(p.id)
        Thread.sleep(300)
        assertTrue(almacen.proyecto(p.id) == null)
        val r = f.inputStream().use { almacen.importarCopia(it, reemplazar = false, versionApp = "test") }
        assertTrue(r.proyectos >= 1)
        assertEquals("Para copia", almacen.proyecto(p.id)?.nombre)
    }
}
