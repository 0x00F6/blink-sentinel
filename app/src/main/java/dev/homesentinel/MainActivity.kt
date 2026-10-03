package dev.homesentinel

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.homesentinel.domain.model.MonitoringMode
import dev.homesentinel.receiver.WifiScanReceiver
import dev.homesentinel.ui.PermissionState
import dev.homesentinel.ui.SentinelViewModel
import dev.homesentinel.ui.screens.SentinelApp

class MainActivity : ComponentActivity() {
    private val graph
        get() = (application as SentinelApplication).graph

    private val model: SentinelViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SentinelViewModel(application, graph) as T
        }
    }
    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updatePermissions()
        }
    private val backgroundRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            updatePermissions()
        }
    private val scanReceiver =
        WifiScanReceiver { success ->
            model.onWifiScanResult(success)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            SentinelApp(
                model,
                ::requestAccess,
                ::requestBootAccess,
                ::openLocation,
                ::openAppSettings,
                ::openBatterySettings,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            scanReceiver,
            android.content.IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onResume() {
        super.onResume()
        updatePermissions()
        model.resumeIfWanted()
    }

    override fun onStop() {
        unregisterReceiver(scanReceiver)
        super.onStop()
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun updatePermissions() {
        model.permissions.value =
            PermissionState(
                granted(Manifest.permission.ACCESS_FINE_LOCATION),
                getSystemService(LocationManager::class.java).isLocationEnabled,
                getSystemService(WifiManager::class.java).isWifiEnabled,
                Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
                granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                !getSystemService(PowerManager::class.java)
                    .isIgnoringBatteryOptimizations(packageName),
                graph.bluetoothScanner.available(),
            )
    }

    private fun requestAccess() {
        val needed = mutableListOf<String>()
        // Android 12+ expects COARSE + FINE together, including an upgrade from approximate access.
        if (model.settings.value.mode == MonitoringMode.BLUETOOTH) {
            if (Build.VERSION.SDK_INT < 31) {
                model.message.value = "Bluetooth without Location requires Android 12 or later"
                return
            }
            if (!granted(Manifest.permission.BLUETOOTH_SCAN)) needed.add(Manifest.permission.BLUETOOTH_SCAN)
            if (!granted(Manifest.permission.BLUETOOTH_CONNECT)) needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            permissionRequest.launch(needed.toTypedArray())
        } else {
            model.message.value = "All required permissions have already been granted"
        }
    }

    private fun requestBootAccess() {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestAccess()
            return
        }
        if (Build.VERSION.SDK_INT == 29) {
            backgroundRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            openAppSettings()
        }
    }

    private fun openLocation() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()),
        )
    }

    private fun openBatterySettings() {
        val exempt =
            getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(packageName)
        val destinations =
            buildList {
                // Android owns the exemption: ask only on a user tap, never silently or on startup.
                if (!exempt) {
                    add(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri()),
                    )
                }
                // Some manufacturers omit the direct request activity. Keep standard settings fallbacks.
                add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
            }
        var failure: RuntimeException? = null
        for (destination in destinations) {
            try {
                startActivity(destination)
                return
            } catch (e: ActivityNotFoundException) {
                failure = e
            } catch (e: SecurityException) {
                failure = e
            }
        }
        graph.logs.addError("Battery settings unavailable", failure, "Open battery settings")
        model.message.value = "Open Android Settings → Apps → Blink Sentinel → Battery and choose Unrestricted if available."
    }
}
