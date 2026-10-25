package co.planizaje.app.ui.asistente

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.planizaje.app.ui.comp.Aviso
import co.planizaje.app.ui.comp.Ayuda
import co.planizaje.app.ui.comp.CampoEntero
import co.planizaje.app.ui.comp.CampoNumero
import co.planizaje.app.ui.comp.CampoPeso
import co.planizaje.app.ui.comp.CampoTexto
import co.planizaje.app.ui.comp.FilaInterruptor
import co.planizaje.app.ui.comp.GaleriaAdjuntos
import co.planizaje.app.ui.comp.Seccion
import co.planizaje.app.ui.comp.Selector
import co.planizaje.core.model.Carga
import co.planizaje.core.model.OrigenPeso

@Composable
fun PasoCarga(c: Ctx) {
    val k = c.p.carga
    fun upd(desc: String, f: (Carga) -> Carga) = c.actualizar(desc) { it.copy(carga = f(it.carga)) }

    Ayuda("Registra solo lo que sabes. Si un dato no se conoce, déjalo vacío: la app lo marcará como pendiente en vez de inventarlo.")

    Seccion("Qué se va a izar", icono = Icons.Filled.Inventory2) {
        CampoTexto(k.descripcion, { v -> upd("Descripción de la carga") { it.copy(descripcion = v) } }, "Descripción de la carga", obligatorio = true, clave = "carga.descripcion", marcador = "Ej. Tanque de agua 5 000 L vacío")
    }

    Seccion("Peso", icono = Icons.Filled.Scale, subtitulo = "Peso solo de la carga, sin eslingas ni accesorios") {
        CampoPeso(k.pesoKg, { v -> upd("Peso de la carga") { it.copy(pesoKg = v) } }, "Peso de la carga", obligatorio = true, clave = "carga.peso")
        Selector(
            "¿De dónde sale este peso?", OrigenPeso.entries, k.origenPeso, { it.etiqueta },
            { o -> upd("Origen del peso") { it.copy(origenPeso = o) } }, clave = "carga.origen",
        )
        CampoTexto(
            k.referenciaPeso, { v -> upd("Referencia del peso") { it.copy(referenciaPeso = v) } },
            "Documento o ticket que lo respalda", clave = "carga.referencia", ayuda = "Ej. Placa del equipo, plano 03 rev. B, ticket báscula Nº 1234",
        )
        if (k.pesoKg != null && !k.origenPeso.documentado) {
            Aviso("Un peso ${k.origenPeso.etiqueta.lowercase()} no es un dato verificado. El plan quedará como borrador hasta confirmarlo.")
        }
        Ayuda("Los accesorios (eslingas, grilletes, balancín) se suman aparte en el paso de maniobra.")
    }

    Seccion("Dimensiones", icono = Icons.Filled.Straighten, desplegable = true, inicialAbierta = k.largo != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoNumero(k.largo, { v -> upd("Dimensiones de la carga") { it.copy(largo = v) } }, "Largo", "m", Modifier.weight(1f))
            CampoNumero(k.ancho, { v -> upd("Dimensiones de la carga") { it.copy(ancho = v) } }, "Ancho", "m", Modifier.weight(1f))
        }
        CampoNumero(k.alto, { v -> upd("Dimensiones de la carga") { it.copy(alto = v) } }, "Alto", "m")
        FilaInterruptor(
            "Conozco el centro de gravedad", k.cdgConocido, { b -> upd("Centro de gravedad") { it.copy(cdgConocido = b) } },
            "Si no se conoce, la app supone que está centrado y lo indica como hipótesis.",
        )
        if (k.cdgConocido) {
            Ayuda("Distancia del centro de gravedad respecto al centro geométrico de la carga. Positivo hacia el lado del ramal 2.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoNumero(k.cdgX, { v -> upd("Centro de gravedad") { it.copy(cdgX = v) } }, "Desplaz. X", "m", Modifier.weight(1f), min = null)
                CampoNumero(k.cdgY, { v -> upd("Centro de gravedad") { it.copy(cdgY = v) } }, "Desplaz. Y", "m", Modifier.weight(1f), min = null)
            }
            CampoNumero(k.cdgZ, { v -> upd("Centro de gravedad") { it.copy(cdgZ = v) } }, "Altura desde la base", "m")
        }
    }

    Seccion("Puntos de izaje y sujeción", icono = Icons.Filled.Anchor, desplegable = true, inicialAbierta = true) {
        CampoEntero(k.numeroPuntosIzaje, { v -> upd("Puntos de izaje") { it.copy(numeroPuntosIzaje = v) } }, "Número de puntos de izaje", max = 8)
        CampoNumero(
            k.wllPuntoIzajeKg, { v -> upd("Puntos de izaje") { it.copy(wllPuntoIzajeKg = v) } }, "Capacidad (WLL) de cada punto", "kg", clave = "carga.wllPuntos",
            ayuda = "Solo si está documentada (orejas, cáncamos, placas). Déjalo vacío si no se conoce.",
        )
        CampoTexto(k.descripcionPuntos, { v -> upd("Puntos de izaje") { it.copy(descripcionPuntos = v) } }, "Descripción de los puntos", multilinea = true)
        CampoTexto(k.metodoSujecion, { v -> upd("Método de sujeción") { it.copy(metodoSujecion = v) } }, "Método de sujeción", clave = "carga.sujecion", marcador = "Ej. 2 eslingas en canasta bajo la base")
    }

    Seccion("Posición inicial y final", icono = Icons.Filled.Place, desplegable = true, inicialAbierta = true) {
        CampoTexto(k.posicionInicial, { v -> upd("Posiciones de la carga") { it.copy(posicionInicial = v) } }, "Posición inicial", clave = "carga.posInicial", marcador = "Ej. Sobre camión en la calle")
        CampoTexto(k.posicionFinal, { v -> upd("Posiciones de la carga") { it.copy(posicionFinal = v) } }, "Posición final", clave = "carga.posFinal", marcador = "Ej. Cubierta del edificio, nivel +9,0 m")
        Ayuda("Las posiciones exactas se marcan en el croquis (paso 4).")
    }

    Seccion("Fotos, documentos y observaciones", icono = Icons.Filled.PhotoLibrary, desplegable = true, inicialAbierta = k.fotos.isNotEmpty()) {
        GaleriaAdjuntos("Fotos de la carga", k.fotos, { l -> upd("Fotos de la carga") { it.copy(fotos = l) } }, documentos = false)
        GaleriaAdjuntos("Documentos técnicos", k.documentos, { l -> upd("Documentos de la carga") { it.copy(documentos = l) } }, fotos = false)
        CampoTexto(k.observaciones, { v -> upd("Observaciones de la carga") { it.copy(observaciones = v) } }, "Observaciones", multilinea = true)
    }
}
