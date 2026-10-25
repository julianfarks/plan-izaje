package co.planizaje.app.ui.proyectos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import co.planizaje.app.ui.Rutas
import co.planizaje.app.ui.comp.BarraSuperior
import co.planizaje.app.ui.comp.Confirmar
import co.planizaje.app.ui.comp.Etiqueta
import co.planizaje.app.ui.comp.LocalAlmacen
import co.planizaje.app.ui.comp.Vacio
import co.planizaje.app.ui.theme.Paleta
import co.planizaje.core.calc.EstadoDocumento
import co.planizaje.core.calc.Motor
import co.planizaje.core.model.Proyecto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PantallaProyectos(nav: NavController, onAtras: () -> Unit) {
    val almacen = LocalAlmacen.current
    val proyectos by almacen.proyectos.collectAsState()
    val gruas by almacen.gruas.collectAsState()
    val accesorios by almacen.accesorios.collectAsState()
    var busqueda by rememberSaveable { mutableStateOf("") }
    var archivados by rememberSaveable { mutableStateOf(false) }
    var borrar by remember { mutableStateOf<Proyecto?>(null) }

    val lista = proyectos.filter { it.archivado == archivados }
        .filter { busqueda.isBlank() || listOf(it.nombre, it.cliente, it.ubicacion, it.empresa).any { s -> s.contains(busqueda, true) } }

    Scaffold(
        topBar = { BarraSuperior("Mis proyectos", onAtras) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { val p = almacen.crearProyecto(); nav.navigate(Rutas.asistente(p.id, 0)) },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nuevo plan") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    busqueda, { busqueda = it }, Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Buscar por nombre, cliente o lugar") }, singleLine = true,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!archivados, { archivados = false }, { Text("Activos") })
                    FilterChip(archivados, { archivados = true }, { Text("Archivados") })
                }
            }
            if (lista.isEmpty()) item {
                Vacio(Icons.Filled.FolderOpen, if (archivados) "Sin proyectos archivados" else "Sin proyectos", "Pulsa «Nuevo plan» para empezar.")
            }
            items(lista, key = { it.id }) { p ->
                val ev = remember(p, gruas, accesorios) { Motor.evaluar(p, gruas.firstOrNull { it.id == p.gruaId }, accesorios) }
                TarjetaProyecto(
                    p, ev.estadoDocumento, ev.puntosPorResolver,
                    onAbrir = { nav.navigate(Rutas.asistente(p.id, -1)) },
                    onDuplicar = { almacen.duplicarProyecto(p.id) },
                    onArchivar = { almacen.actualizarProyecto(p.id, if (p.archivado) "Proyecto desarchivado" else "Proyecto archivado") { it.copy(archivado = !it.archivado) } },
                    onEliminar = { borrar = p },
                )
            }
        }
    }
    borrar?.let { p ->
        Confirmar(
            "¿Eliminar proyecto?", "Se eliminará «${p.titulo}» y sus PDF generados. Esta acción no se puede deshacer. Si quieres conservarlo, mejor archívalo.",
            "Eliminar", onConfirmar = { almacen.eliminarProyecto(p.id); borrar = null }, onCancelar = { borrar = null }, peligroso = true,
        )
    }
}

@Composable
private fun TarjetaProyecto(
    p: Proyecto, estado: EstadoDocumento, pendientes: Int,
    onAbrir: () -> Unit, onDuplicar: () -> Unit, onArchivar: () -> Unit, onEliminar: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val fecha = remember(p.modificado) { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("es-CO")).format(Date(p.modificado)) }
    Card(onClick = onAbrir, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.titulo, style = MaterialTheme.typography.titleMedium)
                val sub = listOf(p.cliente, p.ubicacion).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium)
                Text("Rev. ${p.revision} · Editado $fecha", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (estado.esBorrador) Etiqueta("…", "Borrador · $pendientes por resolver", Paleta.Revisar, Paleta.RevisarFondo)
                else Etiqueta("✓", estado.etiqueta.lowercase().replaceFirstChar { it.uppercase() }, Paleta.Ok, Paleta.OkFondo)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opciones del proyecto") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem({ Text("Abrir") }, { menu = false; onAbrir() }, leadingIcon = { Icon(Icons.Filled.FolderOpen, null) })
                    DropdownMenuItem({ Text("Duplicar") }, { menu = false; onDuplicar() }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) })
                    DropdownMenuItem(
                        { Text(if (p.archivado) "Desarchivar" else "Archivar") }, { menu = false; onArchivar() },
                        leadingIcon = { Icon(if (p.archivado) Icons.Filled.Unarchive else Icons.Filled.Archive, null) },
                    )
                    DropdownMenuItem({ Text("Eliminar") }, { menu = false; onEliminar() }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
                }
            }
        }
    }
}
