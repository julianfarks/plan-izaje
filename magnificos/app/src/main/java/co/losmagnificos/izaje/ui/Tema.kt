package co.losmagnificos.izaje.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colores corporativos tomados del logotipo de Los Magníficos: naranja y negro. */
object Paleta {
    val Naranja = Color(0xFFF15626)
    val NaranjaClaro = Color(0xFFFFE6DC)
    val Negro = Color(0xFF111111)
    val Fondo = Color(0xFFFAF7F4)
    val Gris = Color(0xFF5A5A5A)
    val Rojo = Color(0xFFB02018)
    val RojoFondo = Color(0xFFFCE8E6)
    val Verde = Color(0xFF1E6E3C)
    val VerdeFondo = Color(0xFFE5F3EA)
    val AmbarFondo = Color(0xFFFFF3E0)
}

@Composable
fun Tema(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Paleta.Naranja, onPrimary = Paleta.Negro,
            primaryContainer = Paleta.NaranjaClaro, onPrimaryContainer = Paleta.Negro,
            secondary = Paleta.Negro, onSecondary = Color.White,
            background = Paleta.Fondo, onBackground = Paleta.Negro,
            surface = Color.White, onSurface = Paleta.Negro,
            surfaceVariant = Color(0xFFF1ECE7), onSurfaceVariant = Paleta.Gris,
            error = Paleta.Rojo, onError = Color.White,
            outline = Color(0xFF8A8480),
        ),
        content = content,
    )
}
