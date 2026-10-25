package co.planizaje.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.planizaje.core.model.TipoElemento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Recorrido completo de un usuario: crear proyecto, datos básicos, carga, croquis con edificio,
 * grúa y puntos, fase, generar el PDF y comprobar que todo se conserva al volver a abrir.
 */
@RunWith(AndroidJUnit4::class)
class FlujoUsuarioTest {

    @get:Rule
    val regla = createAndroidComposeRule<MainActivity>()

    private val almacen get() = (regla.activity.application as PlanIzajeApp).almacen

    private fun escribir(etiqueta: String, texto: String) {
        regla.onNode(hasSetTextAction() and hasText(etiqueta, substring = true)).performScrollTo().performTextInput(texto)
    }

    @Test
    fun recorridoCompleto() {
        val antes = almacen.proyectos.value.map { it.id }.toSet()

        // 1-2. Pantalla principal y crear proyecto
        regla.onNodeWithText("Crear plan de izaje").assertIsDisplayed().performClick()
        regla.waitForIdle()
        val id = almacen.proyectos.value.map { it.id }.first { it !in antes }

        // 3. Información básica
        escribir("Nombre del proyecto", "Prueba recorrido")
        escribir("Descripción del trabajo", "Subir tanque a cubierta")
        escribir("Ubicación de la obra", "Medellín")
        escribir("Elaborado por", "Operador de prueba")
        regla.onNodeWithText("Siguiente").performClick()

        // 4. Grúa: crear desde el paso abre la ficha; volvemos atrás
        regla.onNodeWithText("Registrar grúa", substring = true).performScrollTo().performClick()
        regla.waitForIdle()
        regla.onNodeWithContentDescription("Volver").performClick()
        regla.onNodeWithText("Siguiente").performClick()

        // 5. Carga
        escribir("Descripción de la carga", "Tanque 5000 L")
        escribir("Peso de la carga", "850")
        regla.onNodeWithText("Siguiente").performClick()

        // 6-8. Croquis: plantilla inicial (grúa, recogida, descarga) y un edificio
        regla.onNodeWithText("Empezar con grúa", substring = true).performScrollTo().performClick()
        regla.onNodeWithText("Abrir editor de croquis", substring = true).performScrollTo().performClick()
        regla.waitForIdle()
        regla.onNodeWithText("Añadir").performClick()
        regla.onAllNodesWithText("Edificio").onFirst().performClick()
        regla.waitForIdle()
        regla.onNodeWithContentDescription("Volver").performClick()
        regla.onNodeWithText("Siguiente").performClick()

        // 9. Maniobra: añadir fase y operador
        regla.onNodeWithText("Añadir fase", substring = true).performScrollTo().performClick()
        escribir("Operador de la grúa", "Juan")
        regla.onNodeWithText("Siguiente").performClick()

        // 10. Verificación
        regla.onNodeWithText("Estado del plan", substring = true).assertIsDisplayed()
        regla.onNodeWithText("Ir al PDF").performClick()

        // 11. Generar PDF (borrador)
        regla.onNodeWithText("Generar borrador PDF", substring = true).performClick()
        regla.onAllNodesWithText("Generar borrador").onFirst().performClick()
        regla.waitUntil(20_000) { almacen.proyecto(id)?.versionesPdf?.isNotEmpty() == true }

        // 12-13. Cerrar y volver a abrir: los datos persisten en disco
        almacen.volcar()
        almacen.cargarTodo()
        val p = almacen.proyecto(id)!!
        assertEquals("Prueba recorrido", p.nombre)
        assertEquals(850.0, p.carga.pesoKg!!, 1e-9)
        assertTrue(p.croquis.elementos.any { it.tipo == TipoElemento.EDIFICIO })
        assertTrue(p.croquis.grua != null)
        assertEquals(1, p.maniobra.fases.size)
        assertTrue(p.versionesPdf.single().borrador)
        assertTrue(java.io.File(almacen.dirPlanes, p.versionesPdf.single().archivo).exists())
    }
}
