# Mika — Complete Android project (patched source)

## Upload to GitHub
Upload the CONTENTS of this ZIP to the root of your existing `MikaAI` repository,
preserving the folder structure. Allow GitHub to replace files with the same paths.

The project root must contain:
- `.github/workflows/android-apk.yml`
- `app/`
- `docs/`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`

## Build the APK
1. Open the repository's Actions tab.
2. Select "Build Mika Android APK".
3. Press "Run workflow".
4. After a successful run, open the run and download the `Mika-debug-APK` artifact.
5. Extract the artifact ZIP to get `app-debug.apk`.

## Included fixes
- Corrected Android AlarmClock import in ToolExecutor.
- Corrected Bluetooth settings intent action.
- Replaced the unresolved streamed `message.text` access in LocalGemmaEngine.
- Updated Kotlin JVM target configuration to compilerOptions.
- Workflow installs Android SDK packages and explicitly builds `:app:assembleDebug`.

## Important status
This is the complete source project, NOT a precompiled APK. The build has not been run in
this environment. The next GitHub Actions run is still required to confirm compilation.
The on-device Gemma model is not bundled in the APK; the app downloads it when requested.
The avatar is a lightweight 2D portrait with basic speech-state mouth animation, not
phoneme-accurate Live2D lip-sync.
