# Build Mika APK without installing Android Studio

1. Create a new empty GitHub repository in your browser.
2. Upload all files from this ZIP, preserving the `.github/workflows/android-apk.yml` path.
3. Open the repository's **Actions** tab.
4. Choose **Build Mika Android APK** and press **Run workflow**.
5. Wait for the run to finish. Open the completed run and download **Mika-debug-APK** from Artifacts.
6. Extract that artifact ZIP to get `app-debug.apk`, then transfer it to your Oppo.

Notes:
- The cloud runner installs Java 17, Android SDK 36 and Gradle 8.13.
- The APK does not include Gemma's multi-GB model; Mika downloads it after installation.
- The workflow has not been run yet, so this is not a compiled or tested APK.
- The animated anime avatar and lip-sync discussed in the design conversation are not implemented in this source yet.
