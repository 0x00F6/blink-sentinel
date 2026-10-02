package dev.homesentinel.data.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.SystemClock
import dev.homesentinel.domain.model.*
import dev.homesentinel.domain.repository.WifiScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidWifiScanner(private val context: Context) : WifiScanner {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val location = context.getSystemService(LocationManager::class.java)
    private val mutableNetworks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    override val networks = mutableNetworks.asStateFlow()

    override fun available() =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED && location.isLocationEnabled && wifi.isWifiEnabled

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    override fun requestScan(): Boolean {
        if (!available()) return false
        return try {
            wifi.startScan()
        } catch (_: SecurityException) {
            false
        }
    }

    /**
     * Called exclusively after EXTRA_RESULTS_UPDATED=true. Failed broadcasts never reuse caches.
     */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    override fun readSuccessfulScan(homeSsid: String): WifiEvidence? {
        if (!available()) {
            mutableNetworks.value = emptyList()
            return null
        }
        return try {
            val now = SystemClock.elapsedRealtime()
            // ScanResult timestamps are microseconds since boot; policies use monotonic milliseconds.
            val points =
                wifi.scanResults.map { ScanAccessPoint(it.SSID, it.timestamp / 1_000L, it.level) }
            mutableNetworks.value = ScanPolicy.freshNetworks(points, now)
            ScanPolicy.evidence(points, homeSsid, now)
        } catch (_: SecurityException) {
            null
        }
    }

    /** Cache reads populate the picker only; only a successful broadcast may produce evidence. */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    override fun refreshNetworks() {
        if (!available()) {
            mutableNetworks.value = emptyList()
            return
        }
        mutableNetworks.value = try {
            val points = wifi.scanResults.map {
                ScanAccessPoint(it.SSID, it.timestamp / 1_000L, it.level)
            }
            ScanPolicy.freshNetworks(points, SystemClock.elapsedRealtime())
        } catch (_: SecurityException) {
            emptyList()
        }
    }
}
