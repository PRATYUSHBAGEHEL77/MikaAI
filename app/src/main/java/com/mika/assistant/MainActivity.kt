package com.mika.assistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mika.assistant.ui.MikaViewModel

class MainActivity : ComponentActivity() {
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MikaApp(this) }
    }

    fun startVoiceCapture(onText: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            onError("Speech recognition is not available on this device.")
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { onError("I couldn't hear that. Please try again.") }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (spoken.isNullOrBlank()) onError("I didn't catch that.") else onText(spoken)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            recognizer.startListening(intent)
        }
    }

    override fun onDestroy() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }
}

@Composable
private fun MikaApp(activity: MainActivity, vm: MikaViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var typed by remember { mutableStateOf("") }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            vm.setListening(true)
            activity.startVoiceCapture(
                onText = { vm.setListening(false); vm.send(it) },
                onError = { vm.setListening(false); vm.systemMessage(it) }
            )
        } else vm.systemMessage("Microphone permission is needed for voice input.")
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFB59AFF), secondary = Color(0xFF73DCEB),
        background = Color(0xFF0C0D18), surface = Color(0xFF191B2C)
    )) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF111226), Color(0xFF090A12))))) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(54.dp).background(Color(0xFF30294D), CircleShape), contentAlignment = Alignment.Center) {
                        Text("M", color = Color(0xFFE6DFFF), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("MIKA", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                        Text("Your personal voice companion", color = Color(0xFFB8B8CC))
                    }
                    Text(if (state.busy) "THINKING" else "READY", color = Color(0xFF78DCEB), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(10.dp))
                MikaAvatarCard(
                    speaking = state.speaking,
                    listening = state.listening,
                    thinking = state.busy && !state.downloading,
                    subtitle = state.messages.lastOrNull { it.speaker == "Mika" && !it.fromUser }?.text.orEmpty()
                )
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF201D37)), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(state.status, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Gemma 4 E2B · Local AI · No API key · Offline after download", color = Color(0xFFB9B6CC), style = MaterialTheme.typography.bodySmall)
                        if (state.downloading) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { state.downloadProgress }, modifier = Modifier.fillMaxWidth())
                            Text("${(state.downloadProgress * 100).toInt()}% downloaded · Keep Mika open", color = Color(0xFFB9B6CC), style = MaterialTheme.typography.labelSmall)
                        }
                        if (!state.modelReady && !state.downloading) {
                            Spacer(Modifier.height(8.dp))
                            Text("First setup needs about 2.58 GB download and at least 3 GB free internal storage.", color = Color(0xFFDDD8EF), style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { vm.downloadAndLoadModel() },
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (state.modelDownloaded) "Load Mika" else "Download Gemma (2.58 GB)")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.messages) { msg ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start) {
                            Surface(color = if (msg.fromUser) Color(0xFF1E3841) else Color(0xFF292441), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.padding(12.dp).widthIn(max = 300.dp)) {
                                    Text(if (msg.fromUser) "YOU" else "MIKA", color = Color(0xFFBBAAFF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(msg.text, color = Color.White)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = typed, onValueChange = { typed = it }, modifier = Modifier.fillMaxWidth(),
                    enabled = state.modelReady && !state.busy,
                    placeholder = { Text("Ask Mika anything…") }, singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.weight(1f), enabled = state.modelReady && !state.busy, onClick = {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text("🎙 Speak") }
                    Button(modifier = Modifier.weight(1f), enabled = typed.isNotBlank() && state.modelReady && !state.busy, onClick = {
                        vm.send(typed); typed = ""
                    }) { Text("Send") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Notifications") }
                    OutlinedButton(onClick = { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Accessibility") }
                    OutlinedButton(onClick = { activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text("Battery") }
                }
                Text("Use special access only for features you understand. Banking and secure screens may block automation.", color = Color(0xFF9292A8), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}


@Composable
private fun MikaAvatarCard(
    speaking: Boolean,
    listening: Boolean,
    thinking: Boolean,
    subtitle: String
) {
    val infinite = rememberInfiniteTransition(label = "mika-idle")
    val bob by infinite.animateFloat(
        initialValue = -0.8f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mika-subtle-idle"
    )
    val mouth = if (speaking) 0.48f else 0.12f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF211B39))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(190.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color(0xFF151326)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.mika_avatar),
                    contentDescription = "Mika anime avatar",
                    modifier = Modifier.fillMaxSize().offset(y = bob.dp),
                    contentScale = ContentScale.Crop
                )

                // A small animated mouth overlay follows Android TTS utterance callbacks.
                Canvas(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = 0.dp, y = 39.dp + bob.dp)
                        .size(width = (17 + mouth * 8).dp, height = (3 + mouth * 9).dp)
                ) {
                    drawOval(
                        color = Color(0xFFB52E72),
                        alpha = if (speaking) 0.95f else 0.45f
                    )
                    if (speaking) {
                        drawOval(
                            color = Color(0xFFFFA4C8),
                            alpha = 0.85f,
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }
                }

                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    color = Color(0xCC171329),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        when {
                            speaking -> "● SPEAKING"
                            listening -> "● LISTENING"
                            thinking -> "● THINKING"
                            else -> "● MIKA"
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = if (speaking || listening) Color(0xFFFFB4D2) else Color(0xFFCCB8FF),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    listening -> "I'm listening…"
                    thinking -> "Let me think for a moment…"
                    speaking && subtitle.isNotBlank() -> subtitle
                    subtitle.isNotBlank() -> subtitle
                    else -> "Hii! I'm Mika ✨"
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
                color = Color(0xFFE9E2FF),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
        }
    }
}
