package com.mika.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.mika.assistant.MainActivity
import com.mika.assistant.R
import java.util.Locale

/**
 * User-started microphone foreground service.
 *
 * Uses Android's speech recognition service to listen for a wake phrase.
 * This is a best-effort implementation, NOT a dedicated low-power/offline
 * wake-word engine. Recognition availability, network use and OEM background
 * restrictions depend on the device and installed recognition provider.
 */
class MikaForegroundService : Service() {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var restarting = false
    private var lastWakeAt = 0L

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                listening = false
                stopListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, notification("Say “Hey Mika” to wake me"))
                listening = true
                startListening()
            }
        }
        return START_STICKY
    }

    private fun notification(message: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, MikaForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Mika is listening")
            .setContentText(message)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop listening", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(message))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Mika background microphone", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun startListening() {
        if (!listening || restarting) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Speech recognition unavailable on this device")
            return
        }
        restarting = true
        stopRecognizerOnly()
        try {
            val speech = if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    restarting = false
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    restarting = false
                    scheduleRestart()
                }
                override fun onResults(results: android.os.Bundle?) {
                    restarting = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val phrase = matches.firstOrNull().orEmpty().lowercase(Locale.ROOT)
                    val wake = phrase.contains("hey mika") ||
                        phrase.contains("hello mika") ||
                        phrase == "mika" || phrase.startsWith("mika ")
                    if (wake) {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastWakeAt > 5000L) {
                            lastWakeAt = now
                            updateNotification("Mika heard you. Tap notification to give a command.")
                            sendBroadcast(Intent(ACTION_WAKE).setPackage(packageName))
                        }
                    }
                    scheduleRestart()
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.lowercase(Locale.ROOT).orEmpty()
                    if (partial.contains("hey mika") || partial.contains("hello mika")) {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastWakeAt > 5000L) {
                            lastWakeAt = now
                            updateNotification("Mika heard you. Tap notification to give a command.")
                            sendBroadcast(Intent(ACTION_WAKE).setPackage(packageName))
                        }
                    }
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            speech.startListening(request)
        } catch (_: Exception) {
            restarting = false
            scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        if (!listening) return
        android.os.Handler(mainLooper).postDelayed({ startListening() }, 700L)
    }

    private fun stopRecognizerOnly() {
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    private fun stopListening() {
        stopRecognizerOnly()
        restarting = false
    }

    override fun onDestroy() {
        listening = false
        stopListening()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.mika.assistant.START_MIC"
        const val ACTION_STOP = "com.mika.assistant.STOP_MIC"
        const val ACTION_WAKE = "com.mika.assistant.WAKE_DETECTED"
        private const val CHANNEL_ID = "mika_microphone"
        private const val NOTIFICATION_ID = 2401
    }
}
