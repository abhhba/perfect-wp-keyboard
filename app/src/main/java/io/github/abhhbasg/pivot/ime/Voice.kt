package io.github.abhhbasg.pivot.ime

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Dictation with the phone's speech recognition. It listens until stopped, as the phone's keyboard does: what is heard so far
 * goes into the text as it is spoken, and each finished phrase is committed and listening starts again.
 */
class Voice(private val context: Context, private val listener: Listener) {
    interface Listener {
        /** "listening", "heard", or an error to show in the bar. */
        fun onVoiceState(state: State)
        fun onPartial(text: String)
        fun onFinal(text: String)
    }

    enum class State { LISTENING, THINKING, STOPPED, NO_PERMISSION, UNAVAILABLE, ERROR }

    private var recognizer: SpeechRecognizer? = null
    private var language = "zh-CN"
    var active = false; private set

    fun available() = SpeechRecognizer.isRecognitionAvailable(context)

    fun hasPermission() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start(lang: String) {
        language = lang
        if (!available()) { listener.onVoiceState(State.UNAVAILABLE); return }
        if (!hasPermission()) {
            listener.onVoiceState(State.NO_PERMISSION)
            context.startActivity(Intent(context, MicPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        active = true
        listen()
    }

    fun stop() {
        active = false
        recognizer?.cancel(); recognizer?.destroy(); recognizer = null
        listener.onVoiceState(State.STOPPED)
    }

    private fun listen() {
        if (!active) return
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it; it.setRecognitionListener(callbacks) }
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        })
    }

    private val callbacks = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = listener.onVoiceState(State.LISTENING)
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() = listener.onVoiceState(State.THINKING)
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (it.isNotBlank()) listener.onPartial(it) }
        }
        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (it.isNotBlank()) listener.onFinal(it) }
            listen()
        }
        override fun onError(error: Int) {
            // Silence and "nothing understood" are part of listening: start again. Anything else stops.
            if (active && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) { listen(); return }
            active = false
            recognizer?.destroy(); recognizer = null
            listener.onVoiceState(if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) State.NO_PERMISSION else State.ERROR)
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}

/** Asks for the microphone for the keyboard, which cannot show the question itself, and closes. */
class MicPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }
}
