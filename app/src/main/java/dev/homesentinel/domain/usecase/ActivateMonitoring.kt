package dev.homesentinel.domain.usecase

import dev.homesentinel.domain.model.MonitoringMode
import dev.homesentinel.domain.model.Settings

/** Shared activation path for explicit activation and sensor selection. */
class ActivateMonitoring(
    private val snapshot: suspend () -> Settings,
    private val update: suspend ((Settings) -> Settings) -> Unit,
    private val connected: () -> Boolean,
    private val sensorAvailable: (Settings) -> Boolean,
    private val startService: () -> Unit,
    private val stopService: () -> Unit,
) {
    suspend operator fun invoke(selection: ((Settings) -> Settings)? = null) {
        // Persist the target first so startup validation and the service read the same selection.
        if (selection != null) update(selection)
        val config = snapshot()
        val problem = when {
            (if (config.mode == MonitoringMode.WIFI) config.homeSsid else config.bluetoothDeviceAddress).isBlank() ->
                "Select a home sensor in the Network tab"
            !connected() || config.systemId.isBlank() ->
                "Sensor saved. Sign in to Blink and select a system to enable monitoring."
            !sensorAvailable(config) ->
                if (config.mode == MonitoringMode.WIFI)
                    "Sensor saved. Grant precise location permission and enable Wi-Fi and Android Location to start monitoring."
                else "Sensor saved. Android 12+, Nearby devices permission, and Bluetooth enabled are required to start monitoring."
            else -> null
        }
        if (problem != null) {
            update { it.copy(enabled = false) }
            stopService()
            throw IllegalArgumentException(problem)
        }
        update { it.copy(enabled = true) }
        try {
            startService()
        } catch (e: RuntimeException) {
            // A persisted enabled flag must not imply that Android accepted the service start.
            update { it.copy(enabled = false) }
            stopService()
            throw e
        }
    }
}
