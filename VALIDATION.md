# Validation — configurable journal, terminal b icon, and screenshots — October 2, 2026

## Changes

The journal now defaults to **500 entries**, configurable between **50 and 5000** in Event log → Log settings. The setting is stored in Preferences DataStore; a lower limit immediately prunes the oldest entries. Startup temporarily loads the bounded history before applying the saved limit, preventing a saved limit above 500 from losing entries on restart. All add/clear/limit mutations remain serialized. Journal retention is excluded from camera-command configuration comparisons and does not reset pending departure evidence.

Log types are assigned explicitly at production event sources and persisted alongside entries. Dedicated vector icons distinguish signal detection/loss, confirmed absence, unknown state, confirmation waiting, monitoring start/stop, Blink arm/disarm, account events, information, and errors. Old entries without a type remain readable; errors with details are normalized to error entries. Error cards have red text, date, icon and border, plus an explicit View error details action. The dialog retains selectable, scrollable technical context, full causes/trace and copying. Sanitization still runs before storage and on load, including every technical field.

The b artwork was generated with the built-in image-generation tool, copied unchanged with its alpha channel preserved, and integrated through `ic_launcher_terminal`. The prompt and asset paths are recorded in artwork/README.md; the 512 × 512 native launcher preview was inspected for complete framing. Proportional 22.22% margins keep the bitmap inside the adaptive mask. Original logo, legacy adaptive/monochrome resources and notification artwork remain packaged; lint flags five retained legacy resources as unused.

The dark Compose interface follows the supplied terminal-style reference: near-black `#0A0A0A`, neon green `#00FF88`, cyan `#00D1FF`, monospace headings, thin framed sections with cut corners, rectangular buttons, and outlined navigation icons. A faint static grid uses cached drawing paths without animation or background work. The new generated neon b artwork appears in both headers and the adaptive launcher, with a near-black background and a separate monochrome b vector. The earlier house/shield/Wi-Fi/camera artwork is retained. Android window and launcher background colors match the dark palette. Original logo files and default adaptive, monochrome, and notification resources are retained; the light interface keeps its navy/teal/white palette.

Home groups actual presence, Blink state, and the selected sensor in one status panel. HOME uses the primary color, AWAY_PENDING uses amber, and UNKNOWN/AWAY use a separate muted container; an unconfigured installation never reports a secure or connected state. The Network mode choices are visually grouped. Presence decisions, sensor adapters, permissions, and the Blink protocol retain their behavior; event callbacks now carry an explicit log type.

Ten actual application renders were regenerated in `docs/screenshots/` and displayed in README: Home, Network, Blink, Event log, and Error details in both themes. A separate instrumentation APK captures `MainActivity` on an isolated Android 17 / API 37 emulator at 1080 × 2400 pixels. It uses an empty installation with no Blink account, disabled monitoring, and no selected sensor; journal entries marked [DEMO] are synthetic documentation-only fixtures, including a timeout error and detected/missing signal examples. DEMO entries never alter sensor evidence or camera state; no request is sent. All ten images were visually inspected for readable text, navigation selection, and correct light/dark colors. This is emulator rendering/navigation validation, not real-device radio or live Blink validation.

The documentation runner is confined to `src/androidTest`; it rejects physical devices, connected Blink accounts, and enabled monitoring. It waits for application initialization and traverses Compose's virtual accessibility nodes for tab navigation. In-process view drawing produces the PNGs while retaining `FLAG_SECURE`; dialog captures compose the actual visible window roots at their screen positions with their Android dim amount. The runner is absent from the application APK. `tools/capture_screenshots.py` exports and validates the ten PNGs; its usage is documented next to the images.

### Markdown preview compatibility

README image tags were changed from raw HTML to standard Markdown image syntax. All referenced PNG files are checked for existence. The installed Android Studio 2026.2.1 log reports `DefaultImageSourceResolver: Failed to resolve image source` for the relative paths in its Compose preview; it also reports the JCEF module as unresolved. Its bundled runtime has no JCEF files. The screenshots directory documents the compatible full-runtime/Chromium alternative. The native preview's visual recovery has not been verified; changing document syntax alone is not claimed to fix its renderer. IDE runtime/settings were not modified.

### Earlier battery access and English interface changes

Home → Android permissions → Background battery use now displays the real battery optimization status and a package-specific **Allow unrestricted battery use** button. The manifest declares REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. Only an explicit tap opens Android's exemption request; an already exempt app opens settings. Missing/denied activities fall back to the optimization list and app details, with a safe error if all destinations fail. Existing activity-resume access refresh updates the status after return. The UI explains partial exemption, increased battery use and manufacturer settings. README and AGENTS now consolidate every feature added during this session.

All application-authored interface text is now English: Home, Network, Blink sign-in/verification, event log, permission guidance, sensor selection, status labels, notifications, startup/boot errors, protocol errors, and diagnostic field names. Diagnostics explicitly state **No HTTP status received** when no response exists and use **[REDACTED]** for masked secrets. SSIDs, device names, and Blink system names remain external data and are not translated. Previously saved event messages are retained in their original language.

Dates use **yyyy-MM-dd HH:mm:ss** with English formatting and the phone's local time zone. Estimated distances use a decimal point. README and artwork documentation are English, and the repository language convention now matches the request. Relevant English comments explain cache/evidence separation, monotonic timestamp units, radio-only freshness, startup/suspended BLE silence, permission-return refresh, service-start rollback, and retention of original network exceptions.

Automatic activation on sensor selection, the silent notification channel, handset-only departure vibration, arming delays, second confirmation, Wi-Fi freshness checks, and BLE compatibility requirements are retained. Existing JVM tests were updated for English messages and fixtures. No live Blink credentials or real radio devices were used.

## Results

| Check | Result |
| --- | --- |
| JVM tests | **130 passed, 0 failures, 0 errors, 0 skipped** |
| Debug build | Successful; signed debug APK |
| Release build | Successful; unsigned release APK |
| Android debug lint, current offline run | **0 errors, 19 warnings**: 13 SDK/tool/dependency notices, `BatteryLife`, and 5 retained legacy artwork resources marked unused |
| Debug APK signature | Verified with apksigner; valid v2 signature, one signer |
| Debug APK alignment | `zipalign -c -P 16 4` passed |
| Compiled manifest | `dev.homesentinel`, minSdk 29, targetSdk 35, MainActivity launcher |
| Battery exemption permission | REQUEST_IGNORE_BATTERY_OPTIMIZATIONS verified in the compiled APK |
| Bluetooth permissions | `BLUETOOTH_SCAN` with `neverForLocation`, `BLUETOOTH_CONNECT` |
| Service declarations | `location|connectedDevice`; selected at runtime by monitoring mode |
| Launcher icon | Manifest references the packaged adaptive `ic_launcher_terminal`; the release resource table includes the b bitmap, monochrome layer and black background |
| Documentation screenshots | **10 actual 1080 × 2400 application renders**, both themes; DEMO entries explicitly labeled; visually inspected |
| Emulator navigation | Home → Network → Blink → Event log → Error details captured in both themes on API 37; copy and default retention saved successfully |
| Documentation runner isolation | Present only in the separate instrumentation APK; absent from both debug and release application APKs |

Environment: JBR / OpenJDK **21.0.11**, Gradle **9.6.0**, AGP **9.4.1**, Kotlin **2.2.10**, SDK Platform **35**, Build-Tools **35.0.0** explicitly configured; AGP 9.4.1 ignores that lower version and selects installed **36.0.0** for the build. Signature/alignment/aapt checks use 35.0.0. Java bytecode targets 17. The build-tool configuration was updated externally during this task and retained, including `android.builtInKotlin=false` and `android.newDsl=false`. Gradle reports deprecations for these compatibility settings. No local SDK paths or caches were added to the delivery.

## Commands executed

The current journal/icon changes were tested and built with:

```bash
JAVA_HOME=/home/o/.jdks/jbr-21.0.11 ANDROID_USER_HOME=/tmp/sentinel-android ./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest --offline --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
```

After the capture-runner fixes and proportional launcher margins, the final resource builds/lint were run with:

```bash
JAVA_HOME=/home/o/.jdks/jbr-21.0.11 ANDROID_USER_HOME=/tmp/sentinel-android ./gradlew assembleDebugAndroidTest lintDebug --offline --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
JAVA_HOME=/home/o/.jdks/jbr-21.0.11 ANDROID_USER_HOME=/tmp/sentinel-android ./gradlew lintDebug assembleDebug assembleRelease assembleDebugAndroidTest --offline --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
```

The ten screenshots were regenerated from that APK:

```bash
ANDROID_HOME=/home/o/Android/Sdk python3 tools/capture_screenshots.py --serial emulator-5580
```

The runner completed with `Captured 10 real application views with DEMO log fixtures` and `INSTRUMENTATION_CODE: -1`. Initial capture attempts during the earlier light/dark redesign exposed an application-initialization race and unsupported framework text lookup for Compose virtual nodes; both were corrected before capture. The current images come from the journal/icon APK. The documentation runner exercises opening, copying and closing the error dialog, verifies copied diagnostics, and saves the default retention through the real settings dialog/DataStore. It refuses a nonempty journal, seeds only its labeled fixtures, and flushes fixture cleanup on the instrumentation worker before finishing. An initial emulator System UI ANR obscured the application; that system dialog was dismissed on the isolated emulator. A subsequent retry correctly rejected fixtures left by an interrupted asynchronous cleanup. Only the exclusively DEMO data on this isolated emulator was reset, and cleanup was then made durable. Native dialog composition and proportional icon margins were added after inspecting the initial exports. Added JVM tests cover limits, ordering, immediate pruning, restart, legacy types, secret redaction in persisted details, exception causes/status, settings encoding/validation, source event types, duplicates/timer waiting and pending departure during a retention change.

Earlier battery-access changes were tested and built with the previous Gradle 8.13 / AGP 8.13.2 / Kotlin 2.1.20 toolchain:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease --offline --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
```

A previous full clean validation with that earlier toolchain also passed:

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease --offline --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
```

The previous terminal-theme online build resolved uncached dependencies; this journal/icon run passed offline. The current build used the normal Gradle cache at `/home/o/.gradle`; the previous toolchain used `/tmp/sentinel-gradle`. Offline attempts with the updated toolchain stopped at uncached build dependencies, then the online build resolved them and passed. The standard repositories are unchanged. Paths above describe this validation machine and are not committed as SDK/build configuration.

Additional Build-Tools 35.0.0 checks:

```bash
/home/o/.jdks/jbr-21.0.11/bin/java -jar /home/o/Android/Sdk/build-tools/35.0.0/lib/apksigner.jar verify --verbose app/build/outputs/apk/debug/app-debug.apk
/home/o/Android/Sdk/build-tools/35.0.0/zipalign -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk
/home/o/Android/Sdk/build-tools/35.0.0/aapt dump badging app/build/outputs/apk/debug/app-debug.apk
/home/o/Android/Sdk/build-tools/35.0.0/aapt dump resources app/build/outputs/apk/debug/app-debug.apk
/home/o/Android/Sdk/build-tools/35.0.0/aapt dump resources app/build/outputs/apk/release/app-release-unsigned.apk
```

`BatteryLife` flags the direct battery-exemption permission for review against Android/Play acceptable-use rules. The request is retained for the user-requested continuous home-sensor monitoring and is initiated only by a button tap; no store-policy approval is claimed.

The release resource table contains the current b launcher bitmap, adaptive foreground, monochrome layer and near-black background; compiled manifest badging confirms the new icon reference. The original bitmap matches its artwork copy. APK DEX inspection confirms the capture runner is confined to the instrumentation APK. Gradle compatibility-setting deprecations, a pre-existing Kotlin `CookieJar` parameter-name warning, and native-library strip notices did not prevent the tasks.

## Test coverage

| Class | Tests | Failures |
| --- | ---: | ---: |
| ActivateMonitoringTest | 6 | 0 |
| AutomationCoordinatorTest | 18 | 0 |
| BlinkApiServiceTest | 18 | 0 |
| BlinkProtocolTest | 5 | 0 |
| BluetoothObservationWindowTest | 7 | 0 |
| DiagnosticsTest | 6 | 0 |
| EnsureBlinkStateTest | 10 | 0 |
| PresenceMachineTest | 15 | 0 |
| ScanPolicyTest | 11 | 0 |
| BluetoothDeviceCatalogTest | 7 | 0 |
| SignalDistanceEstimatorTest | 9 | 0 |
| DepartureAlertPolicyTest | 6 | 0 |
| EventLogTest | 8 | 0 |
| LogRetentionSettingsTest | 4 | 0 |
| **Total** | **130** | **0** |

Coverage includes selection/activation prerequisites and rollback; deadlines, rapid return, duplicate and stale observations, fresh second confirmation, restart and target changes; actual selected-sensor timestamps and wall-clock conversion; BLE startup silence and delayed windows; radio expiry and metadata-only updates; names, RSSI estimates, grouping and proximity sorting; one handset alert per confirmed departure; state verification, serialization, token refresh, network failures, regional `rest-e005` URLs, original exception causes, response status before/after headers, parsing context, and secret redaction including escaped quotes.

Blink tests use **local MockWebServer and fictitious sessions**, not a live account. The DNS failure test uses an injected failing resolver. Time-dependent decisions use injected clocks and virtual coroutines. Updated JUnit XML and lint results are stored in `validation/`.

## Updated APK

- File: `dist/blink-sentinel-debug.apk`, identical to `app/build/outputs/apk/debug/app-debug.apk`.
- Size and SHA-256: see `dist/SHA256SUMS` for the current artifact.
- Version: 1.0.0 / code 1; debug signing for personal testing.
- Checksum: `dist/SHA256SUMS`.

No new source-delivery archive was created for this modification of the existing project.

## Blink Sentinel branding and theme selector

The launcher and in-app title use **Blink Sentinel**. The Home screen's Appearance section lets users choose Dark or Light and stores the setting in Preferences DataStore; a missing or invalid value selects Dark. The active monitoring indicator pulses, stopped monitoring uses the red Presence card, and Blink state labels highlight Armed in bold green and Disarmed in yellow. The application ID and encrypted-session key context remain unchanged for existing installations.

The Makefile supports `make push` and `make release VERSION=0.1.0`; it initializes Git on first use and uses the GitHub CLI for an existing repository. The `.gitignore` excludes local SDK and signing files. The release command attaches the generated APK; a release build without owner-provided local signing is unsigned and requires signing before distributing an installable production APK.

This update was compiled with:

```bash
JAVA_HOME=/home/o/.jdks/jbr-21.0.11 ANDROID_USER_HOME=/tmp/sentinel-android ./gradlew assembleDebug --offline --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
```

Result: **BUILD SUCCESSFUL**. The first attempt without `JAVA_HOME` stopped before Gradle started; the command above uses the installed JBR. The publishing entry point is now the Makefile; run `make` or `make help` to see its targets. No GitHub push or release was made. Unit/instrumentation tests and a new emulator screenshot capture were not run for this update.

## Real-phone checks still required

- Battery exemption: allow, deny and cancel the package-specific Android request; verify status on return, behavior when already exempt, and navigation on manufacturers without the direct activity. Check extra Unrestricted/Never sleeping apps options, locked-screen behavior and battery consumption. These platform/OEM interactions have not been executed on a real phone. No new JVM test mirrors the intent-navigation code; existing behavioral tests and manifest/lint/build checks remain the automated validation.

1. Verify English text throughout Home, Network, Blink, Event log, permissions, notifications, and diagnostic copy/scroll with the phone set to a non-English language. Check decimal points and **yyyy-MM-dd HH:mm:ss** dates in the local time zone. Older event messages may retain their original language.
2. Switch from Bluetooth with Location off to Wi-Fi. Verify permission and Location buttons, fresh cache loading, automatic scan on returning from settings, scan-refusal messages, distances, and sorting.
3. With Blink ready, select a Wi-Fi SSID or a Bluetooth device during discovery. Verify automatic startup, discovery cancellation, a silent notification, and unknown presence until fresh evidence arrives. Without prerequisites, verify saved selection, disabled monitoring, and an actionable message.
4. Test actual Wi-Fi scans, BLE names, classic discovery with Location off, selectable entries, and expiry/return after radio observations. Estimated distance is uncalibrated and has not been measured against physical distance.
5. Validate last-signal reception timestamps, actual departures/returns, one handset vibration without app audio in headphones, screen lock, silent mode, Do Not Disturb, and migration of the notification channel on an existing installation.
6. Test BLE with Location off on Android 12+, prolonged Doze, battery restrictions, reboot, stable/random addresses, filtered beacons, and manufacturer behavior. Startup or suspension without a real advertisement must remain unknown.
7. Validate Keystore storage, key loss/corrupt-session reauthentication, and actual permitted/restricted boot recovery.
8. Validate real Blink OAuth/2FA, network DNS/TLS, availability of `rest-e005.immedia-semi.com`, actual command confirmation, and cloud latency against the official app.

Bluetooth without Location is offered **only on Android 12+**, for a BLE device that advertises regularly with a stable address. Android 10/11 retain Wi-Fi mode. A build and simulated tests do not validate background radio delivery or the unofficial protocol on a live Blink account. Follow the real-phone scenarios in README before relying on automatic camera control.
