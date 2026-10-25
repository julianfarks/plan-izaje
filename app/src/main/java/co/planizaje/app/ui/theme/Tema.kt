package co.planizaje.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Paleta {
    val Azul = Color(0xFF16263D)
    val AzulMedio = Color(0xFF2B4466)
    val Ambar = Color(0xFFF2A900)
    val AmbarClaro = Color(0xFFFFE3A1)
    val Fondo = Color(0xFFF6F4EF)
    val Superficie = Color(0xFFFFFFFF)
    val Tinta = Color(0xFF1B1F24)

    // Estados (siempre acompañados de símbolo y texto)
    val Ok = Color(0xFF1E7A46)
    val OkFondo = Color(0xFFDFF3E6)
    val Revisar = Color(0xFF9A6200)
    val RevisarFondo = Color(0xFFFFF0CC)
    val Pendiente = Color(0xFF4A5868)
    val PendienteFondo = Color(0xFFE6EAF0)
    val Mal = Color(0xFFB3261E)
    val MalFondo = Color(0xFFFCE1DF)
}

private val Claro = lightColorScheme(
    primary = Paleta.Azul,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3F4),
    onPrimaryContainer = Paleta.Azul,
    secondary = Paleta.Ambar,
    onSecondary = Paleta.Azul,
    secondaryContainer = Paleta.AmbarClaro,
    onSecondaryContainer = Color(0xFF3A2A00),
    background = Paleta.Fondo,
    onBackground = Paleta.Tinta,
    surface = Paleta.Superficie,
    onSurface = Paleta.Tinta,
    surfaceVariant = Color(0xFFEDEAE3),
    onSurfaceVariant = Color(0xFF4A4F57),
    outline = Color(0xFF8A8F98),
    error = Paleta.Mal,
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFA9C7F0),
    onPrimary = Color(0xFF0B1A2C),
    primaryContainer = Paleta.AzulMedio,
    onPrimaryContainer = Color(0xFFD7E3F4),
    secondary = Paleta.Ambar,
    onSecondary = Color(0xFF241A00),
    secondaryContainer = Color(0xFF5A4300),
    onSecondaryContainer = Paleta.AmbarClaro,
    background = Color(0xFF101418),
    onBackground = Color(0xFFE3E6EA),
    surface = Color(0xFF181D23),
    onSurface = Color(0xFFE3E6EA),
    surfaceVariant = Color(0xFF242A31),
    onSurfaceVariant = Color(0xFFC3C7CE),
    outline = Color(0xFF8A8F98),
)

private val Tipografia = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        bodyLarge = t.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = t.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    )
}

val EstiloNumero = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)

@Composable
fun TemaPlanIzaje(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro,
        typography = Tipografia,
        content = content,
    )
}
