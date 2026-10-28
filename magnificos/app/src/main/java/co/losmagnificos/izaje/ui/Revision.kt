package co.losmagnificos.izaje.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.losmagnificos.izaje.AppViewModel
import co.losmagnificos.izaje.core.Amarre
import co.losmagnificos.izaje.core.Campo as C
import co.losmagnificos.izaje.core.ClavesFaltante
import co.losmagnificos.izaje.core.DatosIzaje
import co.losmagnificos.izaje.core.Edicion
import co.losmagnificos.izaje.core.EstadoEslingas
import co.losmagnificos.izaje.core.EstadoFila
import co.losmagnificos.izaje.core.Faltante
import co.losmagnificos.izaje.core.Formato
import co.losmagnificos.izaje.core.RefAngulo
import co.losmagnificos.izaje.core.Texto

private enum class Tipo { TEXTO, KG, M, NUM }

private class Def(val clave: String, val etiqueta: String, val tipo: Tipo, val leer: (DatosIzaje) -> String)

private fun kgS(v: Double?) = Formato.editable(v)

private val DEFS = listOf(
    Def(C.EQUIPO, "Grúa (marca y modelo)", Tipo.TEXTO) { it.equipo.orEmpty() },
    Def(C.NOMINAL, "Capacidad nominal máxima de la grúa", Tipo.KG) { kgS(it.capacidadNominalKg) },
    Def(C.CARGA, "Peso de la carga", Tipo.KG) { kgS(it.cargaKg) },
    Def(C.GANCHO, "Peso del gancho", Tipo.KG) { kgS(it.ganchoKg) },
    Def(C.ESLINGAS_KG, "Peso de las eslingas (total)", Tipo.KG) { kgS(it.eslingasKg) },
    Def(C.ESLINGA_UNIT_KG, "Peso de cada eslinga", Tipo.KG) { kgS(it.eslingaUnitKg) },
    Def(C.GRILLETES_KG, "Peso de los grilletes (total)", Tipo.KG) { kgS(it.grilletesKg) },
    Def(C.GRILLETE_UNIT_KG, "Peso de cada grillete", Tipo.KG) { kgS(it.grilleteUnitKg) },
    Def(C.N_GRILLETES, "Número de grilletes", Tipo.NUM) { kgS(it.nGrilletes?.toDouble()) },
    Def(C.BALANCIN, "Peso del balancín o barra separadora", Tipo.KG) { kgS(it.balancinKg) },
    Def(C.OTROS, "Peso de otros accesorios", Tipo.KG) { kgS(it.otrosKg) },
    Def(C.RADIO_INICIAL, "Radio inicial (donde recoges)", Tipo.M) { kgS(it.radioInicialM) },
    Def(C.RADIO_FINAL, "Radio final (donde colocas)", Tipo.M) { kgS(it.radioFinalM) },
    Def(C.RADIO_MAX, "Radio máximo", Tipo.M) { kgS(it.radioMaximoM) },
    Def(C.ALTURA, "Altura de izaje", Tipo.M) { kgS(it.alturaM) },
    Def(C.N_ESLINGAS, "Número de eslingas", Tipo.NUM) { kgS(it.nEslingas?.toDouble()) },
    Def(C.WLL, "Capacidad de cada eslinga", Tipo.KG) { kgS(it.wllEslingaKg) },
    Def(C.ANGULO, "Ángulo de las eslingas", Tipo.NUM) { kgS(it.anguloGrados) },
)

private val SUGERIDOS = listOf(C.GANCHO, C.ESLINGAS_KG, C.GRILLETES_KG, C.BALANCIN, C.OTROS, C.ALTURA, C.RADIO_MAX, C.NOMINAL, C.ANGULO)

private fun sufijo(t: Tipo, clave: String) = when {
    clave == C.ANGULO -> "°"
    t == Tipo.KG -> "kg"
    t == Tipo.M -> "m"
    else -> null
}

/** Texto numérico suelto → se acepta tal cual; con unidad («3 t», «50 cm») también. */
private fun valor(t: Tipo, texto: String) = texto

@Composable
private fun EntradaDato(vm: AppViewModel, clave: String, etiqueta: String, tipo: Tipo, inicial: String = "", apoyo: String? = null) {
    var texto by remember(vm.versionDatos, clave) { mutableStateOf(inicial) }
    Campo(
        valor = texto,
        onCambio = { t -> texto = t; vm.cambiarDatos { Edicion.establecer(it, clave, valor(tipo, t)) } },
        etiqueta = etiqueta,
        teclado = if (tipo == Tipo.TEXTO) KeyboardType.Text else KeyboardType.Decimal,
        mayusculas = if (tipo == Tipo.TEXTO) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
        sufijo = sufijo(tipo, clave), apoyo = apoyo,
    )
}

@Composable
private fun FaltaItem(vm: AppViewModel, f: Faltante) {
    val k = f.clave
    when {
        k == ClavesFaltante.OPERADOR -> Campo(vm.plan.operador, { v -> vm.cambiar { it.copy(operador = v) } }, f.texto, mayusculas = KeyboardCapitalization.Words)
        k == ClavesFaltante.CEDULA -> Campo(vm.plan.cedula, { v -> vm.cambiar { it.copy(cedula = v) } }, f.texto, teclado = KeyboardType.Number)
        k == ClavesFaltante.CLIENTE -> Campo(vm.plan.cliente, { v -> vm.cambiar { it.copy(cliente = v) } }, f.texto, mayusculas = KeyboardCapitalization.Words)
        k == ClavesFaltante.SITIO -> Campo(vm.plan.sitio, { v -> vm.cambiar { it.copy(sitio = v) } }, f.texto)
        k == ClavesFaltante.CARGA -> EntradaDato(vm, C.CARGA, f.texto, Tipo.KG, apoyo = "Ej.: 3000 o 3 t")
        k == ClavesFaltante.EQUIPO -> EntradaDato(vm, C.EQUIPO, f.texto, Tipo.TEXTO)
        k == ClavesFaltante.RADIO -> {
            Text(f.texto, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            EntradaDato(vm, C.RADIO_INICIAL, "Radio donde recoges", Tipo.M)
            EntradaDato(vm, C.RADIO_FINAL, "Radio donde colocas", Tipo.M)
        }
        k.startsWith(ClavesFaltante.CAPACIDAD) -> {
            val radio = Texto.simple(k.removePrefix(ClavesFaltante.CAPACIDAD))
            var texto by remember(vm.versionDatos, k) { mutableStateOf("") }
            Campo(
                texto,
                { t ->
                    texto = t
                    val kg = Texto.pesoKg(t)
                    if (radio != null && kg != null) vm.cambiarDatos { Edicion.ponerCapacidad(it, radio, kg) }
                },
                f.texto, teclado = KeyboardType.Decimal, sufijo = "kg", apoyo = "Cópiala de la tabla de cargas de tu grúa",
            )
        }
        k == ClavesFaltante.ESLINGAS -> {
            EntradaDato(vm, C.N_ESLINGAS, "Número de eslingas", Tipo.NUM)
            BotonContorno("No uso eslingas", { vm.cambiarDatos { Edicion.conSinEslingas(it, true) } }, Modifier.fillMaxWidth())
        }
        k == ClavesFaltante.WLL -> EntradaDato(vm, C.WLL, f.texto, Tipo.KG, apoyo = "Está en la etiqueta de la eslinga")
        k == ClavesFaltante.ANGULO -> EntradaDato(vm, C.ANGULO, f.texto, Tipo.NUM)
        k == ClavesFaltante.REF_ANGULO -> {
            Text(f.texto, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(vm.plan.datos.refAngulo == RefAngulo.HORIZONTAL, { vm.cambiarDatos { Edicion.conRefAngulo(it, RefAngulo.HORIZONTAL) } }, { Text("Desde la horizontal") })
                FilterChip(vm.plan.datos.refAngulo == RefAngulo.VERTICAL, { vm.cambiarDatos { Edicion.conRefAngulo(it, RefAngulo.VERTICAL) } }, { Text("Desde la vertical") })
            }
        }
        else -> Text("• ${f.texto}", fontSize = 16.sp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaRevision(vm: AppViewModel, onVolver: () -> Unit) {
    val plan = vm.plan
    val d = plan.datos
    val a = vm.analisis()
    // Lo que falta se fija al abrir (o al reinterpretar) para que los campos no desaparezcan mientras se escribe.
    val faltaInicial = remember(vm.versionDatos) { a.criticos }
    val visibles = remember(vm.versionDatos) { mutableStateListOf<String>().apply { addAll(DEFS.filter { it.leer(d).isNotEmpty() }.map { it.clave }) } }
    var nuevoRadio by remember { mutableStateOf("") }
    var nuevaCap by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
            Text("Revisar datos", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        Etiqueta(
            a.estado.etiqueta,
            if (a.estado.name == "PRELIMINAR") Paleta.RojoFondo else Paleta.AmbarFondo,
            if (a.estado.name == "PRELIMINAR") Paleta.Rojo else Paleta.Negro,
        )

        if (faltaInicial.isNotEmpty()) {
            Tarjeta("Falta esto para completar el plan", fondo = Paleta.AmbarFondo) {
                Text("Solo lo indispensable. Si no lo tienes, puedes generar un borrador preliminar.", fontSize = 15.sp)
                faltaInicial.forEach { FaltaItem(vm, it) }
            }
        }

        Tarjeta("Lo que entendí") {
            if (DEFS.none { it.clave in visibles }) {
                Text("Aún no se entendió ningún dato. Vuelve y describe la maniobra, o agrega datos aquí abajo.", fontSize = 15.sp)
            }
            DEFS.filter { it.clave in visibles }.forEach { def ->
                var texto by remember(vm.versionDatos, def.clave) { mutableStateOf(def.leer(d)) }
                val frase = d.frases[def.clave]
                val apoyo = buildString {
                    if (def.clave in d.aproximados) append("≈ aproximado. ")
                    if (def.clave in d.editados) append("Corregido por ti. ")
                    else if (frase != null) append("Dijiste: «$frase»")
                }.ifBlank { null }
                Campo(
                    texto,
                    { t -> texto = t; vm.cambiarDatos { Edicion.establecer(it, def.clave, valor(def.tipo, t)) } },
                    def.etiqueta,
                    teclado = if (def.tipo == Tipo.TEXTO) KeyboardType.Text else KeyboardType.Decimal,
                    mayusculas = if (def.tipo == Tipo.TEXTO) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                    sufijo = sufijo(def.tipo, def.clave), apoyo = apoyo,
                )
            }
            val agregables = SUGERIDOS.filter { it !in visibles }
            if (agregables.isNotEmpty()) {
                Text("Agregar un dato", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    agregables.forEach { c ->
                        val def = DEFS.first { it.clave == c }
                        AssistChip(onClick = { visibles.add(c) }, label = { Text("+ " + def.etiqueta) })
                    }
                }
            }
        }

        Tarjeta("Capacidad de la grúa por radio") {
            Text("Escribe solo lo que dice la tabla de cargas de tu grúa. La app no inventa ni interpola capacidades.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            d.capacidades.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("A ${Formato.num(p.radioM, 2)} m:", fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text(Formato.kgYt(p.capacidadKg), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = {
                        vm.cambiarDatos { x -> Edicion.conCapacidades(x, x.capacidades.filterNot { it.radioM == p.radioM }) }
                    }) { Icon(Icons.Filled.Delete, "Quitar", tint = Paleta.Gris) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campo(nuevoRadio, { nuevoRadio = it }, "Radio", Modifier.weight(1f), teclado = KeyboardType.Decimal, sufijo = "m")
                Campo(nuevaCap, { nuevaCap = it }, "Capacidad", Modifier.weight(1f), teclado = KeyboardType.Decimal, sufijo = "kg")
            }
            BotonContorno("Agregar capacidad", {
                val r = Texto.longitudM(nuevoRadio)
                val c = Texto.pesoKg(nuevaCap)
                if (r != null && c != null) {
                    vm.cambiarDatos { Edicion.ponerCapacidad(it, r, c) }
                    nuevoRadio = ""; nuevaCap = ""
                } else vm.aviso = "Escribe el radio (m) y la capacidad (kg)."
            }, Modifier.fillMaxWidth())
        }

        Tarjeta("Eslingas") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("No uso eslingas", fontSize = 17.sp, modifier = Modifier.weight(1f))
                Switch(d.sinEslingas, { v -> vm.cambiarDatos { Edicion.conSinEslingas(it, v) } })
            }
            if (!d.sinEslingas) {
                Text("Tipo de amarre", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Amarre.values().forEach { am ->
                        FilterChip(d.amarre == am, { vm.cambiarDatos { Edicion.conAmarre(it, if (d.amarre == am) null else am) } }, { Text(am.etiqueta) })
                    }
                }
                Text("El ángulo se mide", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(RefAngulo.HORIZONTAL, RefAngulo.VERTICAL).forEach { r ->
                        FilterChip(d.refAngulo == r, { vm.cambiarDatos { Edicion.conRefAngulo(it, r) } }, { Text(r.etiqueta) })
                    }
                }
            }
        }

        // Resultados
        Tarjeta("Resultados") {
            Text("Peso total suspendido", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            a.peso.lineas.forEach { FilaTexto(it.concepto, Formato.kg(it.kg)) }
            a.peso.incluidosEnOtro.forEach { Text("• $it: ya incluido en otro valor", fontSize = 14.sp) }
            a.peso.sinDato.forEach { Text("• $it: sin dato, no se sumó", fontSize = 14.sp, color = Paleta.Rojo) }
            FilaTexto("TOTAL", if (a.peso.totalKg == null) "sin peso de la carga" else Formato.kgYt(a.peso.totalKg), negrita = true)

            if (a.filas.isNotEmpty()) {
                Spacer(Modifier.width(1.dp))
                Text("Utilización por radio", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                a.filas.forEach { f ->
                    val (txt, col) = when (f.estado) {
                        EstadoFila.DENTRO -> "${Formato.pct(f.utilizacionPct)} · margen ${Formato.kg(f.margenKg)}" to Paleta.Verde
                        EstadoFila.EXCEDE -> "EXCEDE · ${Formato.pct(f.utilizacionPct)}" to Paleta.Rojo
                        EstadoFila.SIN_CAPACIDAD -> "falta capacidad a este radio" to Paleta.Rojo
                        EstadoFila.SIN_PESO -> "falta el peso" to Paleta.Rojo
                    }
                    FilaTexto("${f.etiqueta} · ${Formato.num(f.radioM, 2)} m" + if (f.critico) " (crítico)" else "", txt, color = col)
                }
            }

            val e = a.eslingas
            if (!d.sinEslingas) {
                Text("Eslingas", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                when (e.estado) {
                    EstadoEslingas.NO_CALCULABLE -> Text("No se puede calcular todavía: faltan datos de las eslingas.", fontSize = 15.sp)
                    else -> {
                        e.tensionRamalKg?.let { FilaTexto("Tensión por ramal (${Formato.grados(e.anguloHorizontal)} con la horizontal)", Formato.kg(it)) }
                        e.utilizacionPct?.let { FilaTexto("Utilización de la eslinga", Formato.pct(it), color = if (e.excede) Paleta.Rojo else Paleta.Verde) }
                        e.tensionMinimaKg?.let { FilaTexto("Tensión mínima (ramales verticales)", Formato.kg(it)) }
                        e.anguloMinimoGrados?.let { FilaTexto("Ángulo mínimo con la horizontal", Formato.grados(it)) }
                        if (e.referencias.isNotEmpty()) {
                            Text("Referencia sin ángulo confirmado:", fontSize = 14.sp)
                            e.referencias.forEach { (ang, t) -> FilaTexto("A ${Formato.grados(ang)}", Formato.kg(t)) }
                        }
                    }
                }
                e.notas.forEach { Text("• $it", fontSize = 14.sp) }
            }

            if (a.alertas.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().background(Paleta.RojoFondo, RoundedCornerShape(10.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    a.alertas.forEach { Text("⚠ $it", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Paleta.Rojo) }
                }
            }
            val porConfirmar = a.faltantes.filter { !it.critico }
            if (porConfirmar.isNotEmpty()) {
                Text("Por confirmar", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                porConfirmar.forEach { Text("• ${it.texto}", fontSize = 14.sp) }
            }
            Text(
                "Cálculo informativo con los datos entregados, en condiciones ideales. No reemplaza la tabla de cargas oficial ni el criterio del responsable del izaje, y no declara la maniobra como segura.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Tarjeta("Observaciones (opcional)") {
            Campo(plan.observaciones, { v -> vm.cambiar { it.copy(observaciones = v) } }, "Observaciones del plan", singleLine = false)
        }

        val completo = a.criticos.isEmpty() && !a.hayExcedencias
        BotonPrimario(if (completo) "Generar PDF" else "Generar borrador preliminar", { vm.generarPdf() }, icono = Icons.Filled.PictureAsPdf)
        Spacer(Modifier.padding(bottom = 24.dp))
    }
}
