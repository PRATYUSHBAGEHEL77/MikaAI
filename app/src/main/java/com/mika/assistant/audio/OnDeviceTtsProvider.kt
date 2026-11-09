package com.mika.assistant.audio

/** Compatibility wrapper with no Android system TTS fallback. */
class OnDeviceTtsProvider(private val kokoro: TtsProvider) : TtsProvider {
    override fun speak(text: String) = kokoro.speak(text)
    override fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit) =
        kokoro.speak(text, onStart, onDone)
    override fun shutdown() = kokoro.shutdown()
}
