package com.mika.assistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeech
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeechOptions
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.min

class KokoroTtsProvider(private val context: Context) : TtsProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    @Volatile private var engine: TextToSpeech? = null
    @Volatile private var speaking = false
    private val modelFile = File(context.filesDir, MODEL_NAME)

    override fun speak(text: String) = speak(text, {}, {})

    override fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit) {
        if (text.isBlank()) {
            main.post { onDone() }
            return
        }
        scope.launch {
            try {
                val tts = getEngine()
                val result = tts.synthesizeAsync(text.trim())
                if (result.samples.isEmpty()) throw IllegalStateException("Kokoro returned empty audio")
                playPcm(result.samples, result.sampleRateHz, onStart)
            } catch (_: Exception) {
                // No Android system TTS fallback. Failure leaves the reply as text.
            } finally {
                speaking = false
                main.post { onDone() }
            }
        }
    }

    private suspend fun getEngine(): TextToSpeech {
        engine?.let { return it }
        return withContext(Dispatchers.IO) {
            engine?.let { return@withContext it }
            if (!modelFile.exists() || modelFile.length() < 200_000_000L) downloadModel()
            val options = TextToSpeechOptions(
                baseOptions = BaseOptions.builder()
                    .setModelAssetPath(modelFile.absolutePath)
                    .setDelegate(Delegate.GPU)
                    .build(),
                defaultVoiceId = "hf_alpha",
                defaultLocale = "hi",
                speakingRate = 1.04f,
                targetBucket = 128,
                cacheDirectory = File(context.cacheDir, "kokoro-cache").absolutePath
            )
            try {
                TextToSpeech.create(options).also { engine = it }
            } catch (_: Exception) {
                val cpuOptions = TextToSpeechOptions(
                    baseOptions = BaseOptions.builder()
                        .setModelAssetPath(modelFile.absolutePath)
                        .setDelegate(Delegate.CPU)
                        .build(),
                    defaultVoiceId = "hf_alpha",
                    defaultLocale = "hi",
                    speakingRate = 1.04f,
                    targetBucket = 128,
                    cacheDirectory = File(context.cacheDir, "kokoro-cache").absolutePath
                )
                TextToSpeech.create(cpuOptions).also { engine = it }
            }
        }
    }

    private fun downloadModel() {
        val temp = File(context.filesDir, "$MODEL_NAME.part")
        val request = Request.Builder().url(MODEL_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Kokoro download failed: HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("Kokoro download returned no data")
            FileOutputStream(temp).use { output -> body.byteStream().use { it.copyTo(output) } }
        }
        if (temp.length() < 200_000_000L) {
            temp.delete()
            throw IllegalStateException("Kokoro model download was incomplete")
        }
        if (!temp.renameTo(modelFile)) {
            temp.copyTo(modelFile, overwrite = true)
            temp.delete()
        }
    }

    private suspend fun playPcm(samples: FloatArray, sampleRate: Int, onStart: () -> Unit) {
        withContext(Dispatchers.IO) {
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            try {
                track.play()
                if (!speaking) {
                    speaking = true
                    main.post { onStart() }
                }
                val block = ShortArray(4096)
                var offset = 0
                while (offset < samples.size) {
                    val count = min(block.size, samples.size - offset)
                    for (i in 0 until count) {
                        block[i] = (samples[offset + i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    }
                    var written = 0
                    while (written < count) {
                        val n = track.write(block, written, count - written)
                        if (n <= 0) throw IllegalStateException("Audio playback failed")
                        written += n
                    }
                    offset += count
                }
                track.stop()
            } finally {
                track.release()
            }
        }
    }

    override fun shutdown() {
        scope.cancel()
        try { engine?.close() } catch (_: Exception) {}
        engine = null
    }

    companion object {
        private const val MODEL_NAME = "kokoro_int8_gpu_fp16.litertlm"
        private const val MODEL_URL =
            "https://huggingface.co/litert-community/Kokoro-82M/resolve/main/kokoro_int8_gpu_fp16.litertlm"
    }
}
