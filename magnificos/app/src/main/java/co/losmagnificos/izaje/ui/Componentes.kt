package co.losmagnificos.izaje.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun BotonPrimario(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, icono: ImageVector? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Paleta.Naranja, contentColor = Paleta.Negro),
    ) {
        if (icono != null) { Icon(icono, null); Spacer(Modifier.width(10.dp)) }
        Text(texto, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BotonOscuro(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, icono: ImageVector? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Paleta.Negro, contentColor = Color.White),
    ) {
        if (icono != null) { Icon(icono, null); Spacer(Modifier.width(10.dp)) }
        Text(texto, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BotonContorno(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, icono: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, Paleta.Negro),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Paleta.Negro),
    ) {
        if (icono != null) { Icon(icono, null); Spacer(Modifier.width(8.dp)) }
        Text(texto, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Tarjeta(titulo: String? = null, modifier: Modifier = Modifier, fondo: Color = Color.White, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = fondo),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (titulo != null) Text(titulo, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** Campo grande, con micrófono opcional para dictar ese dato. */
@Composable
fun Campo(
    valor: String, onCambio: (String) -> Unit, etiqueta: String, modifier: Modifier = Modifier,
    teclado: KeyboardType = KeyboardType.Text, mayusculas: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    sufijo: String? = null, apoyo: String? = null, error: Boolean = false, singleLine: Boolean = true,
    onMic: (() -> Unit)? = null, micActivo: Boolean = false,
) {
    OutlinedTextField(
        value = valor, onValueChange = onCambio,
        label = { Text(etiqueta) },
        modifier = modifier.fillMaxWidth().heightIn(min = 60.dp),
        textStyle = TextStyle(fontSize = 18.sp),
        singleLine = singleLine,
        isError = error,
        suffix = if (sufijo != null) ({ Text(sufijo, fontSize = 16.sp) }) else null,
        supportingText = if (apoyo != null) ({ Text(apoyo) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = teclado, capitalization = mayusculas),
        shape = RoundedCornerShape(12.dp),
        trailingIcon = if (onMic != null) ({
            IconButton(onClick = onMic) {
                Icon(
                    if (micActivo) Icons.Filled.Stop else Icons.Filled.Mic,
                    if (micActivo) "Detener dictado" else "Dictar $etiqueta",
                    tint = if (micActivo) Paleta.Rojo else Paleta.Negro,
                )
            }
        }) else null,
    )
}

@Composable
fun Etiqueta(texto: String, fondo: Color, color: Color, modifier: Modifier = Modifier) {
    Text(
        texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color,
        modifier = modifier.background(fondo, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun EtiquetaEstado(estado: String, modifier: Modifier = Modifier) {
    when (estado) {
        "Preliminar" -> Etiqueta("PRELIMINAR", Paleta.RojoFondo, Paleta.Rojo, modifier)
        "Para revisión" -> Etiqueta("PARA REVISIÓN", Paleta.AmbarFondo, Paleta.Negro, modifier)
        else -> Etiqueta("SIN PDF", Color(0xFFEDEDED), Paleta.Gris, modifier)
    }
}

@Composable
fun FilaTexto(izq: String, der: String, modifier: Modifier = Modifier, negrita: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(izq, fontSize = 16.sp, color = color, modifier = Modifier.weight(1f).padding(end = 8.dp), fontWeight = if (negrita) FontWeight.Bold else FontWeight.Normal)
        Text(der, fontSize = 16.sp, color = color, fontWeight = if (negrita) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Abrir, compartir y guardar el PDF con las apps del teléfono. */
object Archivos {
    private fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.archivos", f)

    fun compartir(ctx: Context, f: File, asunto: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri(ctx, f))
            putExtra(Intent.EXTRA_SUBJECT, asunto)
            putExtra(Intent.EXTRA_TEXT, asunto)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Compartir plan de izaje"))
    }

    /** `false` si el teléfono no tiene ninguna app para ver PDF. */
    fun abrir(ctx: Context, f: File): Boolean {
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri(ctx, f), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try { ctx.startActivity(i); true } catch (e: ActivityNotFoundException) { false }
    }

    fun copiarA(ctx: Context, origen: File, destino: Uri): Boolean = try {
        ctx.contentResolver.openOutputStream(destino)?.use { out -> origen.inputStream().use { it.copyTo(out) } } != null
    } catch (e: Exception) { false }
}
