package com.mika.assistant.localai

import android.content.Context
import android.os.StatFs
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * On-device Gemma runtime. The model is downloaded once to app-private storage.
 * No prompt or generated text is sent to a cloud API.
 */
class LocalGemmaEngine(context: Context) {
    private val appContext = context.applicationContext
    private val modelDir = File(appContext.filesDir, "models").apply { mkdirs() }
    private val modelFile = File(modelDir, MODEL_FILE)
    private val partialFile = File(modelDir, "$MODEL_FILE.part")
    private val engineMutex = Mutex()
    private val generationMutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    val isModelDownloaded: Boolean
        get() = modelFile.exists() && modelFile.length() > MIN_VALID_BYTES

    fun modelSizeBytes(): Long = MODEL_SIZE_BYTES

    suspend fun downloadModel(onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        if (isModelDownloaded) {
            onProgress(modelFile.length(), MODEL_SIZE_BYTES)
            return@withContext
        }

        val available = StatFs(appContext.filesDir.absolutePath).availableBytes
        require(available >= REQUIRED_FREE_BYTES) {
            "Mika needs at least 3 GB of free internal storage before downloading Gemma."
        }

        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MikaAndroid/1.1")
        }

        try {
            connection.connect()
            val code = connection.responseCode
            require(code in 200..299) { "Model download failed (HTTP $code)." }
            val reportedLength = connection.contentLengthLong
            val total = if (reportedLength > 0) reportedLength else MODEL_SIZE_BYTES

            partialFile.delete()
            connection.inputStream.use { input ->
                FileOutputStream(partialFile).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var downloaded = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                    output.fd.sync()
                }
            }

            require(partialFile.length() > MIN_VALID_BYTES) {
                "The downloaded model file is incomplete. Please retry."
            }

            val actualSha = sha256(partialFile)
            require(actualSha.equals(MODEL_SHA256, ignoreCase = true)) {
                "Model integrity check failed. Delete the partial download and try again."
            }

            if (modelFile.exists()) modelFile.delete()
            check(partialFile.renameTo(modelFile)) { "Could not save the downloaded model." }
            onProgress(modelFile.length(), MODEL_SIZE_BYTES)
        } catch (t: Throwable) {
            partialFile.delete()
            throw t
        } finally {
            connection.disconnect()
        }
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            if (conversation != null) return@withLock
            require(isModelDownloaded) { "Download Gemma before loading Mika." }

            val newEngine = Engine(
                EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = appContext.cacheDir.absolutePath
                )
            )
            try {
                newEngine.initialize()
                val config = ConversationConfig(
                    systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.92, temperature = 0.75)
                )
                val newConversation = newEngine.createConversation(config)
                engine = newEngine
                conversation = newConversation
            } catch (t: Throwable) {
                runCatching { newEngine.close() }
                throw t
            }
        }
    }

    fun streamReply(prompt: String): Flow<String> = flow {
        generationMutex.withLock {
            val activeConversation = conversation
                ?: throw IllegalStateException("Mika's local model is not loaded yet.")
            activeConversation.sendMessageAsync(prompt).collect { message ->
                val piece = message.text
                if (piece.isNotEmpty()) emit(piece)
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        engineMutex.withLock {
            runCatching { conversation?.close() }
            conversation = null
            runCatching { engine?.close() }
            engine = null
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
        const val MODEL_SIZE_BYTES = 2_580_000_000L
        const val REQUIRED_FREE_BYTES = 3_000_000_000L
        const val MIN_VALID_BYTES = 2_000_000_000L
        const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/08ca8d1009c97dd9a7d2be053860cb03c0d186a5/gemma-4-E2B-it.litertlm"
        const val MODEL_SHA256 =
            "ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42"

        private const val SYSTEM_INSTRUCTION = """
            You are Mika, a cheerful, kind, concise assistant with a cute anime-inspired personality.
            Speak naturally and keep ordinary replies to at most two short sentences when possible.
            You are running locally on the user's phone. Do not claim to have performed phone actions.
            You cannot open apps, set alarms, call people, send messages, or control the phone in this
            local-chat build. Explain this honestly if asked. Never request passwords, OTPs, payment PINs,
            or other sensitive credentials.
        """
    }
}
