package co.planizaje.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import co.planizaje.core.data.Manifiesto
import co.planizaje.core.data.Serializacion
import co.planizaje.core.model.Accesorio
import co.planizaje.core.model.Adjunto
import co.planizaje.core.model.Cambio
import co.planizaje.core.model.Grua
import co.planizaje.core.model.Proyecto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

fun nuevoId(): String = UUID.randomUUID().toString()

/**
 * Almacenamiento local de PlanIzaje.
 *
 * Cada proyecto, grúa y accesorio es un archivo JSON versionado en files/datos/.
 * Las escrituras son atómicas (archivo temporal + renombrado) y se conserva una copia .bak
 * de la versión anterior, de modo que un corte de energía nunca deja un proyecto ilegible.
 * Una actualización de la app no toca estos archivos; si el esquema cambia, se migran al leer.
 */
class Almacen(private val ctx: Context) {

    private val raiz = ctx.filesDir
    private val dirDatos = File(raiz, "datos")
    private val dirProyectos = File(dirDatos, "proyectos")
    private val dirGruas = File(dirDatos, "gruas")
    private val dirAccesorios = File(dirDatos, "accesorios")
    val dirAdjuntos = File(raiz, "adjuntos")
    val dirPlanes = File(raiz, "planes")
    private val dirCopias = File(raiz, "copias")

    private val _proyectos = MutableStateFlow<List<Proyecto>>(emptyList())
    private val _gruas = MutableStateFlow<List<Grua>>(emptyList())
    private val _accesorios = MutableStateFlow<List<Accesorio>>(emptyList())
    private val _cargado = MutableStateFlow(false)
    private val _avisos = MutableStateFlow<List<String>>(emptyList())

    val proyectos: StateFlow<List<Proyecto>> = _proyectos.asStateFlow()
    val gruas: StateFlow<List<Grua>> = _gruas.asStateFlow()
    val accesorios: StateFlow<List<Accesorio>> = _accesorios.asStateFlow()
    val cargado: StateFlow<Boolean> = _cargado.asStateFlow()
    /** Avisos de carga (archivos dañados recuperados desde copia, etc.). */
    val avisos: StateFlow<List<String>> = _avisos.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trabajos = HashMap<String, Job>()
    private val pendientes = HashMap<String, () -> Unit>()
    private val lock = Any()

    init {
        listOf(dirProyectos, dirGruas, dirAccesorios, dirAdjuntos, dirPlanes, dirCopias).forEach { it.mkdirs() }
        // Carga síncrona: son archivos JSON pequeños y así ninguna pantalla ve la lista vacía al arrancar.
        cargarTodo()
    }

    // ------------------------------------------------------------------ carga

    private fun <T> leerDir(dir: File, parse: (String) -> T): List<T> {
        val out = mutableListOf<T>()
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach { f ->
            val r = runCatching { parse(f.readText()) }
            if (r.isSuccess) out += r.getOrThrow()
            else {
                val bak = File(f.path + ".bak")
                val r2 = if (bak.exists()) runCatching { parse(bak.readText()) } else null
                if (r2 != null && r2.isSuccess) {
                    out += r2.getOrThrow()
                    _avisos.update { it + "Se recuperó ${f.name} desde su copia anterior." }
                } else {
                    // No se borra nada: se aparta para poder recuperarlo manualmente.
                    f.renameTo(File(f.path + ".danado-${System.currentTimeMillis()}"))
                    _avisos.update { it + "No se pudo leer ${f.name}: ${r.exceptionOrNull()?.message}. El archivo se conservó aparte." }
                }
            }
        }
        return out
    }

    fun cargarTodo() {
        _proyectos.value = leerDir(dirProyectos, Serializacion::proyectoDesde).sortedByDescending { it.modificado }
        _gruas.value = leerDir(dirGruas, Serializacion::gruaDesde).sortedBy { it.nombreCorto }
        _accesorios.value = leerDir(dirAccesorios, Serializacion::accesorioDesde).sortedBy { it.tipo.ordinal }
        _cargado.value = true
    }

    // ------------------------------------------------------------------ escritura

    private fun escribirAtomico(destino: File, texto: String) {
        destino.parentFile?.mkdirs()
        val tmp = File(destino.path + ".tmp")
        tmp.writeText(texto)
        if (destino.exists()) destino.copyTo(File(destino.path + ".bak"), overwrite = true)
        if (!tmp.renameTo(destino)) {
            destino.delete()
            tmp.renameTo(destino)
        }
    }

    private fun programar(clave: String, accion: () -> Unit) {
        synchronized(lock) {
            pendientes[clave] = accion
            trabajos[clave]?.cancel()
            trabajos[clave] = scope.launch {
                delay(350)
                val a = synchronized(lock) { pendientes.remove(clave) }
                a?.invoke()
            }
        }
    }

    /** Escribe en disco todo lo pendiente. Se llama al salir de la app. */
    fun volcar() {
        val acciones = synchronized(lock) {
            trabajos.values.forEach { it.cancel() }
            trabajos.clear()
            val l = pendientes.values.toList()
            pendientes.clear()
            l
        }
        acciones.forEach { runCatching { it() } }
    }

    // ------------------------------------------------------------------ proyectos

    fun proyecto(id: String): Proyecto? = _proyectos.value.firstOrNull { it.id == id }

    fun guardarProyecto(p: Proyecto) {
        val ahora = System.currentTimeMillis()
        val nuevo = p.copy(modificado = ahora, creado = if (p.creado == 0L) ahora else p.creado)
        _proyectos.update { l -> (listOf(nuevo) + l.filter { it.id != p.id }).sortedByDescending { it.modificado } }
        programar("p:${p.id}") { escribirAtomico(File(dirProyectos, "${p.id}.json"), Serializacion.proyectoAJson(nuevo)) }
    }

    /**
     * Modifica un proyecto. Si [cambio] no es nulo se anota en el historial;
     * cambios iguales y seguidos en menos de 3 minutos se agrupan para no llenar el historial.
     */
    fun actualizarProyecto(id: String, cambio: String? = null, f: (Proyecto) -> Proyecto) {
        val actual = proyecto(id) ?: return
        var nuevo = f(actual)
        if (nuevo == actual) return
        if (cambio != null) {
            val ahora = System.currentTimeMillis()
            val ult = nuevo.historial.lastOrNull()
            val h = if (ult != null && ult.descripcion == cambio && ahora - ult.fecha < 180_000) {
                nuevo.historial.dropLast(1) + ult.copy(fecha = ahora)
            } else nuevo.historial + Cambio(ahora, cambio, nuevo.revision)
            nuevo = nuevo.copy(historial = h.takeLast(300))
        }
        guardarProyecto(nuevo)
    }

    fun crearProyecto(): Proyecto {
        val hoy = java.time.LocalDate.now().toString()
        val p = Proyecto(id = nuevoId(), fecha = hoy, historial = listOf(Cambio(System.currentTimeMillis(), "Proyecto creado", 0)))
        guardarProyecto(p)
        return p
    }

    fun duplicarProyecto(id: String): Proyecto? {
        val p = proyecto(id) ?: return null
        val copia = p.copy(
            id = nuevoId(), nombre = p.nombre.ifBlank { "Proyecto" } + " (copia)", revision = 0,
            versionesPdf = emptyList(), archivado = false, creado = 0L,
            revisionHumana = co.planizaje.core.model.RevisionHumana(),
            historial = listOf(Cambio(System.currentTimeMillis(), "Duplicado de «${p.titulo}»", 0)),
        )
        guardarProyecto(copia)
        return copia
    }

    fun eliminarProyecto(id: String) {
        synchronized(lock) { pendientes.remove("p:$id"); trabajos.remove("p:$id")?.cancel() }
        _proyectos.update { l -> l.filter { it.id != id } }
        scope.launch {
            File(dirProyectos, "$id.json").delete()
            File(dirProyectos, "$id.json.bak").delete()
            File(dirPlanes, id).deleteRecursively()
        }
    }

    // ------------------------------------------------------------------ grúas

    fun grua(id: String?): Grua? = id?.let { i -> _gruas.value.firstOrNull { it.id == i } }

    fun guardarGrua(g: Grua) {
        val ahora = System.currentTimeMillis()
        val n = g.copy(modificado = ahora, creado = if (g.creado == 0L) ahora else g.creado)
        _gruas.update { l -> (l.filter { it.id != g.id } + n).sortedBy { it.nombreCorto } }
        programar("g:${g.id}") { escribirAtomico(File(dirGruas, "${g.id}.json"), Serializacion.gruaAJson(n)) }
    }

    /** Crea una grúa desde una plantilla de foto, copiando la foto del diagrama como documento fuente. */
    fun crearGruaDesdePlantilla(pl: co.planizaje.core.model.PlantillaGrua): Grua {
        val adj = runCatching {
            val id = nuevoId()
            val ext = pl.asset.substringAfterLast('.')
            val archivo = "$id.$ext"
            ctx.assets.open(pl.asset).use { inp -> File(dirAdjuntos, archivo).outputStream().use { inp.copyTo(it) } }
            Adjunto(id, "Diagrama de carga ${pl.fabricante} ${pl.modelo}", archivo, if (ext == "png") "image/png" else "image/jpeg", co.planizaje.core.model.PlantillasGruas.DOC, System.currentTimeMillis())
        }.getOrNull()
        val g = co.planizaje.core.model.PlantillasGruas.crear(pl, ::nuevoId, adj)
        guardarGrua(g)
        return g
    }

    fun eliminarGrua(id: String) {
        synchronized(lock) { pendientes.remove("g:$id"); trabajos.remove("g:$id")?.cancel() }
        _gruas.update { l -> l.filter { it.id != id } }
        scope.launch { File(dirGruas, "$id.json").delete(); File(dirGruas, "$id.json.bak").delete() }
    }

    // ------------------------------------------------------------------ accesorios

    fun accesorio(id: String?): Accesorio? = id?.let { i -> _accesorios.value.firstOrNull { it.id == i } }

    fun guardarAccesorio(a: Accesorio) {
        val ahora = System.currentTimeMillis()
        val n = a.copy(modificado = ahora, creado = if (a.creado == 0L) ahora else a.creado)
        _accesorios.update { l -> (l.filter { it.id != a.id } + n).sortedWith(compareBy({ it.tipo.ordinal }, { it.identificacion })) }
        programar("a:${a.id}") { escribirAtomico(File(dirAccesorios, "${a.id}.json"), Serializacion.accesorioAJson(n)) }
    }

    fun eliminarAccesorio(id: String) {
        synchronized(lock) { pendientes.remove("a:$id"); trabajos.remove("a:$id")?.cancel() }
        _accesorios.update { l -> l.filter { it.id != id } }
        scope.launch { File(dirAccesorios, "$id.json").delete(); File(dirAccesorios, "$id.json.bak").delete() }
    }

    // ------------------------------------------------------------------ adjuntos

    fun archivoAdjunto(a: Adjunto): File = File(dirAdjuntos, a.archivo)

    /** Copia un archivo elegido por el usuario al almacenamiento interno de la app. */
    fun importarAdjunto(uri: Uri, descripcion: String = ""): Adjunto? = runCatching {
        val cr = ctx.contentResolver
        var nombre = "archivo"
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) nombre = c.getString(0) ?: nombre
        }
        val mime = cr.getType(uri) ?: MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(nombre.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: nombre.substringAfterLast('.', "bin")
        val id = nuevoId()
        val archivo = "$id.$ext"
        cr.openInputStream(uri)!!.use { inp -> File(dirAdjuntos, archivo).outputStream().use { inp.copyTo(it) } }
        Adjunto(id, nombre, archivo, mime, descripcion, System.currentTimeMillis())
    }.getOrNull()

    /** Archivo vacío para que la cámara guarde una foto; devuelve el adjunto ya registrado. */
    fun nuevaFoto(): Pair<Adjunto, File> {
        val id = nuevoId()
        val f = File(dirAdjuntos, "$id.jpg")
        return Adjunto(id, "Foto ${java.time.LocalDateTime.now().toString().take(16).replace('T', ' ')}", f.name, "image/jpeg", "", System.currentTimeMillis()) to f
    }

    // ------------------------------------------------------------------ copias de seguridad

    fun exportarCopia(out: OutputStream, versionApp: String) {
        volcar()
        val adj = dirAdjuntos.listFiles()?.filter { it.isFile } ?: emptyList()
        ZipOutputStream(out.buffered()).use { zip ->
            val man = Manifiesto(
                versionApp = versionApp, fecha = System.currentTimeMillis(),
                proyectos = _proyectos.value.size, gruas = _gruas.value.size, accesorios = _accesorios.value.size, adjuntos = adj.size,
            )
            zip.putNextEntry(ZipEntry("manifiesto.json"))
            zip.write(Serializacion.json.encodeToString(Manifiesto.serializer(), man).toByteArray())
            zip.closeEntry()
            fun agregar(dir: File, prefijo: String) {
                dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach { f ->
                    zip.putNextEntry(ZipEntry("$prefijo/${f.name}")); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
            agregar(dirProyectos, "datos/proyectos")
            agregar(dirGruas, "datos/gruas")
            agregar(dirAccesorios, "datos/accesorios")
            adj.forEach { f -> zip.putNextEntry(ZipEntry("adjuntos/${f.name}")); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
        }
    }

    data class ResumenImportacion(val proyectos: Int, val gruas: Int, val accesorios: Int, val adjuntos: Int, val omitidos: Int, val copiaPrevia: String)

    /**
     * Restaura una copia. Antes de tocar nada guarda una copia automática del estado actual.
     * reemplazar = false → combina: si un elemento existe en ambos lados se queda el modificado más recientemente.
     */
    fun importarCopia(entrada: InputStream, reemplazar: Boolean, versionApp: String): ResumenImportacion {
        volcar()
        val previa = File(dirCopias, "auto-antes-de-restaurar-${System.currentTimeMillis()}.zip")
        previa.outputStream().use { exportarCopia(it, versionApp) }

        val tmp = File(ctx.cacheDir, "restaurar-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            var manifiesto: Manifiesto? = null
            ZipInputStream(entrada.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val n = e.name
                    val valido = !e.isDirectory && !n.contains("..") && !n.startsWith("/") &&
                        (n == "manifiesto.json" || n.startsWith("datos/proyectos/") || n.startsWith("datos/gruas/") ||
                            n.startsWith("datos/accesorios/") || n.startsWith("adjuntos/"))
                    if (valido) {
                        val dest = File(tmp, n)
                        require(dest.canonicalPath.startsWith(tmp.canonicalPath)) { "Ruta no válida en la copia" }
                        dest.parentFile?.mkdirs()
                        dest.outputStream().use { zip.copyTo(it) }
                        if (n == "manifiesto.json") manifiesto = Serializacion.json.decodeFromString(Manifiesto.serializer(), dest.readText())
                    }
                    e = zip.nextEntry
                }
            }
            requireNotNull(manifiesto) { "El archivo no es una copia de seguridad de PlanIzaje (falta manifiesto.json)." }
            require(manifiesto!!.esquema <= co.planizaje.core.model.ESQUEMA_VERSION) {
                "La copia es de una versión más nueva de la app. Actualice PlanIzaje antes de restaurarla."
            }
            var omitidos = 0
            fun <T> leer(sub: String, parse: (String) -> T): List<T> =
                File(tmp, sub).listFiles()?.mapNotNull { f -> runCatching { parse(f.readText()) }.getOrElse { omitidos++; null } } ?: emptyList()
            val ps = leer("datos/proyectos", Serializacion::proyectoDesde)
            val gs = leer("datos/gruas", Serializacion::gruaDesde)
            val ac = leer("datos/accesorios", Serializacion::accesorioDesde)

            if (reemplazar) {
                listOf(dirProyectos, dirGruas, dirAccesorios).forEach { d -> d.listFiles()?.forEach { it.delete() } }
            }
            val pAct = if (reemplazar) emptyMap() else _proyectos.value.associateBy { it.id }
            val gAct = if (reemplazar) emptyMap() else _gruas.value.associateBy { it.id }
            val aAct = if (reemplazar) emptyMap() else _accesorios.value.associateBy { it.id }
            var np = 0; var ng = 0; var na = 0
            ps.forEach { p -> if ((pAct[p.id]?.modificado ?: -1) < p.modificado) { escribirAtomico(File(dirProyectos, "${p.id}.json"), Serializacion.proyectoAJson(p)); np++ } }
            gs.forEach { g -> if ((gAct[g.id]?.modificado ?: -1) < g.modificado) { escribirAtomico(File(dirGruas, "${g.id}.json"), Serializacion.gruaAJson(g)); ng++ } }
            ac.forEach { a -> if ((aAct[a.id]?.modificado ?: -1) < a.modificado) { escribirAtomico(File(dirAccesorios, "${a.id}.json"), Serializacion.accesorioAJson(a)); na++ } }
            var nadj = 0
            File(tmp, "adjuntos").listFiles()?.forEach { f ->
                val d = File(dirAdjuntos, f.name)
                if (!d.exists()) { f.copyTo(d); nadj++ }
            }
            cargarTodo()
            return ResumenImportacion(np, ng, na, nadj, omitidos, previa.name)
        } finally {
            tmp.deleteRecursively()
        }
    }

    fun copiasAutomaticas(): List<File> = dirCopias.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
}
