package com.mika.assistant.audio

import android.content.Context

/**
 * Adapter seam for a future sherpa-onnx/Kokoro model.
 * Add a compatible sherpa-onnx Android AAR and model files before selecting this provider.
 * Until then AndroidTtsProvider remains the functional fallback.
 */
class OnDeviceTtsProvider(private val fallback: TtsProvider) : TtsProvider {
    override fun speak(text: String) = fallback.speak(text)
    override fun shutdown() = fallback.shutdown()
}
