package co.planizaje.app.ui.asistente

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.planizaje.app.data.nuevoId
import co.planizaje.app.ui.Rutas
import co.planizaje.app.ui.comp.Aviso
import co.planizaje.app.ui.comp.Ayuda
import co.planizaje.app.ui.comp.CampoNumero
import co.planizaje.app.ui.comp.CampoTexto
import co.planizaje.app.ui.comp.FilaDato
import co.planizaje.app.ui.comp.Seccion
import co.planizaje.app.ui.comp.Selector
import co.planizaje.app.ui.theme.Paleta
import co.planizaje.core.calc.Aptitud
import co.planizaje.core.calc.CalculoEslingas
import co.planizaje.core.calc.Candidatos
import co.planizaje.core.calc.Motor
import co.planizaje.core.model.Accesorio
import co.planizaje.core.model.Aparejo
import co.planizaje.core.model.Enganche
import co.planizaje.core.model.Fase
import co.planizaje.core.model.ItemCheck
import co.planizaje.core.model.MetodoAngulo
import co.planizaje.core.model.Plantillas
import co.planizaje.core.model.Procedimiento
import co.planizaje.core.model.TipoAccesorio
import co.planizaje.core.model.TipoElemento
import co.planizaje.core.util.Unidades
import java.time.LocalDate

@Composable
fun PasoManiobra(c: Ctx) {
    Ayuda("Define las fases del movimiento, los aparejos y el procedimiento. Cada sección se puede completar por separado.")
    SeccionFases(c)
    SeccionAparejo(c)
    SeccionCriterios(c)
    SeccionProcedimiento(c)
}

@Composable
private fun SeccionFases(c: Ctx) {
    val cr = c.p.croquis
    val puntos = cr.elementos.filter { it.tipo == TipoElemento.RECOGIDA || it.tipo == TipoElemento.DESCARGA || it.tipo == TipoElemento.CARGA }
    fun upd(desc: String, f: (List<Fase>) -> List<Fase>) = c.actualizar(desc) { it.copy(maniobra = it.maniobra.copy(fases = f(it.maniobra.fases))) }

    Seccion("Fases del izaje", icono = Icons.Filled.Route, subtitulo = "Recogida → descarga, con radio y altura") {
        if (puntos.isEmpty()) Aviso("Primero marca en el croquis los puntos de recogida y descarga.")
        c.p.maniobra.fases.forEachIndexed { i, f ->
            val rf = Motor.radioFase(f, cr)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Fase ${i + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { upd("Fase eliminada") { l -> l.filter { it.id != f.id } } }) { Icon(Icons.Filled.Delete, "Eliminar fase") }
                    }
                    CampoTexto(f.nombre, { v -> upd("Fases") { l -> l.map { if (it.id == f.id) it.copy(nombre = v) else it } } }, "Nombre de la fase", marcador = "Ej. Del camión a la cubierta")
                    Selector("Desde (recogida)", puntos, cr.elemento(f.origenId), { it.etiqueta.ifBlank { it.tipo.etiqueta } },
                        { e -> upd("Fases") { l -> l.map { if (it.id == f.id) it.copy(origenId = e.id) else it } } })
                    Selector("Hasta (descarga)", puntos, cr.elemento(f.destinoId), { it.etiqueta.ifBlank { it.tipo.etiqueta } },
                        { e -> upd("Fases") { l -> l.map { if (it.id == f.id) it.copy(destinoId = e.id) else it } } })
                    if (rf.radio != null && !rf.exacto) FilaDato("Radio aproximado del croquis", "≈ ${Unidades.m(rf.radio)}")
                    CampoNumero(
                        f.radioMedido, { v -> upd("Radio medido") { l -> l.map { if (it.id == f.id) it.copy(radioMedido = v) else it } } },
                        "Radio verificado en campo", "m",
                        ayuda = "Distancia horizontal del eje de giro al gancho en el punto más alejado. Si lo dejas vacío se usa el del croquis (requiere revisión).",
                    )
                    CampoNumero(
                        f.alturaGancho, { v -> upd("Altura de gancho") { l -> l.map { if (it.id == f.id) it.copy(alturaGancho = v) else it } } },
                        "Altura de gancho necesaria", "m",
                    )
                    CampoTexto(f.descripcion, { v -> upd("Fases") { l -> l.map { if (it.id == f.id) it.copy(descripcion = v) else it } } }, "Descripción / indicaciones", multilinea = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { c.nav.navigate(Rutas.croquis(c.p.id)) }) { Text("Dibujar recorrido") }
                        if (f.recorrido.isNotEmpty()) TextButton(onClick = { upd("Recorrido borrado") { l -> l.map { if (it.id == f.id) it.copy(recorrido = emptyList()) else it } } }) {
                            Text("Recto (${f.recorrido.size} puntos de paso)")
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = {
            val r = cr.elementos.firstOrNull { it.tipo == TipoElemento.RECOGIDA }
            val d = cr.elementos.firstOrNull { it.tipo == TipoElemento.DESCARGA }
            upd("Fase añadida") { it + Fase(nuevoId(), "", r?.id, d?.id) }
        }) { Icon(Icons.Filled.Add, null); Text(" Añadir fase") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeccionAparejo(c: Ctx) {
    val a = c.p.aparejo
    val hoy = remember { LocalDate.now() }
    fun upd(desc: String, f: (Aparejo) -> Aparejo) = c.actualizar(desc) { it.copy(aparejo = f(it.aparejo)) }
    val calc = CalculoEslingas.calcular(a, c.ev.masaTotal ?: c.p.carga.pesoKg, if (c.p.carga.cdgConocido) c.p.carga.cdgX else null)

    Seccion("Aparejos", icono = Icons.Filled.Link, subtitulo = "Configuración de eslingado y accesorios") {
        Text("Número de ramales", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            (1..4).forEach { n ->
                SegmentedButton(a.ramales == n, { upd("Aparejo") { it.copy(ramales = n) } }, SegmentedButtonDefaults.itemShape(n - 1, 4)) { Text("$n") }
            }
        }
        Selector("Tipo de enganche", Enganche.entries, a.enganche, { it.etiqueta }, { e -> upd("Aparejo") { it.copy(enganche = e) } })
        if (a.ramales > 1) {
            Text("Ángulo de los ramales", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(a.metodoAngulo == MetodoAngulo.GEOMETRIA, { upd("Aparejo") { it.copy(metodoAngulo = MetodoAngulo.GEOMETRIA) } }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Por medidas") }
                SegmentedButton(a.metodoAngulo == MetodoAngulo.ANGULO_DIRECTO, { upd("Aparejo") { it.copy(metodoAngulo = MetodoAngulo.ANGULO_DIRECTO) } }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Ángulo directo") }
            }
            if (a.metodoAngulo == MetodoAngulo.GEOMETRIA) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoNumero(a.longitudRamal, { v -> upd("Aparejo") { it.copy(longitudRamal = v) } }, "Largo de cada ramal", "m", Modifier.weight(1f))
                    CampoNumero(a.separacionPuntos, { v -> upd("Aparejo") { it.copy(separacionPuntos = v) } }, "Separación puntos", "m", Modifier.weight(1f))
                }
                Ayuda("Separación = distancia entre dos puntos de izaje opuestos. El ángulo se calcula respecto a la vertical.")
            } else {
                CampoNumero(a.anguloVertical, { v -> upd("Aparejo") { it.copy(anguloVertical = v) } }, "Ángulo respecto a la vertical", "°", max = 89.0,
                    ayuda = "0° = ramal vertical. A 60° cada ramal soporta el doble.")
            }
            if (a.ramales >= 3) {
                CampoNumero(a.ramalesPortantes?.toDouble(), { v -> upd("Aparejo") { it.copy(ramalesPortantes = v?.toInt()) } }, "Ramales que soportan la carga", "", entero = true, max = a.ramales.toDouble(),
                    ayuda = "Vacío = 2 (conservador). Pon más solo si el reparto está garantizado (balancín, ecualizador).")
            }
        }
        if (calc.ok) {
            FilaDato("Ángulo respecto a la vertical", Unidades.grados(calc.anguloVertical))
            FilaDato("Factor de ángulo (1/cos θ)", Unidades.fmt(calc.factorAngulo, 3))
            FilaDato("Tensión máxima por ramal", Unidades.kg(calc.tensionRamal))
            FilaDato("Carga que debe soportar cada eslinga", Unidades.kg(calc.cargaPorEslinga))
            calc.avisos.forEach { Aviso(it, Paleta.Mal, Paleta.MalFondo, "✕") }
        } else Aviso(calc.error ?: "Faltan datos", Paleta.Pendiente, Paleta.PendienteFondo, "…")

        HorizontalDivider()
        val longitud = if (a.metodoAngulo == MetodoAngulo.GEOMETRIA) a.longitudRamal else null
        ListaCandidatos(
            "Eslingas (${CalculoEslingas.eslingasNecesarias(a)} necesarias)",
            Candidatos.clasificar(c.accesorios, { it.tipo.esEslinga }, calc.cargaPorEslinga, a.enganche, hoy, longitud),
            a.eslingaIds,
        ) { ids -> upd("Eslingas seleccionadas") { it.copy(eslingaIds = ids) } }
        ListaCandidatos(
            "Grilletes (uno por ramal)",
            Candidatos.clasificar(c.accesorios, { it.tipo == TipoAccesorio.GRILLETE }, calc.tensionRamal, Enganche.VERTICAL, hoy),
            a.grilleteIds,
        ) { ids -> upd("Grilletes seleccionados") { it.copy(grilleteIds = ids) } }
        ListaCandidatos(
            "Gancho / eslabón maestro",
            Candidatos.clasificar(c.accesorios, { it.tipo == TipoAccesorio.GANCHO || it.tipo == TipoAccesorio.ESLABON_MAESTRO }, c.ev.masaTotal, Enganche.VERTICAL, hoy),
            a.ganchoIds,
        ) { ids -> upd("Ganchos seleccionados") { it.copy(ganchoIds = ids) } }
        ListaCandidatos(
            "Balancines, vigas y otros",
            Candidatos.clasificar(c.accesorios, { it.tipo == TipoAccesorio.BALANCIN || it.tipo == TipoAccesorio.VIGA_IZAJE || it.tipo == TipoAccesorio.OTRO }, c.ev.masaTotal, Enganche.VERTICAL, hoy),
            a.otrosIds,
        ) { ids -> upd("Otros accesorios") { it.copy(otrosIds = ids) } }
        if (c.accesorios.isEmpty()) Aviso("Tu biblioteca de accesorios está vacía. Registra tus eslingas y grilletes con su WLL y certificado.", Paleta.Pendiente, Paleta.PendienteFondo, "…")
        OutlinedButton(onClick = { c.nav.navigate(Rutas.ACCESORIOS) }) { Text("Abrir Mis accesorios") }
        CampoTexto(a.notas, { v -> upd("Notas de aparejo") { it.copy(notas = v) } }, "Notas del aparejo", multilinea = true)
    }
}

@Composable
private fun ListaCandidatos(titulo: String, lista: List<co.planizaje.core.calc.Candidato>, seleccion: List<String>, onCambio: (List<String>) -> Unit) {
    Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    if (lista.isEmpty()) { Text("Sin elementos de este tipo en la biblioteca.", style = MaterialTheme.typography.bodySmall); return }
    lista.forEach { cand ->
        val acc: Accesorio = cand.accesorio
        val marcado = acc.id in seleccion
        val (col, simb) = when (cand.aptitud) {
            Aptitud.APTO -> Paleta.Ok to "✓"
            Aptitud.FALTA_DATO -> Paleta.Revisar to "…"
            Aptitud.NO_APTO -> Paleta.Mal to "✕"
        }
        Row(
            Modifier.fillMaxWidth().clickable { onCambio(if (marcado) seleccion - acc.id else seleccion + acc.id) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(marcado, { onCambio(if (marcado) seleccion - acc.id else seleccion + acc.id) })
            Column(Modifier.weight(1f)) {
                Text(acc.nombreCorto, style = MaterialTheme.typography.bodyLarge)
                Text("$simb ${cand.aptitud.etiqueta} · WLL ${Unidades.kg(cand.wll)}" + (acc.longitud?.let { " · ${Unidades.m(it)}" } ?: ""), color = col, style = MaterialTheme.typography.bodySmall)
                cand.motivos.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun SeccionCriterios(c: Ctx) {
    val m = c.p.maniobra
    fun upd(desc: String, f: (co.planizaje.core.model.Maniobra) -> co.planizaje.core.model.Maniobra) = c.actualizar(desc) { it.copy(maniobra = f(it.maniobra)) }
    Seccion("Criterios y apoyos", icono = Icons.Filled.Rule, desplegable = true, inicialAbierta = false, subtitulo = "Configuración avanzada") {
        CampoNumero(m.limiteUtilizacionEmpresa, { v -> upd("Criterio de utilización") { it.copy(limiteUtilizacionEmpresa = v) } }, "Límite de utilización de la empresa", "%", max = 100.0,
            ayuda = "Opcional. Si tu empresa exige, por ejemplo, no pasar del 75 % de la tabla. Vacío = solo el 100 % del fabricante.")
        Text("Placa de apoyo bajo estabilizadores", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoNumero(m.placaAncho, { v -> upd("Placa de apoyo") { it.copy(placaAncho = v) } }, "Ancho", "m", Modifier.weight(1f))
            CampoNumero(m.placaLargo, { v -> upd("Placa de apoyo") { it.copy(placaLargo = v) } }, "Largo", "m", Modifier.weight(1f))
        }
        CampoNumero(m.presionAdmisibleKpa, { v -> upd("Presión admisible") { it.copy(presionAdmisibleKpa = v) } }, "Presión admisible del terreno", "kPa",
            ayuda = "De un estudio de suelos o de un profesional competente. 100 kPa ≈ 1 kg/cm².")
        CampoTexto(m.fuentePresionAdmisible, { v -> upd("Presión admisible") { it.copy(fuentePresionAdmisible = v) } }, "Fuente de la presión admisible")
        Ayuda("La presión solo se calcula si la ficha de la grúa tiene la fuerza máxima por apoyo documentada por el fabricante.")
    }
}

@Composable
private fun SeccionProcedimiento(c: Ctx) {
    val pr = c.p.procedimiento
    fun upd(desc: String, f: (Procedimiento) -> Procedimiento) = c.actualizar(desc) { it.copy(procedimiento = f(it.procedimiento)) }
    var nuevoItem by remember { mutableStateOf("") }

    Seccion("Responsables y comunicación", icono = Icons.Filled.Groups) {
        CampoTexto(pr.operador, { v -> upd("Responsables") { it.copy(operador = v) } }, "Operador de la grúa", obligatorio = true)
        CampoTexto(pr.senalero, { v -> upd("Responsables") { it.copy(senalero = v) } }, "Señalero / rigger", obligatorio = true)
        CampoTexto(pr.aparejador, { v -> upd("Responsables") { it.copy(aparejador = v) } }, "Aparejador")
        CampoTexto(pr.supervisor, { v -> upd("Responsables") { it.copy(supervisor = v) } }, "Supervisor / coordinador")
        CampoTexto(pr.otrosResponsables, { v -> upd("Responsables") { it.copy(otrosResponsables = v) } }, "Otros (auxiliar de tráfico, vigía…)", multilinea = true)
        CampoTexto(pr.comunicacion, { v -> upd("Comunicación") { it.copy(comunicacion = v) } }, "Comunicación operador–señalero", obligatorio = true,
            marcador = "Ej. Radio canal 3 + señales manuales normalizadas", multilinea = true)
    }

    Seccion("Secuencia y área de trabajo", icono = Icons.Filled.Route, desplegable = true, inicialAbierta = pr.secuencia.isBlank()) {
        OutlinedButton(onClick = { upd("Secuencia") { it.copy(secuencia = Plantillas.secuenciaDesdeFases(c.p.maniobra.fases, c.p.croquis)) } }, enabled = c.p.maniobra.fases.isNotEmpty()) {
            Text("Generar secuencia desde las fases")
        }
        CampoTexto(pr.secuencia, { v -> upd("Secuencia") { it.copy(secuencia = v) } }, "Secuencia de la maniobra", obligatorio = true, multilinea = true)
        CampoTexto(pr.senalizacion, { v -> upd("Señalización") { it.copy(senalizacion = v) } }, "Señalización", multilinea = true, marcador = "Conos, cinta, avisos…")
        CampoTexto(pr.delimitacion, { v -> upd("Delimitación") { it.copy(delimitacion = v) } }, "Delimitación del área", multilinea = true)
        CampoTexto(pr.meteorologia, { v -> upd("Meteorología") { it.copy(meteorologia = v) } }, "Condiciones meteorológicas aplicables", multilinea = true,
            marcador = "Viento máximo del manual, lluvia, tormenta eléctrica…")
        CampoTexto(pr.criteriosSuspension, { v -> upd("Criterios de suspensión") { it.copy(criteriosSuspension = v) } }, "Criterios de suspensión del trabajo", multilinea = true)
    }

    Seccion("Peligros y medidas preventivas", icono = Icons.Filled.Rule, desplegable = true, inicialAbierta = false,
        subtitulo = "${pr.peligros.count { it.aplica }} peligros marcados") {
        pr.peligros.forEachIndexed { i, pel ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(pel.aplica, { b -> upd("Peligros") { it.copy(peligros = it.peligros.mapIndexed { j, x -> if (j == i) x.copy(aplica = b) else x }) } })
                Text(pel.nombre, style = MaterialTheme.typography.bodyLarge)
            }
            if (pel.aplica) CampoTexto(pel.medidas, { v -> upd("Peligros") { it.copy(peligros = it.peligros.mapIndexed { j, x -> if (j == i) x.copy(medidas = v) else x }) } }, "Medidas preventivas", multilinea = true)
        }
    }

    Seccion("Lista de comprobación previa", icono = Icons.Filled.Checklist, desplegable = true, inicialAbierta = false,
        subtitulo = "${pr.checklist.count { it.marcado }} de ${pr.checklist.size} marcadas") {
        Ayuda("Puedes marcarla en campo antes de empezar. En el PDF aparece con casillas para marcar a mano.")
        pr.checklist.forEachIndexed { i, it0 ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(it0.marcado, { b -> upd("Lista de comprobación") { it.copy(checklist = it.checklist.mapIndexed { j, x -> if (j == i) x.copy(marcado = b) else x }) } })
                Text(it0.texto, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { upd("Lista de comprobación") { it.copy(checklist = it.checklist.filterIndexed { j, _ -> j != i }) } }) { Icon(Icons.Filled.Delete, "Quitar") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CampoTexto(nuevoItem, { nuevoItem = it }, "Nuevo punto", Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                if (nuevoItem.isNotBlank()) { val t = nuevoItem; upd("Lista de comprobación") { it.copy(checklist = it.checklist + ItemCheck(t)) }; nuevoItem = "" }
            }) { Icon(Icons.Filled.Add, "Añadir punto") }
        }
    }
}
