package co.planizaje.core.ia

import co.planizaje.core.model.Enganche
import co.planizaje.core.model.Fase
import co.planizaje.core.model.Grua
import co.planizaje.core.model.OrigenPeso
import co.planizaje.core.model.Pendiente
import co.planizaje.core.model.Proyecto
import co.planizaje.core.model.TipoElemento
import co.planizaje.core.util.Unidades

/** Diferencia entre un dato ya guardado y el propuesto. */
data class Conflicto(val clave: Clave, val actual: String, val propuesto: String)

data class ResultadoAplicar<T>(val valor: T, val aplicados: List<Clave>, val omitidos: List<Clave>, val avisos: List<String>)

/**
 * Lleva los campos revisados por el usuario al proyecto (o a la ficha de grúa).
 * - Campos vacíos o «no proporcionado» no tocan nada.
 * - Si el campo ya tenía otro valor, solo se reemplaza cuando el usuario lo decide (no hay sobrescritura silenciosa).
 * - Los textos de tipo «añadir» se agregan al final sin borrar lo anterior.
 * - Los valores aproximados quedan registrados como pendientes de confirmar.
 */
object Aplicador {

    fun pesoKg(valor: String): Double? {
        val n = NumerosTexto.normalizar(valor)
        val m = Regex("(\\d+(?:[.,]\\d+)?)\\s*(kg|kilos?|kilogramos|t|ton|toneladas?|tn)?").find(n) ?: return null
        val v = NumerosTexto.numero(m.groupValues[1]) ?: return null
        val u = m.groupValues[2]
        return if (u.startsWith("t")) v * 1000 else v
    }

    fun numero(valor: String): Double? = Regex("\\d+(?:[.,]\\d+)?").find(valor)?.let { NumerosTexto.numero(it.value) }

    private fun norm(s: String) = NumerosTexto.normalizar(s).replace(Regex("[^a-z0-9,.]"), "")

    /** Valor actual en el proyecto, para comparar. Vacío si no hay dato. */
    fun actual(p: Proyecto, gruas: List<Grua>, c: Clave): String = when (c) {
        Clave.NOMBRE -> p.nombre
        Clave.UBICACION -> p.ubicacion
        Clave.CLIENTE -> p.cliente
        Clave.EMPRESA -> p.empresa
        Clave.DESCRIPCION_TRABAJO -> p.descripcion
        Clave.CARGA_DESCRIPCION -> p.carga.descripcion
        Clave.CARGA_PESO -> p.carga.pesoKg?.let { "${Unidades.fmt(it, 1)} kg" } ?: ""
        Clave.CARGA_LARGO -> Unidades.fmtEditable(p.carga.largo)
        Clave.CARGA_ANCHO -> Unidades.fmtEditable(p.carga.ancho)
        Clave.CARGA_ALTO -> Unidades.fmtEditable(p.carga.alto)
        Clave.CARGA_CDG -> ""
        Clave.CARGA_PUNTOS -> p.carga.numeroPuntosIzaje?.toString() ?: ""
        Clave.CARGA_SUJECION -> p.carga.metodoSujecion
        Clave.CARGA_POS_INICIAL -> p.carga.posicionInicial
        Clave.CARGA_POS_FINAL -> p.carga.posicionFinal
        Clave.ALTURA_RECOGIDA -> Unidades.fmtEditable(p.croquis.elementos.firstOrNull { it.tipo == TipoElemento.RECOGIDA }?.alto)
        Clave.ALTURA_DESCARGA -> Unidades.fmtEditable(p.croquis.elementos.firstOrNull { it.tipo == TipoElemento.DESCARGA }?.alto)
        Clave.RADIO -> Unidades.fmtEditable(p.maniobra.fases.firstOrNull()?.radioMedido)
        Clave.GRUA -> gruas.firstOrNull { it.id == p.gruaId }?.nombreCorto ?: ""
        Clave.CONFIG_EXTENSIONES -> gruas.firstOrNull { it.id == p.gruaId }?.tablas?.firstOrNull { it.id == p.maniobra.tablaId }?.extensiones?.toString() ?: ""
        Clave.APAREJO_RAMALES -> if (p.aparejo.todosIds.isEmpty() && p.aparejo.ramales == 2) "" else p.aparejo.ramales.toString()
        Clave.APAREJO_ENGANCHE -> if (p.aparejo.enganche == Enganche.VERTICAL) "" else p.aparejo.enganche.etiqueta
        Clave.OPERADOR -> p.procedimiento.operador
        Clave.SENALERO -> p.procedimiento.senalero
        Clave.COMUNICACION -> p.procedimiento.comunicacion
        else -> ""
    }

    fun conflictos(p: Proyecto, gruas: List<Grua>, campos: List<CampoExtraido>): List<Conflicto> = campos
        .filter { it.estado != EstadoCampo.NO_PROPORCIONADO && it.valor.isNotBlank() && it.clave.escritura == Escritura.REEMPLAZA }
        .mapNotNull { c ->
            val a = actual(p, gruas, c.clave)
            val iguales = when (c.clave.tipo) {
                TipoValor.PESO -> pesoKg(a) != null && pesoKg(a) == pesoKg(c.valor)
                TipoValor.NUMERO, TipoValor.ENTERO -> numero(a) != null && numero(a) == numero(c.valor)
                TipoValor.OPCION -> c.ref != null && c.ref == p.gruaId
                else -> norm(a) == norm(c.valor)
            }
            if (a.isNotBlank() && !iguales) Conflicto(c.clave, a, c.valor) else null
        }

    private fun anadir(base: String, extra: String): String =
        if (base.isBlank()) extra else if (base.contains(extra.trim(), true)) base else base.trimEnd() + "\n" + extra

    /**
     * @param reemplazar claves en conflicto que el usuario decidió reemplazar. Las demás conservan el valor anterior.
     */
    fun aplicar(
        p0: Proyecto,
        gruas: List<Grua>,
        campos: List<CampoExtraido>,
        reemplazar: Set<Clave>,
        ahora: Long = System.currentTimeMillis(),
    ): ResultadoAplicar<Proyecto> {
        val confl = conflictos(p0, gruas, campos).map { it.clave }.toSet()
        var p = p0
        val aplicados = mutableListOf<Clave>()
        val omitidos = mutableListOf<Clave>()
        val avisos = mutableListOf<String>()
        val pendientes = p.pendientes.toMutableList()

        for (c in campos) {
            if (c.estado == EstadoCampo.NO_PROPORCIONADO || c.valor.isBlank() || c.clave.esDeGrua) continue
            if (c.clave in confl && c.clave !in reemplazar) { omitidos += c.clave; continue }
            val v = c.valor.trim()
            val aprox = c.estado == EstadoCampo.APROXIMADO
            var ok = true
            p = when (c.clave) {
                Clave.NOMBRE -> p.copy(nombre = v)
                Clave.UBICACION -> p.copy(ubicacion = v)
                Clave.CLIENTE -> p.copy(cliente = v)
                Clave.EMPRESA -> p.copy(empresa = v)
                Clave.DESCRIPCION_TRABAJO -> p.copy(descripcion = anadir(p.descripcion, v))
                Clave.CARGA_DESCRIPCION -> p.copy(carga = p.carga.copy(descripcion = v))
                Clave.CARGA_PESO -> {
                    val kg = pesoKg(v)
                    if (kg == null || kg <= 0) { ok = false; avisos += "Peso «$v» no válido."; p } else {
                        val doc = c.ref ?: ""
                        val origen = when {
                            aprox -> OrigenPeso.ESTIMADO
                            doc.contains("báscula") -> OrigenPeso.PESADO
                            doc.startsWith("según") -> OrigenPeso.DOCUMENTADO
                            p.carga.origenPeso.documentado && p.carga.pesoKg == kg -> p.carga.origenPeso
                            else -> OrigenPeso.DESCONOCIDO
                        }
                        val refTxt = if (origen.documentado && doc.isNotBlank() && p.carga.referenciaPeso.isBlank()) "Indicado por el operador: $doc" else p.carga.referenciaPeso
                        p.copy(carga = p.carga.copy(pesoKg = kg, origenPeso = origen, referenciaPeso = refTxt))
                    }
                }
                Clave.CARGA_LARGO -> numero(v)?.let { p.copy(carga = p.carga.copy(largo = it)) } ?: p.also { ok = false }
                Clave.CARGA_ANCHO -> numero(v)?.let { p.copy(carga = p.carga.copy(ancho = it)) } ?: p.also { ok = false }
                Clave.CARGA_ALTO -> numero(v)?.let { p.copy(carga = p.carga.copy(alto = it)) } ?: p.also { ok = false }
                Clave.CARGA_CDG -> p.copy(carga = p.carga.copy(observaciones = anadir(p.carga.observaciones, "Centro de gravedad: $v")))
                Clave.CARGA_PUNTOS -> numero(v)?.toInt()?.let { p.copy(carga = p.carga.copy(numeroPuntosIzaje = it)) } ?: p.also { ok = false }
                Clave.CARGA_SUJECION -> p.copy(carga = p.carga.copy(metodoSujecion = v))
                Clave.CARGA_POS_INICIAL -> p.copy(carga = p.carga.copy(posicionInicial = v))
                Clave.CARGA_POS_FINAL -> p.copy(carga = p.carga.copy(posicionFinal = v))
                Clave.ALTURA_RECOGIDA, Clave.ALTURA_DESCARGA -> {
                    val h = numero(v)
                    val tipo = if (c.clave == Clave.ALTURA_RECOGIDA) TipoElemento.RECOGIDA else TipoElemento.DESCARGA
                    val el = p.croquis.elementos.firstOrNull { it.tipo == tipo }
                    if (h == null) { ok = false; p }
                    else if (el != null) p.copy(croquis = p.croquis.copy(elementos = p.croquis.elementos.map { if (it.id == el.id) it.copy(alto = h) else it }))
                    else {
                        avisos += "${c.clave.etiqueta} (${Unidades.fmt(h, 2)} m) guardada en las notas: marca el punto en el croquis para asociarla."
                        p.copy(maniobra = p.maniobra.copy(notasEmplazamiento = anadir(p.maniobra.notasEmplazamiento, "${c.clave.etiqueta}: ${Unidades.fmt(h, 2)} m${if (aprox) " (aproximada)" else ""}")))
                    }
                }
                Clave.RADIO -> {
                    val r = numero(v)
                    if (r == null) { ok = false; p }
                    else if (aprox) {
                        // Un radio aproximado nunca se registra como medida verificada.
                        p.copy(maniobra = p.maniobra.copy(notasEmplazamiento = anadir(p.maniobra.notasEmplazamiento, "Radio aproximado indicado: ${Unidades.fmt(r, 2)} m (medir en campo)")))
                    } else {
                        val fases = p.maniobra.fases.ifEmpty {
                            listOf(Fase(
                                id = "f-" + ahora.toString(36),
                                origenId = p.croquis.elementos.firstOrNull { it.tipo == TipoElemento.RECOGIDA }?.id,
                                destinoId = p.croquis.elementos.firstOrNull { it.tipo == TipoElemento.DESCARGA }?.id,
                            ))
                        }
                        if (p.maniobra.fases.size > 1) avisos += "El radio se aplicó a la fase 1. Revisa las demás fases."
                        p.copy(maniobra = p.maniobra.copy(fases = fases.mapIndexed { i, f -> if (i == 0) f.copy(radioMedido = r) else f }))
                    }
                }
                Clave.DISTANCIA_REFERENCIA, Clave.POSICION_GRUA, Clave.ESTABILIZADORES -> {
                    val pref = when (c.clave) { Clave.POSICION_GRUA -> "Posición de la grúa: "; Clave.DISTANCIA_REFERENCIA -> "Distancia indicada: "; else -> "" }
                    p.copy(maniobra = p.maniobra.copy(notasEmplazamiento = anadir(p.maniobra.notasEmplazamiento, pref + v)))
                }
                Clave.OBSTACULOS -> {
                    val txt = NumerosTexto.normalizar(v)
                    val marcas = mapOf(
                        "electric" to "Líneas eléctricas cercanas", "personas" to "Carga suspendida sobre personas",
                        "trafico" to "Tráfico vehicular o peatonal", "terreno" to "Terreno inestable o de baja capacidad",
                        "viento" to "Viento", "arbol" to "Obstáculos en el recorrido", "poste" to "Obstáculos en el recorrido",
                        "muro" to "Obstáculos en el recorrido", "techo" to "Obstáculos en el recorrido", "espacio" to "Obstáculos en el recorrido",
                    ).filterKeys { txt.contains(it) }.values.toSet()
                    p.copy(
                        croquis = p.croquis.copy(nota = anadir(p.croquis.nota, "Entorno indicado por el operador: $v")),
                        procedimiento = p.procedimiento.copy(peligros = p.procedimiento.peligros.map { if (it.nombre in marcas) it.copy(aplica = true) else it }),
                    )
                }
                Clave.GRUA -> {
                    val g = gruas.firstOrNull { it.id == c.ref }
                    if (g == null) { ok = false; avisos += "La grúa «$v» no está en la biblioteca."; p }
                    else p.copy(gruaId = g.id, maniobra = p.maniobra.copy(
                        tablaId = if (p.gruaId == g.id) p.maniobra.tablaId else g.tablas.singleOrNull()?.id,
                        estabilizadoresId = if (p.gruaId == g.id) p.maniobra.estabilizadoresId else g.estabilizadores.singleOrNull()?.id,
                    ))
                }
                Clave.CONFIG_EXTENSIONES -> {
                    val n = numero(v)?.toInt()
                    val gid = campos.firstOrNull { it.clave == Clave.GRUA && it.estado != EstadoCampo.NO_PROPORCIONADO }?.ref?.takeIf { Clave.GRUA !in confl || Clave.GRUA in reemplazar } ?: p.gruaId
                    val g = gruas.firstOrNull { it.id == gid }
                    val tablas = g?.tablas?.filter { it.extensiones == n && !it.identificacionPendiente } ?: emptyList()
                    when {
                        g == null -> { avisos += "Selecciona la grúa para elegir la tabla de $n extensiones."; ok = false; p }
                        tablas.size == 1 -> p.copy(maniobra = p.maniobra.copy(tablaId = tablas[0].id))
                        tablas.isEmpty() -> { avisos += "La ficha de ${g.nombreCorto} no tiene una tabla para $n extensiones. Agrégala en «Mis grúas»."; ok = false; p }
                        else -> { avisos += "Hay varias tablas de $n extensiones en ${g.nombreCorto}: elige la correcta en el paso 2."; ok = false; p }
                    }
                }
                Clave.APAREJO_RAMALES -> numero(v)?.toInt()?.takeIf { it in 1..4 }?.let { p.copy(aparejo = p.aparejo.copy(ramales = it)) } ?: p.also { ok = false }
                Clave.APAREJO_ENGANCHE -> {
                    val e = when { v.contains("canast", true) || v.contains("cesta", true) -> Enganche.CANASTA; v.contains("ahorc", true) || v.contains("estrang", true) -> Enganche.AHORCADO; else -> Enganche.VERTICAL }
                    p.copy(aparejo = p.aparejo.copy(enganche = e))
                }
                Clave.APAREJO_DESCRIPCION -> p.copy(aparejo = p.aparejo.copy(notas = anadir(p.aparejo.notas, v)))
                Clave.OPERADOR -> p.copy(procedimiento = p.procedimiento.copy(operador = v))
                Clave.SENALERO -> p.copy(procedimiento = p.procedimiento.copy(senalero = v))
                Clave.COMUNICACION -> p.copy(procedimiento = p.procedimiento.copy(comunicacion = v))
                Clave.OBSERVACIONES -> p.copy(observaciones = anadir(p.observaciones, v))
                Clave.PENDIENTES -> p
                else -> p
            }
            if (!ok) { omitidos += c.clave; continue }
            aplicados += c.clave
            pendientes.removeAll { it.clave == c.clave.name && c.clave != Clave.PENDIENTES }
            if (aprox || c.clave == Clave.PENDIENTES || c.clave == Clave.DISTANCIA_REFERENCIA) {
                pendientes += Pendiente(c.clave.name, c.clave.etiqueta, v, c.nota, c.origen, ahora)
            }
        }
        p = p.copy(pendientes = pendientes.distinctBy { it.clave + "|" + it.valor })
        return ResultadoAplicar(p, aplicados, omitidos, avisos)
    }

    // ------------------------------------------------------------------ ficha de grúa

    fun actualGrua(g: Grua, c: Clave): String = when (c) {
        Clave.GRUA_FABRICANTE -> g.fabricante
        Clave.GRUA_MODELO -> g.modelo
        Clave.GRUA_VARIANTE -> g.variante
        Clave.GRUA_SERIE -> g.numeroSerie
        Clave.GRUA_PLACA -> g.placaVehiculo
        Clave.GRUA_VEHICULO -> g.vehiculo
        Clave.GRUA_EXTENSIONES -> g.extensionesHidraulicas?.toString() ?: ""
        Clave.GRUA_EXT_MANUALES -> g.extensionesManuales?.toString() ?: ""
        else -> ""
    }

    fun conflictosGrua(g: Grua, campos: List<CampoExtraido>): List<Conflicto> = campos
        .filter { it.clave.esDeGrua && it.estado != EstadoCampo.NO_PROPORCIONADO && it.valor.isNotBlank() }
        .mapNotNull { c -> val a = actualGrua(g, c.clave); if (a.isNotBlank() && norm(a) != norm(c.valor)) Conflicto(c.clave, a, c.valor) else null }

    fun aplicarGrua(g0: Grua, campos: List<CampoExtraido>, reemplazar: Set<Clave>): ResultadoAplicar<Grua> {
        val confl = conflictosGrua(g0, campos).map { it.clave }.toSet()
        var g = g0
        val ap = mutableListOf<Clave>(); val om = mutableListOf<Clave>()
        for (c in campos) {
            if (!c.clave.esDeGrua || c.estado == EstadoCampo.NO_PROPORCIONADO || c.valor.isBlank()) continue
            if (c.clave in confl && c.clave !in reemplazar) { om += c.clave; continue }
            val v = c.valor.trim()
            g = when (c.clave) {
                Clave.GRUA_FABRICANTE -> g.copy(fabricante = v)
                Clave.GRUA_MODELO -> g.copy(modelo = v)
                Clave.GRUA_VARIANTE -> g.copy(variante = v)
                Clave.GRUA_SERIE -> g.copy(numeroSerie = v)
                Clave.GRUA_PLACA -> g.copy(placaVehiculo = v)
                Clave.GRUA_VEHICULO -> g.copy(vehiculo = v)
                Clave.GRUA_EXTENSIONES -> g.copy(extensionesHidraulicas = numero(v)?.toInt() ?: g.extensionesHidraulicas)
                Clave.GRUA_EXT_MANUALES -> g.copy(extensionesManuales = numero(v)?.toInt() ?: g.extensionesManuales)
                else -> g
            }
            ap += c.clave
        }
        return ResultadoAplicar(g, ap, om, emptyList())
    }
}
