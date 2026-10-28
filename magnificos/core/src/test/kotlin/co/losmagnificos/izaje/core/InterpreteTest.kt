package co.losmagnificos.izaje.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterpreteTest {

    private val ejemplo =
        "Voy a levantar una carga de tres toneladas. La recojo a dos metros de radio y la voy a colocar a diez metros. " +
            "La grúa tiene una capacidad máxima nominal de 23 toneladas, a dos metros de radio tiene una capacidad de 18 toneladas " +
            "y a diez metros puede levantar cinco toneladas. Voy a utilizar dos eslingas de seis toneladas cada una, " +
            "con un peso combinado de seis kilos. El gancho pesa diez kilos. La altura de trabajo es de dos metros."

    @Test fun interpretaElEjemploCompletoDelOperador() {
        val d = Interprete.interpretar(ejemplo)
        assertEquals(3000.0, d.cargaKg!!, 1e-9)
        assertEquals(2.0, d.radioInicialM!!, 1e-9)
        assertEquals(10.0, d.radioFinalM!!, 1e-9)
        assertEquals(23000.0, d.capacidadNominalKg!!, 1e-9)
        assertEquals(listOf(PuntoCapacidad(2.0, 18000.0), PuntoCapacidad(10.0, 5000.0)), d.capacidades)
        assertEquals(2, d.nEslingas)
        assertEquals(6000.0, d.wllEslingaKg!!, 1e-9)
        assertEquals(6.0, d.eslingasKg!!, 1e-9)
        assertEquals(10.0, d.ganchoKg!!, 1e-9)
        assertEquals(2.0, d.alturaM!!, 1e-9)
        assertNull("no se inventa el ángulo", d.anguloGrados)
        assertNull("no se inventa la grúa", d.equipo)
    }

    @Test fun elEjemploDaLosResultadosDelEnunciado() {
        val d = Interprete.interpretar(ejemplo)
        val peso = Calculos.pesoTotal(d)
        assertEquals(3016.0, peso.totalKg!!, 1e-9)
        val filas = Calculos.filasRadios(d, peso.totalKg)
        assertEquals(16.76, filas[0].utilizacionPct!!, 0.005)
        assertEquals(60.32, filas[1].utilizacionPct!!, 0.005)
        assertEquals(10.0, filas.single { it.critico }.radioM, 1e-9)
    }

    @Test fun laAlturaNoSeConfundeConElRadio() {
        val d = Interprete.interpretar("La altura es de dos metros y el radio es de diez metros")
        assertEquals(2.0, d.alturaM!!, 1e-9)
        assertEquals(10.0, d.radioMaximoM!!, 1e-9)
        assertNull(d.radioInicialM)
        assertNull(d.radioFinalM)
    }

    @Test fun laCapacidadNominalNoSeTomaComoCapacidadDelRadio() {
        val d = Interprete.interpretar("La grúa tiene una capacidad máxima nominal de 23 toneladas. Recojo a 5 metros.")
        assertEquals(23000.0, d.capacidadNominalKg!!, 1e-9)
        assertTrue(d.capacidades.isEmpty())
        assertEquals(5.0, d.radioInicialM!!, 1e-9)
    }

    @Test fun entiendeCifrasUnidadesAbreviadasYCapacidadAlFinal() {
        val d = Interprete.interpretar(
            "Levantar una viga de 2,5 t. Recojo a 3 m y coloco a 8 m. La grúa levanta 6 t a 3 m y 2,4 t a 8 m. Gancho de 15 kg.",
        )
        assertEquals(2500.0, d.cargaKg!!, 1e-9)
        assertEquals(3.0, d.radioInicialM!!, 1e-9)
        assertEquals(8.0, d.radioFinalM!!, 1e-9)
        assertEquals(listOf(PuntoCapacidad(3.0, 6000.0), PuntoCapacidad(8.0, 2400.0)), d.capacidades)
        assertEquals("«gancho de 15 kg»: en kg se entiende como peso", 15.0, d.ganchoKg!!, 1e-9)
    }

    @Test fun lasToneladasDeUnAccesorioSonCapacidadNoPeso() {
        val d = Interprete.interpretar("Gancho de 10 toneladas y grilletes de 5 toneladas")
        assertNull(d.ganchoKg)
        assertNull(d.grilletesKg)
        assertTrue(d.notas.size >= 2)
    }

    @Test fun dictadoSinPuntuacionNoMezclaRadiosYCapacidades() {
        val d = Interprete.interpretar(
            "voy a levantar una carga de tres toneladas la recojo a dos metros de radio y la coloco a diez metros " +
                "la grúa tiene una capacidad de 18 toneladas a dos metros de radio y 5 toneladas a diez metros",
        )
        assertEquals(3000.0, d.cargaKg!!, 1e-9)
        assertEquals(2.0, d.radioInicialM!!, 1e-9)
        assertEquals(10.0, d.radioFinalM!!, 1e-9)
        assertEquals(listOf(PuntoCapacidad(2.0, 18000.0), PuntoCapacidad(10.0, 5000.0)), d.capacidades)
    }

    @Test fun capacidadYRadioSinConectorEntreParesConsecutivos() {
        val d = Interprete.interpretar("a dos metros la grúa levanta 18 toneladas a diez metros puede levantar 5 toneladas")
        assertEquals(listOf(PuntoCapacidad(2.0, 18000.0), PuntoCapacidad(10.0, 5000.0)), d.capacidades)
    }

    @Test fun laCapacidadALosMetrosEsDe() {
        val d = Interprete.interpretar("La capacidad de la grúa a dos metros es de 18 toneladas y a diez metros es de 5 toneladas")
        assertEquals(listOf(PuntoCapacidad(2.0, 18000.0), PuntoCapacidad(10.0, 5000.0)), d.capacidades)
        assertNull(d.cargaKg)
    }

    @Test fun laGruaTieneToneladasAMetros() {
        val d = Interprete.interpretar("La grúa tiene 18 toneladas a 2 metros y 5 toneladas a 10 metros. Recojo a 2 metros.")
        assertEquals(listOf(PuntoCapacidad(2.0, 18000.0), PuntoCapacidad(10.0, 5000.0)), d.capacidades)
        assertEquals(2.0, d.radioInicialM!!, 1e-9)
        assertNull(d.cargaKg)
    }

    @Test fun reconoceMarcaYModeloDeLaGrua() {
        assertEquals("Palfinger PK 23500", Interprete.interpretar("Voy a usar la grúa Palfinger PK 23500 para levantar una caja").equipo)
        assertEquals("Hiab 288 E-8 Hipro", Interprete.interpretar("Trabajo con el Hiab 288 E-8 HIPRO en la obra").equipo)
        assertEquals("PM 85028 SP PT", Interprete.interpretar("El camión es una PM 85028 SP PT.").equipo)
        assertNull(Interprete.interpretar("Voy a usar la grúa del camión").equipo)
    }

    @Test fun anguloYSuReferencia() {
        val h = Interprete.interpretar("Las eslingas forman un ángulo de 60 grados con la horizontal")
        assertEquals(60.0, h.anguloGrados!!, 1e-9)
        assertEquals(RefAngulo.HORIZONTAL, h.refAngulo)
        val v = Interprete.interpretar("El ángulo de las eslingas es de 30 grados respecto a la vertical")
        assertEquals(30.0, v.anguloGrados!!, 1e-9)
        assertEquals(RefAngulo.VERTICAL, v.refAngulo)
        val s = Interprete.interpretar("Las eslingas tienen un ángulo de 50 grados")
        assertEquals(50.0, s.anguloGrados!!, 1e-9)
        assertEquals(RefAngulo.SIN_CONFIRMAR, s.refAngulo)
        assertNull("30 grados de temperatura no son un ángulo de eslinga", Interprete.interpretar("Hace 30 grados de calor").anguloGrados)
    }

    @Test fun pesoYaIncluidoNoSeDuplica() {
        val d = Interprete.interpretar("La carga pesa 3000 kilos, con el gancho y las eslingas ya incluidos en ese peso. El gancho pesa 10 kilos")
        assertTrue("gancho" in d.incluidos)
        assertTrue("eslingas" in d.incluidos)
        assertEquals(3000.0, Calculos.pesoTotal(d).totalKg!!, 1e-9)
    }

    @Test fun sinIncluirNoMarcaComoIncluido() {
        val d = Interprete.interpretar("La carga pesa 3000 kilos sin incluir el gancho")
        assertFalse("gancho" in d.incluidos)
    }

    @Test fun pesoDeCadaEslingaSeMultiplicaPorLaCantidad() {
        val d = Interprete.interpretar("Uso 2 eslingas y cada eslinga pesa 3 kilos. La carga es de 1000 kilos")
        assertEquals(2, d.nEslingas)
        assertEquals(3.0, d.eslingaUnitKg!!, 1e-9)
        assertEquals(1006.0, Calculos.pesoTotal(d).totalKg!!, 1e-9)
    }

    @Test fun detectaTipoDeAmarreYSinEslingas() {
        assertEquals(Amarre.CANASTA, Interprete.interpretar("Voy a amarrar en canasta con una eslinga").amarre)
        assertEquals(Amarre.AHORCADO, Interprete.interpretar("Amarre ahorcado con una eslinga").amarre)
        assertTrue(Interprete.interpretar("Esta vez no uso eslingas, voy con cadena").sinEslingas)
        assertNull(Interprete.interpretar("Dos eslingas de 3 toneladas").amarre)
    }

    @Test fun marcaComoAproximadoLoDichoConDudas() {
        val d = Interprete.interpretar("La carga pesa unos 3000 kilos. El gancho pesa 10 kilos")
        assertTrue(Campo.CARGA in d.aproximados)
        assertFalse(Campo.GANCHO in d.aproximados)
    }

    @Test fun noConfundeDimensionesNiDistanciasConElRadio() {
        val d = Interprete.interpretar("La carga mide 4 metros de largo. El camión queda a 3 metros de la zanja. Recojo a 5 metros de radio.")
        assertNull(d.radioFinalM)
        assertEquals(5.0, d.radioInicialM!!, 1e-9)
        assertTrue(d.notas.any { it.contains("Dimensión") })
        assertTrue(d.notas.any { it.contains("Distancia") })
    }

    @Test fun guardaCentroDeGravedadYConfiguracion() {
        val d = Interprete.interpretar("El centro de gravedad está al centro de la pieza. Los estabilizadores van completamente extendidos.")
        assertTrue(d.notas.any { it.contains("Centro de gravedad") })
        assertEquals(1, d.configuracion.size)
    }

    @Test fun guardaLaFraseOriginalDeCadaDato() {
        val d = Interprete.interpretar(ejemplo)
        assertTrue(d.frases.getValue(Campo.GANCHO).contains("gancho", ignoreCase = true))
        assertNotNull(d.frases[Campo.CARGA])
        assertNotNull(d.frases[Campo.ALTURA])
    }

    @Test fun textoVacioNoInventaNada() {
        val d = Interprete.interpretar("")
        assertEquals(DatosIzaje(), d)
    }

    @Test fun alVolverAInterpretarSeRespetanLasCorrecciones() {
        val inicial = Interprete.interpretar(ejemplo)
        val corregido = Edicion.establecer(inicial, Campo.CARGA, "4 t")
        val nuevo = Interprete.interpretar("$ejemplo Radio máximo de 12 metros.", corregido)
        assertEquals(4000.0, nuevo.cargaKg!!, 1e-9)
        assertEquals(12.0, nuevo.radioMaximoM!!, 1e-9)
        assertTrue(Campo.CARGA in nuevo.editados)
    }

    @Test fun edicionManualDeCapacidades() {
        var d = DatosIzaje()
        d = Edicion.ponerCapacidad(d, 10.0, 5000.0)
        d = Edicion.ponerCapacidad(d, 2.0, 18000.0)
        d = Edicion.ponerCapacidad(d, 10.0, 5200.0)
        assertEquals(listOf(PuntoCapacidad(2.0, 18000.0), PuntoCapacidad(10.0, 5200.0)), d.capacidades)
        assertTrue(Campo.CAPACIDADES in d.editados)
    }

    @Test fun edicionManualAceptaUnidades() {
        val d = Edicion.establecer(DatosIzaje(), Campo.CARGA, "2,5 t")
        assertEquals(2500.0, d.cargaKg!!, 1e-9)
        assertEquals(250.0, Edicion.establecer(d, Campo.RADIO_MAX, "250 cm").radioMaximoM!!.times(100), 1e-9)
        assertNull(Edicion.establecer(d, Campo.CARGA, "").cargaKg)
    }

    @Test
    fun fusionarSumaCapacidadesNuevasYRespetaLasCorregidas() {
        val previo = Edicion.ponerCapacidad(DatosIzaje(), 10.0, 5000.0)
        val nuevo = Interprete.interpretar("La grúa a 10 metros levanta 9 toneladas y a 12 metros levanta 4 toneladas", previo)
        assertEquals(5000.0, nuevo.capacidades.first { it.radioM == 10.0 }.capacidadKg, 1e-9)
        assertEquals(4000.0, nuevo.capacidades.first { it.radioM == 12.0 }.capacidadKg, 1e-9)
    }
}
