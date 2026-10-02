package dev.homesentinel.ui

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.homesentinel.AppGraph
import dev.homesentinel.domain.model.*
import dev.homesentinel.domain.usecase.ActivateMonitoring
import dev.homesentinel.service.WifiMonitorService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PermissionState(
    val precise: Boolean = false,
    val locationEnabled: Boolean = false,
    val wifiEnabled: Boolean = false,
    val notifications: Boolean = false,
    val background: Boolean = false,
    val batteryOptimized: Boolean = true,
    val bluetoothAvailable: Boolean = false,
)

class SentinelViewModel(private val app: Application, val graph: AppGraph) : ViewModel() {
    private val monitorIntent = Intent(app, WifiMonitorService::class.java)
    val settings =
        graph.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val networks = graph.scanner.networks
    val wifiSearchMessage = MutableStateFlow("Open the list to search for Wi-Fi networks.")
    private var lastWifiRequestAt: Long? = null
    val bluetoothDevices = graph.bluetoothScanner.devices
    val bluetoothSearching = graph.bluetoothScanner.searching
    val monitor = graph.monitor.asStateFlow()
    val connected = graph.blink.connected
    val blinkStatus = graph.blink.status
    val logs = graph.logs.entries
    val permissions = MutableStateFlow(PermissionState())
    private val mutableSystems = MutableStateFlow<List<BlinkSystem>>(emptyList())
    val systems = mutableSystems.asStateFlow()
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val twoFactor = MutableStateFlow(false)

    init {
        runAction {
            graph.ready.await()
            if (connected.value) loadSystems()
        }
    }

    private fun runAction(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BlinkException) {
                graph.logs.addError(e.message ?: "Blink error", e, operation = "Blink user action")
                message.value = dev.homesentinel.data.preferences.EventLog.sanitizeSecrets(e.message.orEmpty())
            } catch (e: IllegalArgumentException) {
                graph.logs.addError(e.message ?: "Invalid setting", e, "Settings validation")
                message.value = e.message ?: "Invalid setting"
            } catch (e: Exception) {
                graph.logs.addError("Operation failed: ${e.message}", e, operation = "User action")
                message.value =
                    "Operation failed. Check your connection and sign in to Blink again if needed."
            } finally {
                busy.value = false
            }
        }
    }

    fun update(transform: (Settings) -> Settings) {
        runAction { graph.settings.update(transform) }
    }

    fun setLogRetention(value: Int) {
        runAction {
            graph.settings.update { it.copy(logRetention = value) }
            graph.logs.setRetention(value)
        }
    }

    fun setSsid(ssid: String) {
        selectSensor { it.copy(mode = MonitoringMode.WIFI, homeSsid = ssid) }
    }

    fun login(email: String, password: String) {
        runAction {
            graph.ready.await()
            graph.settings.update { it.copy(enabled = false) }
            app.stopService(monitorIntent)
            twoFactor.value = graph.blink.login(email, password) == LoginResult.TWO_FACTOR_REQUIRED
            if (!twoFactor.value) {
                loadSystems()
                graph.logs.add("Blink account connected", type = LogType.ACCOUNT)
            } else
                message.value =
                    "Enter the code sent by Blink. Your password is not stored."
        }
    }

    fun verify(code: String) {
        runAction {
            graph.blink.verifyCode(code)
            twoFactor.value = false
            loadSystems()
            graph.logs.add("Blink account connected — verification complete", type = LogType.ACCOUNT)
        }
    }

    fun cancelLogin() {
        runAction {
            graph.blink.logout()
            twoFactor.value = false
        }
    }

    fun logout() {
        runAction {
            graph.settings.update { it.copy(enabled = false, systemId = "", systemName = "") }
            app.stopService(monitorIntent)
            graph.blink.logout()
            mutableSystems.value = emptyList()
            twoFactor.value = false
            graph.logs.add("Blink account disconnected — automation stopped", type = LogType.ACCOUNT)
        }
    }

    private suspend fun loadSystems() {
        val available = graph.blink.listSystems()
        mutableSystems.value = available
        val config = graph.settings.snapshot()
        if (available.size == 1 && available.none { it.id == config.systemId }) {
            val one = available.single()
            graph.settings.update { it.copy(systemId = one.id, systemName = one.name) }
        }
        if (graph.settings.snapshot().systemId.isNotBlank()) graph.blink.getStatus()
    }

    fun refreshSystems() {
        runAction { loadSystems() }
    }

    fun selectSystem(system: BlinkSystem) {
        runAction {
            graph.settings.update { it.copy(systemId = system.id, systemName = system.name) }
            graph.blink.getStatus()
        }
    }

    fun refreshStatus() {
        runAction { graph.blink.getStatus() }
    }

    fun manual(desired: BlinkStatus) {
        runAction {
            val changed = graph.ensure(desired)
            val word = if (desired == BlinkStatus.ARMED) "armed" else "disarmed"
            graph.logs.add(
                if (changed) "Blink system $word — manual action"
                else "Blink already $word — no command sent",
                type = if (desired == BlinkStatus.ARMED) LogType.BLINK_ARMED else LogType.BLINK_DISARMED,
            )
            message.value = if (changed) "Blink state confirmed" else "Blink is already in this state"
        }
    }

    private fun sensorAvailable(config: Settings) =
        if (config.mode == MonitoringMode.WIFI) graph.scanner.available()
        else graph.bluetoothScanner.available()

    fun chooseMode(mode: MonitoringMode) {
        runAction {
            graph.settings.update { it.copy(enabled = false, mode = mode) }
            app.stopService(monitorIntent)
            graph.bluetoothScanner.stopDiscovery()
        }
    }

    fun scanBluetooth() {
        runAction {
            require(graph.bluetoothScanner.available()) {
                "Bluetooth requires Android 12+, Nearby devices permission, and Bluetooth enabled"
            }
            graph.settings.update { it.copy(enabled = false) }
            app.stopService(monitorIntent)
            graph.bluetoothScanner.stopMonitoring()
            graph.bluetoothScanner.discover(viewModelScope) { error ->
                graph.logs.addError("Bluetooth discovery: ${error.message}", error, "Bluetooth discovery")
                message.value = "Bluetooth discovery interrupted or denied. Check the event log."
            }
            message.value = "Monitoring stopped: discovering classic Bluetooth, then BLE, for 22 seconds"
        }
    }

    override fun onCleared() {
        graph.bluetoothScanner.stopDiscovery()
        super.onCleared()
    }

    fun refreshWifiNetworks() {
        graph.scanner.refreshNetworks()
        val now = SystemClock.elapsedRealtime()
        if (!graph.scanner.available()) {
            wifiSearchMessage.value = "Discovery requires Wi-Fi, Android Location, and precise location permission."
        } else if (lastWifiRequestAt?.let { now - it < 30_000 } != true) {
            scan()
        }
    }

    fun onWifiScanResult(success: Boolean) {
        if (success) {
            graph.scanner.readSuccessfulScan(settings.value.homeSsid)
            wifiSearchMessage.value = if (networks.value.isEmpty())
                "Scan received: no recent named networks. Move closer to the access point and try again."
            else "Recent networks detected: ${networks.value.size}."
        } else {
            // Refresh display only: a failed broadcast never produces presence evidence.
            graph.scanner.refreshNetworks()
            wifiSearchMessage.value = "Android did not provide a new scan. Recent results remain visible; wait and try again."
        }
    }

    fun scan() {
        graph.scanner.refreshNetworks()
        if (!graph.scanner.available()) {
            wifiSearchMessage.value = "Grant precise location permission and enable Wi-Fi and Android Location."
            message.value = wifiSearchMessage.value
            return
        }
        lastWifiRequestAt = SystemClock.elapsedRealtime()
        wifiSearchMessage.value =
            if (graph.scanner.requestScan()) "Scan requested: waiting for Android results. Available recent results are shown."
            else "Android refused the scan. Recent results remain visible; wait and try again."
    }

    private val activateMonitoring = ActivateMonitoring(
        snapshot = graph.settings::snapshot,
        update = graph.settings::update,
        connected = { connected.value },
        sensorAvailable = ::sensorAvailable,
        startService = {
            app.startForegroundService(Intent(app, WifiMonitorService::class.java))
            Unit
        },
        stopService = {
            app.stopService(monitorIntent)
            Unit
        },
    )

    private fun selectSensor(selection: (Settings) -> Settings) {
        runAction {
            graph.ready.await()
            graph.bluetoothScanner.stopDiscovery()
            startMonitoring(selection)
        }
    }

    fun selectBluetoothDevice(device: BluetoothDeviceItem) {
        selectSensor {
            it.copy(mode = MonitoringMode.BLUETOOTH, bluetoothDeviceAddress = device.address, bluetoothDeviceName = device.name)
        }
    }

    private suspend fun startMonitoring(selection: ((Settings) -> Settings)? = null) {
        try {
            activateMonitoring(selection)
            message.value = "Monitoring enabled with a silent notification. Waiting for a fresh signal."
        } catch (e: RuntimeException) {
            if (e is IllegalArgumentException) throw e
            graph.logs.addError("Monitoring start denied", e, "Monitoring startup")
            message.value = "Android refused to start the service. Keep the app open and check its permissions."
        }
    }

    fun enable() {
        runAction {
            graph.ready.await()
            graph.bluetoothScanner.stopDiscovery()
            startMonitoring()
        }
    }

    fun disable() {
        runAction {
            graph.settings.update { it.copy(enabled = false) }
            app.stopService(monitorIntent)
            graph.logs.add("Automation disabled by user", type = LogType.MONITORING_STOPPED)
        }
    }

    fun resumeIfWanted() {
        viewModelScope.launch {
            graph.ready.await()
            if (
                graph.settings.snapshot().enabled &&
                    !monitor.value.running &&
                    sensorAvailable(graph.settings.snapshot())
            ) {
                try {
                    app.startForegroundService(Intent(app, WifiMonitorService::class.java))
                } catch (e: RuntimeException) {
                    graph.logs.addError("Monitoring resume denied", e, "Monitoring resume")
                    message.value = "Tap Resume monitoring to restart monitoring"
                }
            }
        }
    }
}
