<p align="center">
  <img src="artwork/terminal-b.png" alt="Blink Sentinel logo" width="180">
</p>
<p align="center">
  <a href="https://0x00f6.github.io/blink-sentinel">https://0x00f6.github.io/blink-sentinel</a>
</p>
# Blink Sentinel — Wi-Fi / Bluetooth monitoring

A native Android app built with Kotlin and Jetpack Compose. It arms the selected [Blink](https://blinkforhome.com/) system after a confirmed absence of the home sensor (Wi-Fi or Bluetooth BLE), and optionally disarms it when that sensor reappears. The phone **does not need to connect to the monitored Wi-Fi network**.

## Contents

- 🏠 [Motivation](#motivation) · [Highlights](#highlights)
- 🚀 [Getting started](#getting-started): [Requirements](#requirements) · [Install](#install) · [Quick start](#quick-start) · [Blink account and 2FA](#connect-your-blink-account-and-complete-2fa)
- 🎨 [Interface and themes](#interface-and-themes)
- 📡 [Monitoring behavior](#monitoring-behavior): [Wi-Fi](#wi-fi-monitoring) · [Bluetooth](#bluetooth-without-location) · [Timestamps](#last-received-signal) · [Distances](#estimated-distances-and-sorting) · [Alerts](#silent-departure-alerts)
- 🔋 [Android permissions and background operation](#android-permissions-and-background-operation) · [Event log and diagnostics](#event-log-and-diagnostics)
- 🛠️ [Development](#development): [Android Studio](#open-in-android-studio) · [Makefile commands](#makefile-commands) · [Gradle](#build-and-test) · [Architecture](#architecture)
- 📦 [GitHub publishing](#github-publishing): [Variables](#publishing-variables) · [Create a keystore](#create-a-signing-keystore) · [Release workflow](#what-make-release-does)
- 🔐 [Blink integration and security](#blink-integration-and-security) · [Real-phone validation](#real-phone-validation) · [Project resources](#project-resources)

## Motivation

🏠 I was tired of manually arming and disarming my home Blink cameras every time I left or came back. The Blink app did not offer the automatic arm/disarm behavior I wanted based on leaving home. I almost never forget my phone, so I created Blink Sentinel to use the phone as the presence sensor: it can watch for the visibility of my home Wi-Fi network or a compatible Bluetooth LE device, then automate the selected Blink system after a confirmed departure or return.

## Highlights

- 📶 **Wi-Fi presence:** detect the configured home SSID without connecting the phone to that network.
- 📡 **Bluetooth presence:** monitor compatible BLE advertisements on Android 12 and later, including with Location turned off.
- 🏠 **Automatic Blink control:** arm after a delayed, freshly confirmed departure and optionally disarm when the sensor returns.
- 🎨 **Personalized interface:** terminal-inspired dark theme by default, with a Light theme option and a matching app icon.
- 🧾 **Useful diagnostics:** keep 500 events by default with a configurable limit of 50–5000, show errors in red with copyable technical details, and redact secrets.
- 🔔 **Quiet alerts:** use a silent monitoring notification and one handset vibration after confirmed departure; the app does not play audio.
- 📏 **Signal estimates:** display indicative RSSI-based distance for sorting; these estimates never control automation.

## Getting started

🚀 Set up the Blink account, home sensor, and Android access before relying on automatic control.

### Requirements

- Android **10 / API 29 or later** with Wi-Fi. Permission and service branches cover Android 13, 14, 15, and 16, but the actual phone still needs testing.
- Bluetooth without Location requires **Android 12 / API 31 or later**, a BLE-capable phone, and a compatible home device.
- A Blink account with at least one system. Commands apply to a **system**, not individual cameras.
- Internet access when commands run. Mobile data must reach Blink after leaving home.

For the development toolchain, see [Development requirements](#development-requirements).

### Install

Install a signed release APK, when available in [GitHub Releases](https://github.com/0x00F6/blink-sentinel/releases). Transfer it to your phone, open it from a file manager, and allow installation from that source when Android asks. Application ID: `dev.homesentinel`; launcher name: **Blink Sentinel**.

With USB debugging and `adb`, you can also install a local debug build:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If the source delivery includes `dist/blink-sentinel-debug.apk`:

```bash
adb install -r dist/blink-sentinel-debug.apk
```

Keep the same signing key for app updates. Installing a build signed with another key requires uninstalling the existing app, which erases its settings and local session.

### Quick start

1. 📦 Install the APK or build the project in Android Studio.
2. 📡 In **Network → Monitoring mode**, choose **Wi-Fi** or **Bluetooth**. Changing mode stops monitoring; selecting a sensor enables it automatically when Blink and Android permissions are ready.
3. 📍 For Wi-Fi, grant precise location and notification permissions in **Home → Android permissions**, enable Wi-Fi and Android Location, then search for and select the exact SSID in **Network**. For Bluetooth on Android 12+, grant **Nearby devices** and notifications, enable Bluetooth, search for up to 22 seconds, and choose a compatible BLE device. Android Location can remain off.
4. 🔐 [Connect your Blink account and complete 2FA](#connect-your-blink-account-and-complete-2fa), then select the system to automate; a single available system is selected automatically.
5. ⏱️ Set the **Arming delay** between 10 and 600 seconds. The default is 30 seconds; **Disarm on return** is enabled by default. If a sensor was selected before Blink was ready, select it again or enable **Automatic monitoring** on Home.
6. 🔋 In **Home → Android permissions → Background battery use**, tap **Allow unrestricted battery use** and confirm Android’s request if background monitoring is needed. On phones with an additional per-app battery setting, choose **Unrestricted**.
7. ✅ Check the ongoing silent notification and event log. Test an actual departure and return, then confirm the state in the official Blink app.

🖼️ The screenshots throughout this guide are actual app renders from an isolated Android 17 / API 37 emulator. Monitoring is stopped; no home sensor or real Blink account is configured. Authentication states and journal entries labeled **[DEMO]** are documentation-only fixtures, not live camera states. They make no Blink requests and do not validate real-device radio behavior.

| Steps 2–3: select your sensor | Step 4: connect Blink | Steps 5–6: configure Home | Step 7: review events |
| --- | --- | --- | --- |
| ![Network tab with Wi-Fi and Bluetooth mode selection and home sensor configuration](docs/screenshots/network-dark.png) | ![Blink tab with email and password fields for account sign-in](docs/screenshots/blink-dark.png) | ![Home tab with presence, Blink status, automatic monitoring, and arming delay controls](docs/screenshots/home-dark.png) | ![Event log with DEMO signal events and a red error with a details action](docs/screenshots/event-log-dark.png) |

For the verification-code screen and system picker, see [Blink account and 2FA](#connect-your-blink-account-and-complete-2fa). In Home, scroll to **Android permissions → Background battery use** for step 6.

⚠️ **The arming delay is a minimum confirmation delay, not a guarantee of arming within that time.** Android can delay scans. Missing Android results never become proof of departure.

### Connect your Blink account and complete 2FA

🔐 Use the credentials of your existing Blink account, which must contain at least one system. Keep your phone connected to the Internet during sign-in.

1. Open the **Blink** tab in Blink Sentinel.
2. Enter your account email in **Blink email address** and your password in **Blink password**, then tap **Sign in to Blink**. Signing in stops automatic monitoring while authentication is in progress.
3. If Blink requests two-factor authentication (**2FA**), retrieve the verification code sent by Blink. Enter it in **Verification code** and tap **Verify code**. If no verification is required, the app proceeds directly to the system list.
4. After authentication succeeds, the section displays **Signed in to Blink**. Choose the Blink system to automate; a single available system is selected automatically. Tap **Refresh systems** if you need to reload the list. Only the selected system is controlled.
5. Return to [Quick start](#quick-start) to configure the sensor and activate monitoring. Compare the displayed system state with the official Blink app before relying on automation.

| Enter the 2FA code — Dark | Choose a system — Dark |
| --- | --- |
| ![Dark Blink verification screen with an empty code field and restart sign-in action](docs/screenshots/blink-2fa-dark.png) | ![Dark Blink system picker with DEMO Home and Garage systems](docs/screenshots/blink-systems-dark.png) |

The system names and statuses above are simulated UI examples. For the initial email/password screen, see [Quick start](#quick-start).

If the code expires or verification fails, tap **Restart sign-in**, enter your credentials again, and use the new code supplied by Blink. For connection errors, open **Event log → View error details** to inspect the sanitized diagnostic information. The integration uses an unofficial mobile API; see [Blink integration and security](#blink-integration-and-security) for its limitations.

Passwords and verification codes are not saved. The authenticated session is stored encrypted using Android Keystore. **Sign out of Blink** clears the local session and stops monitoring; signing in again may require another verification code.

## Interface and themes

🎨 Choose **Dark** or **Light** in **Home → Appearance**. Dark is the default: near-black surfaces (`#0A0A0A`), neon green (`#00FF88`), cyan (`#00D1FF`), monospace headings, thin frames, and outlined icons. The light theme uses navy, teal, and white. This guide illustrates the dark theme; see the screens in [Quick start](#quick-start).

The monitoring indicator pulses while active; stopped monitoring uses a red Presence card. Home displays **Armed** in bold neon green and **Disarmed** in yellow. The header and launcher use the neon **b** icon.

The interface, notifications, diagnostics, and documentation are in **English**, regardless of the phone's language. External SSIDs, device/system names, and historical log messages retain their original values. Dates use `yyyy-MM-dd HH:mm:ss` in local time; distances use an English decimal point.

Full-resolution captures, regeneration, and Android Studio Markdown preview troubleshooting are in [docs/screenshots/README.md](docs/screenshots/README.md).

## Monitoring behavior

📡 Automatic decisions use fresh radio evidence from the selected sensor. Selecting a sensor, losing an Internet connection, or reaching a timer deadline is not proof that you left home.

### Sensor selection and automatic activation

Selecting a Wi-Fi network, using a manually entered SSID, or choosing a Bluetooth device saves the target and starts monitoring with the existing silent notification. Any Bluetooth discovery is stopped first. A Blink session, selected Blink system, and the chosen mode's Android permissions are required. If a prerequisite is missing or Android refuses startup, the selection is retained, monitoring is disabled, and an actionable message is shown. Correct the prerequisite, then select the sensor again or enable monitoring on Home.

Changing the sensor requires fresh observations. Selecting a device never establishes presence or absence by itself, and does not bypass the arming delay.

### Wi-Fi monitoring

`WifiScanReceiver` dynamically listens for `SCAN_RESULTS_AVAILABLE_ACTION`. On Android 10+, this includes full scans performed by Android or other apps, allowing passive listening. Presence means visibility of the **exact, case-sensitive configured SSID**, regardless of whether the phone is connected to it. `ConnectivityManager` requests scans after connection changes and retries commands when Internet access returns; losing a connection or disabling Wi-Fi does not prove departure.

A presence observation is accepted only when:

- The broadcast reports `EXTRA_RESULTS_UPDATED = true`.
- Wi-Fi, Android Location, and precise location permission are available.
- Individual monotonic result timestamps are no more than 60 seconds old.
- The batch is newer than the previously accepted batch.

Android may mix old and new results; each timestamp is checked independently. A cached SSID is not fresh presence. A successful empty scan can establish absence; a failed or refused scan cannot.

| State | Meaning | Action |
| --- | --- | --- |
| `UNKNOWN` | No usable sensor evidence | No automatic command |
| `HOME` | Home SSID visible in a recent scan | Disarm if enabled |
| `AWAY_PENDING` | First absence observation | Wait for the configured delay |
| `AWAY` | Another fresh absent scan after the delay | Check Blink, then arm |

When the delay ends, the service requests **one** scan; the timer never confirms absence itself. A return before confirmation cancels departure. Changes to the SSID, system, or delay reset the decision. Five minutes without valid observations resets detection to `UNKNOWN`. A deferred Blink attempt requires evidence less than 60 seconds old.

The Wi-Fi selection list loads cached results younger than 60 seconds on opening or resuming, then requests a scan. Automatic UI requests are spaced at least 30 seconds apart; no extra background polling is added. Cached display results never become presence evidence. The list explains missing permissions, disabled Wi-Fi or Location, and Android scan refusals. Buttons open precise location permission and Location settings, including after switching from Bluetooth. Distances and proximity sorting are retained.

One-off scans are requested when monitoring starts, connectivity changes, the screen wakes, and the absence delay ends. **Supplemental scans** optionally request a scan every two minutes; this is off by default and remains subject to Android throttling.

#### Android limitations

Foreground apps can generally request four scans in two minutes; background apps share a much stricter limit, generally one scan every thirty minutes. A foreground service keeps the process visible and supports permitted location access; it does not bypass quotas, Doze, or manufacturer restrictions.

An ordinary app **cannot guarantee immediate, continuous SSID detection while the phone is locked**. With no new system scans, passive monitoring waits. Supplemental scans may help on some devices but do not guarantee a frequency.

Hidden SSIDs are unsuitable for reliable name-based monitoring. Access points sharing a name count as the same home. An SSID does not authenticate a home or person: an impersonated SSID can trigger presence and disarming. Monitoring follows this phone, not multiple occupants.

### Bluetooth without Location

This mode requires **Android 12 / API 31+**, BLE support, and `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` permissions in **Nearby devices**. `BLUETOOTH_SCAN` declares `neverForLocation`; neither location permission nor the Location switch is required. Android 10/11 require Location for ordinary BLE scanning, so this app offers only Wi-Fi mode on those versions.

The home device must be stationary, continuously powered, advertise BLE every few seconds, and retain a **stable Bluetooth address**. Bonding and classic Bluetooth connections are not presence evidence. Classic-only speakers, devices that stop advertising while connected, and rotating private addresses are unsuitable. Android filters some beacons with `neverForLocation`; confirm the device remains detectable with Location off before using it. Addresses and BLE advertisements do not authenticate the home and can be imitated.

Discovery lasts up to **22 seconds**: roughly 14 seconds of classic discovery followed by eight seconds of BLE scanning. It stops monitoring to avoid radio interference. Selecting a device stops discovery and automatically activates monitoring when the prerequisites are ready. Duplicate discovery requests are prevented, and scanners/receivers are cleaned up on completion or cancellation.

Names come from BLE advertisements, with fallback to Android's known names. Previously resolved names are retained across unnamed advertisements. Classic devices detected during discovery are also listed. Bonded-device metadata can complete a name, but bonding alone never creates a visible entry. Put unpaired classic devices into discoverable mode. If no name is available, the entry shows **Name not provided** and its address.

All displayed devices are selectable. Entries disappear after **30 seconds without a radio observation**, even if their bond/name is still known. A metadata change does not extend freshness. Search again to refresh; no extra continuous discovery is introduced. Disappearance means the observation expired, not a certain physical range measurement. Classic selections are saved, but automatic monitoring still requires actual BLE advertisements; a classic-only device leaves detection unknown.

Home displays **Monitored Wi-Fi** and the SSID in Wi-Fi mode, or **Monitored Bluetooth** and the selected name/address in Bluetooth mode, directly from current settings.

Monitoring uses a continuous BLE scan **filtered by the chosen address**, in a **connectedDevice** foreground service. The filter supports delivery with the screen off. No permanent wake locks, exact alarms, or repeated service starts are used. Android, Doze, or the manufacturer can still suspend the process or delivery; test on the actual phone.

Advertisement timestamps are monotonic, converted from nanoseconds to milliseconds. A ten-second window containing a recent advertisement establishes presence. After the first empty window, monitoring waits for the arming delay and requires another absent window. A fresh return signal cancels departure immediately. Stale/future advertisements and duplicates cannot confirm departure.

After startup, restart, or a window delayed by more than twenty seconds, detection remains `UNKNOWN` until a real advertisement is received. Starting while away therefore requires a detected return before another departure decision. Bluetooth off, revoked permission, or an explicit scanner error means unknown, with no arming. Radio silence can also mean power loss or interference; it does not guarantee a person's absence.

### Last received signal

**Home → Automatic detection → Last signal received** shows the last actual reception of the chosen SSID/device as **yyyy-MM-dd HH:mm:ss** in the phone's local time zone. It is the phone's reception timestamp, not the transmitter's clock. Android monotonic timestamps are converted to wall-clock time on receipt.

The timestamp does not advance on absence, timers, duplicate results, or a BLE window without a new advertisement. It remains visible during absence or sensor unavailability and after stopping the service in the same process. Changing sensor/mode or restarting monitoring clears it. It is not persisted and never replays an arming decision.

### Estimated distances and sorting

Wi-Fi, BLE, and classic Bluetooth lists show **estimated distance in meters** from received RSSI, sorted nearest first. Missing/invalid RSSI shows **Estimated distance: unavailable** and sorts last. BLE and classic devices are sorted together; their types remain visible. Distances update with discovery observations.

This is an indicative radio model, **not a measured distance**:

```text
d = 10^((assumed RSSI at 1 m − received RSSI) / (10 × 2.5))
```

Assumed references are −40 dBm for Wi-Fi and −59 dBm for Bluetooth. Transmit power, walls, band, and antennas can substantially distort values and ranking. BLE transmit power is not treated as calibrated RSSI at one meter. For a shared SSID, the strongest access point is used. No GPS or additional permission is involved. Distances affect only display, never presence or arming decisions.

### Silent departure alerts

Notifications use **Silent Wi-Fi / Bluetooth monitoring**, configured with no sound and no automatic vibration on updates. The channel has a new ID to avoid inheriting older default sound settings retained by Android. The app does not play audio.

Following an observed `HOME`, confirmed `AWAY` triggers **one 300 ms vibration on the phone**. The arming delay and second confirmation remain required. Pending absence, rapid returns, unknown sensors, duplicate scans, and restarting while already away do not vibrate. A new return permits one vibration for the next confirmed departure. This indicates sensor absence, not success of a Blink command.

`VIBRATE` is a normal manifest permission. Silent mode, **Do Not Disturb**, missing vibrator hardware, and platform restrictions can suppress vibration. The app does not change headphone audio settings. Headphone firmware disconnect sounds or other apps' notifications are outside its control. Users retain control over Android channel settings; keep this channel silent.

Test with headphones connected: confirmed departure gives one handset vibration and no app sound; rapid return gives none; repeated `AWAY` updates do not repeat it. Also test with the screen locked, silent mode, and Do Not Disturb.

## Android permissions and background operation

🔋 Configure access in **Home → Android permissions**. Background operation depends on Android and manufacturer restrictions, even after a battery exemption.

### Permissions

| Permission | Purpose | Request |
| --- | --- | --- |
| `ACCESS_WIFI_STATE` | Read scan results | Manifest |
| `CHANGE_WIFI_STATE` | Request one-off scans | Manifest |
| `ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION` | Read SSIDs with precise access | Runtime, requested together |
| `ACCESS_BACKGROUND_LOCATION` | Wi-Fi boot recovery from background | Optional, separate step |
| `BLUETOOTH_SCAN` (`neverForLocation`) + `BLUETOOTH_CONNECT` | Discovery and BLE monitoring without Location, Android 12+ | Runtime, Bluetooth mode |
| `BLUETOOTH` + `BLUETOOTH_ADMIN` (max API 30) | Legacy declarations; no Location-free BLE mode on Android 10/11 | Manifest |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` | Wi-Fi foreground service | Manifest |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Bluetooth foreground service, Android 14+ | Manifest |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Package-specific battery exemption request after a user tap | Manifest; Android confirmation |
| `VIBRATE` | Handset vibration on confirmed departure | Manifest, no runtime prompt |
| `POST_NOTIFICATIONS` | Service notification and resume reminder, Android 13+ | Runtime |
| `INTERNET` | Blink HTTPS authentication and commands | Manifest |
| `ACCESS_NETWORK_STATE` | Observe network availability | Manifest |
| `RECEIVE_BOOT_COMPLETED` | Boot recovery | Manifest |

`NEARBY_WIFI_DEVICES` is not requested: `getScanResults` / `startScan` still require `ACCESS_FINE_LOCATION`, including Android 13+. Nearby permission alone does not authorize these results. Location must be enabled **in Wi-Fi mode**. No GPS API is called and no coordinates are sent.

Without notification permission on Android 13+, Android may allow the service while showing it only in the active-apps task manager; boot reminders will not be visible.

### Background battery access

In **Home → Android permissions → Background battery use**, the **Allow unrestricted battery use** button requests a battery-optimization exemption specifically for Blink Sentinel. Android shows its own confirmation; allow it to reduce power-saving interference with home-sensor monitoring. This can increase battery consumption. The app does not change this setting silently or request it on startup.

The displayed status comes from Android's `isIgnoringBatteryOptimizations` and refreshes when you return to the app. If exemption is already granted, the button becomes **Open battery settings**. If a phone does not support the direct request, the app opens the general optimization list, then app details as a fallback. Select Blink Sentinel and disable optimization there. Some manufacturers additionally expose **App info → Battery → Unrestricted**; on Samsung, also use **Never sleeping apps** if needed.

The exemption is **partial**. It can improve background network availability but does not remove every Android or manufacturer restriction, Wi-Fi scan quotas, foreground-service requirements, or Bluetooth delivery limitations. Keep the silent foreground notification, test with the screen locked, and do not assume continuous operation is guaranteed. Denying or canceling the request leaves the existing Android setting unchanged.

### Background service and reboot

`WifiMonitorService` uses the **location** service type in Wi-Fi mode and **connectedDevice** in Bluetooth mode. It starts from the visible interface with the chosen mode's permissions. It can continue while the interface is closed or the phone is locked, subject to Android restrictions.

Boot recovery requires the relevant permissions. Wi-Fi background recovery requires separately granted background location; without the necessary access, a silent notification asks the user to open the app. Bluetooth recovery requires Android 12+ and retained Nearby devices permissions. Android background-start restrictions still apply; a refused recovery is reported rather than repeatedly retried. A restart always requires new evidence.

## Event log and diagnostics

Errors appear **in red with a date, warning icon, and explicit message**, inside a red framed card. Tap the card or **View error details** for scrollable, selectable, copyable details: operation, HTTP method, actual request URL, HTTP status, exception type/message, causes, and full stack trace. Failure before a response shows **No HTTP status received**. Interrupted reads after headers retain the received status. Parsing failures remain associated with their request.

The persistent log keeps **500 entries by default**, newest first. Open **Event log → Log settings → Entries to keep** to choose **50–5000**, then tap **Save**. The setting survives app restarts. Lowering it immediately removes the oldest entries; increasing it cannot restore deleted history. Changing journal retention does not reset sensor evidence or pending automation. **Use default (500)** restores the default value in the dialog.

Distinct outline icons identify detected signals, missing signals, confirmed absence, unknown signals, waiting for confirmation, monitoring start/stop, Blink arming/disarming, account events, information, and errors. Types are recorded at the event source and preserved with the journal. Historical entries without a type use the information icon; historical error flags and details still identify errors. A timer’s waiting event never means absence was confirmed.

Passwords, tokens, codes, secrets, cookies, authorization values, URL credentials, and query values are redacted **before storage and display**. The marker is `[REDACTED]`. HTTP bodies, forms, and headers are never added to the log. Original network causes are retained, including DNS, connection, TLS, and interrupted reads. Copying uses already sanitized text. Older events remain available in their original language.

![Dark DEMO error details with request context, exception causes, and selectable scrollable technical information](docs/screenshots/error-details-dark.png)

## Development

🛠️ This is a native Kotlin / Jetpack Compose project, directly importable in Android Studio.

### Development requirements

- Android Studio compatible with **Android Gradle Plugin 9.4.1**, a full compatible **JDK** (validated with JBR / OpenJDK **21.0.11**), SDK Platform **35**, Build-Tools **35.0.0** for the documented verification commands, **36.0.0** for the current AGP build, and Platform-Tools for `adb`.
- Internet access for the first download of Gradle and Google Maven / Maven Central dependencies.

Pinned versions: Kotlin 2.2.10, Compose BOM 2025.04.01, AGP 9.4.1, Gradle 9.6.0. Java/Kotlin bytecode targets Java 17; `compileSdk` and `targetSdk` are 35. AGP 9.4.1 selects Build-Tools 36.0.0 despite the retained `buildToolsVersion = "35.0.0"` declaration; both tool versions were available during validation.

GNU Make and Bash are needed for the convenience commands. The Gradle wrapper is included; no separate Gradle installation is required.

### Open in Android Studio

If using a source archive:

```bash
tar -xzf blink-wifi-automation.tar.gz
```

Use **Open** and select the top-level `blink-wifi-automation` directory containing `settings.gradle.kts`, then wait for Gradle synchronization. Open the whole project, not just `app`.

Under **Settings → Build, Execution, Deployment → Build Tools → Gradle**, select a compatible JDK. Install SDK Platform 35 and Build-Tools 35.0.0 in **SDK Manager**. Android Studio creates `local.properties` with your local SDK path; machine-specific SDK paths should not be distributed.

Connect your phone, enable **Developer options → USB debugging**, accept the computer's RSA key, select the `app` module and phone, then choose **Run**.

### Makefile commands

Run these commands from the project root. `make` shows categorized help; use `NO_COLOR= make help` to disable ANSI colors while retaining emojis.

#### Help, formatting, and tests

| Command | What it does |
| --- | --- |
| `make` / `make help` | Shows the logo and categorized command help. |
| `make fmt` | Formats Kotlin and Kotlin DSL files with `ktlintFormat`; modifies source files. |
| `make test` | Runs JVM unit tests with `testDebugUnitTest`. |
| `make test-one TEST=dev.homesentinel.PresenceMachineTest` | Runs one JVM test class; use its fully qualified name. |
| `make lint` | Runs Android lint; the HTML report is under `app/build/reports/lint-results-debug.html`. |
| `make check` | Runs formatting, JVM tests, then lint; stops if a step fails. |
| `make tasks` | Lists available Gradle tasks. |

🧪 `make test` does not start an emulator or show clicks on a phone. Instrumentation tests and the documentation screenshot runner are separate; see [screenshot regeneration](docs/screenshots/README.md#regenerate).

#### Build and install

| Command | What it does |
| --- | --- |
| `make debug` | Builds `app/build/outputs/apk/debug/app-debug.apk`. |
| `make build` | Runs `make check`, then builds the debug APK. |
| `make release-apk` | Builds the local release variant without publishing; unsigned unless release signing is configured. |
| `make install` | Builds and installs the debug variant on a connected, authorized device or emulator using Gradle. |
| `make clean` | Removes Gradle build outputs. |

#### Publishing commands

| Command | What it does |
| --- | --- |
| `make push` | Stages all changes, checks the required Git identity before committing, commits if needed, and pushes the current branch. |
| `make release VERSION=0.1.2 JKS_FILE=/secure/path/release.jks` | Formats, tests, lints, updates the version, builds and verifies a signed release APK, commits/pushes, and creates a GitHub release. |

For publishing prerequisites, destination variables, and signing, see [GitHub publishing](#github-publishing).

### Build and test

You can also run verification and builds directly through the Gradle wrapper. Linux / macOS:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

If the SDK is not detected, set `ANDROID_HOME` or create `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`, signed with the local debug key. `make release-apk` / `assembleRelease` produces an unsigned APK unless release signing is configured. To create a distributable signed release, use [make release](#what-make-release-does) or Android Studio's **Generate Signed Bundle / APK**.

### Architecture

```text
app/src/main/java/dev/homesentinel/
├── data/
│   ├── blink/          # OAuth, HTTPS, strict parsing, Keystore vault
│   ├── bluetooth/      # Filtered BLE scans and observation windows
│   ├── wifi/           # WifiManager adapter and freshness policy
│   └── preferences/    # DataStore and bounded local event log
├── domain/
│   ├── model/          # Settings, Presence, BlinkStatus, WifiEvidence
│   ├── repository/     # BlinkService and WifiScanner
│   └── usecase/        # Presence, command serialization, activation
├── service/
│   ├── WifiMonitorService.kt
│   ├── BlinkAutomationService.kt
│   └── MonitorNotifications.kt
├── receiver/           # Wi-Fi broadcasts and boot recovery
├── ui/
│   ├── screens/        # Home, Network, Blink, Event log
│   ├── components/
│   ├── theme/
│   └── SentinelViewModel.kt
├── MainActivity.kt
└── SentinelApplication.kt
```

`SentinelApplication` / `AppGraph` provide application-scoped dependencies and restoration without a DI framework. `StateFlow` drives Compose; network coroutines are cancellable. A mutex serializes manual/automatic commands. Pure policies are JVM-testable, with injected clocks and MockWebServer endpoints. No periodic WorkManager job polls Wi-Fi or bypasses Android limits.

## GitHub publishing

🚀 Publishing requires GNU Make, Bash, Git, GitHub CLI (`gh`), a compatible JDK with `java` and `keytool`, and Android SDK Build-Tools with `apksigner`. Run the command from an interactive terminal in the project directory. Create the GitHub repository and authenticate with `gh auth login` first.

Configure the repository's Git identity before any automatic commit. Example:

```bash
git config user.name "John Doe"
git config user.email "john.doe@example.com"
```

These are placeholder values. Use your own Git identity; the publishing targets verify that it matches the expected identity configured in the Makefile before creating a commit. Repository and branch selection are configured below.

### Publishing variables

| Variable | Purpose / default |
| --- | --- |
| `GIT_MAIN_REPO` | Default push destination and release destination: `https://github.com/0x00F6/blink-sentinel.git`. |
| `GIT_REPO` | Overrides the destination for `make push`; accepts `OWNER/REPOSITORY` or a GitHub HTTPS URL. Release uses `GIT_MAIN_REPO`. |
| `GIT_BRANCH` | Optional branch check; must match the checked-out branch. Defaults to the current branch. |
| `GIT_REMOTE` | Remote name, default `origin`; its URL must match the destination. |
| `GIT_COMMIT_MESSAGE` | Automatic commit message, default `chore: publish Blink Sentinel project`. |
| `VERSION` | Required for release, in `MAJOR.MINOR.PATCH` format. |
| `JKS_FILE` | Required release keystore path; can be supplied on the command line or exported in the environment. |
| `JKS_ALIAS` | Optional signing key alias; otherwise prompted interactively. |
| `NO_COLOR` | Disables ANSI colors when present, even with an empty value; emojis remain. |

```bash
make push GIT_REPO=OWNER/REPOSITORY GIT_BRANCH=main GIT_REMOTE=origin \
  GIT_COMMIT_MESSAGE="docs: improve setup guide"
```

Review local changes before publishing: `make push` and `make release` stage all repository changes, including formatting edits.

### Create a signing keystore

🔑 A keystore is a local file containing the private key used to sign your APK. To create one in Android Studio:

1. Open **Build → Generate Signed Bundle / APK**, select **APK**, and click **Next**.
2. Beside **Key store path**, click **Create new**.
3. Choose a location outside this Git repository, such as `/home/your-user/.android/blink-sentinel-release.jks`, and set a strong keystore password.
4. Set the key alias, for example `blink-sentinel`, its password, a validity of at least **25 years**, and the certificate information requested by the dialog.
5. Save the keystore. You may finish the wizard to build a signed APK, or cancel the remaining build steps after the keystore has been created.

See the [official Android signing guide](https://developer.android.com/studio/publish/app-signing) for the complete wizard. Keep a secure backup of the keystore, alias, and passwords. Use the same signing key for future updates. Do not commit the keystore or passwords, or store passwords in your shell configuration.

### Configure the keystore path

You can provide the path with each command:

```bash
make release VERSION=0.1.2 JKS_FILE="$HOME/.android/blink-sentinel-release.jks"
```

Or add these exports to `~/.zshrc`, using your actual path and key alias:

```zsh
export JKS_FILE="$HOME/.android/blink-sentinel-release.jks"
export JKS_ALIAS="blink-sentinel"
```

Reload the shell configuration with `source ~/.zshrc` or open a new terminal. Then run:

```bash
make release VERSION=0.1.2
```

`JKS_FILE` is required and must refer to an existing keystore. `JKS_ALIAS` is optional: when omitted, the command asks for the alias. Exported environment variables are accepted; command-line values override them.

### What make release does

📦 For `VERSION=0.1.2`, the command performs these steps:

1. Validates the repository, version format (`MAJOR.MINOR.PATCH`), keystore path, and JDK tools.
2. Runs `make check`: Kotlin formatting (`ktlintFormat`), all JVM unit tests (`testDebugUnitTest`), and Android lint (`lintDebug`), in that order. A failure stops the release before the password prompts or version change. Emulator instrumentation tests are separate and are not part of this check.
3. Prompts for the keystore password without displaying it, asks for the alias if needed, and validates access to that alias. It then prompts for the private key password; press **Enter** to reuse the keystore password.
4. Writes `versionName = "0.1.2"` to `app/build.gradle.kts` and logs the old and new values. Subsequent local builds use this version too. An existing tag or GitHub release with this version stops publication.
5. Builds the **release variant** with the supplied key, using `assembleRelease --no-daemon`. Passwords are passed through temporary environment variables and cleared from the release shell after the build.
6. Verifies the signature of `app/build/outputs/apk/release/app-release.apk` with `apksigner`. A missing APK or failed signature check stops publication.
7. Stages all repository changes, including formatting and version changes, checks the Git identity, commits if needed, and pushes the current branch.
8. Creates the GitHub release tagged **`v0.1.2`**, generates release notes, and uploads the signed APK. The APK is installable on compatible devices; updating an existing installation also requires a compatible signing key.

To select another release repository or branch, use:

```bash
make release VERSION=0.1.2 \
  GIT_MAIN_REPO=https://github.com/OWNER/REPOSITORY.git \
  GIT_BRANCH=main
```

A failure after the version update can leave local changes; review them before retrying. Reuse the same version only if its GitHub release and tag have not already been created.

## Blink integration and security

`BlinkService` is the domain interface. `BlinkApiService` implements the mobile protocol observed in **blinkpy 0.25.9**: OAuth v2 with PKCE, memory-only cookies, CSRF, explicit 2FA, and access/refresh tokens. It recognizes HTTP 412 and HTTP 202 responses carrying 2FA information. It uses a stable UUID hardware identifier and community-observed client parameters centralized in the adapter.

⚠️ This arming/disarming API is **unofficial and unsupported**. Blink's official EU data portability API does not document these commands. Authentication can fail because of account conditions, protocol changes, service protections, or network restrictions. HTTP 403, 406, and 429 are reported with actionable errors. Amazon SSO or CAPTCHA changes would require adapter changes; no protection bypass is implemented.

Regional URLs always use `rest-`: region `e005` becomes **`https://rest-e005.immedia-semi.com/`**, including older persisted sessions. An existing `rest-e005` prefix is not duplicated. Systems come from `homescreen`. Before a command, `EnsureBlinkState` queries the real state; matching states avoid duplicate POSTs, and unknown state blocks the command. After a POST, the adapter checks up to five times at two-second intervals for actual confirmation. HTTP acceptance alone is not confirmed success.

Network/server retries are bounded to four attempts, separated by 15, 30, and 60 seconds, and require fresh sensor evidence. New evidence or restored Internet can trigger reconciliation. Authentication, protocol, and rate-limit errors stop retries. Each retry reads real state first rather than blindly replaying an ambiguous POST.

The selected system ID is captured before status lookup and passed explicitly to the command. Selection changes cancel an action rather than redirecting it to a different system. Persisted settings are checked again before automatic effects, independently of Flow timing.

New evidence cancels obsolete work and its HTTP call. If Blink already received a command, cancellation cannot recall it; the next desired state is queried and reconciled. Account for cloud latency in real tests.

### Storage

- Passwords and 2FA codes are never saved in preferences, saved UI state, or logs.
- Tokens, account ID, region, and UUID use `noBackupFilesDir`, **AES-256-GCM**, a non-exportable **Android Keystore** key, and atomic writes.
- Non-secret settings use **Preferences DataStore**. The bounded local event log excludes secrets and HTTP bodies.
- Android backup and app-data transfer are excluded. TLS validation remains enabled; cleartext HTTP is forbidden by the manifest.
- `FLAG_SECURE` restricts screenshots and recent-app previews.
- A corrupt session or lost key requires reauthentication. No plaintext fallback exists.
- **Sign out of Blink** clears the local session and stops monitoring. It does not claim server-side token revocation; use the official Blink app for remote account/device management.

## Real-phone validation

Automated tests use local **MockWebServer** and fictitious sessions, not a live Blink account. Builds and simulated tests do not validate radio behavior, background delivery, or Blink's actual service. See [VALIDATION.md](VALIDATION.md) for exact commands, results, and limitations.

### Wi-Fi

1. Authenticate with a real account, select the system, and compare state with official Blink.
2. Keep Wi-Fi on but disable automatic connection to home; check that scans still detect the SSID.
3. Leave home with mobile Internet available. Check `AWAY_PENDING`, then `AWAY` only after fresh confirmation and the delay; verify Blink's actual state.
4. Return before confirmation: no arming command should be sent. Return after arming: check optional disarming.
5. Disable Wi-Fi or Location: detection should become unknown without arming from that action alone. Turning Wi-Fi off is not a valid simulated departure.
6. Test prolonged screen lock, optional supplemental scans, Internet loss, reopening, and reboot with/without background location. Test the battery button by allowing, denying and canceling the request; verify status on return and behavior with manufacturer restrictions.
7. Switch from Bluetooth with Location off. Check the Wi-Fi list's permission/Location buttons, automatic scan on return, distances, sorting, and scan-refusal messages.

### Bluetooth and diagnostics

1. On Android 12+, turn Location off, enable Bluetooth, grant Nearby devices, and discover a compatible device. Verify names, selectable entries, sorting, and expiry after 30 seconds without observation.
2. Select a device during discovery: check discovery stops, monitoring starts automatically with the silent notification, and `HOME` requires an actual signal.
3. Lock the phone and leave with mobile Internet. Check pending absence, delay, fresh confirmation, one handset vibration, no app audio, and the actual Blink state.
4. Return before confirmation to cancel, or after arming to verify optional disarming. Check the last-signal date/time changes only with a new actual reception.
5. Disable Bluetooth or revoke permission: expect unknown without arming. Restore it and require fresh evidence.
6. Test prolonged Doze, reboot, and sensor changes. A restart without a received advertisement must stay unknown.
7. Trigger a network failure with no real secrets in logs. Check the red dated error, English diagnostic fields, original causes, full trace, scroll/copy, and redaction.
8. Select a sensor without Blink or permissions ready: confirm the choice is retained, monitoring remains disabled, and the message identifies the prerequisite.

## Project resources

🎨 The current header and launcher use the neon **b** artwork with a near-black adaptive background and a monochrome layer for themed icons. The original house/shield artwork and earlier launcher resources remain included; the white notification icon is retained. See [artwork/README.md](artwork/README.md).

- 📜 [LICENSE](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.md).
- 🧭 [AGENTS.md](AGENTS.md): architecture, security, and behavior invariants for contributors.
- ✅ [VALIDATION.md](VALIDATION.md): exact verification commands, results, and device/network limitations.
- 🖼️ [Screenshot documentation](docs/screenshots/README.md): capture provenance, regeneration, and preview troubleshooting.

### Technical sources

- [Android — Doze, App Standby, and battery exemptions](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Android — notification channels](https://developer.android.com/develop/ui/views/notifications/channels)
- [Android — haptics APIs](https://developer.android.com/develop/ui/views/haptics/haptics-apis)
- [Android — Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Android — BLE background](https://developer.android.com/develop/connectivity/bluetooth/ble/background)
- [Android — Wi-Fi scanning](https://developer.android.com/develop/connectivity/wifi/wifi-scan)
- [Android — foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android — location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions)
- [Android — Wi-Fi permissions](https://developer.android.com/develop/connectivity/wifi/wifi-permissions)
- [blinkpy source and releases](https://github.com/fronzbot/blinkpy)
- [Blink — HTTP 406 errors](https://support.blinkforhome.com/fr_FR/how-to-resolve-http-406-errors)
- [Blink — EU data portability](https://support.blinkforhome.com/fr_FR/contact-support/transfer-your-blink-data-to-third-parties)

Blink Sentinel is independent and is not affiliated with Amazon or Blink.
