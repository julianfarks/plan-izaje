package co.planizaje.app.ui.asistente

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import co.planizaje.app.ui.Rutas
import co.planizaje.app.ui.comp.Aviso
import co.planizaje.app.ui.comp.BarraSuperior
import co.planizaje.app.ui.comp.LocalAlmacen
import co.planizaje.app.ui.theme.Paleta
import co.planizaje.core.calc.Evaluacion
import co.planizaje.core.calc.Motor
import co.planizaje.core.model.Accesorio
import co.planizaje.core.model.Grua
import co.planizaje.core.model.Paso
import co.planizaje.core.model.Proyecto

/** Contexto común que reciben todos los pasos del asistente. */
class Ctx(
    val p: Proyecto,
    val grua: Grua?,
    val gruas: List<Grua>,
    val accesorios: List<Accesorio>,
    val ev: Evaluacion,
    val nav: NavController,
    val actualizar: (cambio: String?, f: (Proyecto) -> Proyecto) -> Unit,
    val irAPaso: (Paso) -> Unit,
    /** Lleva al paso y al campo concreto (clave de enfoque). */
    val irACampo: (Paso, String?) -> Unit = { p, _ -> irAPaso(p) },
    val abrirVoz: (co.planizaje.core.ia.Ambito) -> Unit = {},
)

enum class EstadoPaso { SIN_VER, ATENCION, COMPLETO }

fun estadoPaso(paso: Paso, p: Proyecto, ev: Evaluacion): EstadoPaso {
    val atencion = ev.faltantesDe(paso).isNotEmpty() || ev.resultadosDe(paso).any { it.bloquea }
    return when {
        paso !in p.pasosVistos -> EstadoPaso.SIN_VER
        atencion -> EstadoPaso.ATENCION
        else -> EstadoPaso.COMPLETO
    }
}

@Composable
fun PantallaAsistente(nav: NavController, id: String, pasoInicial: Int, onAtras: () -> Unit, vozAlAbrir: Boolean = false) {
    val almacen = LocalAlmacen.current
    val proyectos by almacen.proyectos.collectAsState()
    val gruas by almacen.gruas.collectAsState()
    val accesorios by almacen.accesorios.collectAsState()
    val cargado by almacen.cargado.collectAsState()
    val p = proyectos.firstOrNull { it.id == id }

    if (p == null) {
        Scaffold(topBar = { BarraSuperior("Proyecto", onAtras) }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text(if (cargado) "Este proyecto ya no existe." else "Cargando…")
            }
        }
        return
    }
    val grua = gruas.firstOrNull { it.id == p.gruaId }
    val ev = remember(p, grua, accesorios) { Motor.evaluar(p, grua, accesorios) }
    val pasos = Paso.entries
    var indice by rememberSaveable {
        mutableIntStateOf(
            if (pasoInicial >= 0) pasoInicial.coerceIn(0, pasos.lastIndex)
            else pasos.indexOfFirst { estadoPaso(it, p, ev) != EstadoPaso.COMPLETO }.let { if (it < 0) pasos.lastIndex else it },
        )
    }
    val paso = pasos[indice]
    LaunchedEffect(paso) {
        if (paso !in p.pasosVistos) almacen.actualizarProyecto(id) { it.copy(pasosVistos = it.pasosVistos + paso) }
    }
    val objetivo = remember { co.planizaje.app.ui.comp.Objetivo() }
    var voz by rememberSaveable { mutableStateOf<String?>(if (vozAlAbrir) co.planizaje.core.ia.Ambito.GENERAL.name else null) }
    val ctx = Ctx(
        p, grua, gruas, accesorios, ev, nav, { c, f -> almacen.actualizarProyecto(id, c, f) }, { indice = it.ordinal },
        irACampo = { pp, campo -> indice = pp.ordinal; objetivo.clave = campo },
        abrirVoz = { voz = it.name },
    )
    val scroll = rememberScrollState()
    LaunchedEffect(indice) { scroll.scrollTo(0) }

    Scaffold(
        topBar = {
            Column {
                BarraSuperior(
                    p.titulo, onAtras, subtitulo = "Paso ${paso.numero} de ${pasos.size} · ${paso.titulo}",
                    acciones = {
                        IconButton(onClick = { voz = ambitoDe(paso).name }) { Icon(Icons.Filled.Mic, "Completar con voz") }
                        IconButton(onClick = { nav.popBackStack(Rutas.INICIO, false) }) { Icon(Icons.Filled.Home, "Inicio") }
                    },
                )
                Pasos(p, ev, indice) { indice = it }
                LinearProgressIndicator(
                    progress = { (indice + 1f) / pasos.size },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Paleta.Ambar,
                )
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = { if (indice > 0) indice-- else onAtras() }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(6.dp))
                        Text(if (indice > 0) "Anterior" else "Salir")
                    }
                    if (indice < pasos.lastIndex) {
                        Button(onClick = { indice++ }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Text(if (indice == pasos.lastIndex - 1) "Ir al PDF" else "Siguiente"); Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                        }
                    } else {
                        Button(onClick = { nav.popBackStack(Rutas.INICIO, false) }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Terminar") }
                    }
                }
            }
        },
    ) { pad ->
        androidx.compose.runtime.CompositionLocalProvider(co.planizaje.app.ui.comp.LocalObjetivo provides objetivo) {
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (paso != Paso.VERIFICACION && paso != Paso.PDF) BotonVoz(paso) { voz = ambitoDe(paso).name }
            PanelAsistente(ctx, paso)
            when (paso) {
                Paso.INFO -> PasoInfo(ctx)
                Paso.GRUA -> PasoGrua(ctx)
                Paso.CARGA -> PasoCarga(ctx)
                Paso.CROQUIS -> PasoCroquis(ctx)
                Paso.MANIOBRA -> PasoManiobra(ctx)
                Paso.VERIFICACION -> PasoVerificacion(ctx)
                Paso.PDF -> PasoPdf(ctx)
            }
            FaltantesDelPaso(ctx, paso)
            Text(
                "Los cambios se guardan automáticamente. Puedes saltar pasos y volver cuando quieras.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
        }
        }
    }
    voz?.let { a -> co.planizaje.app.voz.HojaVoz(co.planizaje.core.ia.Ambito.valueOf(a), p.id, null) { voz = null } }
}

fun ambitoDe(paso: Paso): co.planizaje.core.ia.Ambito = when (paso) {
    Paso.INFO -> co.planizaje.core.ia.Ambito.INFO
    Paso.GRUA -> co.planizaje.core.ia.Ambito.GRUA_PASO
    Paso.CARGA -> co.planizaje.core.ia.Ambito.CARGA
    Paso.CROQUIS -> co.planizaje.core.ia.Ambito.CROQUIS
    Paso.MANIOBRA -> co.planizaje.core.ia.Ambito.MANIOBRA
    else -> co.planizaje.core.ia.Ambito.GENERAL
}

@Composable
private fun BotonVoz(paso: Paso, onClick: () -> Unit) {
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(containerColor = Paleta.AmbarClaro, contentColor = Paleta.Azul),
    ) {
        Icon(Icons.Filled.Mic, null); Spacer(Modifier.width(8.dp))
        Text(if (paso == Paso.INFO) "Hablar del izaje" else "Completar «${paso.titulo.lowercase()}» con voz")
    }
}

@Composable
private fun Pasos(p: Proyecto, ev: Evaluacion, actual: Int, onElegir: (Int) -> Unit) {
    val estado = rememberLazyListState()
    LaunchedEffect(actual) { estado.animateScrollToItem((actual - 1).coerceAtLeast(0)) }
    LazyRow(state = estado, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Spacer(Modifier.width(10.dp)) }
        itemsIndexed(Paso.entries) { i, paso ->
            val e = estadoPaso(paso, p, ev)
            val sel = i == actual
            val (fondo, texto, simbolo) = when (e) {
                EstadoPaso.COMPLETO -> Triple(Paleta.OkFondo, Paleta.Ok, "✓")
                EstadoPaso.ATENCION -> Triple(Paleta.RevisarFondo, Paleta.Revisar, "!")
                EstadoPaso.SIN_VER -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "${paso.numero}")
            }
            Row(
                Modifier
                    .background(if (sel) MaterialTheme.colorScheme.primary else fondo, RoundedCornerShape(50))
                    .border(if (sel) 0.dp else 1.dp, texto.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .clickable { onElegir(i) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(22.dp).background(if (sel) Color.White else texto, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text(simbolo, color = if (sel) MaterialTheme.colorScheme.primary else fondo, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) }
                Spacer(Modifier.width(6.dp))
                Text(paso.titulo, color = if (sel) MaterialTheme.colorScheme.onPrimary else texto, style = MaterialTheme.typography.labelLarge)
            }
        }
        item { Spacer(Modifier.width(10.dp)) }
    }
}

@Composable
private fun ColumnScope.FaltantesDelPaso(c: Ctx, paso: Paso) {
    val f = c.ev.faltantesDe(paso)
    if (f.isEmpty() || paso == Paso.VERIFICACION || paso == Paso.PDF) return
    HorizontalDivider()
    Text("Falta en este paso", style = MaterialTheme.typography.titleMedium)
    f.forEach { Aviso(it.texto, Paleta.Pendiente, Paleta.PendienteFondo, "…") }
}
