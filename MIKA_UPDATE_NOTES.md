# Mika — Hinglish + responsiveness update

This ZIP contains the Android project with a calmer avatar and more concise, accuracy-first
Hinglish assistant instructions.

## Changes in this update
- Default conversational language is Hinglish, while matching mostly-Hindi or mostly-English input.
- More conservative generation sampling for steadier answers.
- System instructions emphasize uncertainty, clarification instead of guessing, and no fabricated actions.
- Ordinary voice replies are guided to stay short to reduce generated output length.
- Avatar idle movement is subtle; rapid mouth pulsing is removed.
- Android built-in TTS pitch/rate are less exaggerated.

## Honest limitations
- Shorter replies and conservative sampling can reduce unnecessary output, but cannot guarantee factual
  accuracy or faster generation on every phone. Local inference speed depends heavily on the device,
  runtime, model and thermal state.
- The current project still uses Android's installed TextToSpeech voice; it is not a natural neural
  character voice and it does not clone the sample voice.
- This local Gemma chat engine does not yet connect every UI phone-control tool to model decisions.
- The background microphone service in this source is a scaffold, not a production wake-word detector.
- This source ZIP has not been compiled or device-tested in this environment. Run GitHub Actions to
  verify the build before installing.

## Build
Upload the ZIP contents to the repository root, replacing files at matching paths, then run the
GitHub Actions workflow. Download the APK artifact only after a successful build.
