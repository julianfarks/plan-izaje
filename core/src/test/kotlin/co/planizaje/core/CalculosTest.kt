package co.planizaje.core

import co.planizaje.core.calc.Aptitud
import co.planizaje.core.calc.CalculoEslingas
import co.planizaje.core.calc.Candidatos
import co.planizaje.core.calc.ConsultaTabla
import co.planizaje.core.calc.EstadoDato
import co.planizaje.core.calc.EstadoDocumento
import co.planizaje.core.calc.Motor
import co.planizaje.core.calc.TipoConsulta
import co.planizaje.core.calc.Veredicto
import co.planizaje.core.data.Serializacion
import co.planizaje.core.geo.Geometria
import co.planizaje.core.model.*
import co.planizaje.core.util.UnidadPeso
import co.planizaje.core.util.Unidades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UnidadesTest {
    @Test fun conversionKgToneladas() {
        assertEquals(2500.0, Unidades.aKg(2.5, UnidadPeso.T), 1e-9)
        assertEquals(2.5, Unidades.desdeKg(2500.0, UnidadPeso.T), 1e-9)
        assertEquals(800.0, Unidades.aKg(800.0, UnidadPeso.KG), 1e-9)
    }

    @Test fun kgAKilonewton() {
        assertEquals(9.80665, Unidades.kgAkN(1000.0), 1e-9)
        assertEquals(1000.0, Unidades.kNaKg(Unidades.kgAkN(1000.0)), 1e-9)
    }

    @Test fun parseAceptaComaYPunto() {
        assertEquals(2.5, Unidades.parse("2,5")!!, 1e-9)
        assertEquals(2.5, Unidades.parse("2.5")!!, 1e-9)
        assertEquals(1250.5, Unidades.parse("1.250,5")!!, 1e-9)
        assertEquals(1250.5, Unidades.parse("1,250.5")!!, 1e-9)
        assertNull(Unidades.parse(""))
        assertNull(Unidades.parse("abc"))
        assertNull(Unidades.parse("1,2,3"))
    }

    @Test fun formatoEditableIdaYVuelta() {
        assertEquals("2,5", Unidades.fmtEditable(2.5))
        assertEquals("12", Unidades.fmtEditable(12.0))
        assertEquals(2.5, Unidades.parse(Unidades.fmtEditable(2.5))!!, 1e-9)
    }
}

class TablaCargaTest {
    private val tabla = TablaCarga(
        id = "t", puntos = listOf(
            PuntoTabla(4.0, 5000.0), PuntoTabla(2.0, 9000.0, 10.0), PuntoTabla(6.0, 3000.0, 8.0),
            PuntoTabla(8.0, 2000.0), PuntoTabla(6.0, 3100.0),
        ),
    )

    @Test fun valorExacto() {
        val q = ConsultaTabla.capacidad(tabla, 4.0)
        assertEquals(TipoConsulta.EN_TABLA, q.tipo)
        assertEquals(5000.0, q.capacidad!!, 1e-9)
    }

    @Test fun radioRepetidoUsaMenorCapacidad() {
        assertEquals(3000.0, ConsultaTabla.capacidad(tabla, 6.0).capacidad!!, 1e-9)
    }

    @Test fun entreRadiosSinInterpolarEsConservador() {
        val q = ConsultaTabla.capacidad(tabla, 5.0)
        assertEquals(3000.0, q.capacidad!!, 1e-9)
        assertEquals(4.0, q.radioInferior!!, 1e-9)
        assertEquals(6.0, q.radioSuperior!!, 1e-9)
    }

    @Test fun interpolacionLinealSoloSiSeConfigura() {
        val q = ConsultaTabla.capacidad(tabla.copy(interpolacion = Interpolacion.LINEAL), 5.0)
        assertEquals(4000.0, q.capacidad!!, 1e-9)
    }

    @Test fun fueraDeTablaPorEncima() {
        val q = ConsultaTabla.capacidad(tabla, 8.01)
        assertEquals(TipoConsulta.FUERA_MAYOR, q.tipo)
        assertNull(q.capacidad)
    }

    @Test fun fueraDeTablaPorDebajoNoDaCapacidad() {
        val q = ConsultaTabla.capacidad(tabla, 1.0)
        assertEquals(TipoConsulta.FUERA_MENOR, q.tipo)
        assertNull(q.capacidad)
    }

    @Test fun tablaVaciaYRadioInvalido() {
        assertEquals(TipoConsulta.SIN_DATOS, ConsultaTabla.capacidad(TablaCarga("v"), 3.0).tipo)
        assertEquals(TipoConsulta.RADIO_INVALIDO, ConsultaTabla.capacidad(tabla, -1.0).tipo)
        assertEquals(TipoConsulta.RADIO_INVALIDO, ConsultaTabla.capacidad(tabla, Double.NaN).tipo)
    }

    @Test fun limitesExactosDeLaTabla() {
        assertEquals(9000.0, ConsultaTabla.capacidad(tabla, 2.0).capacidad!!, 1e-9)
        assertEquals(2000.0, ConsultaTabla.capacidad(tabla, 8.0).capacidad!!, 1e-9)
    }
}

class EslingasTest {
    @Test fun dosRamalesVerticales() {
        val r = CalculoEslingas.calcular(Aparejo(ramales = 2, anguloVertical = 0.0), 2000.0)
        assertTrue(r.ok)
        assertEquals(1000.0, r.tensionRamal!!, 1e-6)
    }

    @Test fun dosRamalesA60Grados() {
        val r = CalculoEslingas.calcular(Aparejo(ramales = 2, anguloVertical = 60.0), 2000.0)
        assertEquals(2.0, r.factorAngulo!!, 1e-9)
        assertEquals(2000.0, r.tensionRamal!!, 1e-6)
        assertTrue(r.avisos.isEmpty())
    }

    @Test fun avisoPorAnguloMayorA60() {
        val r = CalculoEslingas.calcular(Aparejo(ramales = 2, anguloVertical = 65.0), 1000.0)
        assertTrue(r.ok)
        assertTrue(r.avisos.isNotEmpty())
    }

    @Test fun cuatroRamalesSeConsiderandos() {
        val r = CalculoEslingas.calcular(Aparejo(ramales = 4, anguloVertical = 0.0), 4000.0)
        assertEquals(2, r.ramalesPortantes)
        assertEquals(2000.0, r.tensionRamal!!, 1e-6)
    }

    @Test fun cuatroRamalesConRepartoJustificado() {
        val r = CalculoEslingas.calcular(Aparejo(ramales = 4, anguloVertical = 0.0, ramalesPortantes = 4), 4000.0)
        assertEquals(1000.0, r.tensionRamal!!, 1e-6)
    }

    @Test fun anguloDesdeGeometria() {
        // L = 2 m, separación 2 m → sen θ = 0,5 → 30°
        assertEquals(30.0, CalculoEslingas.anguloDesdeGeometria(2.0, 2.0)!!, 1e-9)
        assertNull(CalculoEslingas.anguloDesdeGeometria(1.0, 2.0))
    }

    @Test fun repartoAsimetricoPorCentroDeGravedad() {
        // s = 2 m, CdG desplazado 0,5 m hacia el ramal 1: V1 = W·1,5/2, V2 = W·0,5/2
        val r = CalculoEslingas.calcular(Aparejo(ramales = 2, anguloVertical = 0.0, separacionPuntos = 2.0), 1000.0, cdgX = -0.5)
        assertEquals(750.0, r.tensiones.max(), 1e-6)
        assertEquals(250.0, r.tensiones.min(), 1e-6)
    }

    @Test fun cdgFueraDePuntosEsError() {
        val r = CalculoEslingas.calcular(Aparejo(ramales = 2, anguloVertical = 0.0, separacionPuntos = 2.0), 1000.0, cdgX = 1.2)
        assertFalse(r.ok)
    }

    @Test fun canastaComparaDosRamales() {
        val r = CalculoEslingas.calcular(Aparejo(ramales = 2, enganche = Enganche.CANASTA, anguloVertical = 0.0), 1000.0)
        assertEquals(500.0, r.tensionRamal!!, 1e-6)
        assertEquals(1000.0, r.cargaPorEslinga!!, 1e-6)
        assertEquals(1, CalculoEslingas.eslingasNecesarias(Aparejo(ramales = 2, enganche = Enganche.CANASTA)))
    }

    @Test fun faltaPesoOAngulo() {
        assertFalse(CalculoEslingas.calcular(Aparejo(ramales = 2, anguloVertical = 10.0), null).ok)
        assertFalse(CalculoEslingas.calcular(Aparejo(ramales = 2), 1000.0).ok)
        assertFalse(CalculoEslingas.calcular(Aparejo(ramales = 2, anguloVertical = 90.0), 1000.0).ok)
    }
}

class GeometriaTest {
    @Test fun distanciaPuntoSegmento() {
        assertEquals(1.0, Geometria.distanciaPuntoSegmento(Pt(0.0, 1.0), Pt(-1.0, 0.0), Pt(1.0, 0.0)), 1e-9)
        assertEquals(1.0, Geometria.distanciaPuntoSegmento(Pt(2.0, 0.0), Pt(-1.0, 0.0), Pt(1.0, 0.0)), 1e-9)
    }

    @Test fun segmentosQueSeCortan() {
        assertTrue(Geometria.segmentosSeCortan(Pt(0.0, 0.0), Pt(2.0, 2.0), Pt(0.0, 2.0), Pt(2.0, 0.0)))
        assertFalse(Geometria.segmentosSeCortan(Pt(0.0, 0.0), Pt(1.0, 0.0), Pt(0.0, 1.0), Pt(1.0, 1.0)))
    }

    @Test fun recorridoQueAtraviesaEdificio() {
        val ed = Elemento("e", TipoElemento.EDIFICIO, x = 5.0, y = 0.0, ancho = 2.0, largo = 2.0)
        assertEquals(0.0, Geometria.distanciaPolilineaElemento(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0)), ed), 1e-9)
        assertEquals(2.0, Geometria.distanciaPolilineaElemento(listOf(Pt(0.0, 3.0), Pt(10.0, 3.0)), ed), 1e-9)
    }

    @Test fun rotacionDelContorno() {
        val e = Elemento("e", TipoElemento.MURO, x = 0.0, y = 0.0, ancho = 2.0, largo = 4.0, rotacion = 90.0)
        val c = Geometria.caja(Geometria.contorno(e))!!
        assertEquals(4.0, c.ancho, 1e-9)
        assertEquals(2.0, c.alto, 1e-9)
    }

    @Test fun puntoEnPoligono() {
        val sq = listOf(Pt(0.0, 0.0), Pt(2.0, 0.0), Pt(2.0, 2.0), Pt(0.0, 2.0))
        assertTrue(Geometria.puntoEnPoligono(Pt(1.0, 1.0), sq))
        assertFalse(Geometria.puntoEnPoligono(Pt(3.0, 1.0), sq))
    }
}

class MotorTest {
    private val hoy = LocalDate.of(2026, 10, 25)

    private val tabla = TablaCarga(
        id = "t1", nombre = "6 extensiones", configuracionPluma = "6 ext.", extensiones = 6,
        puntos = listOf(PuntoTabla(4.0, 4000.0), PuntoTabla(8.0, 2000.0), PuntoTabla(12.0, 1200.0)),
        descuentaGancho = true,
        fuente = FuenteTecnica("Diagrama de carga en cabina", "Fig. 1", "A"),
        verificacion = Verificacion(true, "J. Pérez", "2026-10-20"),
    )
    private val grua = Grua(
        id = "g1", fabricante = "Fabricante X", modelo = "Modelo Y", numeroSerie = "123",
        tablas = listOf(tabla), vientoMaxMs = 9.0,
    )
    private val eslinga = Accesorio(
        "a1", TipoAccesorio.ESLINGA_TEXTIL, identificacion = "E-01", pesoKg = 2.0,
        wllVertical = 2000.0, wllAhorcado = 1600.0, wllCanasta = 4000.0, cargaRotura = 14000.0,
        numeroCertificado = "C-1", estado = EstadoInspeccion.APTO, proximaInspeccion = "2027-01-01",
    )
    private val eslinga2 = eslinga.copy(id = "a2", identificacion = "E-02")

    private fun proyectoBase(peso: Double? = 1500.0, radioMedido: Double? = 7.0): Proyecto {
        val croquis = Croquis(
            elementos = listOf(
                Elemento("g", TipoElemento.GRUA, x = 0.0, y = 0.0),
                Elemento("r", TipoElemento.RECOGIDA, x = 5.0, y = 0.0),
                Elemento("d", TipoElemento.DESCARGA, x = 0.0, y = -7.0),
            ),
        )
        return Proyecto(
            id = "p", nombre = "Prueba", ubicacion = "Bogotá", fecha = "2026-10-25", elaboradoPor = "Yo",
            descripcion = "Izaje de prueba", gruaId = "g1",
            carga = Carga("Tanque", peso, OrigenPeso.DOCUMENTADO, "Plano 01", wllPuntoIzajeKg = 2000.0),
            croquis = croquis,
            maniobra = Maniobra(fases = listOf(Fase("f1", "Única", "r", "d", radioMedido = radioMedido)), tablaId = "t1"),
            aparejo = Aparejo(ramales = 2, anguloVertical = 30.0, eslingaIds = listOf("a1", "a2")),
            procedimiento = Procedimiento(secuencia = "1. Izar", operador = "A", senalero = "B", comunicacion = "Radio"),
        )
    }

    @Test fun capacidadYUtilizacionConDatosDocumentados() {
        val ev = Motor.evaluar(proyectoBase(), grua, listOf(eslinga, eslinga2), hoy)
        val cap = ev.resultados.first { it.id == "capacidad_f1" }
        assertEquals(EstadoDato.DOCUMENTADO, cap.estado)
        assertEquals(2000.0, cap.valorNumerico!!, 1e-9) // 7 m → se toma 8 m
        val u = ev.resultados.first { it.id == "utilizacion_f1" }
        assertEquals(1504.0 / 2000.0 * 100, u.valorNumerico!!, 1e-6)
        assertEquals(Veredicto.CUMPLE, u.veredicto)
    }

    @Test fun sobrecargaDaNoCumple() {
        val ev = Motor.evaluar(proyectoBase(peso = 2500.0), grua, listOf(eslinga, eslinga2), hoy)
        assertEquals(Veredicto.NO_CUMPLE, ev.resultados.first { it.id == "utilizacion_f1" }.veredicto)
        assertEquals(EstadoDocumento.BORRADOR_NO_CUMPLE, ev.estadoDocumento)
    }

    @Test fun criterioDeEmpresa() {
        val p = proyectoBase().let { it.copy(maniobra = it.maniobra.copy(limiteUtilizacionEmpresa = 70.0)) }
        val ev = Motor.evaluar(p, grua, listOf(eslinga, eslinga2), hoy)
        assertEquals(Veredicto.NO_CUMPLE, ev.resultados.first { it.id == "utilizacion_f1" }.veredicto)
    }

    @Test fun sinTablaNoEsVerificable() {
        val ev = Motor.evaluar(proyectoBase(), grua.copy(tablas = emptyList()), listOf(eslinga, eslinga2), hoy)
        assertEquals(EstadoDato.NO_VERIFICABLE, ev.resultados.first { it.id == "tabla" }.estado)
        assertEquals(EstadoDato.NO_VERIFICABLE, ev.resultados.first { it.id == "capacidad_f1" }.estado)
        assertTrue(ev.estadoDocumento.esBorrador)
    }

    @Test fun tablaSinVerificarRequiereRevision() {
        val g = grua.copy(tablas = listOf(tabla.copy(verificacion = Verificacion())))
        val ev = Motor.evaluar(proyectoBase(), g, listOf(eslinga, eslinga2), hoy)
        assertEquals(EstadoDato.REVISAR, ev.resultados.first { it.id == "tabla" }.estado)
    }

    @Test fun radioDelCroquisRequiereRevision() {
        val ev = Motor.evaluar(proyectoBase(radioMedido = null), grua, listOf(eslinga, eslinga2), hoy)
        val r = ev.resultados.first { it.id == "radio_f1" }
        assertEquals(EstadoDato.REVISAR, r.estado)
        assertEquals(7.0, r.valorNumerico!!, 1e-9)
    }

    @Test fun pesoEstimadoNoSePresentaComoDocumentado() {
        val p = proyectoBase().let { it.copy(carga = it.carga.copy(origenPeso = OrigenPeso.ESTIMADO)) }
        val ev = Motor.evaluar(p, grua, listOf(eslinga, eslinga2), hoy)
        assertEquals(EstadoDato.REVISAR, ev.resultados.first { it.id == "peso_carga" }.estado)
        assertTrue(ev.estadoDocumento.esBorrador)
    }

    @Test fun pesoDesconocidoQuedaPendiente() {
        val ev = Motor.evaluar(proyectoBase(peso = null), grua, listOf(eslinga, eslinga2), hoy)
        assertEquals(EstadoDato.PENDIENTE, ev.resultados.first { it.id == "masa_total" }.estado)
        assertNull(ev.masaTotal)
    }

    @Test fun eslingaVencidaNoCumple() {
        val vencida = eslinga.copy(proximaInspeccion = "2026-01-01")
        val ev = Motor.evaluar(proyectoBase(), grua, listOf(vencida, eslinga2), hoy)
        assertEquals(Veredicto.NO_CUMPLE, ev.resultados.first { it.id == "acc_a1" }.veredicto)
    }

    @Test fun eslingaUsaWllNoRotura() {
        val debil = eslinga.copy(wllVertical = 500.0)
        val ev = Motor.evaluar(proyectoBase(), grua, listOf(debil, eslinga2), hoy)
        // tensión ≈ 1504 / (2·cos30) ≈ 868 kg > 500 kg aunque la rotura sea 14 000 kg
        assertEquals(Veredicto.NO_CUMPLE, ev.resultados.first { it.id == "acc_a1" }.veredicto)
    }

    @Test fun planCompletoQuedaListoParaRevisionNuncaSeguro() {
        val g = grua.copy(estabilizadores = emptyList())
        val p = proyectoBase().let { it.copy(aparejo = it.aparejo.copy(metodoAngulo = MetodoAngulo.GEOMETRIA, longitudRamal = 2.0, separacionPuntos = 2.0), maniobra = it.maniobra.copy(comprobarTerreno = true)) }
        val ev = Motor.evaluar(p, g, listOf(eslinga, eslinga2), hoy)
        // La presión sobre el terreno no es verificable sin datos del fabricante: el plan sigue en borrador.
        assertEquals(EstadoDato.NO_VERIFICABLE, ev.resultados.first { it.id == "apoyos" }.estado)
        assertTrue(ev.estadoDocumento.esBorrador)
    }

    @Test fun presionSobreTerreno() {
        val est = ConfigEstabilizadores("e1", "Total", fuerzaMaxApoyoKn = 100.0, fuente = FuenteTecnica("Manual", "p. 30"))
        val g = grua.copy(estabilizadores = listOf(est))
        val p = proyectoBase().let { it.copy(maniobra = it.maniobra.copy(estabilizadoresId = "e1", placaAncho = 0.5, placaLargo = 0.5, presionAdmisibleKpa = 300.0, fuentePresionAdmisible = "Estudio")) }
        val r = Motor.evaluar(p, g, listOf(eslinga, eslinga2), hoy).resultados.first { it.id == "apoyos" }
        assertEquals(400.0, r.valorNumerico!!, 1e-9)
        assertEquals(Veredicto.NO_CUMPLE, r.veredicto)
    }

    @Test fun lineaElectricaCercana() {
        val p = proyectoBase().let {
            val linea = Elemento("l", TipoElemento.LINEA_ELECTRICA, x = 2.5, y = 2.0, rotacion = 90.0, largo = 20.0, distanciaMinima = 3.0)
            it.copy(croquis = it.croquis.copy(elementos = it.croquis.elementos + linea))
        }
        val r = Motor.evaluar(p, grua, listOf(eslinga, eslinga2), hoy).resultados.first { it.id == "interferencias" }
        assertEquals(Veredicto.NO_CUMPLE, r.veredicto)
        assertNotEquals(EstadoDato.DOCUMENTADO, r.estado)
    }

    @Test fun radioFueraDeTabla() {
        val ev = Motor.evaluar(proyectoBase(radioMedido = 15.0), grua, listOf(eslinga, eslinga2), hoy)
        assertEquals(Veredicto.NO_CUMPLE, ev.resultados.first { it.id == "capacidad_f1" }.veredicto)
    }

    @Test fun faltantesDeProyectoVacio() {
        val f = Motor.faltantes(Proyecto("x"), null, null)
        assertTrue(f.any { it.paso == Paso.INFO })
        assertTrue(f.any { it.texto.contains("grúa", ignoreCase = true) })
    }

    @Test fun revisionHumanaCaducaSiCambianLosDatos() {
        val g = grua.copy(estabilizadores = listOf(ConfigEstabilizadores("e1", fuerzaMaxApoyoKn = 50.0, fuente = FuenteTecnica("Manual", "p. 3"))), pesoGanchoKg = 0.0)
        val acc = listOf(eslinga, eslinga2)
        var p = proyectoBase().let { it.copy(
            maniobra = it.maniobra.copy(estabilizadoresId = "e1", placaAncho = 0.6, placaLargo = 0.6, presionAdmisibleKpa = 300.0, fuentePresionAdmisible = "Estudio"),
            aparejo = it.aparejo.copy(metodoAngulo = MetodoAngulo.GEOMETRIA, longitudRamal = 2.0, separacionPuntos = 2.0),
        ) }
        val base = Motor.evaluar(p, g, acc, hoy)
        assertEquals(EstadoDocumento.LISTO_PARA_REVISION, base.estadoDocumento)
        val h = Serializacion.huellaRevision(p, g, acc)
        p = p.copy(revisionHumana = RevisionHumana("Rev", "2026-10-25", true, huellaDatos = h))
        assertEquals(EstadoDocumento.REVISADO, Motor.evaluar(p, g, acc, hoy).estadoDocumento)
        val cambiado = p.copy(carga = p.carga.copy(pesoKg = 1400.0))
        assertEquals(EstadoDocumento.LISTO_PARA_REVISION, Motor.evaluar(cambiado, g, acc, hoy).estadoDocumento)
    }

    @Test fun aprobacionSoloSiSeRegistraExplicitamente() {
        val ev = Motor.evaluar(proyectoBase(), grua, listOf(eslinga, eslinga2), hoy)
        assertNotEquals(EstadoDocumento.APROBACION_REGISTRADA, ev.estadoDocumento)
    }
}

class CandidatosTest {
    private val hoy = LocalDate.of(2026, 10, 25)
    private fun e(id: String, wll: Double?, estado: EstadoInspeccion = EstadoInspeccion.APTO, prox: String = "") =
        Accesorio(id, TipoAccesorio.ESLINGA_CABLE, identificacion = id, wllVertical = wll, estado = estado, proximaInspeccion = prox, numeroCertificado = "c", cargaRotura = 99999.0)

    @Test fun clasificaYOrdena() {
        val lista = listOf(e("grande", 5000.0), e("justa", 1200.0), e("corta", 800.0), e("sinwll", null), e("mala", 9000.0, EstadoInspeccion.NO_APTO), e("vencida", 9000.0, prox = "2025-01-01"))
        val c = Candidatos.clasificar(lista, { it.tipo.esEslinga }, 1000.0, Enganche.VERTICAL, hoy)
        assertEquals("justa", c.first().accesorio.id)
        assertEquals(Aptitud.APTO, c.first { it.accesorio.id == "grande" }.aptitud)
        assertEquals(Aptitud.NO_APTO, c.first { it.accesorio.id == "corta" }.aptitud)
        assertEquals(Aptitud.FALTA_DATO, c.first { it.accesorio.id == "sinwll" }.aptitud)
        assertEquals(Aptitud.NO_APTO, c.first { it.accesorio.id == "mala" }.aptitud)
        assertEquals(Aptitud.NO_APTO, c.first { it.accesorio.id == "vencida" }.aptitud)
    }

    @Test fun wllPorEnganche() {
        val a = e("x", 1000.0).copy(wllAhorcado = 800.0)
        val c = Candidatos.clasificar(listOf(a), { true }, 900.0, Enganche.AHORCADO, hoy)
        assertEquals(Aptitud.NO_APTO, c.first().aptitud)
    }
}

class SerializacionTest {
    @Test fun proyectoIdaYVuelta() {
        val p = Proyecto(
            "p1", nombre = "Obra", carga = Carga(pesoKg = 1234.5, origenPeso = OrigenPeso.PESADO),
            croquis = Croquis(elementos = listOf(Elemento("e", TipoElemento.EDIFICIO, x = 1.0, y = 2.0, alto = 6.0))),
            pasosVistos = setOf(Paso.INFO, Paso.CARGA),
        )
        val back = Serializacion.proyectoDesde(Serializacion.proyectoAJson(p))
        assertEquals(p, back)
    }

    @Test fun ignoraCamposDesconocidos() {
        val t = """{"esquema":1,"dato":{"id":"g","fabricante":"X","campoFuturo":42}}"""
        assertEquals("X", Serializacion.gruaDesde(t).fabricante)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rechazaEsquemaMasNuevo() {
        Serializacion.gruaDesde("""{"esquema":999,"dato":{"id":"g"}}""")
    }

    @Test fun huellaCambiaConDatosRelevantes() {
        val p = Proyecto("p", carga = Carga(pesoKg = 100.0))
        val h1 = Serializacion.huella(p, null, emptyList())
        val h2 = Serializacion.huella(p.copy(modificado = 999L, pasosVistos = setOf(Paso.INFO)), null, emptyList())
        val h3 = Serializacion.huella(p.copy(carga = Carga(pesoKg = 101.0)), null, emptyList())
        assertEquals(h1, h2)
        assertNotEquals(h1, h3)
    }
}
