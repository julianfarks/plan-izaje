package co.planizaje.core.ia

import co.planizaje.core.calc.EstadoDato
import co.planizaje.core.calc.Evaluacion
import co.planizaje.core.calc.Veredicto
import co.planizaje.core.model.Grua
import co.planizaje.core.model.Paso
import co.planizaje.core.model.Proyecto
import co.planizaje.core.util.Unidades

/** Acción concreta que lleva al usuario al campo que debe completar. */
data class Accion(val texto: String, val paso: Paso, val campo: String?, val urgente: Boolean = false)

data class Resumen(val tenemos: List<String>, val acciones: List<Accion>) {
    /** Frase corta en lenguaje natural. */
    fun frase(): String {
        val t = if (tenemos.isEmpty()) "Aún no hay datos del izaje." else "Ya tenemos ${unir(tenemos)}."
        val f = if (acciones.isEmpty()) " No falta nada para presentarlo a revisión." else " Falta ${unir(acciones.take(4).map { it.texto.replaceFirstChar { c -> c.lowercase() } })}${if (acciones.size > 4) " y ${acciones.size - 4} punto(s) más" else ""}."
        return t + f
    }

    private fun unir(l: List<String>) = when (l.size) {
        0 -> ""
        1 -> l[0]
        else -> l.dropLast(1).joinToString(", ") + " y " + l.last()
    }
}

/**
 * Asistente de datos faltantes. Trabaja solo con los datos reales del proyecto actual y su evaluación
 * (no recuerda otros trabajos ni mezcla grúas). Es determinista: no genera texto libre.
 */
object Asistente {

    fun resumen(p: Proyecto, grua: Grua?, ev: Evaluacion, paso: Paso? = null): Resumen {
        val tenemos = mutableListOf<String>()
        val c = p.carga
        if (c.descripcion.isNotBlank()) tenemos += "la descripción de la carga"
        if (c.pesoKg != null) tenemos += if (c.origenPeso.documentado) "el peso documentado (${Unidades.kg(c.pesoKg)})" else "el peso ${if (c.origenPeso.name == "ESTIMADO") "aproximado" else "indicado"} (${Unidades.kg(c.pesoKg)})"
        if (grua != null) tenemos += "la grúa ${grua.nombreCorto}"
        if (p.croquis.elementos.isNotEmpty()) tenemos += "el croquis"
        if (p.maniobra.fases.isNotEmpty()) tenemos += "${p.maniobra.fases.size} fase(s) de maniobra"
        if (p.aparejo.todosIds.isNotEmpty()) tenemos += "los aparejos"

        val acc = mutableListOf<Accion>()
        // Lo que más pesa en la seguridad, primero
        ev.resultados.filter { it.veredicto == Veredicto.NO_CUMPLE }.forEach { r ->
            acc += Accion("Resolver: ${r.titulo} excede el límite", r.paso, r.campo, urgente = true)
        }
        if (c.pesoKg != null && !c.origenPeso.documentado) acc += Accion("Confirmar el peso con placa, plano o báscula", Paso.CARGA, "carga.origen")
        ev.faltantes.forEach { acc += Accion(it.texto, it.paso, it.campo) }
        ev.resultados.filter { it.id.startsWith("radio_") && it.estado != EstadoDato.DOCUMENTADO && it.valorNumerico != null }.forEach {
            acc += Accion("Medir el radio real de trabajo (${it.titulo.substringAfter("· ")})", Paso.MANIOBRA, "fase.radio")
        }
        ev.resultados.firstOrNull { it.id == "tabla" && it.estado != EstadoDato.DOCUMENTADO && grua != null }?.let {
            acc += Accion(
                when {
                    grua!!.tablas.isEmpty() -> "Cargar la tabla de capacidad de ${grua.nombreCorto}"
                    it.estado == EstadoDato.NO_VERIFICABLE -> "Identificar la tabla de capacidad de la configuración seleccionada"
                    else -> "Comprobar la tabla de capacidad de la configuración seleccionada"
                },
                Paso.GRUA, "tabla",
            )
        }
        p.pendientes.forEach { pend ->
            val k = Clave.entries.firstOrNull { it.name == pend.clave }
            acc += Accion("Confirmar ${pend.etiqueta.lowercase()}: ${pend.valor.take(60)}", k?.paso ?: Paso.VERIFICACION, k?.campo)
        }
        ev.resultados.filter { it.bloquea && it.veredicto != Veredicto.NO_CUMPLE && !it.id.startsWith("radio_") && it.id != "tabla" && it.id != "peso_carga" && it.id != "masa_total" }
            .forEach { r -> acc += Accion("${r.titulo}: ${r.mensajes.firstOrNull()?.take(70) ?: r.estado.etiqueta}", r.paso, r.campo) }

        val unicos = acc.distinctBy { it.texto }
        val ordenados = if (paso == null) unicos else unicos.sortedBy { if (it.paso == paso) 0 else 1 }
        return Resumen(tenemos, ordenados.sortedByDescending { it.urgente })
    }
}
