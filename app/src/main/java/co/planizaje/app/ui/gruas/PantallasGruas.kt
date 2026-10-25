package co.planizaje.app.ui.gruas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import co.planizaje.app.data.nuevoId
import co.planizaje.app.ui.Rutas
import co.planizaje.app.ui.comp.Archivos
import co.planizaje.app.ui.comp.Aviso
import co.planizaje.app.ui.comp.Ayuda
import co.planizaje.app.ui.comp.BarraSuperior
import co.planizaje.app.ui.comp.CampoEntero
import co.planizaje.app.ui.comp.CampoFecha
import co.planizaje.app.ui.comp.CampoNumero
import co.planizaje.app.ui.comp.CampoTexto
import co.planizaje.app.ui.comp.Confirmar
import co.planizaje.app.ui.comp.Etiqueta
import co.planizaje.app.ui.comp.FilaInterruptor
import co.planizaje.app.ui.comp.GaleriaAdjuntos
import co.planizaje.app.ui.comp.LocalAlmacen
import co.planizaje.app.ui.comp.Seccion
import co.planizaje.app.ui.comp.Selector
import co.planizaje.app.ui.comp.Vacio
import co.planizaje.app.ui.theme.Paleta
import co.planizaje.core.model.ConfigEstabilizadores
import co.planizaje.core.model.FuenteTecnica
import co.planizaje.core.model.Grua
import co.planizaje.core.model.Interpolacion
import co.planizaje.core.model.PlantillasGruas
import co.planizaje.core.model.PuntoTabla
import co.planizaje.core.model.TablaCarga
import co.planizaje.core.model.TipoGrua
import co.planizaje.core.model.Verificacion
import co.planizaje.core.util.Unidades

@Composable
fun PantallaGruas(nav: NavController, onAtras: () -> Unit) {
    val almacen = LocalAlmacen.current
    val gruas by almacen.gruas.collectAsState()
    var plantillas by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { BarraSuperior("Mis grúas", onAtras) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = {
                val g = Grua(id = nuevoId()); almacen.guardarGrua(g); nav.navigate(Rutas.grua(g.id))
            }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nueva grúa") })
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Ayuda("Registra cada grúa con su identificación exacta y sus tablas de carga copiadas del diagrama de cabina o del manual. La app no trae capacidades de fábrica: siempre se usan tus documentos.")
            }
            item {
                OutlinedButton(onClick = { plantillas = true }, modifier = Modifier.fillMaxWidth()) { Text("Crear desde un diagrama fotografiado") }
            }
            if (gruas.isEmpty()) item { Vacio(Icons.Filled.LocalShipping, "Sin grúas", "Pulsa «Nueva grúa» o usa un diagrama fotografiado.") }
            items(gruas, key = { it.id }) { g ->
                val verificadas = g.tablas.count { it.verificacion.valida }
                Card(onClick = { nav.navigate(Rutas.grua(g.id)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(g.nombreCorto, style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(g.tipo.etiqueta, g.numeroSerie.takeIf { it.isNotBlank() }?.let { "Serie $it" }, g.placaVehiculo.takeIf { it.isNotBlank() }?.let { "Placa $it" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        if (!g.identificada) Etiqueta("!", "Falta identificación exacta", Paleta.Revisar, Paleta.RevisarFondo)
                        if (g.tablas.isEmpty()) Etiqueta("✕", "Sin tablas de carga", Paleta.Mal, Paleta.MalFondo)
                        else Etiqueta(if (verificadas == g.tablas.size) "✓" else "!", "$verificadas de ${g.tablas.size} tablas verificadas", if (verificadas == g.tablas.size) Paleta.Ok else Paleta.Revisar, if (verificadas == g.tablas.size) Paleta.OkFondo else Paleta.RevisarFondo)
                    }
                }
            }
        }
    }

    if (plantillas) AlertDialog(
        onDismissRequest = { plantillas = false },
        title = { Text("Diagramas fotografiados") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Valores transcritos de fotos de placas. Se crean SIN VERIFICAR, con la foto adjunta. Debes completar el nº de serie y comprobar cada valor contra la placa de tu grúa.", style = MaterialTheme.typography.bodySmall)
                PlantillasGruas.todas.forEach { pl ->
                    TextButton(onClick = {
                        plantillas = false
                        val g = almacen.crearGruaDesdePlantilla(pl)
                        nav.navigate(Rutas.grua(g.id))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("${pl.fabricante} ${pl.modelo} ${pl.variante}", fontWeight = FontWeight.Bold)
                            Text("${pl.tablas.size} tabla(s) · ${pl.referenciaPlaca}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { plantillas = false }) { Text("Cerrar") } },
    )
}

@Composable
fun EditorGrua(nav: NavController, id: String, onAtras: () -> Unit) {
    val almacen = LocalAlmacen.current
    val gruas by almacen.gruas.collectAsState()
    val g = gruas.firstOrNull { it.id == id }
    var borrar by remember { mutableStateOf(false) }
    if (g == null) {
        Scaffold(topBar = { BarraSuperior("Grúa", onAtras) }) { pad -> Text("Grúa no encontrada", Modifier.padding(pad).padding(16.dp)) }
        return
    }
    fun upd(f: (Grua) -> Grua) = almacen.guardarGrua(f(g))

    Scaffold(topBar = {
        BarraSuperior(g.nombreCorto, onAtras, subtitulo = "Ficha de la grúa", acciones = {
            IconButton(onClick = { borrar = true }) { Icon(Icons.Filled.Delete, "Eliminar grúa") }
        })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (g.notas.isNotBlank()) Aviso(g.notas)
            Seccion("Identificación", icono = Icons.Filled.Badge) {
                CampoTexto(g.fabricante, { v -> upd { it.copy(fabricante = v) } }, "Fabricante", obligatorio = true, marcador = "Ej. Palfinger")
                CampoTexto(g.modelo, { v -> upd { it.copy(modelo = v) } }, "Modelo exacto", obligatorio = true, marcador = "Ej. PK 23500")
                CampoTexto(g.variante, { v -> upd { it.copy(variante = v) } }, "Variante / versión", ayuda = "Tal como figura en la placa (A, B, C, Performance…)")
                CampoTexto(g.numeroSerie, { v -> upd { it.copy(numeroSerie = v) } }, "Número de serie", obligatorio = true)
                CampoTexto(g.identificacionInterna, { v -> upd { it.copy(identificacionInterna = v) } }, "Identificación interna")
                Selector("Tipo de grúa", TipoGrua.entries, g.tipo, { it.etiqueta }, { t -> upd { it.copy(tipo = t) } })
            }
            Seccion("Vehículo portador", icono = Icons.Filled.LocalShipping, desplegable = true, inicialAbierta = g.vehiculo.isBlank()) {
                CampoTexto(g.vehiculo, { v -> upd { it.copy(vehiculo = v) } }, "Vehículo", marcador = "Marca, modelo, ejes")
                CampoTexto(g.placaVehiculo, { v -> upd { it.copy(placaVehiculo = v) } }, "Placa")
            }
            Seccion("Pluma y configuración", icono = Icons.Filled.Settings, desplegable = true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoEntero(g.extensionesHidraulicas, { v -> upd { it.copy(extensionesHidraulicas = v) } }, "Ext. hidráulicas", Modifier.weight(1f), max = 20)
                    CampoEntero(g.extensionesManuales, { v -> upd { it.copy(extensionesManuales = v) } }, "Ext. manuales", Modifier.weight(1f), max = 20)
                }
                CampoTexto(g.longitudesPluma, { v -> upd { it.copy(longitudesPluma = v) } }, "Longitudes / alcances documentados", multilinea = true)
                CampoTexto(g.plumin, { v -> upd { it.copy(plumin = v) } }, "Plumín (jib)", marcador = "Modelo y extensiones, si tiene")
                CampoTexto(g.contrapeso, { v -> upd { it.copy(contrapeso = v) } }, "Contrapeso (si aplica)")
                CampoTexto(g.sectoresPermitidos, { v -> upd { it.copy(sectoresPermitidos = v) } }, "Sectores de trabajo permitidos")
                CampoNumero(g.pesoGanchoKg, { v -> upd { it.copy(pesoGanchoKg = v) } }, "Peso del gancho de la grúa", "kg", ayuda = "Solo si consta en la documentación")
                CampoNumero(g.vientoMaxMs, { v -> upd { it.copy(vientoMaxMs = v) } }, "Viento máximo de trabajo (manual)", "m/s", ayuda = g.vientoMaxMs?.let { "= ${Unidades.fmt(it * 3.6, 0)} km/h" })
                CampoTexto(g.restricciones, { v -> upd { it.copy(restricciones = v) } }, "Restricciones operativas", multilinea = true)
            }
            Seccion("Estabilizadores", icono = Icons.Filled.Settings, desplegable = true, subtitulo = "${g.estabilizadores.size} configuración(es)") {
                g.estabilizadores.forEach { est ->
                    Card {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Configuración", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                IconButton(onClick = { upd { gg -> gg.copy(estabilizadores = gg.estabilizadores.filter { it.id != est.id }) } }) { Icon(Icons.Filled.Delete, "Quitar") }
                            }
                            fun ue(f: (ConfigEstabilizadores) -> ConfigEstabilizadores) = upd { gg -> gg.copy(estabilizadores = gg.estabilizadores.map { if (it.id == est.id) f(it) else it }) }
                            CampoTexto(est.nombre, { v -> ue { it.copy(nombre = v) } }, "Nombre", marcador = "Ej. Extensión total ambos lados")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CampoNumero(est.anchoApoyos, { v -> ue { it.copy(anchoApoyos = v) } }, "Ancho apoyos", "m", Modifier.weight(1f))
                                CampoNumero(est.largoApoyos, { v -> ue { it.copy(largoApoyos = v) } }, "Largo apoyos", "m", Modifier.weight(1f))
                            }
                            CampoNumero(est.fuerzaMaxApoyoKn, { v -> ue { it.copy(fuerzaMaxApoyoKn = v) } }, "Fuerza máx. por apoyo (fabricante)", "kN")
                            CampoTexto(est.fuente.documento, { v -> ue { it.copy(fuente = it.fuente.copy(documento = v)) } }, "Documento fuente")
                            CampoTexto(est.fuente.paginaOFigura, { v -> ue { it.copy(fuente = it.fuente.copy(paginaOFigura = v)) } }, "Página / figura")
                        }
                    }
                }
                OutlinedButton(onClick = { upd { it.copy(estabilizadores = it.estabilizadores + ConfigEstabilizadores(nuevoId())) } }) { Icon(Icons.Filled.Add, null); Text(" Añadir configuración") }
            }
            Seccion("Tablas de carga", icono = Icons.Filled.TableChart, subtitulo = "Una tabla por configuración (extensiones, plumín, estabilizadores)") {
                if (g.tablas.isEmpty()) Aviso("Sin tabla de carga la app no puede verificar la capacidad.", Paleta.Mal, Paleta.MalFondo, "✕")
                g.tablas.forEach { t ->
                    Card(onClick = { nav.navigate(Rutas.tabla(g.id, t.id)) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(t.nombre.ifBlank { "Tabla sin nombre" }, fontWeight = FontWeight.Bold)
                            Text("${t.puntos.size} valores · ${t.configuracionPluma}", style = MaterialTheme.typography.bodySmall)
                            if (t.verificacion.valida) Etiqueta("✓", "Verificada por ${t.verificacion.porQuien}", Paleta.Ok, Paleta.OkFondo)
                            else Etiqueta("!", "Sin verificar", Paleta.Revisar, Paleta.RevisarFondo)
                        }
                    }
                }
                OutlinedButton(onClick = {
                    val t = TablaCarga(nuevoId(), nombre = "Tabla ${g.tablas.size + 1}")
                    upd { it.copy(tablas = it.tablas + t) }
                    nav.navigate(Rutas.tabla(g.id, t.id))
                }) { Icon(Icons.Filled.Add, null); Text(" Añadir tabla de carga") }
            }
            Seccion("Documentos del fabricante", icono = Icons.Filled.Description, desplegable = true, inicialAbierta = g.documentos.isNotEmpty()) {
                Ayuda("Adjunta el manual, la ficha técnica o fotos del diagrama de carga de cabina. Sirven como fuente de las tablas.")
                GaleriaAdjuntos("Documentos y fotos", g.documentos, { l -> upd { it.copy(documentos = l) } })
            }
            Seccion("Notas", desplegable = true, inicialAbierta = false) {
                CampoTexto(g.notas, { v -> upd { it.copy(notas = v) } }, "Notas", multilinea = true)
            }
        }
    }
    if (borrar) Confirmar("¿Eliminar grúa?", "Se eliminará la ficha de ${g.nombreCorto} y sus tablas. Los proyectos que la usan quedarán sin grúa.", "Eliminar",
        onConfirmar = { borrar = false; almacen.eliminarGrua(g.id); onAtras() }, onCancelar = { borrar = false }, peligroso = true)
}

@Composable
fun EditorTabla(gruaId: String, tablaId: String, onAtras: () -> Unit) {
    val almacen = LocalAlmacen.current
    val ctx = LocalContext.current
    val gruas by almacen.gruas.collectAsState()
    val g = gruas.firstOrNull { it.id == gruaId }
    val t = g?.tablas?.firstOrNull { it.id == tablaId }
    var pegar by remember { mutableStateOf(false) }
    var textoPegar by remember { mutableStateOf("") }
    var borrar by remember { mutableStateOf(false) }
    if (g == null || t == null) {
        Scaffold(topBar = { BarraSuperior("Tabla de carga", onAtras) }) { pad -> Text("Tabla no encontrada", Modifier.padding(pad).padding(16.dp)) }
        return
    }
    fun upd(f: (TablaCarga) -> TablaCarga) {
        // Cualquier cambio en los valores invalida la verificación previa.
        almacen.guardarGrua(g.copy(tablas = g.tablas.map { if (it.id == t.id) f(it) else it }))
    }
    fun updValores(f: (TablaCarga) -> TablaCarga) = upd { f(it).copy(verificacion = Verificacion()) }
    val docFuente = g.documentos.firstOrNull { it.id == t.fuente.adjuntoId }

    Scaffold(topBar = {
        BarraSuperior(t.nombre.ifBlank { "Tabla de carga" }, onAtras, subtitulo = g.nombreCorto, acciones = {
            IconButton(onClick = { borrar = true }) { Icon(Icons.Filled.Delete, "Eliminar tabla") }
        })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (t.notas.isNotBlank()) Aviso(t.notas)
            Seccion("Configuración", icono = Icons.Filled.Settings) {
                CampoTexto(t.nombre, { v -> upd { it.copy(nombre = v) } }, "Nombre de la tabla", marcador = "Ej. 6 extensiones hidráulicas")
                CampoTexto(t.configuracionPluma, { v -> updValores { it.copy(configuracionPluma = v) } }, "Configuración de pluma", obligatorio = true)
                CampoEntero(t.extensiones, { v -> updValores { it.copy(extensiones = v) } }, "Número de extensiones", max = 20)
                FilaInterruptor("Con plumín (jib)", t.conPlumin, { b -> updValores { it.copy(conPlumin = b) } })
                if (g.estabilizadores.isNotEmpty())
                    Selector("Estabilizadores", g.estabilizadores, g.estabilizadores.firstOrNull { it.id == t.estabilizadoresId }, { it.nombre.ifBlank { "Sin nombre" } }, { e -> updValores { it.copy(estabilizadoresId = e.id) } })
                CampoTexto(t.sector, { v -> updValores { it.copy(sector = v) } }, "Sector de trabajo", marcador = "Ej. 360°, trasero, lateral")
                Selector("Entre dos radios tabulados", Interpolacion.entries, t.interpolacion, { it.etiqueta }, { i -> updValores { it.copy(interpolacion = i) } })
                Selector("¿La capacidad ya descuenta el gancho?", listOf<Boolean?>(true, false, null), t.descuentaGancho,
                    { when (it) { true -> "Sí, según el diagrama"; false -> "No, hay que sumarlo"; null -> "No lo sé (revisar)" } },
                    { b -> updValores { it.copy(descuentaGancho = b) } })
            }
            Seccion("Valores (radio → capacidad)", icono = Icons.Filled.TableChart, subtitulo = "Copia exactamente lo que dice el diagrama") {
                Row { Text("Radio (m)", Modifier.weight(1f), fontWeight = FontWeight.Bold); Text("Capacidad (kg)", Modifier.weight(1.2f), fontWeight = FontWeight.Bold); Text("Alt. máx (m)", Modifier.weight(1f), fontWeight = FontWeight.Bold); Spacer(Modifier.width(40.dp)) }
                t.puntos.forEachIndexed { i, pt ->
                    androidx.compose.runtime.key(i, t.puntos.size) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CampoNumero(pt.radio, { v -> v?.let { r -> updValores { tt -> tt.copy(puntos = tt.puntos.mapIndexed { j, x -> if (j == i) x.copy(radio = r) else x }) } } }, "", "m", Modifier.weight(1f))
                            CampoNumero(pt.capacidad, { v -> v?.let { c -> updValores { tt -> tt.copy(puntos = tt.puntos.mapIndexed { j, x -> if (j == i) x.copy(capacidad = c) else x }) } } }, "", "kg", Modifier.weight(1.2f))
                            CampoNumero(pt.alturaMax, { v -> updValores { tt -> tt.copy(puntos = tt.puntos.mapIndexed { j, x -> if (j == i) x.copy(alturaMax = v) else x }) } }, "", "m", Modifier.weight(1f))
                            IconButton(onClick = { updValores { tt -> tt.copy(puntos = tt.puntos.filterIndexed { j, _ -> j != i }) } }) { Icon(Icons.Filled.Delete, "Quitar fila") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val ultimo = t.puntos.maxOfOrNull { it.radio } ?: 0.0
                        updValores { it.copy(puntos = it.puntos + PuntoTabla(ultimo + 2.0, 0.0)) }
                    }) { Icon(Icons.Filled.Add, null); Text(" Fila") }
                    OutlinedButton(onClick = { pegar = true }) { Text("Pegar lista") }
                    OutlinedButton(onClick = { updValores { it.copy(puntos = it.ordenados) } }) { Text("Ordenar") }
                }
                val duplicados = t.puntos.groupBy { it.radio }.filter { it.value.size > 1 }.keys
                if (duplicados.isNotEmpty()) Aviso("Hay radios repetidos (${duplicados.joinToString { Unidades.fmt(it) }} m). Se usará la menor capacidad.")
                val ord = t.ordenados
                if (ord.zipWithNext().any { (a, b) -> b.capacidad > a.capacidad }) Aviso("Hay capacidades que aumentan al aumentar el radio. Compruébalo con el diagrama.")
                if (t.puntos.any { it.capacidad <= 0 }) Aviso("Hay filas con capacidad 0.", Paleta.Mal, Paleta.MalFondo, "✕")
            }
            Seccion("Fuente y verificación", icono = Icons.Filled.Description) {
                CampoTexto(t.fuente.documento, { v -> upd { it.copy(fuente = it.fuente.copy(documento = v)) } }, "Documento", obligatorio = true, marcador = "Ej. Diagrama de carga en cabina / Manual de uso")
                CampoTexto(t.fuente.paginaOFigura, { v -> upd { it.copy(fuente = it.fuente.copy(paginaOFigura = v)) } }, "Página, figura o referencia de placa", obligatorio = true)
                CampoTexto(t.fuente.revisionDocumento, { v -> upd { it.copy(fuente = it.fuente.copy(revisionDocumento = v)) } }, "Revisión / edición del documento")
                if (docFuente != null) OutlinedButton(onClick = { Archivos.abrir(ctx, almacen.archivoAdjunto(docFuente), docFuente.mime) }) {
                    Icon(Icons.Filled.PhotoLibrary, null); Text(" Ver documento fuente")
                }
                if (g.documentos.isNotEmpty()) Selector("Documento adjunto de la grúa", g.documentos, docFuente, { it.nombre }, { d -> upd { it.copy(fuente = it.fuente.copy(adjuntoId = d.id)) } })
                HorizontalDivider()
                Text("Verificación contra el original", style = MaterialTheme.typography.titleSmall)
                Ayuda("Marca la verificación solo después de comparar cada valor con la placa o el manual de ESTA grúa (mismo modelo, variante y nº de serie). Si cambias un valor, la verificación se borra.")
                CampoTexto(t.verificacion.porQuien, { v -> upd { it.copy(verificacion = it.verificacion.copy(porQuien = v)) } }, "Verificado por")
                CampoFecha(t.verificacion.fecha, { v -> upd { it.copy(verificacion = it.verificacion.copy(fecha = v)) } }, "Fecha")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(t.verificacion.verificado, { b -> upd { it.copy(verificacion = it.verificacion.copy(verificado = b)) } },
                        enabled = t.verificacion.porQuien.isNotBlank() && t.fuente.completa && t.puntos.isNotEmpty() && g.identificada)
                    Text("Confirmo que los valores coinciden con el documento original de esta grúa", style = MaterialTheme.typography.bodyMedium)
                }
                if (!g.identificada) Text("Para verificar, completa primero fabricante, modelo y nº de serie de la grúa.", style = MaterialTheme.typography.bodySmall, color = Paleta.Revisar)
            }
            Seccion("Notas", desplegable = true, inicialAbierta = false) {
                CampoTexto(t.notas, { v -> upd { it.copy(notas = v) } }, "Notas", multilinea = true)
            }
        }
    }

    if (pegar) AlertDialog(
        onDismissRequest = { pegar = false },
        title = { Text("Pegar valores") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Una fila por línea: radio y capacidad en kg, separados por espacio, tabulador o punto y coma. Ej.:\n4,6  4520\n6,1  3180", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(textoPegar, { textoPegar = it }, Modifier.fillMaxWidth(), minLines = 6)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val nuevos = textoPegar.lines().mapNotNull { l ->
                    val partes = l.trim().split(Regex("[;\\t ]+")).filter { it.isNotBlank() }
                    if (partes.size < 2) null else {
                        val r = Unidades.parse(partes[0]); val c = Unidades.parse(partes[1]); val h = partes.getOrNull(2)?.let { Unidades.parse(it) }
                        if (r != null && c != null) PuntoTabla(r, c, h) else null
                    }
                }
                if (nuevos.isNotEmpty()) updValores { it.copy(puntos = (it.puntos + nuevos)) }
                pegar = false; textoPegar = ""
            }) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = { pegar = false }) { Text("Cancelar") } },
    )
    if (borrar) Confirmar("¿Eliminar tabla?", "Se eliminará la tabla «${t.nombre}».", "Eliminar",
        onConfirmar = { borrar = false; almacen.guardarGrua(g.copy(tablas = g.tablas.filter { it.id != t.id })); onAtras() }, onCancelar = { borrar = false }, peligroso = true)
}

@Suppress("unused") private val f = FuenteTecnica()
