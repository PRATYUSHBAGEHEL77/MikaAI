# Mika AI — Local Gemma Android Project

Mika is an Android/Kotlin voice-chat prototype using Google's LiteRT-LM runtime and a local Gemma 4 E2B instruction model. After the model is downloaded, ordinary chat generation runs on the phone without sending prompts to a cloud AI API.

## What changed in this build

- Removed the Claude API client, API-key BuildConfig field, and Claude agent loop.
- Added LiteRT-LM Android dependency.
- Added first-run Gemma model download to app-private internal storage.
- Added SHA-256 verification for the pinned model file.
- Added model download progress and model-loading state in the UI.
- Added local conversation with streamed response updates.
- Kept Android SpeechRecognizer input and Android TextToSpeech output.
- Kept the existing safety, accessibility, notification and service source scaffolds, but they are not connected as model tools in this local-chat build.

## Model and storage

Model: `Gemma 4 E2B IT` in LiteRT-LM format  
Approximate download: 2.58 GB  
Storage requirement: allow at least 3 GB free internal storage before first download. The app stores the file under its private `files/models` directory.

The app downloads a pinned model revision from the LiteRT Community Hugging Face repository and checks its SHA-256 before loading it. The first download requires internet. Afterward, text chat inference is local and can run offline.

Model source:
https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm

Model file revision:
https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/08ca8d1009c97dd9a7d2be053860cb03c0d186a5/gemma-4-E2B-it.litertlm

Review the model's license and terms at the model repository before redistributing model weights or distributing an APK that bundles them.

## Build

1. Install Android Studio with Android SDK 36 and JDK 17.
2. Extract this folder and open it in Android Studio.
3. Allow Gradle to sync. LiteRT-LM is resolved from Google's Maven repository using the official `latest.release` dependency.
4. Build > Build Bundle(s) / APK(s) > Build APK(s).
5. Install the debug APK on the phone.
6. Open Mika and tap **Download Gemma (2.58 GB)**. Use a reliable Wi-Fi connection and keep the app open during first download.
7. Once the download verifies, Mika loads the model. The first model load can take several seconds.
8. After Mika shows **Ready · On-device · Offline**, text chat and SpeechRecognizer input can use the local model.

## Important device-performance note

The 12 GB RAM figure alone does not guarantee a specific speed. Android, ColorOS and other apps also consume memory. This project starts with the CPU backend for compatibility; GPU acceleration can be evaluated later on the actual device. Close memory-heavy apps if model loading fails or Android terminates Mika.

## Current feature boundaries

This is a local-chat integration, not a complete autonomous phone agent:
- Local Gemma chat and streaming are connected.
- One-shot Android SpeechRecognizer and Android TextToSpeech remain available.
- The old phone-action executor and safety classes remain as scaffolding, but are intentionally **not registered as Gemma tools** yet. This avoids letting a local model trigger calls, messages, settings changes or other phone actions without a separate confirmation-aware integration.
- The foreground microphone service is still a scaffold, not a real always-on "Hey Mika" wake-word detector.
- Accessibility and notification services are not connected to the local model.
- Kokoro/sherpa-onnx is not bundled; Android TextToSpeech remains the voice output.
- No Gradle build or real-device test was performed in this environment. Treat this as an integration source update that must be synced and built in Android Studio.

## Android 16 / ColorOS notes

- First download needs internet and at least 3 GB free internal storage.
- Keep Mika open during download; do not clear app data after downloading or the private model file will be deleted.
- If Android reports memory pressure, close other apps and retry model loading.
- Background service and accessibility behavior remains subject to Android/ColorOS restrictions.
- Secure and banking apps may block accessibility automation; Mika must not bypass those protections.

## References

- LiteRT-LM Android guide: https://developers.google.com/edge/litert-lm/android
- LiteRT-LM API overview: https://developers.google.com/edge/litert-lm/api_overview
- Gemma 4 E2B LiteRT-LM model: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm


## Anime avatar and lip-sync layer

The main screen now includes the anime portrait cropped from the reference video supplied in this conversation. Compose animates a gentle idle bob, shows listening/thinking/speaking status, and displays Mika's latest spoken line as a subtitle. Android TextToSpeech utterance callbacks drive a simple open/close mouth overlay while speech is playing.

This is lightweight 2D avatar animation, not a full Live2D/3D rig or phoneme-accurate viseme lip-sync. The face artwork itself is a static portrait; eye blinking, hair physics, multiple facial expressions and per-phoneme mouth shapes are not included yet.
