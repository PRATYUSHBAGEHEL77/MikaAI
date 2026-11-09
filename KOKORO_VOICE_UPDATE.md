# Mika Kokoro voice update

- Active Android system TextToSpeech use has been removed.
- Mika uses Kokoro-82M Hindi female voice `hf_alpha` through MediaPipe Tasks Audio.
- The ~295 MB model downloads on first spoken response and stays in app-private storage.
- After download, synthesis runs locally. No paid API or account is required.
- GPU is attempted first; CPU is tried only if GPU initialization fails. Both paths use Kokoro.
- There is deliberately NO Android TTS fallback. If Kokoro fails, Mika remains text-only.

## Important limitations
- First use needs internet and about 300 MB of free storage; later synthesis works offline.
- First model initialization can take longer than later replies.
- Hindi is the selected locale. Roman-script Hinglish and English insertions may not be pronounced perfectly; a dedicated Hinglish transliteration layer is not included.
- This source update has not been compiled or tested on the Oppo device. Run GitHub Actions and share any build error log.
