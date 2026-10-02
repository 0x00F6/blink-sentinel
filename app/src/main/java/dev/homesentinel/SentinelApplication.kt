package dev.homesentinel

import android.app.Application
import dev.homesentinel.data.blink.*
import dev.homesentinel.data.bluetooth.AndroidBluetoothScanner
import dev.homesentinel.data.preferences.*
import dev.homesentinel.data.wifi.AndroidWifiScanner
import dev.homesentinel.domain.model.*
import dev.homesentinel.domain.usecase.EnsureBlinkState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

class SentinelApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

class AppGraph(application: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsRepository(application)
    // Retain the full bounded history until DataStore has supplied the saved user limit.
    val logs = EventLog(application, initialRetention = LogRetention.MAX)
    val scanner = AndroidWifiScanner(application)
    val bluetoothScanner = AndroidBluetoothScanner(application)
    val monitor = MutableStateFlow(MonitorStatus())
    val blink = BlinkApiService(KeystoreSessionVault(application), { settings.snapshot().systemId })
    val ensure = EnsureBlinkState(blink) { settings.snapshot().systemId }
    init {
        scope.launch {
            settings.settings.map { it.logRetention }.distinctUntilChanged().collect(logs::setRetention)
        }
    }
    val ready: Deferred<Unit> =
        scope.async {
            logs.setRetention(settings.snapshot().logRetention)
            try {
                blink.restore()
            } catch (e: BlinkException) {
                logs.addError(
                    "Encrypted session unavailable — sign in to Blink again",
                    throwable = e,
                    operation = "Session restoration",
                )
            }
        }
}
