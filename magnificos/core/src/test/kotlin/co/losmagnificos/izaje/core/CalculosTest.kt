package co.losmagnificos.izaje.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.asin
import kotlin.math.sin

class CalculosTest {

    private fun base() = DatosIzaje(cargaKg = 3000.0, ganchoKg = 10.0, eslingasKg = 6.0)

    // ---------------------------------------------------------------- peso total

    @Test fun pesoTotalSumaCargaGanchoYEslingas() {
        val p = Calculos.pesoTotal(base())
        assertEquals(3016.0, p.totalKg!!, 1e-9)
        assertEquals(3, p.lineas.size)
    }

    @Test fun pesoTotalSumaGrilletesBalancinYOtros() {
        val p = Calculos.pesoTotal(base().copy(grilletesKg = 4.0, balancinKg = 50.0, otrosKg = 1.5))
        assertEquals(3071.5, p.totalKg!!, 1e-9)
    }

    @Test fun pesoTotalNoDuplicaLoQueYaEstaIncluido() {
        val p = Calculos.pesoTotal(base().copy(incluidos = setOf("gancho", "eslingas")))
        assertEquals(3000.0, p.totalKg!!, 1e-9)
        assertEquals(listOf("Gancho", "Eslingas"), p.incluidosEnOtro)
    }

    @Test fun pesoTotalConPesoUnitarioDeEslingasPorCantidad() {
        val p = Calculos.pesoTotal(DatosIzaje(cargaKg = 1000.0, eslingaUnitKg = 3.0, nEslingas = 2))
        assertEquals(1006.0, p.totalKg!!, 1e-9)
    }

    @Test fun pesoTotalSinPesoDeCargaNoSeInventa() {
        val p = Calculos.pesoTotal(DatosIzaje(ganchoKg = 10.0))
        assertNull(p.totalKg)
    }

    @Test fun pesoTotalMarcaSinDatoLoQueFalta() {
        val p = Calculos.pesoTotal(DatosIzaje(cargaKg = 500.0))
        assertEquals(500.0, p.totalKg!!, 1e-9)
        assertTrue("Gancho" in p.sinDato)
        assertTrue("Eslingas" in p.sinDato)
    }

    // ---------------------------------------------------------------- utilización

    @Test fun utilizacionEjemploDosMetros() {
        assertEquals(16.76, Calculos.utilizacionPct(3016.0, 18000.0), 0.005)
    }

    @Test fun utilizacionEjemploDiezMetros() {
        assertEquals(60.32, Calculos.utilizacionPct(3016.0, 5000.0), 0.005)
    }

    @Test fun filasPorRadioConMargen() {
        val d = base().copy(
            radioInicialM = 2.0, radioFinalM = 10.0,
            capacidades = listOf(PuntoCapacidad(2.0, 18000.0), PuntoCapacidad(10.0, 5000.0)),
            capacidadNominalKg = 23000.0,
        )
        val filas = Calculos.filasRadios(d, 3016.0)
        assertEquals(2, filas.size)
        assertEquals(16.76, filas[0].utilizacionPct!!, 0.005)
        assertEquals(14984.0, filas[0].margenKg!!, 1e-9)
        assertEquals(60.32, filas[1].utilizacionPct!!, 0.005)
        assertEquals(1984.0, filas[1].margenKg!!, 1e-9)
        assertFalse(filas[0].critico)
        assertTrue("el radio con menor capacidad es el crítico", filas[1].critico)
    }

    @Test fun laCapacidadNominalNoSeUsaComoCapacidadEnElRadio() {
        val d = base().copy(radioInicialM = 5.0, capacidadNominalKg = 23000.0)
        val f = Calculos.filasRadios(d, 3016.0).single()
        assertEquals(EstadoFila.SIN_CAPACIDAD, f.estado)
        assertNull(f.capacidadKg)
        assertNull(f.utilizacionPct)
    }

    @Test fun noInterpolaCapacidades() {
        val d = base().copy(
            radioInicialM = 2.0, radioFinalM = 10.0, radioMaximoM = 6.0,
            capacidades = listOf(PuntoCapacidad(2.0, 18000.0), PuntoCapacidad(10.0, 5000.0)),
        )
        val filas = Calculos.filasRadios(d, 3016.0)
        val seis = filas.first { it.radioM == 6.0 }
        assertEquals(EstadoFila.SIN_CAPACIDAD, seis.estado)
        assertNull(seis.capacidadKg)
    }

    @Test fun incluyeRadiosIntermediosConCapacidadInformada() {
        val d = base().copy(
            radioInicialM = 2.0, radioFinalM = 10.0,
            capacidades = listOf(PuntoCapacidad(2.0, 18000.0), PuntoCapacidad(6.0, 9000.0), PuntoCapacidad(10.0, 5000.0)),
        )
        val filas = Calculos.filasRadios(d, 3016.0)
        assertEquals(listOf(2.0, 6.0, 10.0), filas.map { it.radioM })
        assertTrue(filas[1].etiqueta.contains("intermedio"))
    }

    @Test fun detectaExcedenciaMayorAlCienPorCiento() {
        val d = DatosIzaje(cargaKg = 6000.0, radioInicialM = 10.0, capacidades = listOf(PuntoCapacidad(10.0, 5000.0)))
        val f = Calculos.filasRadios(d, 6000.0).single()
        assertEquals(EstadoFila.EXCEDE, f.estado)
        assertEquals(120.0, f.utilizacionPct!!, 1e-9)
        assertEquals(-1000.0, f.margenKg!!, 1e-9)
    }

    // ---------------------------------------------------------------- eslingas

    private fun dosEslingas(ang: Double?, ref: RefAngulo = RefAngulo.HORIZONTAL) =
        DatosIzaje(nEslingas = 2, wllEslingaKg = 6000.0, anguloGrados = ang, refAngulo = ref)

    @Test fun tensionDosRamalesSimetricosAnguloDesdeHorizontal() {
        val r = Calculos.eslingas(dosEslingas(60.0), 3016.0)
        assertEquals(EstadoEslingas.CALCULADA, r.estado)
        assertEquals(3016.0 / (2 * sin(Math.toRadians(60.0))), r.tensionRamalKg!!, 1e-6)
        assertEquals(1741.2, r.tensionRamalKg!!, 0.1)
        assertEquals(2, r.ramalesPortantes)
    }

    @Test fun tensionConAnguloDeTreintaGradosIgualAlPeso() {
        val r = Calculos.eslingas(dosEslingas(30.0), 3016.0)
        assertEquals(3016.0, r.tensionRamalKg!!, 1e-6)
    }

    @Test fun tensionConRamalesVerticalesEsLaMitad() {
        val r = Calculos.eslingas(dosEslingas(90.0), 3016.0)
        assertEquals(1508.0, r.tensionRamalKg!!, 1e-6)
    }

    @Test fun anguloDesdeLaVerticalSeConvierteAlDeLaHorizontal() {
        val vert = Calculos.eslingas(dosEslingas(30.0, RefAngulo.VERTICAL), 3016.0)
        val hor = Calculos.eslingas(dosEslingas(60.0, RefAngulo.HORIZONTAL), 3016.0)
        assertEquals(hor.tensionRamalKg!!, vert.tensionRamalKg!!, 1e-6)
        assertEquals(60.0, vert.anguloHorizontal!!, 1e-9)
    }

    @Test fun conReferenciaSinConfirmarUsaLaInterpretacionMasDesfavorable() {
        val r = Calculos.eslingas(dosEslingas(30.0, RefAngulo.SIN_CONFIRMAR), 3016.0)
        assertEquals(EstadoEslingas.CONDICIONADA, r.estado)
        assertTrue(r.referenciaPendiente)
        assertEquals(30.0, r.anguloHorizontal!!, 1e-9)       // 30° con la horizontal es peor que 60°
        assertEquals(3016.0, r.tensionRamalKg!!, 1e-6)
    }

    @Test fun sinAnguloNoDaTensionDefinitiva() {
        val r = Calculos.eslingas(dosEslingas(null), 3016.0)
        assertEquals(EstadoEslingas.CONDICIONADA, r.estado)
        assertNull(r.tensionRamalKg)
        assertEquals(1508.0, r.tensionMinimaKg!!, 1e-6)
        assertEquals(3, r.referencias.size)
        val esperado = Math.toDegrees(asin(3016.0 / (2 * 6000.0)))
        assertEquals(esperado, r.anguloMinimoGrados!!, 1e-6)
        assertFalse(r.excede)
    }

    @Test fun laCapacidadDeDosEslingasNoSeSumaAutomaticamente() {
        // 12.500 kg con ramales verticales: 6.250 kg por ramal > 6.000 kg de cada eslinga (no 12.000 kg sumados).
        val r = Calculos.eslingas(dosEslingas(90.0), 12500.0)
        assertEquals(6250.0, r.tensionRamalKg!!, 1e-6)
        assertTrue(r.excede)
        assertEquals(104.17, r.utilizacionPct!!, 0.01)
    }

    @Test fun conMasDeDosRamalesSoloSeConsideranDosPortantes() {
        val r = Calculos.eslingas(DatosIzaje(nEslingas = 4, wllEslingaKg = 3000.0, anguloGrados = 60.0, refAngulo = RefAngulo.HORIZONTAL), 4000.0)
        assertEquals(2, r.ramalesPortantes)
        assertEquals(4000.0 / (2 * sin(Math.toRadians(60.0))), r.tensionRamalKg!!, 1e-6)
        assertTrue(r.notas.any { it.contains("solo 2 portantes") })
    }

    @Test fun ramalesPortantesIndicadosPorElUsuarioSeRespetan() {
        val r = Calculos.eslingas(
            DatosIzaje(nEslingas = 4, wllEslingaKg = 3000.0, anguloGrados = 60.0, refAngulo = RefAngulo.HORIZONTAL, ramalesPortantes = 4), 4000.0,
        )
        assertEquals(4, r.ramalesPortantes)
        assertEquals(4000.0 / (4 * sin(Math.toRadians(60.0))), r.tensionRamalKg!!, 1e-6)
    }

    @Test fun enCanastaCadaEslingaTrabajaConDosRamales() {
        val d = DatosIzaje(nEslingas = 1, wllEslingaKg = 5000.0, anguloGrados = 60.0, refAngulo = RefAngulo.HORIZONTAL, amarre = Amarre.CANASTA)
        val r = Calculos.eslingas(d, 2000.0)
        assertEquals(2, r.ramalesPortantes)
        assertEquals(2000.0 / (2 * sin(Math.toRadians(60.0))), r.tensionRamalKg!!, 1e-6)
        assertEquals(r.tensionRamalKg!! * 2, r.cargaPorEslingaKg!!, 1e-6)
    }

    @Test fun unSoloRamalLaTensionEsElPeso() {
        val r = Calculos.eslingas(DatosIzaje(nEslingas = 1, wllEslingaKg = 5000.0), 2000.0)
        assertEquals(EstadoEslingas.CALCULADA, r.estado)
        assertEquals(2000.0, r.tensionRamalKg!!, 1e-9)
    }

    @Test fun anguloInvalidoNoSeCalcula() {
        val r = Calculos.eslingas(dosEslingas(120.0), 3016.0)
        assertEquals(EstadoEslingas.NO_CALCULABLE, r.estado)
    }

    @Test fun anguloPequenoGeneraAdvertencia() {
        val r = Calculos.eslingas(dosEslingas(25.0), 3016.0)
        assertTrue(r.notas.any { it.contains("30°") })
    }

    @Test fun sinNumeroDeEslingasONoSeCalcula() {
        assertEquals(EstadoEslingas.NO_CALCULABLE, Calculos.eslingas(DatosIzaje(wllEslingaKg = 6000.0), 3016.0).estado)
        assertEquals(EstadoEslingas.NO_CALCULABLE, Calculos.eslingas(dosEslingas(60.0), null).estado)
        assertEquals(EstadoEslingas.NO_CALCULABLE, Calculos.eslingas(dosEslingas(60.0).copy(sinEslingas = true), 3016.0).estado)
    }

    // ---------------------------------------------------------------- unidades y formato

    @Test fun conversionDePesos() {
        assertEquals(3000.0, Texto.pesoKg("3 t")!!, 1e-9)
        assertEquals(2500.0, Texto.pesoKg("2,5 toneladas")!!, 1e-9)
        assertEquals(1250.0, Texto.pesoKg("1.250 kg")!!, 1e-9)
        assertEquals(6.0, Texto.pesoKg("6 kilos")!!, 1e-9)
        assertEquals(6.0, Texto.pesoKg("6")!!, 1e-9)
        assertEquals(6000.0, Texto.pesoKg("6", tonPorDefecto = true)!!, 1e-9)
        assertNull(Texto.pesoKg("abc"))
    }

    @Test fun conversionDeLongitudes() {
        assertEquals(2.5, Texto.longitudM("2,5 m")!!, 1e-9)
        assertEquals(2.5, Texto.longitudM("250 cm")!!, 1e-9)
        assertEquals(10.0, Texto.longitudM("10 metros")!!, 1e-9)
        assertEquals(7.5, Texto.longitudM("7.5")!!, 1e-9)
    }

    @Test fun numerosEscritosConLetras() {
        assertEquals("3 toneladas", Texto.cifras("tres toneladas"))
        assertEquals("2500 kilos", Texto.cifras("dos mil quinientos kilos"))
        assertEquals("35", Texto.cifras("treinta y cinco"))
        assertEquals("1,5 toneladas", Texto.cifras("una tonelada y media"))
        assertEquals("2,5 metros", Texto.cifras("dos metros y medio"))
        assertEquals("12,5", Texto.cifras("doce coma cinco"))
        assertEquals("1 eslinga", Texto.cifras("una eslinga"))
        assertEquals("1000", Texto.cifras("mil"))
        assertEquals("una carga de 23 toneladas", Texto.cifras("una carga de veintitrés toneladas"))
    }

    @Test fun formatoColombiano() {
        assertEquals("3.016", Formato.num(3016.0, 2))
        assertEquals("16,76 %", Formato.pct(16.7553))
        assertEquals("60,32 %", Formato.pct(60.32))
        assertEquals("3.016 kg", Formato.kg(3016.0))
        assertEquals("1.508,5", Formato.num(1508.5, 2))
        assertEquals("3016,5", Formato.editable(3016.5))
    }
}
