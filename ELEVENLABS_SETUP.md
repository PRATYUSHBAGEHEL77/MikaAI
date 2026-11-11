# Mika — ElevenLabs voice setup

Voice ID configured: `9SsFrOutdZkCkU5hIoQm`
Model: `eleven_flash_v2_5` (low-latency multilingual model)
Android system TTS and Kokoro are no longer in the active speech path.

## Security
The ElevenLabs API key MUST stay on the backend. Never paste it into Kotlin, Gradle, GitHub source, or the APK. Because this account may involve billing, an adult should manage the ElevenLabs account, API key, and any payment settings.

## Deploy the proxy
1. Create a separate GitHub repository for the contents of `elevenlabs-proxy/`, or deploy that folder as the Render service root directory.
2. Create a Render Web Service with runtime Node, build command `npm install`, start command `npm start`.
3. In Render Environment, add `ELEVENLABS_API_KEY` as a secret environment variable. Do not add it to the repository.
4. Deploy. Check `https://YOUR-SERVICE.onrender.com/health` returns `{ "ok": true }`.

## Connect Android app
Build the Android app with Gradle project property:
`ELEVENLABS_PROXY_URL=https://YOUR-SERVICE.onrender.com`

For GitHub Actions, add a repository variable named `ELEVENLABS_PROXY_URL` (the proxy URL is not secret), then pass it to Gradle as:
`-PELEVENLABS_PROXY_URL=${{ vars.ELEVENLABS_PROXY_URL }}`

The app sends speech text to `/v1/tts`; the backend calls ElevenLabs and returns MP3 audio. The API key is never shipped in the APK.

## Notes
- Requires internet for speech generation.
- First request may take longer while Render wakes from an idle state on some plans.
- Flash model is selected for low latency; speed depends on network and account limits.
- The proxy streams audio from ElevenLabs to the app, but this Android implementation buffers the returned MP3 before playback. It is not true progressive playback yet.
- No Android TTS fallback exists. If the proxy is missing or unavailable, Mika remains text-only.
- This source has not been compiled or device-tested. Run GitHub Actions and share any compiler errors.
