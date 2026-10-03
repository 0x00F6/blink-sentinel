package dev.homesentinel.ui.screens

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.homesentinel.R
import dev.homesentinel.domain.model.AppThemeMode
import dev.homesentinel.domain.model.BlinkStatus
import dev.homesentinel.domain.model.LogEntry
import dev.homesentinel.domain.model.MonitorStatus
import dev.homesentinel.domain.model.MonitoringMode
import dev.homesentinel.domain.model.Presence
import dev.homesentinel.domain.model.Settings
import dev.homesentinel.ui.SentinelViewModel
import dev.homesentinel.ui.components.EventLogCard
import dev.homesentinel.ui.components.LogErrorDialog
import dev.homesentinel.ui.components.LogRetentionDialog
import dev.homesentinel.ui.components.Section
import dev.homesentinel.ui.components.SentinelButton
import dev.homesentinel.ui.components.SentinelOutlinedButton
import dev.homesentinel.ui.components.SettingSwitch
import dev.homesentinel.ui.components.TerminalNavigation
import dev.homesentinel.ui.components.label
import dev.homesentinel.ui.components.terminalBackdrop
import dev.homesentinel.ui.theme.BlinkSentinelTheme
import dev.homesentinel.ui.theme.LocalTerminalTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SentinelApp(
    vm: SentinelViewModel,
    requestAccess: () -> Unit,
    requestBoot: () -> Unit,
    openLocation: () -> Unit,
    openSettings: () -> Unit,
    openBattery: () -> Unit,
) {
    val config by vm.settings.collectAsStateWithLifecycle()
    val monitor by vm.monitor.collectAsStateWithLifecycle()
    val status by vm.blinkStatus.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val events by vm.logs.collectAsStateWithLifecycle()
    // Use an unambiguous date and English number formatting even on a non-English phone.
    val logFormatter =
        remember {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", java.util.Locale.ENGLISH).withZone(ZoneId.systemDefault())
        }
    var selectedError by remember { mutableStateOf<LogEntry?>(null) }
    var showLogRetention by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.message.value = null
        }
    }
    val darkTheme = config.themeMode == AppThemeMode.DARK
    BlinkSentinelTheme(darkTheme = darkTheme) {
        val view = LocalView.current
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                val barColor = if (darkTheme) Color(0xFF0A0A0A) else Color(0xFFF4F7FB)
                window.statusBarColor = barColor.toArgb()
                window.navigationBarColor = barColor.toArgb()
                WindowInsetsControllerCompat(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
        selectedError?.let { entry ->
            LogErrorDialog(
                entry = entry,
                date = logFormatter.format(Instant.ofEpochMilli(entry.at)),
                onDismiss = { selectedError = null },
            )
        }
        if (showLogRetention) {
            LogRetentionDialog(
                current = config.logRetention,
                onSave = { value ->
                    vm.setLogRetention(value)
                    showLogRetention = false
                },
                onDismiss = { showLogRetention = false },
            )
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (LocalTerminalTheme.current) {
                    TerminalNavigation(tab) { tab = it }
                } else {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        val icons = listOf(R.drawable.ic_home, R.drawable.ic_network, R.drawable.ic_blink, R.drawable.ic_event_log)
                        listOf("Home", "Network", "Blink", "Event log").forEachIndexed { i, label ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = {
                                    Icon(
                                        painter = painterResource(icons[i]),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                label = { Text(label) },
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).terminalBackdrop(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color =
                                    androidx.compose.ui.graphics
                                        .Color(0xFF0A0A0A),
                                border =
                                    if (LocalTerminalTheme.current) {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    } else {
                                        null
                                    },
                            ) {
                                Image(
                                    painterResource(R.drawable.terminal_b),
                                    contentDescription = "Neon b application logo",
                                    modifier = Modifier.size(64.dp),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Blink Sentinel",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color =
                                        if (LocalTerminalTheme.current) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                )
                                Text(
                                    "Wi-Fi & Bluetooth monitoring",
                                    style =
                                        if (LocalTerminalTheme.current) {
                                            MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                        } else {
                                            MaterialTheme.typography.bodyMedium
                                        },
                                    color =
                                        if (LocalTerminalTheme.current) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            }
                            if (busy) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                    when (tab) {
                        0 -> {
                            item {
                                PresenceCard(monitor, config, status)
                            }
                            item {
                                Section("Appearance", "Choose the app color theme.") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FilterChip(
                                            selected = darkTheme,
                                            onClick = { vm.update { it.copy(themeMode = AppThemeMode.DARK) } },
                                            label = { Text("Dark") },
                                        )
                                        FilterChip(
                                            selected = !darkTheme,
                                            onClick = { vm.update { it.copy(themeMode = AppThemeMode.LIGHT) } },
                                            label = { Text("Light") },
                                        )
                                    }
                                }
                            }
                            item {
                                Section(
                                    "Automatic detection",
                                    if (config.mode == MonitoringMode.WIFI) {
                                        "A Wi-Fi scan is enough: no connection to your home network is required."
                                    } else {
                                        "Monitoring uses BLE signals received from the selected device."
                                    },
                                ) {
                                    SettingSwitch(
                                        "Automatic monitoring",
                                        "Silent notification; phone vibration when absence is confirmed",
                                        config.enabled,
                                        !busy,
                                    ) {
                                        if (it) vm.enable() else vm.disable()
                                    }
                                    val lastSignal =
                                        monitor.lastSignalAtEpochMillis
                                            .takeIf { monitor.lastSignalSource == config.signalSourceKey }
                                    Text(
                                        "Last signal received: " + (
                                            lastSignal?.let {
                                                logFormatter.format(Instant.ofEpochMilli(it))
                                            } ?: "no signal received since monitoring started"
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (config.enabled && !monitor.running) {
                                        SentinelButton(
                                            onClick = vm::enable,
                                            enabled = !busy,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text("Resume monitoring")
                                        }
                                    }
                                    SettingSwitch(
                                        "Disarm on return",
                                        "After a fresh detection of the home sensor",
                                        config.autoDisarm,
                                        !busy,
                                    ) { checked ->
                                        vm.update { it.copy(autoDisarm = checked) }
                                    }
                                }
                            }
                            item {
                                Section(
                                    "Blink commands",
                                    "Each action checks the current system state before sending a command.",
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        SentinelButton(
                                            onClick = { vm.manual(BlinkStatus.ARMED) },
                                            enabled =
                                                connected && config.systemId.isNotBlank() && !busy,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text("Arm")
                                        }
                                        SentinelOutlinedButton(
                                            onClick = { vm.manual(BlinkStatus.DISARMED) },
                                            enabled =
                                                connected && config.systemId.isNotBlank() && !busy,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text("Disarm")
                                        }
                                    }
                                    TextButton(
                                        onClick = vm::refreshStatus,
                                        enabled = connected && config.systemId.isNotBlank() && !busy,
                                    ) {
                                        Text("Refresh status")
                                    }
                                }
                            }
                            item {
                                PermissionsSection(
                                    vm,
                                    requestAccess,
                                    requestBoot,
                                    openLocation,
                                    openSettings,
                                    openBattery,
                                )
                            }
                        }
                        1 -> {
                            item {
                                Section(
                                    "Monitoring mode",
                                    "Changing mode stops monitoring. Selecting a sensor enables it automatically when Blink and Android permissions are ready.",
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        MonitoringMode.entries.forEach { mode ->
                                            val selected = config.mode == mode
                                            Surface(
                                                modifier = Modifier.weight(1f),
                                                shape = MaterialTheme.shapes.medium,
                                                color =
                                                    if (selected) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.surface
                                                    },
                                                border =
                                                    BorderStroke(
                                                        1.dp,
                                                        if (selected) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.outlineVariant
                                                        },
                                                    ),
                                            ) {
                                                Row(
                                                    Modifier
                                                        .clickable(
                                                            enabled = !busy,
                                                        ) { vm.chooseMode(mode) }
                                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    RadioButton(selected, { vm.chooseMode(mode) }, enabled = !busy)
                                                    Text(
                                                        if (mode ==
                                                            MonitoringMode.WIFI
                                                        ) {
                                                            "Wi-Fi"
                                                        } else {
                                                            "Bluetooth"
                                                        },
                                                        style = MaterialTheme.typography.titleSmall,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (config.mode == MonitoringMode.BLUETOOTH) {
                                item { BluetoothSection(vm, config, busy, requestAccess) }
                                item { DelaySection(vm, config, busy) }
                            } else {
                                item {
                                    Section(
                                        "Home Wi-Fi",
                                        "Select the exact, case-sensitive SSID. Access points with the same name are grouped together.",
                                    ) {
                                        Text(
                                            config.homeSsid.ifBlank { "No network selected" },
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        SentinelButton(onClick = vm::scan, enabled = !busy) {
                                            Text("Search for networks")
                                        }
                                        Text(
                                            "Android may limit or delay scans, especially when the screen is locked. Missing scans do not prove you have left.",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                item { WifiList(vm, config, busy, requestAccess, openLocation, openSettings) }
                                item { DelaySection(vm, config, busy) }
                                item {
                                    Section(
                                        "Supplemental scans",
                                        "System scans are received passively. A scan is also requested on startup, connection changes, screen wake, and when the absence delay ends.",
                                    ) {
                                        SettingSwitch(
                                            "Request a scan every 2 minutes",
                                            "Optional: uses more battery and remains subject to Android restrictions",
                                            config.supplementalScan,
                                            !busy,
                                        ) { checked ->
                                            vm.update { it.copy(supplementalScan = checked) }
                                        }
                                        SentinelOutlinedButton(onClick = requestAccess) {
                                            Text("Wi-Fi permissions")
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            item { BlinkSection(vm, config, connected, busy) }
                            item {
                                Section("About Blink") {
                                    Text(
                                        "This independent app uses Blink's mobile protocol, an unofficial API that may change. Sign in with your Blink credentials, then enter the verification code if requested.",
                                    )
                                    Text(
                                        "Your password is never saved. Tokens are encrypted with an Android Keystore key. Only the selected system is automated.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                        3 -> {
                            item {
                                Section(
                                    "Event log",
                                    "${events.size} / ${config.logRetention} entries stored on this phone. Secrets are redacted. Tap an error to view and copy its details.",
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        TextButton(onClick = { showLogRetention = true }, enabled = !busy) { Text("Log settings") }
                                        TextButton(onClick = vm.graph.logs::clear) { Text("Clear event log") }
                                    }
                                }
                            }
                            if (events.isEmpty()) item { Text("The event log is empty.") }
                            items(events) { entry ->
                                EventLogCard(entry, logFormatter.format(Instant.ofEpochMilli(entry.at))) { selectedError = entry }
                            }
                        }
                    }
                    item {
                        Text(
                            "Sensor detection does not guarantee a person's presence. Test departures and returns on your phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresenceCard(
    monitor: MonitorStatus,
    config: Settings,
    status: BlinkStatus,
) {
    val colors = MaterialTheme.colorScheme
    val terminalTheme = LocalTerminalTheme.current
    val (background, foreground) =
        if (!monitor.running) {
            colors.errorContainer to colors.onErrorContainer
        } else {
            when (monitor.presence) {
                Presence.HOME -> colors.primaryContainer to colors.onPrimaryContainer
                Presence.AWAY_PENDING -> colors.tertiaryContainer to colors.onTertiaryContainer
                Presence.AWAY, Presence.UNKNOWN -> colors.secondaryContainer to colors.onSecondaryContainer
            }
        }
    Card(
        colors = CardDefaults.cardColors(containerColor = background, contentColor = foreground),
        border = if (LocalTerminalTheme.current) BorderStroke(1.dp, foreground.copy(alpha = 0.35f)) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("PRESENCE", style = MaterialTheme.typography.labelMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (monitor.running) {
                        val pulse by rememberInfiniteTransition(label = "monitoring pulse")
                            .animateFloat(
                                initialValue = 0.25f,
                                targetValue = 1f,
                                animationSpec =
                                    infiniteRepeatable(
                                        animation = tween(850, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse,
                                    ),
                                label = "monitoring dot alpha",
                            )
                        Box(Modifier.size(8.dp).background(Color(0xFF00FF88).copy(alpha = pulse), CircleShape))
                    } else {
                        Box(Modifier.size(8.dp).background(colors.error, CircleShape))
                    }
                    Text(if (monitor.running) "Monitoring active" else "Service stopped", style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(monitor.presence.label(), style = MaterialTheme.typography.headlineMedium)
                Text(monitor.note, style = MaterialTheme.typography.bodyMedium, color = foreground.copy(alpha = 0.85f))
            }
            HorizontalDivider(color = foreground.copy(alpha = 0.15f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(painterResource(R.drawable.ic_blink), contentDescription = null, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        buildAnnotatedString {
                            append("Blink: ")
                            when (status) {
                                BlinkStatus.ARMED ->
                                    withStyle(
                                        SpanStyle(
                                            color = if (terminalTheme) Color(0xFF00FF88) else Color(0xFF006B35),
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    ) { append("Armed") }
                                BlinkStatus.DISARMED ->
                                    withStyle(
                                        SpanStyle(
                                            color = if (terminalTheme) Color(0xFFFFD36A) else Color(0xFF795500),
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    ) { append("Disarmed") }
                                BlinkStatus.UNKNOWN -> append("State unknown")
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(config.systemName.ifBlank { "No system selected" }, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val wifi = config.mode == MonitoringMode.WIFI
                Icon(
                    painterResource(if (wifi) R.drawable.ic_network else R.drawable.ic_bluetooth),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (wifi) "Monitored Wi-Fi" else "Monitored Bluetooth", style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (wifi) {
                            config.homeSsid.ifBlank { "Not configured" }
                        } else {
                            config.bluetoothDeviceName.ifBlank { config.bluetoothDeviceAddress.ifBlank { "Not configured" } }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            monitor.deadline?.let { Countdown(it) }
        }
    }
}

@Composable
private fun Countdown(deadline: Long) {
    var remaining by
        remember(deadline) {
            mutableLongStateOf((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0) / 1000)
        }
    LaunchedEffect(deadline) {
        while (remaining > 0) {
            kotlinx.coroutines.delay(1000)
            remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0) / 1000
        }
    }
    Text(
        if (remaining > 0) {
            "Confirmation in $remaining s"
        } else {
            "Waiting for a confirmation scan"
        },
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun PermissionsSection(
    vm: SentinelViewModel,
    access: () -> Unit,
    boot: () -> Unit,
    location: () -> Unit,
    settings: () -> Unit,
    battery: () -> Unit,
) {
    val p by vm.permissions.collectAsStateWithLifecycle()
    val config by vm.settings.collectAsStateWithLifecycle()
    val bluetooth = config.mode == MonitoringMode.BLUETOOTH
    Section(
        "Android permissions",
        if (bluetooth) {
            "Android 12+: Nearby devices permission. Android Location can remain off."
        } else {
            "Android requires precise location permission to read SSIDs. GPS is not used."
        },
    ) {
        if (bluetooth) {
            Text("Bluetooth ready: ${if (p.bluetoothAvailable) "yes" else "check Android 12+, permissions, and Bluetooth"}")
        } else {
            Text(
                "Precise location: ${if (p.precise) "granted" else "not granted"}\nAndroid Location: ${if (p.locationEnabled) "on" else "off"}\nWi-Fi: ${if (p.wifiEnabled) "on" else "off"}\nNotifications: ${if (p.notifications) "allowed" else "not allowed"}",
            )
        }
        SentinelButton(onClick = access, modifier = Modifier.fillMaxWidth()) {
            Text("Grant required permissions")
        }
        if (!bluetooth && !p.locationEnabled) {
            TextButton(onClick = location) { Text("Enable Android Location") }
        }
        TextButton(onClick = settings) { Text("Open app settings") }
        HorizontalDivider()
        Text("Resume after reboot", style = MaterialTheme.typography.titleSmall)
        Text(
            if (bluetooth) {
                "Bluetooth can resume without Location if permissions are retained. Otherwise, open the app from the notification."
            } else if (p.background) {
                "Background location granted: automatic recovery after reboot is available."
            } else {
                "Optional: to resume after reboot without opening the app, choose Location → Allow all the time in settings. Without this access, a notification will offer to resume."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        if (!bluetooth) SentinelOutlinedButton(onClick = boot) { Text("Configure boot recovery") }
        HorizontalDivider()
        Text("Background battery use", style = MaterialTheme.typography.titleSmall)
        Text(
            if (p.batteryOptimized) {
                "Battery optimization: enabled for this app"
            } else {
                "Battery optimization: disabled for this app"
            },
        )
        Text(
            "Allow unrestricted battery use to help monitoring continue in the background. Confirm the Android request; this may use more battery. The status refreshes when you return.",
            style = MaterialTheme.typography.bodySmall,
        )
        SentinelButton(onClick = battery, modifier = Modifier.fillMaxWidth()) {
            Text(if (p.batteryOptimized) "Allow unrestricted battery use" else "Open battery settings")
        }
        Text(
            "Some phones also require App info → Battery → Unrestricted. On Samsung, add Blink Sentinel to Never sleeping apps if needed. Android scan limits and other manufacturer restrictions still apply.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BluetoothSection(
    vm: SentinelViewModel,
    config: Settings,
    busy: Boolean,
    access: () -> Unit,
) {
    val devices by vm.bluetoothDevices.collectAsStateWithLifecycle()
    val searching by vm.bluetoothSearching.collectAsStateWithLifecycle()
    Section("Home Bluetooth device") {
        Text("Android 12 or later is required to work with Location off. On Android 10/11, use Wi-Fi mode.")
        Text(
            "Choose a stationary, continuously powered BLE device that advertises every few seconds with a stable address. Pairing alone does not prove presence. Classic-only Bluetooth devices, rotating addresses, and some beacons filtered by Android are unsuitable.",
        )
        Text(
            "Monitoring uses 10-second BLE windows, followed by the arming delay and another absence confirmation. A returning signal cancels departure immediately. Bluetooth off or scanner failure means an unknown state.",
        )
        Text(config.bluetoothDeviceName.ifBlank { "No device selected" } + "\n" + config.bluetoothDeviceAddress)
        SentinelOutlinedButton(onClick = access) { Text("Allow Bluetooth and notifications") }
        SentinelButton(onClick = vm::scanBluetooth, enabled = !busy && !searching && Build.VERSION.SDK_INT >= 31) {
            Text(if (searching) "Bluetooth discovery in progress…" else "Search for 22 seconds")
        }
        Text(
            "Discovery stops monitoring to avoid interference. Selecting a device enables monitoring with a silent notification when Blink and Android permissions are ready.",
        )
        Text(
            "Detected paired and discoverable classic Bluetooth devices also show their Android name. Put unpaired classic devices in discoverable mode. All detected devices can be selected. Results disappear after 30 seconds without detection; search again to refresh the list.",
        )
        Text(
            "Approximate distances use the received signal without calibration. Pairing alone provides no measured signal or distance.",
            style = MaterialTheme.typography.bodySmall,
        )
        devices.forEach { device ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(config.bluetoothDeviceAddress == device.address, {
                    vm.selectBluetoothDevice(device)
                }, enabled = !busy)
                Column {
                    Text(device.name, style = MaterialTheme.typography.titleSmall)
                    Text(device.address, style = MaterialTheme.typography.bodySmall)
                    Text(distanceLabel(device.estimatedDistanceMeters), style = MaterialTheme.typography.bodySmall)
                    Text(
                        (
                            if (device.bleObserved) {
                                "BLE detected · ${device.rssi} dBm"
                            } else if (device.classic) {
                                "Classic Bluetooth · selectable, BLE monitoring unconfirmed"
                            } else {
                                "Bluetooth · no BLE advertisements detected"
                            }
                        ) +
                            if (device.bonded) " · paired" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Text(
            "Test detection with the phone locked and Location off before automating Blink. Android and the manufacturer may suspend monitoring.",
        )
    }
}

private fun distanceLabel(meters: Double?): String {
    if (meters == null) return "Estimated distance: unavailable (signal not measured)"
    val distance =
        when {
            meters < 0.1 -> "< 0.1 m"
            meters < 10 -> "≈ %.1f m".format(java.util.Locale.ENGLISH, meters)
            else -> "≈ %.0f m".format(java.util.Locale.ENGLISH, meters)
        }
    return "Estimated distance: $distance"
}

@Composable
private fun WifiList(
    vm: SentinelViewModel,
    config: Settings,
    busy: Boolean,
    access: () -> Unit,
    location: () -> Unit,
    settings: () -> Unit,
) {
    val networks by vm.networks.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val searchMessage by vm.wifiSearchMessage.collectAsStateWithLifecycle()
    // Returning from Android settings must refresh the list after permissions or Location change.
    LifecycleResumeEffect(permissions.precise, permissions.locationEnabled, permissions.wifiEnabled) {
        vm.refreshWifiNetworks()
        onPauseOrDispose { }
    }
    var manual by remember(config.homeSsid) { mutableStateOf(config.homeSsid) }
    Section("Detected networks") {
        Text(
            "Approximate distances use the strongest access point's signal. Walls and transmit power can greatly affect the estimate.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(searchMessage)
        if (!permissions.precise) {
            Text("Precise location permission is missing (required for Wi-Fi, including after using Bluetooth).")
            SentinelOutlinedButton(onClick = access) { Text("Allow precise location") }
            TextButton(onClick = settings) { Text("View Android permissions") }
        }
        if (!permissions.locationEnabled) {
            Text("Android Location is off: Wi-Fi discovery is unavailable.")
            SentinelOutlinedButton(onClick = location) { Text("Enable Location") }
        }
        if (!permissions.wifiEnabled) {
            Text("Wi-Fi is off: enable it in Android Quick Settings.")
        }
        if (networks.isEmpty()) Text("No recent results are available yet.")
        Text(
            "Selecting a network enables monitoring with a silent notification when Blink and Android permissions are ready.",
            style = MaterialTheme.typography.bodySmall,
        )
        networks.forEach { network ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = config.homeSsid == network.ssid,
                    onClick = { vm.setSsid(network.ssid) },
                    enabled = !busy,
                )
                Column(Modifier.weight(1f)) {
                    Text(network.ssid, style = MaterialTheme.typography.titleSmall)
                    Text(distanceLabel(network.estimatedDistanceMeters), style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${network.signalDbm} dBm · ${network.accessPoints} access point(s)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { vm.setSsid(network.ssid) }, enabled = !busy) {
                    Text("Select")
                }
            }
        }
        HorizontalDivider()
        OutlinedTextField(
            value = manual,
            onValueChange = { manual = it },
            label = { Text("Exact SSID (optional manual entry)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { vm.setSsid(manual) }, enabled = manual.isNotBlank() && !busy) {
            Text("Use this SSID")
        }
        Text(
            "A hidden SSID cannot be reliably detected by name.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DelaySection(
    vm: SentinelViewModel,
    config: Settings,
    busy: Boolean,
) {
    var input by remember(config.delaySeconds) { mutableStateOf(config.delaySeconds.toString()) }
    val valid = input.toIntOrNull()?.let { it in 10..600 } == true
    Section(
        "Arming delay",
        "Between 10 and 600 seconds. A fresh scan must follow the minimum delay; Android may delay confirmation.",
    ) {
        OutlinedTextField(
            input,
            { input = it.filter(Char::isDigit).take(3) },
            label = { Text("Seconds") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = !valid,
            modifier = Modifier.fillMaxWidth(),
        )
        SentinelButton(
            onClick = { vm.update { it.copy(delaySeconds = input.toInt()) } },
            enabled = valid && !busy,
        ) {
            Text("Save delay")
        }
    }
}

@Composable
private fun BlinkSection(
    vm: SentinelViewModel,
    config: Settings,
    connected: Boolean,
    busy: Boolean,
) {
    val twoFactor by vm.twoFactor.collectAsStateWithLifecycle()
    val systems by vm.systems.collectAsStateWithLifecycle()
    val selectedStatus by vm.blinkStatus.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    // Sensitive text intentionally uses remember, never rememberSaveable / saved state.
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    Section(if (connected) "Signed in to Blink" else "Sign in to Blink") {
        if (connected) {
            Text(
                "Choose the system to automate. Other systems in your account are unaffected.",
            )
            if (systems.isEmpty()) Text("No systems available: refresh the list.")
            systems.forEach { system ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        config.systemId == system.id,
                        { vm.selectSystem(system) },
                        enabled = !busy,
                    )
                    Column {
                        Text(system.name)
                        Text(
                            (if (system.id == config.systemId) selectedStatus else system.status)
                                .label(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            SentinelOutlinedButton(onClick = vm::refreshSystems, enabled = !busy) {
                Text("Refresh systems")
            }
            TextButton(onClick = vm::logout, enabled = !busy) { Text("Sign out of Blink") }
        } else if (twoFactor) {
            Text(
                "Enter the code received from Blink. If verification has expired, start signing in again.",
            )
            OutlinedTextField(
                code,
                { code = it.filter(Char::isDigit).take(10) },
                label = { Text("Verification code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            SentinelButton(
                onClick = {
                    val temporary = code
                    code = ""
                    vm.verify(temporary)
                },
                enabled = !busy && code.length >= 4,
            ) {
                Text("Verify code")
            }
            TextButton(onClick = vm::cancelLogin, enabled = !busy) {
                Text("Restart sign-in")
            }
        } else {
            OutlinedTextField(
                email,
                { email = it },
                label = { Text("Blink email address") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                password,
                { password = it },
                label = { Text("Blink password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            SentinelButton(
                onClick = {
                    val temporary = password
                    password = ""
                    vm.login(email, temporary)
                },
                enabled = email.isNotBlank() && password.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign in to Blink")
            }
            Text(
                "Your session is stored encrypted. Your password and verification code are not saved.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
