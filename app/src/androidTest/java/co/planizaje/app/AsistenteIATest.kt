package co.planizaje.app

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.planizaje.app.data.nuevoId
import co.planizaje.app.pdf.GeneradorPdf
import co.planizaje.app.tabla.Imagenes
import co.planizaje.app.ui.Rutas
import co.planizaje.core.calc.EstadoDato
import co.planizaje.core.calc.Motor
import co.planizaje.core.ia.LectorTabla
import co.planizaje.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AsistenteIATest {

    @get:Rule
    val regla = createAndroidComposeRule<MainActivity>()
    private val almacen get() = (regla.activity.application as PlanIzajeApp).almacen

    private val dictado = "Vamos a levantar un equipo eléctrico que pesa aproximadamente 850 kilos. Está ubicado al lado del edificio y tenemos que " +
        "colocarlo en una plataforma que queda a unos 12 metros del camión. La recogida se hace desde el suelo y la descarga es sobre la plataforma. " +
        "Vamos a utilizar dos eslingas y sus respectivos grilletes, pero todavía tengo que comprobar sus capacidades."

    private fun abrirVoz(id: String) {
        regla.runOnUiThread { }
        regla.onNodeWithText("Hablar del izaje").performClick()
        regla.waitForIdle()
        regla.onNode(hasSetTextAction() and hasText("Lo que se va a hacer", substring = true)).performTextInput(dictado)
        regla.onNode(hasText("Interpretar") and hasClickAction()).performClick()
        regla.waitForIdle()
    }

    /** Casos 1, 3 y 4: dictado completo, peso aproximado y distancia que no es el radio. */
    @Test
    fun dictadoSeRevisaYSeAplica() {
        regla.onNodeWithText("Crear plan de izaje").performClick()
        regla.waitForIdle()
        val id = almacen.proyectos.value.maxBy { it.creado }.id
        abrirVoz(id)
        regla.onAllNodesWithText("Equipo eléctrico").onFirst().assertExists()
        regla.onAllNodesWithText("850 kg").onFirst().assertExists()
        regla.onNodeWithText("¿Qué es esa distancia?", substring = true).assertExists()
        regla.onNode(hasText("Aplicar datos") and hasClickAction()).performClick()
        regla.onNode(hasText("Listo") and hasClickAction()).performClick()
        regla.waitForIdle()
        val p = almacen.proyecto(id)!!
        assertEquals("Equipo eléctrico", p.carga.descripcion)
        assertEquals(850.0, p.carga.pesoKg!!, 1e-9)
        assertEquals(OrigenPeso.ESTIMADO, p.carga.origenPeso)
        assertTrue(p.pendientes.any { it.clave == "CARGA_PESO" })
        assertTrue(p.maniobra.fases.all { it.radioMedido == null })
        assertTrue(p.maniobra.notasEmplazamiento.contains("12"))
        assertTrue(p.carga.posicionFinal.contains("plataforma", true))
        assertEquals(2, p.aparejo.ramales)
        // Caso 10 (parcial): el PDF se genera con la sección de pendientes y sale como borrador
        val salida = GeneradorPdf(almacen, "test").generar(p, null, emptyList())
        assertTrue(salida.version.borrador && salida.archivo.length() > 10_000)
    }

    /** Caso: contradicción con un dato guardado → no se sobrescribe sin permiso. */
    @Test
    fun conflictoConservaElDatoAnterior() {
        val p0 = almacen.crearProyecto()
        almacen.actualizarProyecto(p0.id) { it.copy(carga = it.carga.copy(pesoKg = 900.0, origenPeso = OrigenPeso.PESADO, referenciaPeso = "Ticket 1")) }
        regla.runOnUiThread { }
        regla.activity.runOnUiThread { }
        // Abre el asistente del proyecto en el paso de información
        regla.onNodeWithText("Continuar un proyecto").performClick()
        regla.waitForIdle()
        regla.onNodeWithText("Hablar del izaje").performScrollTo().performClick()
        regla.onNode(hasSetTextAction() and hasText("Lo que se va a hacer", substring = true)).performTextInput("La carga pesa 850 kilos según la placa.")
        regla.onNode(hasText("Interpretar") and hasClickAction()).performClick()
        regla.onNodeWithText("Ya estaba guardado", substring = true).assertExists()
        regla.onNode(hasText("Aplicar datos") and hasClickAction()).performClick()
        regla.onNode(hasText("Listo") and hasClickAction()).performClick()
        assertEquals(900.0, almacen.proyecto(p0.id)!!.carga.pesoKg!!, 1e-9)
        assertEquals(OrigenPeso.PESADO, almacen.proyecto(p0.id)!!.carga.origenPeso)
    }
}

/** OCR real sobre las fotos de placas incluidas en la app (casos 6, 8 y 9). */
@RunWith(AndroidJUnit4::class)
class LecturaTablasRealesTest {

    private val ctx get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<PlanIzajeApp>()

    private val esperados = mapOf(
        "diagramas/hiab_111_b3_clx.jpg" to listOf(3.1 to 3150.0, 4.3 to 2200.0, 5.9 to 1560.0, 7.8 to 1140.0, 9.8 to 900.0, 11.8 to 680.0),
        "diagramas/hiab_288_e8_hipro.png" to listOf(2.5 to 9200.0, 4.7 to 5100.0, 6.3 to 3550.0, 8.1 to 2540.0, 10.0 to 1880.0, 12.0 to 1480.0),
        "diagramas/pm_85028_sp_pt.jpg" to listOf(2.80 to 23000.0, 4.70 to 14270.0, 6.40 to 10200.0, 8.10 to 7670.0, 10.00 to 6000.0),
        "diagramas/palfinger_pk13000c.jpg" to listOf(4.2 to 2730.0, 6.2 to 1710.0, 8.2 to 1210.0, 10.4 to 920.0, 12.5 to 740.0),
        "diagramas/palfinger_pk23500.png" to listOf(4.6 to 4520.0, 6.1 to 3180.0, 8.0 to 2280.0, 10.1 to 1700.0, 12.3 to 1340.0),
    )

    @Test
    fun leerFotosDeMuestra() {
        val informe = StringBuilder()
        var aciertosHiab111 = 0
        for ((asset, esperado) in esperados) {
            val bmp = ctx.assets.open(asset).use { BitmapFactory.decodeStream(it) }
            val frags = Imagenes.ocr(Imagenes.mejorar(bmp))
            // Volcado para análisis y para pruebas de regresión del lector
            runCatching {
                val dir = java.io.File(ctx.filesDir, "ocr").apply { mkdirs() }
                val txt = frags.joinToString("\n") { f -> listOf(f.texto.replace("\t", " "), f.x, f.y, f.ancho, f.alto, f.confianza ?: -1f).joinToString("\t") }
                java.io.File(dir, asset.substringAfterLast('/') + ".tsv").writeText("#${bmp.width}x${bmp.height}\n" + txt)
            }
            val l = LectorTabla.leer(frags)
            val todos = l.tablas.flatMap { it.puntos }
            val aciertos = esperado.count { (r, c) -> todos.any { kotlin.math.abs(it.radio - r) < 0.01 && kotlin.math.abs(it.capacidad - c) < 0.5 } }
            val erroneos = todos.count { p -> esperado.any { kotlin.math.abs(it.first - p.radio) < 0.01 } && esperado.none { kotlin.math.abs(it.first - p.radio) < 0.01 && kotlin.math.abs(it.second - p.capacidad) < 0.5 } }
            if (asset.contains("111")) aciertosHiab111 = aciertos
            informe.append("$asset: modelo='${l.fabricante} ${l.modelo}' tablas=${l.tablas.size} aciertos=$aciertos/${esperado.size} valores_distintos_en_radios_esperados=$erroneos dudosos=${todos.count { it.dudoso }}\n")
        }
        informe.lines().filter { it.isNotBlank() }.forEach { Log.i("OCR_PLANIZAJE", it) }
        // El objetivo de la app no es acertar siempre, sino mostrar todo para revisión. Se exige un mínimo en la placa más limpia.
        assertTrue("Lectura insuficiente de la placa Hiab 111:\n$informe", aciertosHiab111 >= 4)
    }

    @Test
    fun tablaDeOtraGruaQuedaPendienteYPersiste() {
        val almacen = ctx.almacen
        val bmp = ctx.assets.open("diagramas/hiab_111_b3_clx.jpg").use { BitmapFactory.decodeStream(it) }
        val l = LectorTabla.leer(Imagenes.ocr(Imagenes.mejorar(bmp)))
        val g0 = Grua(nuevoId(), "Palfinger", "PK 23500", numeroSerie = "123")
        almacen.guardarGrua(g0)
        // La placa leída es de una Hiab 111: no coincide con la PK 23500
        val coincide = LectorTabla.coincideModelo(l.modelo, l.fabricante, g0.modelo, g0.fabricante)
        assertTrue("coincide=$coincide modelo='${l.modelo}'", coincide != true)
        val t = TablaCarga(nuevoId(), "Importada", puntos = l.tablas.flatMap { it.puntos }.ifEmpty { listOf(PuntoTabla(3.1, 3150.0, dudoso = true)) },
            origen = OrigenTabla.IMPORTADA_OCR, identificacionPendiente = true, fechaImportacion = System.currentTimeMillis(),
            modeloDetectado = "${l.fabricante} ${l.modelo}", fuente = FuenteTecnica("Foto", "placa"))
        almacen.guardarGrua(g0.copy(tablas = listOf(t)))
        val p = Proyecto(nuevoId(), gruaId = g0.id, carga = Carga(pesoKg = 500.0, origenPeso = OrigenPeso.PESADO, referenciaPeso = "x"),
            maniobra = Maniobra(fases = listOf(Fase("f", radioMedido = 5.0)), tablaId = t.id))
        val ev = Motor.evaluar(p, almacen.grua(g0.id), emptyList())
        assertEquals(EstadoDato.NO_VERIFICABLE, ev.resultados.first { it.id == "capacidad_f" }.estado)
        assertNull(ev.resultados.first { it.id == "capacidad_f" }.valorNumerico)
        // Caso 9: guardar y volver a abrir
        almacen.volcar(); almacen.cargarTodo()
        val t2 = almacen.grua(g0.id)!!.tablas.single()
        assertTrue(t2.identificacionPendiente)
        assertEquals(OrigenTabla.IMPORTADA_OCR, t2.origen)
        assertEquals(t.puntos, t2.puntos)
        almacen.eliminarGrua(g0.id)
    }
}

@Suppress("unused") private val r = Rutas.INICIO
