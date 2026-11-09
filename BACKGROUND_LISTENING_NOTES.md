# Mika background listening update

This update adds a user-started microphone foreground service with a persistent notification and a best-effort “Hey Mika / Hello Mika / Mika” phrase detector using Android SpeechRecognizer.

## Enable it
1. Install the APK.
2. Open Mika and grant Microphone permission.
3. Tap **Hey Mika: ON** while Mika is open.
4. Keep the persistent “Mika is listening” notification visible.
5. Say “Hey Mika”. When Mika is in the foreground, it starts command capture. If Mika is backgrounded or the screen is locked, tap the persistent notification to open Mika and then speak your command.
6. Use **Stop background** or the notification's **Stop listening** action to end microphone use.

## Important limitations
- This is not a dedicated low-power wake-word model. It uses Android's speech recognition service in repeated short sessions.
- Some recognition providers require internet and may not support Hindi/Hinglish consistently. The request uses hi-IN.
- Android/ColorOS may stop or throttle microphone services due to battery management, reboot, force-stop, or OS policy.
- Android restricts launching screens from the background. Therefore a wake phrase while Mika is not visible may require tapping the notification before giving the command.
- It does not guarantee Siri-level lock-screen behavior or work after force-stop/reboot.
- This source update has not been built or device-tested. Run GitHub Actions and share the first failing log if the build fails.
