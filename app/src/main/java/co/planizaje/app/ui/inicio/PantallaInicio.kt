package co.planizaje.app.ui.inicio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import co.planizaje.app.BuildConfig
import co.planizaje.app.ui.Rutas
import co.planizaje.app.ui.comp.Aviso
import co.planizaje.app.ui.comp.LocalAlmacen
import co.planizaje.app.ui.theme.Paleta

@Composable
fun PantallaInicio(nav: NavController) {
    val almacen = LocalAlmacen.current
    val proyectos by almacen.proyectos.collectAsState()
    val gruas by almacen.gruas.collectAsState()
    val accesorios by almacen.accesorios.collectAsState()
    val avisos by almacen.avisos.collectAsState()
    val ultimo = proyectos.filter { !it.archivado }.maxByOrNull { it.modificado }
    val totalPdf = proyectos.sumOf { it.versionesPdf.size }
    var menu by remember { mutableStateOf(false) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()),
        ) {
            // Cabecera con identidad visual
            Box(
                Modifier.fillMaxWidth().background(Paleta.Azul, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .padding(horizontal = 20.dp, vertical = 22.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(Paleta.Ambar, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text("PlanIzaje", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más opciones", tint = Color.White) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Copias de seguridad") }, leadingIcon = { Icon(Icons.Filled.Backup, null) },
                                    onClick = { menu = false; nav.navigate(Rutas.COPIAS) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height8())
                    Text("Planes de izaje claros, paso a paso.", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyLarge)
                }
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                avisos.forEach { Aviso(it) }

                OpcionInicio(
                    Icons.Filled.AddCircle, "Crear plan de izaje",
                    "Un asistente te guía en 7 pasos: obra, grúa, carga, croquis, maniobra, verificación y PDF.",
                    destacado = true,
                ) {
                    val p = almacen.crearProyecto()
                    nav.navigate(Rutas.asistente(p.id, 0))
                }
                OpcionInicio(
                    Icons.Filled.History, "Continuar un proyecto",
                    if (ultimo != null) "Seguir con «${ultimo.titulo}» donde lo dejaste." else "Aún no hay proyectos empezados.",
                    habilitado = ultimo != null,
                ) { ultimo?.let { nav.navigate(Rutas.asistente(it.id, -1)) } }
                OpcionInicio(Icons.Filled.FolderOpen, "Mis proyectos", "Abrir, duplicar, archivar o eliminar planes. (${proyectos.count { !it.archivado }} activos)") {
                    nav.navigate(Rutas.PROYECTOS)
                }
                OpcionInicio(Icons.Filled.LocalShipping, "Mis grúas", "Equipos, configuraciones y tablas de carga con su fuente. (${gruas.size})") {
                    nav.navigate(Rutas.GRUAS)
                }
                OpcionInicio(Icons.Filled.Link, "Mis accesorios", "Eslingas, grilletes, ganchos y balancines con WLL e inspección. (${accesorios.size})") {
                    nav.navigate(Rutas.ACCESORIOS)
                }
                OpcionInicio(Icons.Filled.PictureAsPdf, "Planes PDF", "Documentos generados, listos para abrir o compartir. ($totalPdf)") {
                    nav.navigate(Rutas.PLANES)
                }
                Text(
                    "Herramienta de planificación y documentación. No sustituye el manual del fabricante ni la revisión de una persona competente.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text("Versión ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

private fun Modifier.height8() = this.then(Modifier.padding(top = 8.dp))

@Composable
private fun OpcionInicio(
    icono: ImageVector, titulo: String, descripcion: String,
    destacado: Boolean = false, habilitado: Boolean = true, onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = habilitado,
        modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (destacado) Paleta.Ambar else MaterialTheme.colorScheme.surface,
            contentColor = if (destacado) Paleta.Azul else MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(if (destacado) 3.dp else 1.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(
                    if (destacado) Paleta.Azul else MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp),
                ),
                contentAlignment = Alignment.Center,
            ) { Icon(icono, null, tint = if (destacado) Paleta.Ambar else MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(descripcion, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
        }
    }
}
