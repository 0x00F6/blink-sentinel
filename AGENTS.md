# Instructions for agents working on Blink Sentinel

## Project and boundaries

This is a native Android application, not a Flutter project. Use Kotlin, Jetpack Compose, coroutines, Flow, ViewModel, and the existing repository interfaces. Package/application ID: `dev.homesentinel`. Minimum API: 29; compile/target API: 35. Keep the project directly importable in Android Studio.

Read `README.md` and `VALIDATION.md` before changing device behavior or the remote protocol. Do not describe mock-server validation as live Blink or real-device validation.

## Architecture

- `SentinelApplication` / `AppGraph`: application-scoped dependencies and session restoration.
- `data/preferences/SettingsRepository`: non-secret Preferences DataStore settings.
- `data/preferences/EventLog`: bounded, persistent event log without secrets.
- `data/wifi/AndroidWifiScanner`: the only direct WifiManager adapter; implements `WifiScanner`.
- `data/wifi/ScanPolicy`: pure timestamp/freshness and SSID matching logic.
- `data/bluetooth/AndroidBluetoothScanner`: Android discovery and address-filtered BLE monitoring; main-thread ownership.
- `data/bluetooth/BluetoothDeviceCatalog`: name merging, radio-only freshness, and proximity sorting.
- `data/bluetooth/BluetoothObservationWindow`: pure fresh-advertisement and window policy.
- `domain/usecase/PresenceMachine`: deterministic UNKNOWN/HOME/AWAY_PENDING/AWAY state machine.
- `domain/usecase/ActivateMonitoring`: shared activation path for explicit activation and sensor selection, with prerequisite checks and startup rollback.
- `domain/usecase/DepartureAlertPolicy`: one handset vibration per confirmed departure after HOME.
- `domain/usecase/SignalDistanceEstimator`: indicative RSSI-to-distance display model, independent of presence decisions.
- `domain/usecase/EnsureBlinkState`: serializes manual/automatic commands and avoids duplicate POSTs.
- `service/BlinkAutomationService`: cancellable deadlines, evidence expiry, desired-state reconciliation and bounded retries; called on the main dispatcher.
- `service/WifiMonitorService`: foreground service (`location` for Wi-Fi, `connectedDevice` for Bluetooth), dynamic receivers and ConnectivityManager callbacks.
- `service/MonitorNotifications`: silent channel, persistent notification and handset-only vibration.
- `MainActivity`: runtime permissions and user-initiated battery exemption/settings navigation; refresh access state on resume.
- `receiver/BootReceiver`: permitted boot recovery or notification requiring visible user action.
- `data/blink/BlinkApiService`: unsupported remote mobile OAuth/API adapter behind `BlinkService`.
- `data/blink/SessionVault`: AES-GCM Android Keystore persistence; no plaintext fallback.
- `ui/SentinelViewModel`: UI actions and StateFlow.
- `ui/screens`, `ui/components`, `ui/theme`: Compose UI.

Keep Android adapters out of pure domain logic. Inject clocks, transport endpoints and test doubles where useful. Use `AppGraph` for dependency injection; add a framework only if justified by a concrete architectural need.

## Build and tests

Use a full compatible JDK and SDK platform 35 / Build-Tools 35.0.0:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew assembleRelease
./gradlew clean
```

Windows uses `gradlew.bat`. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Release signing is configured by the owner; never commit keystores, passwords or signing secrets. The Gradle wrapper JAR and distribution checksum belong in the project. Do not commit `local.properties`, `.gradle`, IDE caches or dependency caches.

Use meaningful JVM tests for behavioral changes. Preserve coverage of deadlines, rapid return, duplicate broadcasts, stale evidence, fresh second confirmation, errors, token refresh and restart behavior. Mock remote requests with MockWebServer; do not use credentials or a real Blink account in automated tests. Use `kotlinx-coroutines-test` and virtual time for delayed decisions.

## Wi-Fi invariants

- Presence means visibility of the configured exact SSID, not connection to it.
- Process only successful `SCAN_RESULTS_AVAILABLE_ACTION` broadcasts. Never reinterpret a failed scan as absence.
- Validate per-result timestamps from ScanResult, in monotonic milliseconds since boot. Keep microsecond conversion correct.
- Duplicate cached batches must not advance the state machine.
- AWAY requires a first absence and a later fresh absent batch after the configured minimum delay. A timer may request a scan but cannot itself confirm AWAY.
- Disabled Wi-Fi, missing permission, disabled location and expired evidence mean UNKNOWN, not AWAY.
- Restart resets presence to UNKNOWN; never persist/replay a stale arm/disarm decision.
- `startScan()` is deprecated and throttled, but intentionally used as an optional, best-effort supplement to passive listening. Do not claim a foreground service bypasses throttling or Doze.
- Keep background scan polling disabled by default. Do not add permanent wake locks, exact alarms or repeated service starts to bypass platform restrictions.
- Request COARSE and FINE together, including upgrades from approximate access. NEARBY_WIFI_DEVICES alone does not authorize scan result access. Background location is a separate optional step for permitted boot recovery.
- Retain Android 14+ service type and permission declarations, Android 13+ notification handling, and the background-start restrictions.

## Bluetooth and device-list invariants

- Bluetooth without Location is available only on Android 12 / API 31+. Use SCAN with `neverForLocation` and CONNECT; do not require the Location switch in this mode. Android 10/11 retain Wi-Fi mode.
- Automatic Bluetooth presence requires actual BLE advertisements with a stable address, not pairing or a classic connection. Explain fixed-power/advertising compatibility and beacon filtering.
- Use address-filtered continuous BLE monitoring for screen-off delivery. Ten-second windows, the arming delay, and a later absence confirmation protect against transient disconnects.
- Startup silence, a window suspended for more than twenty seconds, revoked access, disabled Bluetooth, and scanner errors mean UNKNOWN until actual fresh evidence returns.
- Discovery lasts up to 22 seconds (classic then BLE), stops automation, rejects overlapping discovery, and cleans up on selection/completion/cancellation.
- Every displayed Bluetooth entry is selectable, including classic devices; selection does not guarantee BLE compatibility. Classic-only devices must not create presence evidence.
- Merge advertised and Android-known names; retain resolved names across unnamed announcements. Pairing/name metadata alone must not create a visible entry or refresh radio freshness.
- Remove Bluetooth entries after 30 seconds without observation. Explain that expiry is not a certain physical range measurement; allow another search without adding permanent discovery.
- Display estimated meters from RSSI for Wi-Fi/BLE/classic, nearest first and unknown last. Group Wi-Fi by SSID using the strongest valid RSSI. The uncalibrated model must never drive automation.
- Refresh the Wi-Fi picker from individually fresh cached results and request a one-off scan on opening/resuming. Space automatic UI requests by at least 30 seconds. Cache-only refresh and failed broadcasts must never become presence evidence.

## Activation, alerts, timestamps, and battery access

- Selecting a Wi-Fi SSID (including manual entry) or Bluetooth device saves it and automatically activates monitoring when Blink/system/access prerequisites are ready. Stop Bluetooth discovery before activation. Share the same activation path as the explicit switch.
- Missing prerequisites or startup refusal retain the selected sensor, disable monitoring, and show an actionable English message. A selection is not evidence; a changed sensor requires fresh detection.
- Use silent notification channel `presence-silent-v2`, with no sound or automatic update vibration and `setOnlyAlertOnce(true)`. Do not play audio or change headphone routing.
- Vibrate the handset once for 300 ms only after HOME followed by confirmed AWAY. Pending absence, rapid returns, duplicates, unknown state, and startup while away do not alert. Respect silent mode and Do Not Disturb.
- Last signal time comes from the chosen sensor's actual radio timestamp, converted from monotonic to wall-clock time with injected clocks. Absences, duplicates, and timers must not advance it. Bind it to the sensor, reset on restart/target change, and never persist it as an automation decision.
- Keep Home → Android permissions → Background battery use visible in both modes. Display the real `isIgnoringBatteryOptimizations` result; refresh it on activity resume. Do not persist or infer successful exemption from a button tap.
- Declare REQUEST_IGNORE_BATTERY_OPTIMIZATIONS and issue its package-specific request only after an explicit user tap. If already exempt, open settings; if direct navigation is unavailable/denied, fall back to the optimization list then app details without crashing.
- Explain higher battery use and manufacturer-specific Unrestricted/Never sleeping apps settings. Exemption is partial: do not promise unlimited background operation, scan frequency, or bypass of Android restrictions. Never add wake locks, exact alarms, or service-restart loops as a battery workaround.

## Blink and security invariants

- Regional API URLs normalize `e005` to `rest-e005.immedia-semi.com`, including old sessions, without duplicating an existing `rest-` prefix. Keep strict host construction and test-only overrides.
- The mobile API is unofficial and can change. Keep endpoints, OAuth fields, parsers and compatibility handling in `data/blink`.
- Login requires PKCE, memory-only cookies and CSRF state; handle 2FA explicitly. Do not store passwords or OTP codes in saved UI state, preferences, files or logs.
- Use a stable UUID hardware identifier; never invent authentication success or silently simulate live operation.
- Never log HTTP bodies, headers, tokens, cookies, passwords or request forms.
- Never disable TLS validation, permit arbitrary user-controlled API endpoints or forward bearer tokens to redirects. Test endpoint overrides are code-only test seams.
- Session persistence must remain authenticated encryption with a Keystore key, atomic writes and backup exclusions. Key loss means reauthentication, never plaintext fallback.
- Query the selected system's real state before sending a command. UNKNOWN is not DISARMED.
- A POST accepted by Blink is not confirmation; verify actual state and report uncertainty when it cannot be confirmed.
- Cancel obsolete jobs on new presence evidence or disabled automation. Check the desired revision after reading remote status and before sending a command.
- Bind `EnsureBlinkState` to the selected-system provider in production. Capture the target ID before querying status and pass it explicitly to arm/disarm; never resolve a new target between the status query and the command. Revalidate persisted settings before automatic effects, independently of Flow delivery timing.
- Retry boundedly and only with fresh evidence. Do not automatically replay ambiguous network POSTs. Rate-limit and auth errors must not cause request loops.
- Local logout stops automation and clears local secrets. Do not claim server-side revocation unless implemented and verified.

## Event log and diagnostics

- Keep a bounded persistent log of 500 events by default, configurable from 50 to 5000 in Event log settings. Apply lower limits immediately and retain the saved limit across restarts. Assign event types at the source for distinct icons. Display dated errors in red and provide an explicit, scrollable, selectable, copyable detail panel.
- Record operation, actual HTTP method/URL, response status if received, exception type/message, causes and full stack trace. A pre-response failure must say `No HTTP status received`; body-read failures retain the received status.
- Preserve original network exceptions as causes and use `networkErrorDetail` for explicit safe user messages. Keep parsing errors attached to their actual request context.
- Sanitize messages and every technical field before storage/display/copy. Redact passwords, tokens, cookies, codes, authorization, URL credentials/query values and other secrets using `[REDACTED]`. Never add bodies, headers, or forms.

## Conventions and delivery

- Use English identifiers and technical comments, English UI/documentation, and short English event logs consistent with the existing journal.
- Keep all authored UI/notification/error text and documentation in English, even on a non-English phone. Preserve external SSIDs/device/system names and historical log messages. Use `yyyy-MM-dd HH:mm:ss` in local time and English decimal formatting.
- Add English comments where they explain invariants, units, cancellation, security or platform behavior; avoid narrating obvious code.
- Format Kotlin according to Kotlin language style. Preserve cancellation exceptions; avoid blocking the main thread.
- Keep state transitions and adapter parsing strict; return typed errors with safe messages.
- Update README, tests and manifest together when permissions or user flows change.
- Keep the original logo and adaptive/monochrome/notification resources. Verify the manifest references the packaged icon.
- Record exact validation commands and honest device/network limitations in VALIDATION.md.
- When packaging, use one top-level `blink-wifi-automation/` directory. Include source, build configuration, wrapper, docs, artwork, notices and the debug APK if built. Exclude local SDK paths, caches, build directories and secrets.
