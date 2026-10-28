package co.losmagnificos.izaje.voz

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Dictado en español con el reconocedor de voz del propio teléfono (SpeechRecognizer).
 * - No usa servicios de pago ni claves: depende del reconocimiento instalado en el teléfono.
 * - Según el teléfono puede necesitar internet o el paquete de voz en español sin conexión (Ajustes de Android > Idiomas).
 * - En modo continuo se reinicia solo tras cada pausa hasta que el usuario pulsa «Detener».
 */
class Dictado(private val ctx: Context) {

    /** Identifica el campo que se está dictando (para pintar su micrófono activo). */
    var destino by mutableStateOf<String?>(null); private set
    var escuchando by mutableStateOf(false); private set
    var parcial by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null); private set
    var nivel by mutableStateOf(0f); private set

    private var rec: SpeechRecognizer? = null
    private var continuo = false
    private var activo = false
    private var alTexto: ((String) -> Unit)? = null

    val disponible: Boolean get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    private fun intencion() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-CO")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
    }

    private val oyente = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { error = null }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { nivel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { nivel = 0f }
        override fun onError(code: Int) {
            parcial = ""
            when (code) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    if (activo && continuo) reiniciar() else detener()
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> if (activo && continuo) reiniciar() else detener()
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                    error = "El reconocimiento de voz necesita internet en este teléfono, o instalar el paquete de voz en español sin conexión. Puedes escribir."
                    detener()
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { error = "Falta el permiso del micrófono. Puedes escribir."; detener() }
                else -> { error = "No se pudo reconocer la voz (código $code). Inténtalo de nuevo o escribe."; detener() }
            }
        }
        override fun onResults(results: Bundle?) {
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            parcial = ""
            if (t.isNotBlank()) alTexto?.invoke(t)
            if (activo && continuo) reiniciar() else detener()
        }
        override fun onPartialResults(partialResults: Bundle?) {
            parcial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun iniciar(idDestino: String, enContinuo: Boolean, alRecibir: (String) -> Unit) {
        if (!disponible) {
            error = "Este teléfono no tiene un servicio de reconocimiento de voz. Escribe los datos con el teclado."
            return
        }
        error = null
        destino = idDestino
        continuo = enContinuo
        alTexto = alRecibir
        activo = true
        if (rec == null) rec = SpeechRecognizer.createSpeechRecognizer(ctx).also { it.setRecognitionListener(oyente) }
        rec?.startListening(intencion())
        escuchando = true
    }

    private fun reiniciar() {
        rec?.cancel()
        rec?.startListening(intencion())
    }

    fun fallo(mensaje: String) { error = mensaje }

    fun detener() {
        activo = false
        escuchando = false
        destino = null
        nivel = 0f
        runCatching { rec?.stopListening() }
    }

    fun liberar() {
        activo = false
        escuchando = false
        destino = null
        runCatching { rec?.destroy() }
        rec = null
    }
}
