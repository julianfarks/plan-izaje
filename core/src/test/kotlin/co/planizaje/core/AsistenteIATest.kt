package co.planizaje.core

import co.planizaje.core.calc.EstadoDato
import co.planizaje.core.calc.Motor
import co.planizaje.core.ia.Ambito
import co.planizaje.core.ia.Aplicador
import co.planizaje.core.ia.Asistente
import co.planizaje.core.ia.CampoExtraido
import co.planizaje.core.ia.Clave
import co.planizaje.core.ia.EstadoCampo
import co.planizaje.core.ia.Extractor
import co.planizaje.core.ia.Fragmento
import co.planizaje.core.ia.LectorTabla
import co.planizaje.core.ia.NumerosTexto
import co.planizaje.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorTest {

    private val ejemplo = "Vamos a levantar un equipo eléctrico que pesa aproximadamente 850 kilos. Está ubicado al lado del edificio y tenemos que " +
        "colocarlo en una plataforma que queda a unos 12 metros del camión. La grúa va montada sobre el camión, con los estabilizadores " +
        "desplegados. La recogida se hace desde el suelo y la descarga es sobre la plataforma. Vamos a utilizar dos eslingas y sus " +
        "respectivos grilletes, pero todavía tengo que comprobar sus capacidades."

    private val pk = Grua("g1", "Palfinger", "PK 23500", numeroSerie = "S1",
        tablas = listOf(TablaCarga("t6", "6 ext", extensiones = 6, puntos = listOf(PuntoTabla(4.6, 4520.0)))))
    private val hiab = Grua("g2", "Hiab", "288 E-8", tablas = emptyList())

    @Test fun numerosEnLetras() {
        assertEquals("850 kilos", NumerosTexto.cifras("ochocientos cincuenta kilos"))
        assertEquals("2 eslingas y 3 grilletes", NumerosTexto.cifras("dos eslingas y tres grilletes"))
        assertEquals("12,5 metros", NumerosTexto.cifras("doce coma cinco metros"))
        assertEquals("1,5 toneladas", NumerosTexto.cifras("una tonelada y media"))
        assertEquals("2500 kg", NumerosTexto.cifras("dos mil quinientos kg"))
        assertEquals("levantar un equipo", NumerosTexto.cifras("levantar un equipo"))
        assertEquals("1 eslinga", NumerosTexto.cifras("una eslinga"))
    }

    // Caso 1: dictado completo distribuido en los campos
    @Test fun dictadoCompletoSeDistribuye() {
        val e = Extractor.extraer(ejemplo)
        assertEquals("Equipo eléctrico", e.campo(Clave.CARGA_DESCRIPCION)?.valor)
        assertEquals("850 kg", e.campo(Clave.CARGA_PESO)?.valor)
        assertTrue(e.campo(Clave.CARGA_POS_INICIAL)!!.valor.contains("suelo", true))
        assertTrue(e.campo(Clave.CARGA_POS_FINAL)!!.valor.contains("plataforma", true))
        assertTrue(e.campo(Clave.ESTABILIZADORES)!!.valor.contains("desplegados"))
        assertTrue(e.campo(Clave.APAREJO_DESCRIPCION)!!.valor.contains("eslingas", true))
        assertTrue(e.campo(Clave.APAREJO_DESCRIPCION)!!.valor.contains("grilletes", true))
        assertEquals("2", e.campo(Clave.APAREJO_RAMALES)?.valor)
        assertEquals(EstadoCampo.APROXIMADO, e.campo(Clave.APAREJO_RAMALES)?.estado) // deducido
        assertNotNull(e.campo(Clave.PENDIENTES))
    }

    // Caso 3: peso aproximado
    @Test fun pesoAproximado() {
        val e = Extractor.extraer(ejemplo)
        assertEquals(EstadoCampo.APROXIMADO, e.campo(Clave.CARGA_PESO)?.estado)
        assertEquals(EstadoCampo.APROXIMADO, Extractor.extraer("Creo que pesa 850 kilos").campo(Clave.CARGA_PESO)?.estado)
        assertEquals(EstadoCampo.APROXIMADO, Extractor.extraer("pesa unos ochocientos cincuenta kilos").campo(Clave.CARGA_PESO)?.estado)
        val exacto = Extractor.extraer("La carga pesa 1,2 toneladas según la placa.").campo(Clave.CARGA_PESO)!!
        assertEquals("1.200 kg", exacto.valor)
        assertEquals(EstadoCampo.CONFIRMADO, exacto.estado)
        assertEquals("según placa", exacto.ref)
    }

    @Test fun sinPesoNoSeInventa() {
        val e = Extractor.extraer("Vamos a subir un tanque vacío a la terraza.")
        assertNull(e.campo(Clave.CARGA_PESO))
        assertEquals("Tanque vacío", e.campo(Clave.CARGA_DESCRIPCION)?.valor)
    }

    @Test fun capacidadDeEslingaNoEsPesoDeCarga() {
        val e = Extractor.extraer("Usaremos una eslinga de 2 toneladas para un motor de 600 kg.")
        assertEquals("600 kg", e.campo(Clave.CARGA_PESO)?.valor)
    }

    @Test fun dosPesosDistintosPregunta() {
        val e = Extractor.extraer("El motor pesa 600 kg, aunque el plano dice 750 kg.")
        assertNull(e.campo(Clave.CARGA_PESO))
        assertTrue(e.preguntas.any { it.id == "peso" && it.opciones.size == 3 })
    }

    // Caso 4: distancia que no es necesariamente el radio
    @Test fun distanciaAlCamionNoEsRadio() {
        val e = Extractor.extraer(ejemplo)
        assertNull(e.campo(Clave.RADIO))
        assertTrue(e.campo(Clave.DISTANCIA_REFERENCIA)!!.valor.contains("12 m respecto a el camión") || e.campo(Clave.DISTANCIA_REFERENCIA)!!.valor.contains("12 m"))
        val q = e.preguntas.first { it.id.startsWith("radio") }
        assertTrue(q.opciones.first().campos.isEmpty())
        assertEquals(Clave.RADIO, q.opciones[1].campos.single().clave)
    }

    @Test fun radioExplicito() {
        val e = Extractor.extraer("El radio de trabajo es de 9,5 metros medido desde la columna.")
        assertEquals("9,5", e.campo(Clave.RADIO)?.valor)
        assertEquals(EstadoCampo.CONFIRMADO, e.campo(Clave.RADIO)?.estado)
    }

    // Caso 2: información parcial
    @Test fun informacionParcial() {
        val e = Extractor.extraer("Pesa 300 kilos según la ficha.", ambito = Ambito.CARGA)
        assertEquals(listOf(Clave.CARGA_PESO), e.campos.map { it.clave })
        val p = Proyecto("p", nombre = "Obra existente", carga = Carga(descripcion = "Motor"))
        val r = Aplicador.aplicar(p, emptyList(), e.campos, emptySet())
        assertEquals("Obra existente", r.valor.nombre)
        assertEquals("Motor", r.valor.carga.descripcion)
        assertEquals(300.0, r.valor.carga.pesoKg!!, 1e-9)
        assertEquals(OrigenPeso.DOCUMENTADO, r.valor.carga.origenPeso)
        val ev = Motor.evaluar(r.valor, null, emptyList())
        assertTrue(ev.faltantes.any { it.campo == "ubicacion" })
    }

    @Test fun gruaDeLaBibliotecaYExtensiones() {
        val e = Extractor.extraer("Trabajamos con la Palfinger 23500 con 6 extensiones.", Extractor.Contexto(listOf(pk, hiab)))
        assertEquals("g1", e.campo(Clave.GRUA)?.ref)
        val r = Aplicador.aplicar(Proyecto("p"), listOf(pk, hiab), e.campos, emptySet())
        assertEquals("g1", r.valor.gruaId)
        assertEquals("t6", r.valor.maniobra.tablaId)
    }

    @Test fun gruaNoRegistradaAvisa() {
        val e = Extractor.extraer("Usaremos una Fassi 150.", Extractor.Contexto(listOf(pk)))
        assertNull(e.campo(Clave.GRUA))
        assertTrue(e.avisos.any { it.contains("fassi", true) })
    }

    @Test fun lineasElectricasMarcanPeligro() {
        val e = Extractor.extraer("Hay cables de luz cerca y mucho tráfico en la calle.")
        val r = Aplicador.aplicar(Proyecto("p"), emptyList(), e.campos, emptySet())
        val pel = r.valor.procedimiento.peligros.filter { it.aplica }.map { it.nombre }
        assertTrue(pel.contains("Líneas eléctricas cercanas"))
        assertTrue(pel.contains("Tráfico vehicular o peatonal"))
        assertTrue(r.valor.croquis.nota.contains("líneas eléctricas", ignoreCase = true))
    }

    @Test fun fichaDeGrua() {
        val e = Extractor.extraer("Es una Palfinger PK 23500, número de serie AB1234, placa TTK 456, con 6 extensiones hidráulicas.", ambito = Ambito.FICHA_GRUA)
        val g = Aplicador.aplicarGrua(Grua("x"), e.campos, emptySet()).valor
        assertEquals("Palfinger", g.fabricante)
        assertEquals("PK 23500", g.modelo)
        assertEquals("AB1234", g.numeroSerie)
        assertEquals("TTK456", g.placaVehiculo)
        assertEquals(6, g.extensionesHidraulicas)
    }
}

class AplicadorTest {
    // Caso 5: el usuario modifica un valor extraído
    @Test fun valorEditadoSeAplicaComoConfirmado() {
        val campos = listOf(CampoExtraido(Clave.CARGA_PESO, "850 kg", EstadoCampo.APROXIMADO))
        val editado = campos.map { it.copy(valor = "870 kg", estado = EstadoCampo.CONFIRMADO) }
        val p = Aplicador.aplicar(Proyecto("p"), emptyList(), editado, emptySet()).valor
        assertEquals(870.0, p.carga.pesoKg!!, 1e-9)
        assertTrue(p.pendientes.none { it.clave == Clave.CARGA_PESO.name })
    }

    @Test fun aproximadoQuedaPendienteYNoDocumentado() {
        val p = Aplicador.aplicar(Proyecto("p"), emptyList(), listOf(CampoExtraido(Clave.CARGA_PESO, "850 kg", EstadoCampo.APROXIMADO)), emptySet()).valor
        assertEquals(OrigenPeso.ESTIMADO, p.carga.origenPeso)
        assertTrue(p.pendientes.any { it.clave == Clave.CARGA_PESO.name })
        assertEquals(EstadoDato.REVISAR, Motor.evaluar(p, null, emptyList()).resultados.first { it.id == "peso_carga" }.estado)
    }

    @Test fun conflictoNoSeSobrescribeSinPermiso() {
        val p = Proyecto("p", carga = Carga(descripcion = "Transformador", pesoKg = 900.0))
        val campos = listOf(CampoExtraido(Clave.CARGA_PESO, "850 kg", EstadoCampo.CONFIRMADO), CampoExtraido(Clave.CARGA_DESCRIPCION, "Transformador", EstadoCampo.CONFIRMADO))
        val conf = Aplicador.conflictos(p, emptyList(), campos)
        assertEquals(listOf(Clave.CARGA_PESO), conf.map { it.clave })
        assertEquals(900.0, Aplicador.aplicar(p, emptyList(), campos, emptySet()).valor.carga.pesoKg!!, 1e-9)
        assertEquals(850.0, Aplicador.aplicar(p, emptyList(), campos, setOf(Clave.CARGA_PESO)).valor.carga.pesoKg!!, 1e-9)
    }

    @Test fun radioAproximadoNoSeGuardaComoMedido() {
        val p = Aplicador.aplicar(Proyecto("p"), emptyList(), listOf(CampoExtraido(Clave.RADIO, "12", EstadoCampo.APROXIMADO)), emptySet()).valor
        assertTrue(p.maniobra.fases.isEmpty() || p.maniobra.fases.all { it.radioMedido == null })
        assertTrue(p.maniobra.notasEmplazamiento.contains("12"))
    }

    @Test fun textosSeAnadenSinBorrar() {
        val p = Proyecto("p", observaciones = "Primera nota")
        val r = Aplicador.aplicar(p, emptyList(), listOf(CampoExtraido(Clave.OBSERVACIONES, "Cuidado con el viento", EstadoCampo.CONFIRMADO)), emptySet()).valor
        assertTrue(r.observaciones.contains("Primera nota") && r.observaciones.contains("viento"))
    }

    @Test fun asistenteResumeLoQueFalta() {
        val p = Aplicador.aplicar(Proyecto("p"), emptyList(), listOf(
            CampoExtraido(Clave.CARGA_DESCRIPCION, "Equipo eléctrico", EstadoCampo.CONFIRMADO),
            CampoExtraido(Clave.CARGA_PESO, "850 kg", EstadoCampo.APROXIMADO),
        ), emptySet()).valor
        val r = Asistente.resumen(p, null, Motor.evaluar(p, null, emptyList()))
        val f = r.frase()
        assertTrue(f, f.contains("descripción de la carga") && f.contains("peso aproximado"))
        assertTrue(r.acciones.any { it.campo == "carga.origen" })
        assertTrue(r.acciones.any { it.campo == "grua" })
    }
}

class LectorTablaTest {
    /** Simula el OCR de la placa Hiab 111 B-3 CLX: radios y capacidades en dos filas. */
    private fun frags(conf: Float? = 0.95f, borrosos: Set<Int> = emptySet()): List<Fragmento> {
        val radios = listOf("3.1", "4.3", "5.9", "7.8", "9.8", "11.8")
        val caps = listOf("3150", "2200", "1560", "1140", "900", "680")
        val l = mutableListOf(
            Fragmento("HIAB 111 B-3 CLX", 50f, 20f, 300f, 30f, conf),
            Fragmento("m", 30f, 100f, 15f, 30f, conf),
            Fragmento("Kg", 30f, 160f, 30f, 30f, conf),
            Fragmento("381 1760", 900f, 240f, 100f, 20f, conf),
        )
        radios.forEachIndexed { i, r -> l += Fragmento(r, 120f + i * 130, 100f, 50f, 30f, conf) }
        caps.forEachIndexed { i, c -> l += Fragmento(c, 115f + i * 130, 160f, 60f, 30f, if (i in borrosos) 0.4f else conf) }
        return l
    }

    // Caso 6: tabla legible
    @Test fun tablaLegible() {
        val r = LectorTabla.leer(frags())
        assertEquals("Hiab", r.fabricante)
        assertTrue(r.modelo, r.modelo.contains("111"))
        val t = r.tablas.single()
        assertEquals(6, t.puntos.size)
        assertEquals(3150.0, t.puntos.first { it.radio == 3.1 }.capacidad, 1e-9)
        assertEquals(680.0, t.puntos.first { it.radio == 11.8 }.capacidad, 1e-9)
        assertFalse(r.hayDudosos)
    }

    // Caso 7: tabla borrosa
    @Test fun tablaBorrosaMarcaDudosos() {
        val r = LectorTabla.leer(frags(borrosos = setOf(2, 4)))
        val t = r.tablas.single()
        assertEquals(2, t.puntos.count { it.dudoso })
        assertTrue(r.hayDudosos)
        assertTrue(r.advertencias.any { it.contains("pendientes de verificar") })
    }

    @Test fun ocrCorrigeLetrasYLasMarca() {
        val l = frags().map { if (it.texto == "1140") it.copy(texto = "114O") else it }
        val p = LectorTabla.leer(l).tablas.single().puntos.first { it.radio == 7.8 }
        assertEquals(1140.0, p.capacidad, 1e-9)
        assertTrue(p.dudoso)
    }

    @Test fun celdaIlegibleNoSeRellena() {
        val l = frags().filterNot { it.texto == "1560" }
        val t = LectorTabla.leer(l).tablas.single()
        assertEquals(5, t.puntos.size)
        assertTrue(t.puntos.none { it.radio == 5.9 })
        assertTrue(t.sinEmparejar.any { it.startsWith("5.9") })
    }

    @Test fun filaDeLibrasSeIgnora() {
        val l = frags() + listOf("6940 lbs", "4850 lbs", "3440 lbs").mapIndexed { i, s -> Fragmento(s, 110f + i * 130, 220f, 70f, 30f, 0.95f) }
        val r = LectorTabla.leer(l)
        assertEquals(1, r.tablas.size)
        assertTrue(r.advertencias.any { it.contains("libras") })
    }

    // Caso 8: tabla de otra grúa
    @Test fun coincidenciaDeModelo() {
        assertEquals(true, LectorTabla.coincideModelo("HIAB 111 B-3 CLX", "Hiab", "111 B-3", "Hiab"))
        assertEquals(false, LectorTabla.coincideModelo("HIAB 111 B-3 CLX", "Hiab", "PK 23500", "Palfinger"))
        assertNull(LectorTabla.coincideModelo("", "", "PK 23500", "Palfinger"))
    }

    @Test fun tablaPendienteDeIdentificacionNoSeUsa() {
        val t = TablaCarga("t", puntos = listOf(PuntoTabla(4.0, 2000.0), PuntoTabla(8.0, 1000.0)), identificacionPendiente = true,
            fuente = FuenteTecnica("Foto", "placa"), verificacion = Verificacion(true, "Yo", "2026-10-25"), origen = OrigenTabla.IMPORTADA_OCR)
        val g = Grua("g", "Palfinger", "PK 23500", numeroSerie = "1", tablas = listOf(t))
        val p = Proyecto("p", gruaId = "g", carga = Carga(pesoKg = 500.0, origenPeso = OrigenPeso.PESADO, referenciaPeso = "t"),
            maniobra = Maniobra(fases = listOf(Fase("f", radioMedido = 5.0)), tablaId = "t"))
        val ev = Motor.evaluar(p, g, emptyList())
        assertEquals(EstadoDato.NO_VERIFICABLE, ev.resultados.first { it.id == "tabla" }.estado)
        val cap = ev.resultados.first { it.id == "capacidad_f" }
        assertEquals(EstadoDato.NO_VERIFICABLE, cap.estado)
        assertNull(cap.valorNumerico)
    }

    @Test fun tablaConDudososRequiereRevision() {
        val t = TablaCarga("t", puntos = listOf(PuntoTabla(4.0, 2000.0, dudoso = true)), fuente = FuenteTecnica("Foto", "placa"),
            verificacion = Verificacion(true, "Yo", "x"))
        val g = Grua("g", "A", "B", numeroSerie = "1", tablas = listOf(t))
        val ev = Motor.evaluar(Proyecto("p", gruaId = "g", maniobra = Maniobra(tablaId = "t")), g, emptyList())
        assertEquals(EstadoDato.REVISAR, ev.resultados.first { it.id == "tabla" }.estado)
    }
}

class ArticuladaTest {
    @Test fun terrenoYPuntosNoBloqueanEnArticuladaPorDefecto() {
        val g = Grua("g", tipo = TipoGrua.ARTICULADA)
        val p = Proyecto("p", carga = Carga(pesoKg = 500.0), aparejo = Aparejo(ramales = 2, anguloVertical = 45.0, anguloEsMaximo = true))
        val ev = Motor.evaluar(p, g, emptyList())
        val apoyos = ev.resultados.first { it.id == "apoyos" }
        assertFalse(apoyos.bloquea)
        assertFalse(ev.resultados.first { it.id == "puntos_izaje" }.bloquea)
        assertEquals(EstadoDato.DOCUMENTADO, ev.resultados.first { it.id == "eslingas_tension" }.estado)
    }

    @Test fun terrenoActivadoSiBloquea() {
        val g = Grua("g", tipo = TipoGrua.ARTICULADA)
        val p = Proyecto("p", maniobra = Maniobra(comprobarTerreno = true))
        assertTrue(Motor.evaluar(p, g, emptyList()).resultados.first { it.id == "apoyos" }.bloquea)
    }

    @Test fun telescopicaExigeTerreno() {
        val g = Grua("g", tipo = TipoGrua.TELESCOPICA_CAMION)
        assertTrue(Motor.evaluar(Proyecto("p"), g, emptyList()).resultados.first { it.id == "apoyos" }.bloquea)
    }
}

