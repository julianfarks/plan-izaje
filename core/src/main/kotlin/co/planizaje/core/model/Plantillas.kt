package co.planizaje.core.model

/**
 * Textos de partida editables para el procedimiento. Son buenas prácticas generales,
 * NO datos técnicos de ningún fabricante. El usuario debe revisarlos y adaptarlos.
 */
object Plantillas {

    const val CRITERIOS_SUSPENSION = """- Viento por encima del límite indicado en el manual de la grúa o cambio brusco de las condiciones meteorológicas.
- Tormenta eléctrica, lluvia intensa o visibilidad insuficiente para el operador o el señalero.
- Pérdida de comunicación entre operador y señalero.
- Hundimiento, asentamiento o deformación del terreno o de los apoyos.
- Activación del limitador de momento o de cualquier alarma de la grúa.
- Ingreso de personas no autorizadas a la zona delimitada.
- Cualquier condición distinta de la planificada (peso, radio, obstáculos, aparejos)."""

    fun peligros(): List<Peligro> = listOf(
        Peligro("Líneas eléctricas cercanas", "Identificar tensión con el operador de red, respetar la distancia mínima exigida por la norma aplicable, señalero dedicado y, si es posible, desenergizar."),
        Peligro("Carga suspendida sobre personas", "Delimitar el área bajo el recorrido, prohibir el paso, usar cuerdas guía para controlar la carga."),
        Peligro("Terreno inestable o de baja capacidad", "Verificar el terreno, usar placas de reparto adecuadas y comprobar los apoyos antes y durante la maniobra."),
        Peligro("Viento", "Medir el viento antes y durante la maniobra; suspender si supera el límite del fabricante. Considerar el área expuesta de la carga."),
        Peligro("Tráfico vehicular o peatonal", "Plan de manejo de tráfico, conos, vallas y paletero o auxiliar de tráfico."),
        Peligro("Obstáculos en el recorrido", "Verificar holguras en campo; mantener distancia con estructuras, árboles y otros equipos."),
        Peligro("Atrapamiento y golpes", "Mantener manos fuera de puntos de pellizco, usar cuerdas guía, nadie entre la carga y estructuras fijas."),
        Peligro("Falla de aparejos", "Inspección preuso de eslingas, grilletes y ganchos; retirar elementos dañados o sin identificación."),
        Peligro("Trabajo en altura en la recepción", "Personal con protección contra caídas y punto de anclaje certificado."),
    )

    fun checklist(): List<ItemCheck> = listOf(
        "Grúa con inspección preoperacional realizada y sin fallas",
        "Operador habilitado y con certificación vigente",
        "Señalero / aparejador designado y presente",
        "Diagrama de carga correcto para la configuración disponible en cabina",
        "Limitador de momento / dispositivos de seguridad operativos",
        "Terreno verificado y estabilizadores extendidos según el plan",
        "Placas de apoyo colocadas y niveladas",
        "Peso de la carga confirmado",
        "Aparejos inspeccionados, identificados y con WLL suficiente",
        "Área delimitada y señalizada",
        "Distancia a líneas eléctricas verificada",
        "Condiciones meteorológicas dentro de los límites",
        "Charla previa realizada con todo el personal",
        "Comunicación (radio / señales) probada",
    ).map { ItemCheck(it) }

    fun secuenciaDesdeFases(fases: List<Fase>, croquis: Croquis): String {
        if (fases.isEmpty()) return ""
        return fases.mapIndexed { i, f ->
            val o = croquis.elemento(f.origenId)?.let { it.etiqueta.ifBlank { it.tipo.etiqueta } } ?: "origen sin definir"
            val d = croquis.elemento(f.destinoId)?.let { it.etiqueta.ifBlank { it.tipo.etiqueta } } ?: "destino sin definir"
            "${i + 1}. ${f.nombre.ifBlank { "Fase ${i + 1}" }}: trasladar la carga desde $o hasta $d." +
                (if (f.descripcion.isNotBlank()) " ${f.descripcion}" else "")
        }.joinToString("\n")
    }
}
