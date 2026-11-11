package com.mika.assistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.mika.assistant.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * ElevenLabs speech provider. The API key lives only on the protected proxy server.
 * The APK contains the voice ID and proxy URL, never the ElevenLabs secret.
 */
class ElevenLabsTtsProvider(private val context: Context) : TtsProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(100, TimeUnit.SECONDS)
        .build()
    @Volatile private var player: MediaPlayer? = null

    override fun speak(text: String) = speak(text, {}, {})

    override fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit) {
        val clean = text.trim().take(MAX_TEXT_CHARS)
        if (clean.isBlank()) {
            main.post { onDone() }
            return
        }
        scope.launch {
            var temp: File? = null
            try {
                val base = BuildConfig.ELEVENLABS_PROXY_URL.trim().trimEnd('/')
                if (base.isBlank()) throw IllegalStateException("ElevenLabs proxy URL is not configured")
                val payload = JSONObject().put("text", clean).toString()
                val request = Request.Builder()
                    .url("$base/v1/tts")
                    .post(payload.toRequestBody(JSON))
                    .build()
                val response = client.newCall(request).execute()
                response.use { res ->
                    if (!res.isSuccessful) {
                        val detail = res.body?.string()?.take(300).orEmpty()
                        throw IllegalStateException("Voice service error ${res.code}: $detail")
                    }
                    val body = res.body ?: throw IllegalStateException("Voice service returned no audio")
                    temp = File.createTempFile("mika-eleven-", ".mp3", context.cacheDir)
                    FileOutputStream(temp).use { out -> body.byteStream().use { input -> input.copyTo(out) } }
                }
                val audioFile = temp ?: throw IllegalStateException("No audio file was created")
                withContext(Dispatchers.Main) {
                    stopPlayer()
                    val mp = MediaPlayer()
                    player = mp
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    mp.setDataSource(audioFile.absolutePath)
                    mp.setOnPreparedListener {
                        onStart()
                        it.start()
                    }
                    mp.setOnCompletionListener {
                        stopPlayer()
                        audioFile.delete()
                        onDone()
                    }
                    mp.setOnErrorListener { _, what, extra ->
                        stopPlayer()
                        audioFile.delete()
                        onDone()
                        true
                    }
                    mp.prepareAsync()
                }
            } catch (e: Exception) {
                temp?.delete()
                main.post { onDone() }
            }
        }
    }

    private fun stopPlayer() {
        try { player?.setOnCompletionListener(null); player?.setOnErrorListener(null); player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    override fun shutdown() {
        scope.cancel()
        main.post { stopPlayer() }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val MAX_TEXT_CHARS = 1200
    }
}
