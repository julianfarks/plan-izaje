package co.planizaje.core.calc

import co.planizaje.core.geo.Geometria
import co.planizaje.core.model.Accesorio
import co.planizaje.core.model.Croquis
import co.planizaje.core.model.Elemento
import co.planizaje.core.model.Fase
import co.planizaje.core.model.Grua
import co.planizaje.core.model.Paso
import co.planizaje.core.model.Proyecto
import co.planizaje.core.model.Pt
import co.planizaje.core.model.TablaCarga
import co.planizaje.core.model.TipoAccesorio
import co.planizaje.core.model.TipoElemento
import co.planizaje.core.util.Unidades
import co.planizaje.core.util.Unidades.fmt
import java.time.LocalDate

/**
 * Motor de verificación. Toma el proyecto, la grúa y la biblioteca de accesorios
 * y produce resultados explicados. No inventa datos: lo que falta queda pendiente
 * o no verificable, y nunca declara una maniobra "segura".
 */
object Motor {

    private const val G_CARGA = "Masas"
    private const val G_GRUA = "Grúa y tabla de carga"
    private const val G_APAREJO = "Aparejos"
    private const val G_ENTORNO = "Entorno y apoyos"

    data class RadioFase(val fase: Fase, val radio: Double?, val exacto: Boolean, val explicacion: String)

    fun evaluar(
        p: Proyecto,
        grua: Grua?,
        biblioteca: List<Accesorio>,
        hoy: LocalDate = LocalDate.now(),
    ): Evaluacion {
        val res = mutableListOf<Resultado>()
        val porId = biblioteca.associateBy { it.id }
        val usados = p.aparejo.todosIds.mapNotNull { porId[it] }
        val perdidos = p.aparejo.todosIds.filter { it !in porId }
        val tabla = grua?.tablas?.firstOrNull { it.id == p.maniobra.tablaId }

        // 1. Peso de la carga ------------------------------------------------
        val c = p.carga
        val rCarga = when {
            c.pesoKg == null -> Resultado(
                "peso_carga", G_CARGA, "Peso de la carga", EstadoDato.PENDIENTE,
                mensajes = listOf("Introduzca el peso de la carga o márquelo como pendiente de confirmar."), paso = Paso.CARGA,
            )
            !c.origenPeso.documentado -> Resultado(
                "peso_carga", G_CARGA, "Peso de la carga", EstadoDato.REVISAR, Unidades.kgYt(c.pesoKg), c.pesoKg,
                datos = listOf(Dato("Origen del dato", c.origenPeso.etiqueta)),
                mensajes = listOf("El peso no está documentado (${c.origenPeso.etiqueta.lowercase()}). Confírmelo con placa, plano, certificado o báscula antes de ejecutar."),
                paso = Paso.CARGA,
            )
            c.referenciaPeso.isBlank() -> Resultado(
                "peso_carga", G_CARGA, "Peso de la carga", EstadoDato.REVISAR, Unidades.kgYt(c.pesoKg), c.pesoKg,
                datos = listOf(Dato("Origen del dato", c.origenPeso.etiqueta)),
                mensajes = listOf("Indique la referencia del documento o ticket de báscula que respalda el peso."), paso = Paso.CARGA,
            )
            else -> Resultado(
                "peso_carga", G_CARGA, "Peso de la carga", EstadoDato.DOCUMENTADO, Unidades.kgYt(c.pesoKg), c.pesoKg,
                datos = listOf(Dato("Origen del dato", c.origenPeso.etiqueta), Dato("Referencia", c.referenciaPeso)),
                fuente = c.referenciaPeso, paso = Paso.CARGA,
            )
        }
        res += rCarga

        // 2. Peso de accesorios y gancho ------------------------------------
        val sinPeso = usados.filter { it.pesoKg == null }
        val pesoAcc = usados.sumOf { it.pesoKg ?: 0.0 }
        val msgAcc = mutableListOf<String>()
        var estAcc = EstadoDato.DOCUMENTADO
        if (usados.isEmpty()) {
            estAcc = EstadoDato.PENDIENTE; msgAcc += "No hay aparejos seleccionados en la maniobra."
        }
        if (sinPeso.isNotEmpty()) {
            estAcc = EstadoDato.peor(estAcc, EstadoDato.REVISAR)
            msgAcc += "Sin peso registrado: " + sinPeso.joinToString { it.nombreCorto } + ". El total es un mínimo."
        }
        if (perdidos.isNotEmpty()) {
            estAcc = EstadoDato.peor(estAcc, EstadoDato.REVISAR)
            msgAcc += "${perdidos.size} accesorio(s) seleccionados ya no existen en la biblioteca."
        }
        var pesoGancho = 0.0
        val datosAcc = usados.map { Dato(it.nombreCorto, Unidades.kg(it.pesoKg)) }.toMutableList()
        if (tabla != null) {
            when (tabla.descuentaGancho) {
                null -> {
                    estAcc = EstadoDato.peor(estAcc, EstadoDato.REVISAR)
                    msgAcc += "Confirme en el diagrama de carga si la capacidad ya descuenta el gancho o bloque de la grúa."
                }
                false -> {
                    val pg = grua?.pesoGanchoKg
                    if (pg == null) {
                        estAcc = EstadoDato.peor(estAcc, EstadoDato.REVISAR)
                        msgAcc += "La tabla no descuenta el gancho y falta el peso del gancho de la grúa."
                    } else {
                        pesoGancho = pg
                        datosAcc += Dato("Gancho/bloque de la grúa", Unidades.kg(pg), "Ficha de la grúa")
                    }
                }
                true -> datosAcc += Dato("Gancho/bloque de la grúa", "Incluido en la tabla", tabla.fuente.texto())
            }
        }
        res += Resultado(
            "peso_accesorios", G_CARGA, "Peso de accesorios suspendidos", estAcc,
            Unidades.kg(pesoAcc + pesoGancho), pesoAcc + pesoGancho,
            formula = "Σ pesos de accesorios registrados" + if (pesoGancho > 0) " + gancho de la grúa" else "",
            datos = datosAcc, mensajes = msgAcc, paso = Paso.MANIOBRA,
        )

        // 3. Masa total suspendida ------------------------------------------
        val total = c.pesoKg?.let { it + pesoAcc + pesoGancho }
        val estTotal = EstadoDato.peor(rCarga.estado, if (estAcc == EstadoDato.PENDIENTE) EstadoDato.REVISAR else estAcc)
        res += Resultado(
            "masa_total", G_CARGA, "Masa total suspendida", if (total == null) EstadoDato.PENDIENTE else estTotal,
            Unidades.kgYt(total), total,
            formula = "M = carga + accesorios + gancho (si la tabla no lo descuenta)",
            datos = listOf(
                Dato("Carga", Unidades.kg(c.pesoKg)),
                Dato("Accesorios", Unidades.kg(pesoAcc)),
                Dato("Gancho no descontado", Unidades.kg(pesoGancho)),
            ),
            mensajes = if (total == null) listOf("Se calculará al conocer el peso de la carga.") else emptyList(),
        )

        // 4. Grúa y tabla -----------------------------------------------------
        val rTabla = evaluarTabla(p, grua, tabla)
        res += rTabla

        // 5. Radios por fase ----------------------------------------------
        val radios = p.maniobra.fases.map { radioFase(it, p.croquis) }
        if (radios.isEmpty()) {
            res += Resultado(
                "radio", G_GRUA, "Radio de trabajo", EstadoDato.PENDIENTE,
                mensajes = listOf("Cree al menos una fase de maniobra con su punto de recogida y de descarga."), paso = Paso.MANIOBRA,
            )
        }
        var utilMax: Double? = null
        var radioMax: Double? = null
        radios.forEachIndexed { i, rf ->
            val nombre = rf.fase.nombre.ifBlank { "Fase ${i + 1}" }
            val estR = when {
                rf.radio == null -> EstadoDato.PENDIENTE
                rf.exacto -> EstadoDato.DOCUMENTADO
                else -> EstadoDato.REVISAR
            }
            res += Resultado(
                "radio_${rf.fase.id}", G_GRUA, "Radio de trabajo · $nombre", estR, Unidades.m(rf.radio), rf.radio,
                formula = if (rf.exacto) "Radio medido e introducido por el usuario" else "R = distancia en planta del eje de giro de la grúa al punto más alejado de la fase",
                mensajes = listOf(rf.explicacion), paso = Paso.MANIOBRA,
            )
            if (rf.radio != null) radioMax = maxOf(radioMax ?: 0.0, rf.radio)
            val u = evaluarCapacidad(i, rf, nombre, tabla, rTabla.estado, total, estTotal, p)
            res += u
            u.second?.let { utilMax = maxOf(utilMax ?: 0.0, it) }
        }

        // 6. Aparejos -------------------------------------------------------
        res += evaluarAparejos(p, usados, total, hoy)

        // 7. Entorno ----------------------------------------------------------
        res += evaluarApoyos(p, grua)
        res += evaluarInterferencias(p)
        res += evaluarViento(grua)

        val lista = res.toList()
        val faltantes = faltantes(p, grua, tabla)
        val huella = co.planizaje.core.data.Serializacion.huellaRevision(p, grua, biblioteca)
        val estadoDoc = estadoDocumento(p, lista, faltantes, huella)
        return Evaluacion(lista, faltantes, estadoDoc, radioMax, utilMax, total)
    }

    // ------------------------------------------------------------------- helpers

    private operator fun MutableList<Resultado>.plusAssign(par: Pair<List<Resultado>, Double?>) { addAll(par.first) }

    private fun evaluarTabla(p: Proyecto, grua: Grua?, tabla: TablaCarga?): Resultado {
        if (grua == null) return Resultado(
            "tabla", G_GRUA, "Tabla de carga aplicable", EstadoDato.PENDIENTE,
            mensajes = listOf("Seleccione la grúa del proyecto."), paso = Paso.GRUA,
        )
        if (tabla == null) return Resultado(
            "tabla", G_GRUA, "Tabla de carga aplicable", EstadoDato.NO_VERIFICABLE,
            mensajes = listOf(
                if (grua.tablas.isEmpty()) "La grúa ${grua.nombreCorto} no tiene tablas de carga registradas. Sin tabla oficial no se puede verificar la capacidad."
                else "Seleccione la tabla de carga que corresponde a la configuración real (extensiones, estabilizadores, sector).",
            ),
            paso = Paso.GRUA,
        )
        val msg = mutableListOf<String>()
        var est = EstadoDato.DOCUMENTADO
        if (tabla.puntos.isEmpty()) { est = EstadoDato.NO_VERIFICABLE; msg += "La tabla no tiene valores de radio y capacidad." }
        if (!tabla.fuente.completa) { est = EstadoDato.peor(est, EstadoDato.REVISAR); msg += "Falta identificar el documento y la página/figura de donde salen los valores." }
        if (!tabla.verificacion.valida) { est = EstadoDato.peor(est, EstadoDato.REVISAR); msg += "Nadie ha registrado la verificación de la tabla contra el diagrama original." }
        if (!grua.identificada) { est = EstadoDato.peor(est, EstadoDato.REVISAR); msg += "Identifique fabricante, modelo y nº de serie: una tabla no pertenece a una grúa solo porque el nombre comercial coincida." }
        val estabSel = p.maniobra.estabilizadoresId
        if (tabla.estabilizadoresId != null && estabSel != null && tabla.estabilizadoresId != estabSel) {
            est = EstadoDato.peor(est, EstadoDato.REVISAR)
            msg += "La tabla corresponde a otra configuración de estabilizadores distinta de la seleccionada."
        }
        if (tabla.estabilizadoresId != null && estabSel == null) {
            est = EstadoDato.peor(est, EstadoDato.REVISAR)
            msg += "Indique la configuración de estabilizadores de la maniobra."
        }
        return Resultado(
            "tabla", G_GRUA, "Tabla de carga aplicable", est, tabla.nombre.ifBlank { tabla.configuracionPluma.ifBlank { "Tabla sin nombre" } },
            datos = listOfNotNull(
                Dato("Grúa", grua.nombreCorto),
                Dato("Nº de serie", grua.numeroSerie.ifBlank { "sin dato" }),
                Dato("Configuración de pluma", tabla.configuracionPluma.ifBlank { "sin dato" }),
                tabla.extensiones?.let { Dato("Extensiones", it.toString()) },
                Dato("Sector", tabla.sector.ifBlank { "sin dato" }),
                Dato("Método entre radios", tabla.interpolacion.etiqueta),
                Dato("Verificada por", if (tabla.verificacion.valida) "${tabla.verificacion.porQuien} ${tabla.verificacion.fecha}" else "sin verificar"),
            ),
            fuente = tabla.fuente.texto(), mensajes = msg, paso = Paso.GRUA,
        )
    }

    fun radioFase(f: Fase, croquis: Croquis): RadioFase {
        if (f.radioMedido != null) return RadioFase(f, f.radioMedido, true, "Radio introducido como medida verificada.")
        val g = croquis.grua ?: return RadioFase(f, null, false, "Coloque la grúa en el croquis o introduzca el radio medido.")
        val pts = rutaFase(f, croquis)
        if (pts.isEmpty()) return RadioFase(f, null, false, "Asigne punto de recogida y de descarga a la fase.")
        val centro = Pt(g.x, g.y)
        val r = pts.maxOf { Geometria.distancia(centro, it) }
        return RadioFase(
            f, r, false,
            "Radio aproximado medido sobre el croquis (${fmt(r)} m). Un croquis no es una medición topográfica: confirme el radio en campo e introdúzcalo como medida verificada.",
        )
    }

    fun rutaFase(f: Fase, croquis: Croquis): List<Pt> {
        val o = croquis.elemento(f.origenId)
        val d = croquis.elemento(f.destinoId)
        return buildList {
            o?.let { add(Pt(it.x, it.y)) }
            addAll(f.recorrido)
            d?.let { add(Pt(it.x, it.y)) }
        }
    }

    private fun evaluarCapacidad(
        i: Int, rf: RadioFase, nombre: String, tabla: TablaCarga?, estTabla: EstadoDato,
        total: Double?, estTotal: EstadoDato, p: Proyecto,
    ): Pair<List<Resultado>, Double?> {
        val out = mutableListOf<Resultado>()
        val idCap = "capacidad_${rf.fase.id}"
        if (tabla == null || tabla.puntos.isEmpty()) {
            out += Resultado(
                idCap, G_GRUA, "Capacidad nominal · $nombre", EstadoDato.NO_VERIFICABLE,
                mensajes = listOf("Sin tabla de carga fiable no se puede determinar la capacidad."), paso = Paso.GRUA,
            )
            return out to null
        }
        if (rf.radio == null) {
            out += Resultado(idCap, G_GRUA, "Capacidad nominal · $nombre", EstadoDato.PENDIENTE, mensajes = listOf("Falta el radio de la fase."), paso = Paso.MANIOBRA)
            return out to null
        }
        val q = ConsultaTabla.capacidad(tabla, rf.radio)
        val estR = if (rf.exacto) EstadoDato.DOCUMENTADO else EstadoDato.REVISAR
        when (q.tipo) {
            TipoConsulta.FUERA_MAYOR -> {
                out += Resultado(
                    idCap, G_GRUA, "Capacidad nominal · $nombre", EstadoDato.peor(estTabla, estR), "Fuera de tabla",
                    veredicto = Veredicto.NO_CUMPLE, mensajes = listOf(q.explicacion, "No ejecute con esta configuración: cambie la posición de la grúa o la configuración."),
                    fuente = tabla.fuente.texto(), paso = Paso.MANIOBRA,
                )
                return out to null
            }
            TipoConsulta.FUERA_MENOR, TipoConsulta.SIN_DATOS, TipoConsulta.RADIO_INVALIDO -> {
                out += Resultado(
                    idCap, G_GRUA, "Capacidad nominal · $nombre", EstadoDato.REVISAR,
                    mensajes = listOf(q.explicacion), fuente = tabla.fuente.texto(), paso = Paso.MANIOBRA,
                )
                return out to null
            }
            TipoConsulta.EN_TABLA -> {}
        }
        val cap = q.capacidad!!
        val estCap = EstadoDato.peor(estTabla, estR)
        val alturaMsgs = mutableListOf<String>()
        var verAltura: Veredicto? = null
        if (rf.fase.alturaGancho != null && q.alturaMax != null) {
            verAltura = if (rf.fase.alturaGancho <= q.alturaMax) Veredicto.CUMPLE else Veredicto.NO_CUMPLE
            alturaMsgs += "Altura de gancho requerida ${fmt(rf.fase.alturaGancho)} m; máxima tabulada ${fmt(q.alturaMax)} m."
        }
        out += Resultado(
            idCap, G_GRUA, "Capacidad nominal · $nombre", estCap, Unidades.kgYt(cap), cap,
            veredicto = verAltura,
            formula = "Capacidad = tabla(radio)",
            datos = listOf(
                Dato("Radio de trabajo", Unidades.m(rf.radio), if (rf.exacto) "Medido" else "Croquis (aprox.)"),
                Dato("Radios tabulados usados", if (q.radioInferior == q.radioSuperior) "${fmt(q.radioInferior)} m" else "${fmt(q.radioInferior)} m – ${fmt(q.radioSuperior)} m"),
            ),
            hipotesis = listOf(q.explicacion, "La capacidad nominal de la tabla no es la capacidad disponible: aplican además las condiciones y limitaciones del fabricante (terreno, nivelación, viento, sector)."),
            fuente = tabla.fuente.texto(), mensajes = alturaMsgs, paso = Paso.MANIOBRA,
        )
        // Utilización
        val idU = "utilizacion_${rf.fase.id}"
        if (total == null) {
            out += Resultado(idU, G_GRUA, "Utilización · $nombre", EstadoDato.PENDIENTE, mensajes = listOf("Falta la masa total suspendida."), paso = Paso.VERIFICACION)
            return out to null
        }
        val u = total / cap * 100.0
        val lim = p.maniobra.limiteUtilizacionEmpresa
        val msg = mutableListOf<String>()
        val ver = when {
            u > 100.0 -> { msg += "La masa suspendida supera la capacidad nominal de la tabla."; Veredicto.NO_CUMPLE }
            lim != null && u > lim -> { msg += "Supera el criterio de la empresa (${fmt(lim, 1)} %)."; Veredicto.NO_CUMPLE }
            else -> Veredicto.CUMPLE
        }
        msg += "Un porcentaje dentro del límite no significa que la maniobra sea segura: verifique terreno, viento, holguras y condiciones del manual."
        out += Resultado(
            idU, G_GRUA, "Utilización de la grúa · $nombre", EstadoDato.peor(estCap, estTotal), Unidades.pct(u), u, ver,
            formula = "U = M / Capacidad × 100",
            datos = listOf(
                Dato("Masa suspendida M", Unidades.kg(total)),
                Dato("Capacidad nominal", Unidades.kg(cap)),
                Dato("Criterio de la empresa", if (lim == null) "No definido (solo límite del fabricante, 100 %)" else Unidades.pct(lim)),
            ),
            fuente = tabla.fuente.texto(), mensajes = msg, paso = Paso.VERIFICACION,
        )
        return out to u
    }

    private fun evaluarAparejos(p: Proyecto, usados: List<Accesorio>, total: Double?, hoy: LocalDate): List<Resultado> {
        val out = mutableListOf<Resultado>()
        val a = p.aparejo
        val eslingas = usados.filter { it.tipo.esEslinga && it.id in a.eslingaIds }
        // Carga sobre eslingas: carga + accesorios que cuelgan bajo las eslingas se simplifican como la masa total (conservador)
        val pesoEslingas = total
        val calc = CalculoEslingas.calcular(a, pesoEslingas, if (p.carga.cdgConocido) p.carga.cdgX else null)
        if (!calc.ok) {
            out += Resultado(
                "eslingas_tension", G_APAREJO, "Tensión en ramales", EstadoDato.PENDIENTE,
                mensajes = listOf(calc.error ?: "Faltan datos"), paso = Paso.MANIOBRA,
            )
        } else {
            out += Resultado(
                "eslingas_tension", G_APAREJO, "Tensión en ramales", EstadoDato.peor(
                    if (a.ramales > 1 && a.metodoAngulo == co.planizaje.core.model.MetodoAngulo.ANGULO_DIRECTO) EstadoDato.REVISAR else EstadoDato.DOCUMENTADO,
                    if (total == null) EstadoDato.PENDIENTE else EstadoDato.DOCUMENTADO,
                ),
                Unidades.kg(calc.tensionRamal), calc.tensionRamal,
                formula = "T = M / (n × cos θ)   ·   factor de ángulo = 1 / cos θ",
                datos = listOf(
                    Dato("Masa sobre las eslingas M", Unidades.kg(pesoEslingas), "Masa total suspendida (conservador)"),
                    Dato("Ramales / portantes n", "${a.ramales} / ${calc.ramalesPortantes}"),
                    Dato("Ángulo respecto a la vertical θ", Unidades.grados(calc.anguloVertical), if (a.metodoAngulo == co.planizaje.core.model.MetodoAngulo.GEOMETRIA) "Calculado con longitud y separación" else "Introducido (estimar en campo)"),
                    Dato("Factor de ángulo", fmt(calc.factorAngulo, 3)),
                    Dato("Enganche", a.enganche.etiqueta),
                    Dato("Carga por eslinga", Unidades.kg(calc.cargaPorEslinga)),
                ),
                hipotesis = calc.hipotesis,
                mensajes = calc.avisos + if (a.ramales > 1 && a.metodoAngulo == co.planizaje.core.model.MetodoAngulo.ANGULO_DIRECTO)
                    listOf("Ángulo introducido directamente: confírmelo midiendo longitud de eslinga y separación de puntos.") else emptyList(),
                paso = Paso.MANIOBRA,
            )
        }
        val necesarias = CalculoEslingas.eslingasNecesarias(a)
        if (eslingas.size < necesarias) {
            out += Resultado(
                "eslingas_numero", G_APAREJO, "Número de eslingas", EstadoDato.PENDIENTE, "${eslingas.size} de $necesarias",
                mensajes = listOf("Seleccione $necesarias eslinga(s) para ${a.ramales} ramal(es) en ${a.enganche.etiqueta.lowercase()}."), paso = Paso.MANIOBRA,
            )
        }
        for (e in eslingas) out += verificarAccesorio(e, calc.cargaPorEslinga, "Carga por eslinga", a.enganche, hoy)
        for (g in usados.filter { it.id in a.grilleteIds }) out += verificarAccesorio(g, calc.tensionRamal, "Tensión de ramal (grillete en cada ramal)", a.enganche, hoy)
        for (g in usados.filter { it.id in a.ganchoIds || it.id in a.otrosIds }) {
            val req = if (g.tipo == TipoAccesorio.OTRO) null else total
            out += verificarAccesorio(g, req, "Masa total bajo el elemento", a.enganche, hoy, otro = g.tipo == TipoAccesorio.OTRO)
        }
        // Puntos de izaje de la carga
        val wllP = p.carga.wllPuntoIzajeKg
        if (calc.ok && calc.tensionRamal != null) {
            out += if (wllP == null) Resultado(
                "puntos_izaje", G_APAREJO, "Puntos de izaje de la carga", EstadoDato.PENDIENTE,
                mensajes = listOf("Registre la capacidad (WLL) documentada de los puntos de izaje de la carga, o indique que se izará con abrazo/canasta."), paso = Paso.CARGA,
            ) else Resultado(
                "puntos_izaje", G_APAREJO, "Puntos de izaje de la carga", EstadoDato.DOCUMENTADO,
                "${Unidades.kg(calc.tensionRamal)} / WLL ${Unidades.kg(wllP)}", calc.tensionRamal,
                if (calc.tensionRamal <= wllP) Veredicto.CUMPLE else Veredicto.NO_CUMPLE,
                formula = "T ramal ≤ WLL del punto de izaje",
                hipotesis = listOf("Se compara la tensión del ramal con el WLL del punto. Si el fabricante limita el ángulo de tiro del punto, debe verificarse aparte."),
                paso = Paso.CARGA,
            )
        }
        return out
    }

    private fun verificarAccesorio(
        acc: Accesorio, requerido: Double?, concepto: String, enganche: co.planizaje.core.model.Enganche,
        hoy: LocalDate, otro: Boolean = false,
    ): Resultado {
        val wll = acc.wll(enganche)
        val probl = Inspeccion.problemas(acc, hoy)
        var est = EstadoDato.DOCUMENTADO
        val msg = probl.toMutableList()
        if (probl.isNotEmpty()) est = EstadoDato.REVISAR
        var ver: Veredicto? = null
        if (otro) {
            est = EstadoDato.peor(est, EstadoDato.REVISAR); msg += "Accesorio genérico: verifique manualmente su posición y la carga que soporta."
        } else if (wll == null) {
            est = EstadoDato.NO_VERIFICABLE; msg += "Sin WLL registrado para esta configuración de uso."
        } else if (requerido == null) {
            est = EstadoDato.peor(est, EstadoDato.PENDIENTE)
        } else {
            ver = if (wll >= requerido) Veredicto.CUMPLE else Veredicto.NO_CUMPLE
        }
        if (acc.estado == co.planizaje.core.model.EstadoInspeccion.NO_APTO || Inspeccion.vencida(acc, hoy)) ver = Veredicto.NO_CUMPLE
        return Resultado(
            "acc_${acc.id}", G_APAREJO, acc.nombreCorto, est,
            "Req. ${Unidades.kg(requerido)} · WLL ${Unidades.kg(wll)}", requerido, ver,
            formula = "$concepto ≤ WLL",
            datos = listOfNotNull(
                Dato("Fabricante / modelo", listOf(acc.fabricante, acc.modelo).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "sin dato" }),
                Dato("WLL usado", Unidades.kg(wll), if (acc.tipo.esEslinga) enganche.etiqueta else "Uso directo"),
                acc.cargaRotura?.let { Dato("Carga de rotura (solo informativa)", Unidades.kg(it), "No se usa como capacidad") },
                Dato("Certificado", acc.numeroCertificado.ifBlank { "sin dato" }),
                Dato("Inspección", "${acc.estado.etiqueta}${if (acc.fechaInspeccion.isNotBlank()) " · " + acc.fechaInspeccion else ""}"),
            ),
            hipotesis = listOf("WLL tomado de la etiqueta/certificado registrado. Nunca se usa la carga de rotura como capacidad."),
            mensajes = msg, paso = Paso.MANIOBRA,
        )
    }

    private fun evaluarApoyos(p: Proyecto, grua: Grua?): Resultado {
        val est = grua?.estabilizadores?.firstOrNull { it.id == p.maniobra.estabilizadoresId }
        val m = p.maniobra
        val f = est?.fuerzaMaxApoyoKn
        if (est == null || f == null) return Resultado(
            "apoyos", G_ENTORNO, "Presión sobre el terreno", EstadoDato.NO_VERIFICABLE,
            mensajes = listOf("Para calcular la presión se necesita la fuerza máxima por apoyo documentada por el fabricante para la configuración de estabilizadores. Sin ese dato no se calcula."),
            paso = Paso.MANIOBRA,
        )
        val a = m.placaAncho
        val b = m.placaLargo
        if (a == null || b == null || a <= 0 || b <= 0) return Resultado(
            "apoyos", G_ENTORNO, "Presión sobre el terreno", EstadoDato.PENDIENTE,
            mensajes = listOf("Indique las dimensiones de la placa de apoyo."), paso = Paso.MANIOBRA,
        )
        val pres = f / (a * b) // kN/m² = kPa
        var estado = if (est.fuente.completa) EstadoDato.DOCUMENTADO else EstadoDato.REVISAR
        val msg = mutableListOf<String>()
        if (!est.fuente.completa) msg += "Identifique la fuente de la fuerza máxima por apoyo."
        var ver: Veredicto? = null
        val adm = m.presionAdmisibleKpa
        if (adm == null) {
            estado = EstadoDato.peor(estado, EstadoDato.PENDIENTE); msg += "Falta la presión admisible del terreno (estudio de suelos o criterio de un profesional competente)."
        } else {
            if (m.fuentePresionAdmisible.isBlank()) { estado = EstadoDato.peor(estado, EstadoDato.REVISAR); msg += "Indique de dónde sale la presión admisible del terreno." }
            ver = if (pres <= adm) Veredicto.CUMPLE else Veredicto.NO_CUMPLE
        }
        return Resultado(
            "apoyos", G_ENTORNO, "Presión sobre el terreno", estado, "${fmt(pres, 1)} kPa", pres, ver,
            formula = "p = F máx. por apoyo / (ancho × largo de placa)",
            datos = listOf(
                Dato("F máx. por apoyo", "${fmt(f, 1)} kN", est.fuente.texto()),
                Dato("Placa", "${fmt(a)} m × ${fmt(b)} m"),
                Dato("Presión admisible", if (adm == null) "sin dato" else "${fmt(adm, 1)} kPa", m.fuentePresionAdmisible),
            ),
            hipotesis = listOf(
                "Se usa la fuerza máxima por apoyo del fabricante (envolvente), no la fuerza real de esta maniobra.",
                "Reparto uniforme bajo la placa, placa suficientemente rígida.",
            ),
            fuente = est.fuente.texto(), mensajes = msg, paso = Paso.MANIOBRA,
        )
    }

    private val TIPOS_OBSTACULO = setOf(
        TipoElemento.EDIFICIO, TipoElemento.MURO, TipoElemento.COLUMNA, TipoElemento.CUBIERTA,
        TipoElemento.ESTRUCTURA_METALICA, TipoElemento.OBSTACULO, TipoElemento.ARBOL, TipoElemento.POSTE,
        TipoElemento.CAMION,
    )

    fun evaluarInterferencias(p: Proyecto): Resultado {
        val cr = p.croquis
        val g = cr.grua
        val fases = p.maniobra.fases
        if (g == null || fases.isEmpty()) return Resultado(
            "interferencias", G_ENTORNO, "Interferencias en planta", EstadoDato.PENDIENTE,
            mensajes = listOf("Coloque la grúa y defina las fases para analizar el recorrido."), paso = Paso.CROQUIS,
        )
        val msgs = mutableListOf<String>()
        var est = EstadoDato.REVISAR
        var ver: Veredicto? = Veredicto.CUMPLE
        val centro = Pt(g.x, g.y)
        for ((i, f) in fases.withIndex()) {
            val nombre = f.nombre.ifBlank { "Fase ${i + 1}" }
            val ruta = rutaFase(f, cr)
            if (ruta.isEmpty()) continue
            for (e in cr.elementos) {
                val nom = e.etiqueta.ifBlank { e.tipo.etiqueta }
                when {
                    e.tipo == TipoElemento.LINEA_ELECTRICA -> {
                        val dCarga = Geometria.distanciaPolilineaElemento(ruta, e)
                        val dPluma = ruta.minOf { Geometria.distanciaPolilineaElemento(listOf(centro, it), e) }
                        val d = minOf(dCarga, dPluma)
                        val min = e.distanciaMinima
                        if (min == null) {
                            est = EstadoDato.peor(est, EstadoDato.PENDIENTE)
                            msgs += "$nombre: $nom a ${fmt(d)} m en planta. Indique la distancia mínima exigida para su tensión."
                        } else if (d < min) {
                            ver = Veredicto.NO_CUMPLE
                            msgs += "$nombre: la pluma o la carga pasan a ${fmt(d)} m en planta de $nom; mínimo exigido ${fmt(min)} m."
                        } else {
                            msgs += "$nombre: $nom a ${fmt(d)} m en planta (mínimo ${fmt(min)} m). Verifique también en altura."
                        }
                    }
                    e.tipo == TipoElemento.ZONA_EXCLUSION -> {
                        if (Geometria.distanciaPolilineaElemento(ruta, e) == 0.0) {
                            ver = Veredicto.NO_CUMPLE
                            msgs += "$nombre: el recorrido de la carga atraviesa la zona de exclusión «$nom»."
                        }
                    }
                    e.tipo in TIPOS_OBSTACULO -> {
                        val d = Geometria.distanciaPolilineaElemento(ruta, e)
                        if (d == 0.0) {
                            val alt = e.alto
                            val hg = f.alturaGancho
                            msgs += if (alt != null && hg != null) {
                                val bajoCarga = hg - (p.carga.alto ?: 0.0)
                                "$nombre: el recorrido pasa sobre $nom (altura ${fmt(alt)} m). Parte inferior de la carga ≈ ${fmt(bajoCarga)} m → holgura aparente ${fmt(bajoCarga - alt)} m. Confírmela en campo."
                            } else "$nombre: el recorrido pasa sobre $nom. Registre alturas para estimar la holgura vertical."
                        }
                    }
                    else -> {}
                }
            }
        }
        if (msgs.isEmpty()) msgs += "No se detectaron cruces en la vista en planta con los elementos dibujados."
        msgs += "Este análisis es solo en planta y depende de lo dibujado. No garantiza que el recorrido esté libre: verifique alturas, holguras y obstáculos en campo."
        return Resultado(
            "interferencias", G_ENTORNO, "Interferencias en planta", est, if (ver == Veredicto.NO_CUMPLE) "Conflictos detectados" else "Sin cruces en planta",
            veredicto = ver,
            formula = "Distancia mínima en planta entre el recorrido de la carga / línea de pluma y cada elemento",
            hipotesis = listOf("Posiciones tomadas del croquis (aproximadas salvo medidas verificadas)."),
            mensajes = msgs, paso = Paso.CROQUIS, bloqueante = false,
        )
    }

    private fun evaluarViento(grua: Grua?): Resultado {
        val v = grua?.vientoMaxMs
        return if (v == null) Resultado(
            "viento", G_ENTORNO, "Límite de viento", EstadoDato.PENDIENTE,
            mensajes = listOf("Registre en la ficha de la grúa la velocidad máxima de viento indicada en su manual."), paso = Paso.GRUA,
        ) else Resultado(
            "viento", G_ENTORNO, "Límite de viento", EstadoDato.DOCUMENTADO, "${fmt(v, 1)} m/s (${fmt(v * 3.6, 0)} km/h)", v,
            mensajes = listOf("Medir el viento antes y durante la maniobra; suspender si se supera. Las cargas con gran superficie expuesta pueden requerir un límite menor."),
            fuente = "Ficha de la grúa", paso = Paso.GRUA,
        )
    }

    // ------------------------------------------------------------------ completitud

    fun faltantes(p: Proyecto, grua: Grua?, tabla: TablaCarga?): List<Faltante> = buildList {
        if (p.nombre.isBlank()) add(Faltante(Paso.INFO, "Nombre del proyecto"))
        if (p.ubicacion.isBlank()) add(Faltante(Paso.INFO, "Ubicación de la obra"))
        if (p.fecha.isBlank()) add(Faltante(Paso.INFO, "Fecha de la maniobra"))
        if (p.elaboradoPor.isBlank()) add(Faltante(Paso.INFO, "Responsable de elaboración"))
        if (p.descripcion.isBlank()) add(Faltante(Paso.INFO, "Descripción del trabajo"))
        if (grua == null) add(Faltante(Paso.GRUA, "Seleccionar la grúa"))
        else if (tabla == null) add(Faltante(Paso.GRUA, "Seleccionar la tabla de carga aplicable"))
        if (p.carga.descripcion.isBlank()) add(Faltante(Paso.CARGA, "Descripción de la carga"))
        if (p.carga.pesoKg == null) add(Faltante(Paso.CARGA, "Peso de la carga"))
        val cr = p.croquis
        if (cr.grua == null) add(Faltante(Paso.CROQUIS, "Colocar la grúa en el croquis"))
        if (cr.elementos.none { it.tipo == TipoElemento.RECOGIDA }) add(Faltante(Paso.CROQUIS, "Marcar el punto de recogida"))
        if (cr.elementos.none { it.tipo == TipoElemento.DESCARGA }) add(Faltante(Paso.CROQUIS, "Marcar el punto de descarga"))
        if (p.maniobra.fases.isEmpty()) add(Faltante(Paso.MANIOBRA, "Crear al menos una fase de la maniobra"))
        p.maniobra.fases.forEachIndexed { i, f ->
            if (f.origenId == null || f.destinoId == null) add(Faltante(Paso.MANIOBRA, "Fase ${i + 1}: asignar recogida y descarga"))
        }
        if (p.aparejo.todosIds.isEmpty()) add(Faltante(Paso.MANIOBRA, "Seleccionar los aparejos"))
        val pr = p.procedimiento
        if (pr.operador.isBlank()) add(Faltante(Paso.MANIOBRA, "Nombre del operador"))
        if (pr.senalero.isBlank()) add(Faltante(Paso.MANIOBRA, "Nombre del señalero / aparejador"))
        if (pr.comunicacion.isBlank()) add(Faltante(Paso.MANIOBRA, "Método de comunicación operador–señalero"))
        if (pr.secuencia.isBlank()) add(Faltante(Paso.MANIOBRA, "Secuencia de la maniobra"))
    }

    fun estadoDocumento(p: Proyecto, res: List<Resultado>, falt: List<Faltante>, huellaActual: String? = null): EstadoDocumento {
        if (res.any { it.veredicto == Veredicto.NO_CUMPLE }) return EstadoDocumento.BORRADOR_NO_CUMPLE
        if (falt.isNotEmpty() || res.any { it.bloquea }) return EstadoDocumento.BORRADOR
        val r = p.revisionHumana
        val vigente = huellaActual == null || r.huellaDatos == huellaActual
        val revisado = vigente && r.confirmaRevision && r.revisadoPor.isNotBlank() && r.fechaRevision.isNotBlank()
        val aprobado = revisado && r.confirmaAprobacion && r.aprobadoPor.isNotBlank() && r.fechaAprobacion.isNotBlank()
        return when {
            aprobado -> EstadoDocumento.APROBACION_REGISTRADA
            revisado -> EstadoDocumento.REVISADO
            else -> EstadoDocumento.LISTO_PARA_REVISION
        }
    }

    /** Elementos del croquis que representan riesgos, para la leyenda o resúmenes. */
    fun elementosDeRiesgo(c: Croquis): List<Elemento> =
        c.elementos.filter { it.tipo == TipoElemento.LINEA_ELECTRICA || it.tipo == TipoElemento.ZONA_EXCLUSION }
}
