package com.mika.assistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mika.assistant.agent.ChatMessage
import com.mika.assistant.audio.ElevenLabsTtsProvider
import com.mika.assistant.audio.TtsProvider
import com.mika.assistant.localai.LocalGemmaEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MikaUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage("Mika", "Hii! I'm Mika. Download my local AI model and let's chat offline! ✨", false)
    ),
    val status: String = "Gemma model not downloaded",
    val busy: Boolean = false,
    val modelDownloaded: Boolean = false,
    val modelReady: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val speaking: Boolean = false,
    val listening: Boolean = false
)

class MikaViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(MikaUiState())
    val state: StateFlow<MikaUiState> = _state.asStateFlow()

    private val tts: TtsProvider = ElevenLabsTtsProvider(app)
    private val localAi = LocalGemmaEngine(app)

    init {
        if (localAi.isModelDownloaded) {
            _state.value = _state.value.copy(modelDownloaded = true, status = "Gemma found. Loading Mika…")
            loadModel()
        }
    }

    fun setListening(value: Boolean) {
        _state.value = _state.value.copy(listening = value, status = if (value) "Listening…" else _state.value.status)
    }

    private fun speakWithAvatar(text: String) {
        tts.speak(
            text,
            onStart = { _state.value = _state.value.copy(speaking = true, listening = false) },
            onDone = { _state.value = _state.value.copy(speaking = false) }
        )
    }

    fun systemMessage(text: String) {
        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatMessage("System", text, false),
            status = text
        )
    }

    fun downloadAndLoadModel() {
        if (_state.value.downloading || _state.value.modelReady) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                downloading = true,
                busy = false,
                status = "Downloading Gemma 4 E2B…",
                downloadProgress = 0f
            )
            try {
                localAi.downloadModel { downloaded, total ->
                    _state.value = _state.value.copy(
                        downloadProgress = (downloaded.toFloat() / total.coerceAtLeast(1L)).coerceIn(0f, 1f),
                        status = "Downloading Gemma: ${(downloaded / 1_000_000)} MB"
                    )
                }
                _state.value = _state.value.copy(
                    downloading = false,
                    modelDownloaded = true,
                    status = "Download complete. Loading Mika…"
                )
                loadModel()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    downloading = false,
                    modelDownloaded = localAi.isModelDownloaded,
                    status = "Download failed"
                )
                systemMessage(t.message ?: "Couldn't download the model. Check storage and internet, then retry.")
            }
        }
    }

    private fun loadModel() {
        if (_state.value.busy || _state.value.modelReady) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, status = "Loading local AI model…")
            try {
                localAi.load()
                _state.value = _state.value.copy(
                    busy = false,
                    modelDownloaded = true,
                    modelReady = true,
                    status = "Ready · ElevenLabs voice · Online"
                )
                speakWithAvatar("Mika is ready!")
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    busy = false,
                    modelReady = false,
                    modelDownloaded = localAi.isModelDownloaded,
                    status = "Couldn't load Gemma"
                )
                systemMessage(t.message ?: "Model loading failed. Try restarting Mika.")
            }
        }
    }

    fun send(text: String) {
        if (text.isBlank() || _state.value.busy) return
        if (!_state.value.modelReady) {
            systemMessage("Please download and load my local AI model first.")
            return
        }

        val assistantIndex = _state.value.messages.size + 1
        _state.value = _state.value.copy(
            messages = _state.value.messages + listOf(
                ChatMessage("You", text, true),
                ChatMessage("Mika", "", false)
            ),
            busy = true,
            status = "Mika is thinking locally…"
        )

        viewModelScope.launch {
            val response = StringBuilder()
            try {
                localAi.streamReply(text).collect { piece ->
                    response.append(piece)
                    val messages = _state.value.messages.toMutableList()
                    val lastIndex = messages.lastIndex
                    if (lastIndex >= 0 && messages[lastIndex].speaker == "Mika" && !messages[lastIndex].fromUser) {
                        messages[lastIndex] = messages[lastIndex].copy(text = response.toString())
                        _state.value = _state.value.copy(messages = messages, status = "Generating on your phone…")
                    }
                }
                val finalText = response.toString().trim().ifBlank { "I'm ready for your next message!" }
                val messages = _state.value.messages.toMutableList()
                if (messages.isNotEmpty() && messages.last().speaker == "Mika") {
                    messages[messages.lastIndex] = messages.last().copy(text = finalText)
                }
                _state.value = _state.value.copy(messages = messages, busy = false, status = "Ready · ElevenLabs voice")
                speakWithAvatar(finalText)
            } catch (t: Throwable) {
                val messages = _state.value.messages.toMutableList()
                if (messages.isNotEmpty() && messages.last().speaker == "Mika" && messages.last().text.isBlank()) {
                    messages[messages.lastIndex] = messages.last().copy(text = t.message ?: "Local generation failed.")
                }
                _state.value = _state.value.copy(messages = messages, busy = false, status = "Generation error")
            }
        }
    }

    override fun onCleared() {
        tts.shutdown()
        viewModelScope.launch { localAi.close() }
        super.onCleared()
    }
}
