package com.mika.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.mika.assistant.R

/**
 * User-started foreground microphone session.
 * Android SpeechRecognizer is used as a speech input service, not a true always-on wake-word detector.
 * A dedicated on-device wake-word model/engine is a separate integration step.
 */
class MikaForegroundService : Service() {
    private var recognizer: SpeechRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopListening(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, notification())
                startListening()
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle("Mika is active")
        .setContentText("Microphone session is running. Tap Stop in Mika to end it.")
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Mika microphone", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { speech ->
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {}
                override fun onResults(results: android.os.Bundle?) {
                    // The foreground capture is intentionally not an always-on keyword engine.
                    // The Activity's interactive recognizer handles command capture in Stage 1.
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            // Keep service active with persistent notification; recognizer sessions are deliberately
            // not looped as a covert always-listening mechanism.
        }
    }

    private fun stopListening() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    override fun onDestroy() { stopListening(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.mika.assistant.START_MIC"
        const val ACTION_STOP = "com.mika.assistant.STOP_MIC"
        private const val CHANNEL_ID = "mika_microphone"
        private const val NOTIFICATION_ID = 2401
    }
}
