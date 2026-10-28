package co.losmagnificos.izaje.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalisisTest {

    private val ejemplo =
        "Voy a levantar una carga de tres toneladas. La recojo a dos metros de radio y la voy a colocar a diez metros. " +
            "La grúa tiene una capacidad máxima nominal de 23 toneladas, a dos metros de radio tiene una capacidad de 18 toneladas " +
            "y a diez metros puede levantar cinco toneladas. Voy a utilizar dos eslingas de seis toneladas cada una, " +
            "con un peso combinado de seis kilos. El gancho pesa diez kilos. La altura de trabajo es de dos metros."

    private fun plan(d: DatosIzaje, basicos: Boolean = true) = Plan(
        id = "t", creadoMs = 0, modificadoMs = 0,
        operador = if (basicos) "Julián Pérez" else "", cedula = if (basicos) "123456" else "",
        cliente = if (basicos) "Constructora X" else "", sitio = if (basicos) "Bogotá" else "", fecha = "2026-10-28",
        datos = d,
    )

    @Test fun elEjemploSalePreliminarPorqueFaltanGruaYAngulo() {
        val a = Analizador.analizar(plan(Interprete.interpretar(ejemplo)))
        val claves = a.criticos.map { it.clave }
        assertTrue(ClavesFaltante.EQUIPO in claves)
        assertTrue(ClavesFaltante.ANGULO in claves)
        assertEquals(EstadoPlan.PRELIMINAR, a.estado)
        assertEquals(3016.0, a.peso.totalKg!!, 1e-9)
        assertEquals(EstadoEslingas.CONDICIONADA, a.eslingas.estado)
    }

    @Test fun conTodosLosDatosIndispensablesQuedaParaRevisionNuncaAprobado() {
        var d = Interprete.interpretar(ejemplo)
        d = Edicion.establecer(d, Campo.EQUIPO, "Palfinger PK 23500")
        d = Edicion.establecer(d, Campo.ANGULO, "60")
        d = Edicion.conRefAngulo(d, RefAngulo.HORIZONTAL)
        val a = Analizador.analizar(plan(d))
        assertTrue(a.criticos.isEmpty())
        assertEquals(EstadoPlan.PARA_REVISION, a.estado)
        assertTrue(a.estado.etiqueta.contains("NO APROBADO"))
        assertEquals(EstadoEslingas.CALCULADA, a.eslingas.estado)
    }

    @Test fun siempreQuedaPendienteConfirmarConLaTablaOficial() {
        var d = Interprete.interpretar(ejemplo)
        d = Edicion.establecer(d, Campo.EQUIPO, "Palfinger PK 23500")
        d = Edicion.establecer(d, Campo.ANGULO, "60")
        d = Edicion.conRefAngulo(d, RefAngulo.HORIZONTAL)
        val a = Analizador.analizar(plan(d))
        assertTrue(a.faltantes.any { it.clave == ClavesFaltante.TABLA && !it.critico })
    }

    @Test fun faltanLosDatosBasicosDelTrabajo() {
        val a = Analizador.analizar(plan(Interprete.interpretar(ejemplo), basicos = false))
        val claves = a.criticos.map { it.clave }
        assertTrue(ClavesFaltante.OPERADOR in claves && ClavesFaltante.CEDULA in claves)
        assertTrue(ClavesFaltante.CLIENTE in claves && ClavesFaltante.SITIO in claves)
    }

    @Test fun pideLaCapacidadDeCadaRadioSinTabla() {
        val d = DatosIzaje(cargaKg = 1000.0, radioInicialM = 4.0, radioFinalM = 9.0, capacidades = listOf(PuntoCapacidad(4.0, 8000.0)))
        val a = Analizador.analizar(plan(d))
        val caps = a.criticos.filter { it.clave.startsWith(ClavesFaltante.CAPACIDAD) }
        assertEquals(1, caps.size)
        assertEquals(ClavesFaltante.CAPACIDAD + "9", caps.single().clave)
    }

    @Test fun laExcedenciaHaceElPlanPreliminarYAvisa() {
        var d = Interprete.interpretar(ejemplo)
        d = d.copy(cargaKg = 5000.0)
        d = Edicion.establecer(d, Campo.EQUIPO, "Palfinger PK 23500")
        d = Edicion.establecer(d, Campo.ANGULO, "60")
        d = Edicion.conRefAngulo(d, RefAngulo.HORIZONTAL)
        val a = Analizador.analizar(plan(d))
        assertTrue(a.hayExcedencias)
        assertTrue(a.alertas.any { it.contains("EXCEDE") })
        assertEquals(EstadoPlan.PRELIMINAR, a.estado)
    }

    @Test fun laExcedenciaDeEslingasTambienAvisa() {
        var d = Interprete.interpretar(ejemplo)
        d = d.copy(wllEslingaKg = 1000.0)
        d = Edicion.establecer(d, Campo.EQUIPO, "Palfinger PK 23500")
        d = Edicion.establecer(d, Campo.ANGULO, "60")
        d = Edicion.conRefAngulo(d, RefAngulo.HORIZONTAL)
        val a = Analizador.analizar(plan(d))
        assertTrue(a.alertas.any { it.contains("eslinga") })
    }

    @Test fun sinEslingasNoPideDatosDeEslingas() {
        val d = DatosIzaje(cargaKg = 1000.0, radioInicialM = 4.0, capacidades = listOf(PuntoCapacidad(4.0, 8000.0)), equipo = "Hiab 288", sinEslingas = true)
        val a = Analizador.analizar(plan(d))
        assertFalse(a.criticos.any { it.clave in setOf(ClavesFaltante.ESLINGAS, ClavesFaltante.WLL, ClavesFaltante.ANGULO) })
    }

    @Test fun unaEslingaVerticalNoPideAngulo() {
        val d = DatosIzaje(cargaKg = 1000.0, nEslingas = 1, wllEslingaKg = 3000.0)
        val a = Analizador.analizar(plan(d))
        assertFalse(a.criticos.any { it.clave == ClavesFaltante.ANGULO })
    }

    @Test fun pideConfirmarDesdeDondeSeMideElAngulo() {
        val d = DatosIzaje(cargaKg = 1000.0, nEslingas = 2, wllEslingaKg = 3000.0, anguloGrados = 50.0)
        val a = Analizador.analizar(plan(d))
        assertTrue(a.criticos.any { it.clave == ClavesFaltante.REF_ANGULO })
    }

    @Test fun lasCifrasAproximadasQuedanPendientesDeConfirmar() {
        val d = Interprete.interpretar("La carga pesa unos 3000 kilos")
        val a = Analizador.analizar(plan(d))
        assertTrue(a.faltantes.any { it.clave == ClavesFaltante.APROX + Campo.CARGA && !it.critico })
    }
}
