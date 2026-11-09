package com.mika.assistant.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class AndroidTtsProvider(context: Context) : TtsProvider {
    private var ready = false
    private var startCallback: (() -> Unit)? = null
    private var doneCallback: (() -> Unit)? = null
    private val cache = linkedMapOf<String, String>()
    private val engine = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            engineLanguage()
        }
    }

    init {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                startCallback?.invoke()
            }
            override fun onDone(utteranceId: String?) {
                doneCallback?.invoke()
                startCallback = null
                doneCallback = null
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                doneCallback?.invoke()
                startCallback = null
                doneCallback = null
            }
        })
    }

    private fun engineLanguage() {
        engine.language = Locale.getDefault()
        engine.setPitch(1.12f)
        engine.setSpeechRate(1.02f)
    }

    override fun speak(text: String) {
        speak(text, {}, {})
    }

    override fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit) {
        if (!ready || text.isBlank()) {
            onDone()
            return
        }
        startCallback = onStart
        doneCallback = onDone
        val normalized = text.trim()
        if (normalized.length < 48) cache[normalized] = normalized
        engine.speak(
            cache[normalized] ?: normalized,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "mika-${System.nanoTime()}"
        )
    }

    override fun shutdown() {
        engine.stop()
        engine.shutdown()
    }
}
