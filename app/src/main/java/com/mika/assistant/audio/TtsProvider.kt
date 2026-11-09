package com.mika.assistant.audio

interface TtsProvider {
    fun speak(text: String)
    fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit) {
        speak(text)
        onStart()
        onDone()
    }
    fun shutdown()
}
