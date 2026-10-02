package dev.homesentinel.service

import android.bluetooth.BluetoothAdapter
import dev.homesentinel.domain.repository.WifiScanner
import dev.homesentinel.domain.usecase.DepartureAlertPolicy
import android.app.Service
import android.content.*
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.net.*
import android.net.wifi.WifiManager
import android.os.*
import androidx.core.content.ContextCompat
import dev.homesentinel.SentinelApplication
import dev.homesentinel.domain.model.*
import dev.homesentinel.receiver.WifiScanReceiver
import kotlinx.coroutines.*

class WifiMonitorService : Service() {
    private val graph
        get() = (application as SentinelApplication).graph

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var automation: BlinkAutomationService
    private var current = Settings()
    private val departureAlerts = DepartureAlertPolicy()
    private var supplementalJob: Job? = null
    private var registered = false
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val scanReceiver = WifiScanReceiver { success ->
        if (::automation.isInitialized && current.mode == MonitoringMode.WIFI) {
            if (!graph.scanner.available())
                automation.invalid("Wi-Fi, Location, or permission unavailable")
            else if (success)
                graph.scanner.readSuccessfulScan(current.homeSsid)?.let(automation::evidence)
                    ?: automation.scanFailed()
            else automation.scanFailed()
        }
    }
    private val systemReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!::automation.isInitialized) return
                if (current.mode == MonitoringMode.BLUETOOTH) {
                    if (!graph.bluetoothScanner.available()) {
                        graph.bluetoothScanner.stopMonitoring()
                        bluetoothStarted = false
                        automation.invalid("Bluetooth or permission unavailable")
                    } else if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) startBluetooth()
                } else if (!graph.scanner.available())
                    automation.invalid("Enable Wi-Fi and grant precise location access")
                else graph.scanner.requestScan()
            }
        }
    private val wifiCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { if (current.mode == MonitoringMode.WIFI) graph.scanner.requestScan() }
            }

            override fun onLost(network: Network) {
                scope.launch { if (current.mode == MonitoringMode.WIFI) graph.scanner.requestScan() }
            }
        }
    private val internetCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                    scope.launch { if (::automation.isInitialized) automation.retryNow() }
            }
        }

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            try {
                current = graph.settings.snapshot()
                foreground()
                initialize()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                graph.logs.addError(
                    "Monitoring service denied: ${e.message}", e, "Monitoring startup",
                )
                graph.monitor.value = MonitorStatus(
                    note = "Service denied: open the app and check its permissions",
                )
                stopSelf()
            }
        }
    }

    private fun foreground() {
        startForeground(
            MonitorNotifications.ID,
            MonitorNotifications.notification(this, "Waiting for detection"),
            if (current.mode == MonitoringMode.BLUETOOTH)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
    }

    private fun initialize() {
        automation =
            BlinkAutomationService(
                scope,
                object : WifiScanner by graph.scanner {
                    override fun requestScan(): Boolean =
                        if (current.mode == MonitoringMode.WIFI) graph.scanner.requestScan()
                        else graph.bluetoothScanner.available()
                },
                graph.ensure,
                SystemClock::elapsedRealtime,
                { status ->
                    graph.monitor.value = status
                    if (departureAlerts.onStatus(status)) {
                        try {
                            MonitorNotifications.vibrateDeparture(this)
                        } catch (e: RuntimeException) {
                            graph.logs.addError("Phone vibration unavailable", e, "Departure alert")
                        }
                    }
                },
                { message, type -> graph.logs.add(message, type = type) },
                settingsStillCurrent = { expected ->
                    // Journal retention cannot invalidate an otherwise current camera command.
                    graph.settings.snapshot().copy(logRetention = expected.logRetention) == expected &&
                        if (expected.mode == MonitoringMode.WIFI) graph.scanner.available()
                        else graph.bluetoothScanner.available()
                },
                logError = { message, error -> graph.logs.addError(message, error, "Automatic Blink command") },
            )
        ContextCompat.registerReceiver(
            this,
            scanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            systemReceiver,
            IntentFilter().apply {
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(LocationManager.MODE_CHANGED_ACTION)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            wifiCallback,
        )
        connectivity.registerDefaultNetworkCallback(internetCallback)
        registered = true
        scope.launch {
            graph.ready.await()
            graph.settings.settings.collect { next ->
                val sensorChanged = next.mode != current.mode ||
                    next.bluetoothDeviceAddress != current.bluetoothDeviceAddress ||
                    next.enabled != current.enabled
                val supplementalChanged = next.supplementalScan != current.supplementalScan || sensorChanged
                current = next
                automation.configure(next)
                if (!next.enabled) {
                    stopSelf()
                    return@collect
                }
                if (sensorChanged) {
                    bluetoothStarted = false
                    foreground()
                }
                if (next.mode == MonitoringMode.BLUETOOTH &&
                    (sensorChanged || !bluetoothStarted)) startBluetooth()
                if (next.mode == MonitoringMode.WIFI) {
                    graph.bluetoothScanner.stopMonitoring()
                    bluetoothStarted = false
                }
                if (supplementalChanged || (next.supplementalScan && supplementalJob == null)) {
                    supplementalJob?.cancel()
                    if (next.supplementalScan && next.mode == MonitoringMode.WIFI)
                        supplementalJob = launch {
                            while (isActive) {
                                delay(120_000)
                                graph.scanner.requestScan()
                            }
                        }
                }
            }
        }
        scope.launch {
            graph.monitor.collect { status ->
                getSystemService(android.app.NotificationManager::class.java)
                    .notify(
                        MonitorNotifications.ID,
                        MonitorNotifications.notification(this@WifiMonitorService, status.note),
                    )
            }
        }
        graph.logs.add("${current.mode} monitor started — fresh evidence required", type = LogType.MONITORING_STARTED)
    }

    private var bluetoothStarted = false

    private fun startBluetooth() {
        if (bluetoothStarted && graph.bluetoothScanner.available()) return
        bluetoothStarted = true
        graph.bluetoothScanner.monitor(
            scope, current.bluetoothDeviceAddress, automation::evidence,
            { bluetoothStarted = false; automation.invalid(it) },
            { graph.logs.addError("Bluetooth error: ${it.message}", it, "Bluetooth monitoring") },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            scope.launch {
                graph.settings.update { it.copy(enabled = false) }
                stopSelf()
            }
            return START_NOT_STICKY
        }
        if (!::automation.isInitialized) return START_STICKY
        if (intent?.action == RESCAN && current.mode == MonitoringMode.WIFI) graph.scanner.requestScan()
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) {
            unregisterReceiver(scanReceiver)
            unregisterReceiver(systemReceiver)
            connectivity.unregisterNetworkCallback(wifiCallback)
            connectivity.unregisterNetworkCallback(internetCallback)
        }
        if (::automation.isInitialized) automation.close()
        graph.bluetoothScanner.stopMonitoring()
        scope.cancel()
        graph.monitor.value =
            MonitorStatus(
                note = "Monitoring stopped: open the app to resume",
                lastSignalAtEpochMillis = graph.monitor.value.lastSignalAtEpochMillis,
                lastSignalSource = graph.monitor.value.lastSignalSource,
            )
        graph.logs.add("Presence monitor stopped", type = LogType.MONITORING_STOPPED)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val STOP = "dev.homesentinel.STOP"
        const val RESCAN = "dev.homesentinel.RESCAN"
    }
}
