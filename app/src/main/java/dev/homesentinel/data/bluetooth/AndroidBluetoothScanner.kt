package dev.homesentinel.data.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dev.homesentinel.domain.model.BluetoothDeviceItem
import dev.homesentinel.domain.model.WifiEvidence
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Android 12+ BLE detection without location. All callbacks and ownership are on main. */
class AndroidBluetoothScanner(private val context: Context) {
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val mutableDevices = MutableStateFlow<List<BluetoothDeviceItem>>(emptyList())
    val devices = mutableDevices.asStateFlow()
    private val mutableSearching = MutableStateFlow(false)
    val searching = mutableSearching.asStateFlow()
    private var discovery: Job? = null
    private var deviceExpiry: Job? = null
    private var monitoring: Job? = null
    private var generation = 0L

    @SuppressLint("MissingPermission")
    fun available(): Boolean = Build.VERSION.SDK_INT >= 31 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED &&
        adapter?.isEnabled == true && adapter?.bluetoothLeScanner != null

    @SuppressLint("MissingPermission")
    fun discover(scope: CoroutineScope, error: (Throwable) -> Unit) {
        val previous = discovery
        previous?.cancel()
        deviceExpiry?.cancel()
        discovery = scope.launch {
            previous?.join()
            mutableDevices.value = emptyList()
            if (!available()) {
                error(IllegalStateException("Bluetooth requires Android 12+, Nearby devices permission, and Bluetooth enabled"))
                return@launch
            }
            val bluetooth = adapter ?: return@launch
            val scanner = bluetooth.bluetoothLeScanner ?: return@launch
            val catalog = BluetoothDeviceCatalog(SystemClock::elapsedRealtime)
            var bleStartedAt = Long.MAX_VALUE
            var acceptingResults = true
            var receiverRegistered = false
            var classicStarted = false
            var bleStarted = false
            fun updateDevice(device: BluetoothDevice, name: String? = null, rssi: Int? = null, ble: Boolean = false, observed: Boolean = false) {
                if (!acceptingResults) return
                try {
                    catalog.update(
                        device.address, name, device.name, rssi,
                        device.bondState == BluetoothDevice.BOND_BONDED,
                        bleObserved = ble, classic = device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC,
                        radioObserved = observed,
                    )
                    mutableDevices.value = catalog.items()
                } catch (e: SecurityException) {
                    error(e)
                }
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val device = if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device ?: return
                    val rssi = if (intent.hasExtra(BluetoothDevice.EXTRA_RSSI))
                        intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, 0).toInt() else null
                    updateDevice(device, intent.getStringExtra(BluetoothDevice.EXTRA_NAME), rssi,
                        observed = intent.action == BluetoothDevice.ACTION_FOUND)
                }
            }
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val observedAt = result.timestampNanos / 1_000_000
                    if (observedAt < bleStartedAt || observedAt > SystemClock.elapsedRealtime()) return
                    updateDevice(result.device, result.scanRecord?.deviceName, result.rssi, ble = true, observed = true)
                }
                override fun onScanFailed(errorCode: Int) {
                    if (acceptingResults)
                        error(IllegalStateException("BLE discovery failed (Android code $errorCode)"))
                }
            }
            mutableDevices.value = emptyList()
            mutableSearching.value = true
            deviceExpiry = scope.launch {
                while (isActive) {
                    delay(1_000)
                    mutableDevices.value = catalog.items()
                    if (!mutableSearching.value && mutableDevices.value.isEmpty()) break
                }
            }
            try {
                bluetooth.bondedDevices.forEach { updateDevice(it) }
                ContextCompat.registerReceiver(
                    context, receiver,
                    IntentFilter().apply {
                        addAction(BluetoothDevice.ACTION_FOUND)
                        addAction(BluetoothDevice.ACTION_NAME_CHANGED)
                    },
                    ContextCompat.RECEIVER_EXPORTED,
                )
                receiverRegistered = true
                // Sequential scans avoid radio contention between classic inquiry and BLE.
                classicStarted = bluetooth.startDiscovery()
                if (!classicStarted)
                    error(IllegalStateException("Android refused classic Bluetooth discovery"))
                delay(14_000)
                if (classicStarted) bluetooth.cancelDiscovery()
                classicStarted = false
                bleStartedAt = SystemClock.elapsedRealtime()
                scanner.startScan(callback)
                bleStarted = true
                delay(8_000)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error(e)
            } finally {
                acceptingResults = false
                mutableSearching.value = false
                if (receiverRegistered) context.unregisterReceiver(receiver)
                try {
                    if (classicStarted) bluetooth.cancelDiscovery()
                    if (bleStarted) scanner.stopScan(callback)
                } catch (e: SecurityException) {
                    error(e)
                }
            }

        }
    }

    @SuppressLint("MissingPermission")
    fun monitor(
        scope: CoroutineScope,
        address: String,
        evidence: (WifiEvidence) -> Unit,
        invalid: (String) -> Unit,
        error: (Throwable) -> Unit,
    ) {
        stopMonitoring()
        val previousDiscovery = discovery
        previousDiscovery?.cancel()
        val epoch = generation
        monitoring = scope.launch {
            previousDiscovery?.join()
            if (!available()) {
                invalid("Bluetooth unavailable: check permissions and enable Bluetooth")
                return@launch
            }
            val scanner = adapter?.bluetoothLeScanner ?: return@launch
            val window = BluetoothObservationWindow(SystemClock.elapsedRealtime())
            var failed = false
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (epoch != generation || failed || !result.device.address.equals(address, true)) return
                    if (!window.advertisement(result.timestampNanos / 1_000_000, SystemClock.elapsedRealtime())) return
                    // Return cancels a pending departure immediately, before the window ends.
                    val now = SystemClock.elapsedRealtime()
                    if (now - result.timestampNanos / 1_000_000 in 0..2_000) {
                        evidence(WifiEvidence(now, now, true, result.timestampNanos / 1_000_000))
                    }
                }
                override fun onScanFailed(errorCode: Int) {
                    if (epoch != generation) return
                    failed = true
                    invalid("Bluetooth scan failed: state unknown")
                    error(IllegalStateException("BLE monitoring failed (Android code $errorCode)"))
                    monitoring?.cancel()
                }
            }
            try {
                // A non-empty filter keeps scanning eligible with the screen off. One continuous
                // scan avoids start/stop throttling; silence is evaluated only while it is active.
                scanner.startScan(
                    listOf(ScanFilter.Builder().setDeviceAddress(address).build()),
                    ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setReportDelay(0).build(),
                    callback,
                )
                while (isActive) {
                    delay(10_000)
                    if (!available()) {
                        invalid("Bluetooth or permission unavailable: state unknown")
                        break
                    }
                    if (!failed) {
                        val observation = window.complete(SystemClock.elapsedRealtime())
                        if (observation != null) evidence(observation)
                        else invalid("Waiting for a fresh BLE signal: state unknown")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                invalid("Bluetooth monitoring interrupted: state unknown")
                error(e)
            } finally {
                try { scanner.stopScan(callback) } catch (e: SecurityException) { error(e) }
            }
        }
    }

    fun stopMonitoring() {
        generation++
        monitoring?.cancel()
        monitoring = null
    }

    fun stopDiscovery() {
        deviceExpiry?.cancel()
        deviceExpiry = null
        mutableDevices.value = emptyList()
        discovery?.cancel()
        discovery = null
    }
}
