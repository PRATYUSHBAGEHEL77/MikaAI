# Android 16 and ColorOS 16 notes

## Microphone foreground service

Manifest permissions:
- `android.permission.RECORD_AUDIO`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`
- `android.permission.POST_NOTIFICATIONS`

The service declares `android:foregroundServiceType="microphone"`. Start it only after the user opens Mika, taps Start Mika, and grants microphone permission. Android restricts creating microphone services from the background because microphone access is a while-in-use permission.

## Restricted settings for sideloaded APKs

When Android blocks Accessibility or Notification access:
1. Open Settings > Apps > Mika AI > App info.
2. Tap the three-dot menu.
3. Choose Allow restricted settings (if displayed).
4. Return to Accessibility or Notification access settings and enable Mika.

Labels vary across Android/ColorOS versions.

## Battery and auto-launch

1. Open Settings > Apps > Mika AI > Battery usage.
2. Select Unrestricted / Don't optimize if available.
3. Allow Auto-launch if available in ColorOS app management.
4. Open Recents and lock Mika if your phone offers an app-lock gesture.

These settings are best-effort and do not guarantee survival after OS cleanup.

## Accessibility and secure screens

Accessibility may be disabled after cleanup or reboot. Recheck its status after system maintenance. Secure windows can block screenshots, and some financial apps intentionally prevent automation. Do not attempt to bypass these restrictions.
