# Application screenshots

These fourteen PNG files are actual renders of `MainActivity` and its Compose interface on an isolated Android 17 / API 37 emulator at 1080 × 2400 pixels. They show an unconfigured installation without Blink credentials or radio devices. The light and dark views cover Home, Network, Blink sign-in, 2FA, system selection, Event log, and Error details. The 2FA and system-selection screens use documentation-only UI state fixtures; names and example statuses are labeled **[DEMO]**. They do not represent a successful login or real camera states, and no credentials or verification codes are used. Journal entries prefixed **[DEMO]** are synthetic documentation fixtures for a network error and signal events. They never change presence or camera state, and no network request is made. Production does not create these fixtures.

The light theme uses navy, teal, and white. The dark views show the terminal-inspired theme: near-black surfaces, neon green/cyan accents, monospace headings, thin borders, a static grid, and framed navigation icons.

## Android Studio Markdown preview

The main README uses standard Markdown image syntax with portable relative paths. If Android Studio's **Compose (experimental)** preview reports `Failed to resolve image source` for these files, the files may still be present: that renderer can fail to resolve local paths. Open the PNG files directly from this directory to view them.

The Chromium preview requires a compatible JetBrains Runtime that includes **JCEF**. If Android Studio uses a runtime without JCEF, the Chromium renderer is unavailable. Use **Choose Boot Java Runtime for the IDE** to select a compatible full runtime with JCEF, restart the IDE, then choose **Chromium browser** under **Settings → Languages & Frameworks → Markdown → Preview rendering engine**, if available. See [JetBrains Markdown documentation](https://www.jetbrains.com/help/idea/markdown.html) and [IDE boot runtime settings](https://www.jetbrains.com/help/idea/switching-boot-jdk.html). Do not replace portable image links with machine-specific absolute paths.

## Regenerate

Use an empty emulator, not a personal phone or an emulator signed into Blink. The capture instrumentation rejects physical devices, connected Blink accounts, enabled monitoring, and a nonempty event log. It draws the activity and visible dialog windows at their actual positions, including the Android window dim amount, inside the instrumentation process; it never removes the application's `FLAG_SECURE` window protection. The capture runner is included only in the separate test APK, never the debug/release application APK.

With a compatible JDK, SDK, and `adb` available:

```bash
./gradlew assembleDebug assembleDebugAndroidTest
adb devices
python3 tools/capture_screenshots.py --serial emulator-5582
```

Replace `emulator-5582` with the explicitly selected emulator serial. `adb` must be on `PATH`, or `ANDROID_HOME` / `ANDROID_SDK_ROOT` must point to the SDK. The script installs the application and its separate instrumentation APK, captures all fourteen views, validates the PNG files, and updates this directory. It does not clear app data, sign into Blink, or enable monitoring. It sets temporary authentication UI states in memory and seeds the DEMO entries only after checking the journal is empty, exercises error opening/copying/closing and the default-retention settings dialog, then resets its in-memory authentication states and clears the journal fixtures. It never creates or writes an authenticated session. The test APK uses reflection to set the private state flows; production contains no simulated-login hook. The emulator finishes with the application in dark mode.

Screen capture validates rendering and tab navigation in this emulator only. It does not validate real Wi-Fi/BLE delivery, battery behavior, live authentication, or Blink command execution. See [VALIDATION.md](../../VALIDATION.md).
