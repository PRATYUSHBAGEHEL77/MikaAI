package com.mika.assistant.audio

/** Legacy Android system TTS is intentionally disabled. */
@Deprecated("Use KokoroTtsProvider. Android system TTS is disabled.")
class AndroidTtsProvider : TtsProvider {
    override fun speak(text: String) = Unit
    override fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit) { onDone() }
    override fun shutdown() = Unit
}
