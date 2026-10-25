package co.planizaje.app.ui.accesorios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import co.planizaje.app.data.nuevoId
import co.planizaje.app.ui.Rutas
import co.planizaje.app.ui.comp.Aviso
import co.planizaje.app.ui.comp.Ayuda
import co.planizaje.app.ui.comp.BarraSuperior
import co.planizaje.app.ui.comp.CampoFecha
import co.planizaje.app.ui.comp.CampoNumero
import co.planizaje.app.ui.comp.CampoTexto
import co.planizaje.app.ui.comp.Confirmar
import co.planizaje.app.ui.comp.Etiqueta
import co.planizaje.app.ui.comp.GaleriaAdjuntos
import co.planizaje.app.ui.comp.LocalAlmacen
import co.planizaje.app.ui.comp.Seccion
import co.planizaje.app.ui.comp.Selector
import co.planizaje.app.ui.comp.Vacio
import co.planizaje.app.ui.theme.Paleta
import co.planizaje.core.calc.Inspeccion
import co.planizaje.core.model.Accesorio
import co.planizaje.core.model.EstadoInspeccion
import co.planizaje.core.model.TipoAccesorio
import co.planizaje.core.util.Unidades
import java.time.LocalDate

@Composable
fun PantallaAccesorios(nav: NavController, onAtras: () -> Unit) {
    val almacen = LocalAlmacen.current
    val lista by almacen.accesorios.collectAsState()
    var filtro by rememberSaveable { mutableStateOf<String?>(null) }
    val hoy = remember { LocalDate.now() }
    val grupos = listOf("Eslingas" to { a: Accesorio -> a.tipo.esEslinga }, "Grilletes" to { a: Accesorio -> a.tipo == TipoAccesorio.GRILLETE }, "Otros" to { a: Accesorio -> !a.tipo.esEslinga && a.tipo != TipoAccesorio.GRILLETE })
    val visibles = lista.filter { a -> filtro == null || grupos.first { it.first == filtro }.second(a) }

    Scaffold(
        topBar = { BarraSuperior("Mis accesorios", onAtras) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = {
                val a = Accesorio(nuevoId()); almacen.guardarAccesorio(a); nav.navigate(Rutas.accesorio(a.id))
            }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nuevo accesorio") })
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(filtro == null, { filtro = null }, { Text("Todos") })
                    grupos.forEach { (n, _) -> FilterChip(filtro == n, { filtro = n }, { Text(n) }) }
                }
            }
            if (visibles.isEmpty()) item { Vacio(Icons.Filled.Link, "Sin accesorios", "Registra tus eslingas, grilletes y ganchos con los datos de su etiqueta y certificado.") }
            items(visibles, key = { it.id }) { a ->
                val probl = Inspeccion.problemas(a, hoy)
                Card(onClick = { nav.navigate(Rutas.accesorio(a.id)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(a.nombreCorto, style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(
                            a.fabricante.ifBlank { null }, a.longitud?.let { Unidades.m(it) },
                            "WLL ${Unidades.kg(a.wllVertical)}",
                        ).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        when {
                            a.estado == EstadoInspeccion.NO_APTO || Inspeccion.vencida(a, hoy) -> Etiqueta("✕", probl.firstOrNull() ?: "No apto", Paleta.Mal, Paleta.MalFondo)
                            probl.isNotEmpty() -> Etiqueta("!", probl.first(), Paleta.Revisar, Paleta.RevisarFondo)
                            else -> Etiqueta("✓", "Apto · inspección ${a.fechaInspeccion}", Paleta.Ok, Paleta.OkFondo)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditorAccesorio(id: String, onAtras: () -> Unit) {
    val almacen = LocalAlmacen.current
    val lista by almacen.accesorios.collectAsState()
    val a = lista.firstOrNull { it.id == id }
    var borrar by remember { mutableStateOf(false) }
    if (a == null) {
        Scaffold(topBar = { BarraSuperior("Accesorio", onAtras) }) { pad -> Text("No encontrado", Modifier.padding(pad).padding(16.dp)) }
        return
    }
    fun upd(f: (Accesorio) -> Accesorio) = almacen.guardarAccesorio(f(a))
    val hoy = remember { LocalDate.now() }

    Scaffold(topBar = {
        BarraSuperior(a.nombreCorto, onAtras, acciones = {
            IconButton(onClick = {
                val c = a.copy(id = nuevoId(), identificacion = a.identificacion + " (copia)", creado = 0L, fotos = emptyList(), certificado = null)
                almacen.guardarAccesorio(c)
            }) { Icon(Icons.Filled.ContentCopy, "Duplicar") }
            IconButton(onClick = { borrar = true }) { Icon(Icons.Filled.Delete, "Eliminar") }
        })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Inspeccion.problemas(a, hoy).forEach { Aviso(it) }
            Seccion("Identificación", icono = Icons.Filled.Badge) {
                Selector("Tipo", TipoAccesorio.entries, a.tipo, { it.etiqueta }, { t -> upd { it.copy(tipo = t) } })
                CampoTexto(a.identificacion, { v -> upd { it.copy(identificacion = v) } }, "Identificación / código", obligatorio = true, marcador = "Ej. ES-04")
                CampoTexto(a.fabricante, { v -> upd { it.copy(fabricante = v) } }, "Fabricante")
                CampoTexto(a.modelo, { v -> upd { it.copy(modelo = v) } }, "Modelo / referencia")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoNumero(a.longitud, { v -> upd { it.copy(longitud = v) } }, "Longitud", "m", Modifier.weight(1f))
                    CampoNumero(a.pesoKg, { v -> upd { it.copy(pesoKg = v) } }, "Peso propio", "kg", Modifier.weight(1f))
                }
            }
            Seccion("Capacidad (WLL)", icono = Icons.Filled.Scale, subtitulo = "Copia los valores de la etiqueta o del certificado") {
                Ayuda("WLL es la carga de trabajo límite. No es la carga de rotura: nunca la uses como capacidad.")
                if (a.tipo.esEslinga) {
                    CampoNumero(a.wllVertical, { v -> upd { it.copy(wllVertical = v) } }, "WLL vertical (directo)", "kg")
                    CampoNumero(a.wllAhorcado, { v -> upd { it.copy(wllAhorcado = v) } }, "WLL ahorcado", "kg")
                    CampoNumero(a.wllCanasta, { v -> upd { it.copy(wllCanasta = v) } }, "WLL canasta (ramales verticales)", "kg")
                } else {
                    CampoNumero(a.wllVertical, { v -> upd { it.copy(wllVertical = v) } }, "WLL", "kg")
                }
                CampoTexto(a.configuracionPermitida, { v -> upd { it.copy(configuracionPermitida = v) } }, "Configuraciones permitidas / limitaciones", multilinea = true)
                CampoNumero(a.cargaRotura, { v -> upd { it.copy(cargaRotura = v) } }, "Carga de rotura (solo informativa)", "kg", ayuda = "No se usa en ningún cálculo")
            }
            Seccion("Certificado e inspección", icono = Icons.AutoMirrored.Filled.FactCheck) {
                CampoTexto(a.numeroCertificado, { v -> upd { it.copy(numeroCertificado = v) } }, "Número de certificado")
                CampoFecha(a.fechaInspeccion, { v -> upd { it.copy(fechaInspeccion = v) } }, "Fecha de última inspección")
                CampoFecha(a.proximaInspeccion, { v -> upd { it.copy(proximaInspeccion = v) } }, "Próxima inspección")
                Selector("Estado", EstadoInspeccion.entries, a.estado, { it.etiqueta }, { e -> upd { it.copy(estado = e) } })
                GaleriaAdjuntos("Certificado (PDF o foto)", listOfNotNull(a.certificado), { l -> upd { it.copy(certificado = l.lastOrNull()) } })
            }
            Seccion("Fotos y observaciones", icono = Icons.Filled.PhotoLibrary, desplegable = true, inicialAbierta = a.fotos.isNotEmpty()) {
                GaleriaAdjuntos("Fotos", a.fotos, { l -> upd { it.copy(fotos = l) } }, documentos = false)
                CampoTexto(a.observaciones, { v -> upd { it.copy(observaciones = v) } }, "Observaciones", multilinea = true)
            }
        }
    }
    if (borrar) Confirmar("¿Eliminar accesorio?", "Se eliminará «${a.nombreCorto}» de la biblioteca. Los proyectos que lo usaban lo mostrarán como faltante.", "Eliminar",
        onConfirmar = { borrar = false; almacen.eliminarAccesorio(a.id); onAtras() }, onCancelar = { borrar = false }, peligroso = true)
}
