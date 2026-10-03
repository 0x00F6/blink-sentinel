package dev.homesentinel.domain.model

enum class MonitoringMode {
    WIFI,
    BLUETOOTH,
}

enum class AppThemeMode { DARK, LIGHT }

enum class Presence {
    HOME,
    AWAY_PENDING,
    AWAY,
    UNKNOWN,
}

enum class BlinkStatus {
    ARMED,
    DISARMED,
    UNKNOWN,
}

data class BlinkSystem(
    val id: String,
    val name: String,
    val status: BlinkStatus,
)

data class WifiNetwork(
    val ssid: String,
    val signalDbm: Int,
    val accessPoints: Int,
    val estimatedDistanceMeters: Double? = null,
)

data class BluetoothDeviceItem(
    val name: String,
    val address: String,
    val rssi: Int?,
    val bonded: Boolean = false,
    val bleObserved: Boolean = false,
    val classic: Boolean = false,
    val estimatedDistanceMeters: Double? = null,
)

data class Settings(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val enabled: Boolean = false,
    val mode: MonitoringMode = MonitoringMode.WIFI,
    val homeSsid: String = "",
    val bluetoothDeviceAddress: String = "",
    val bluetoothDeviceName: String = "",
    val delaySeconds: Int = 30,
    val autoDisarm: Boolean = true,
    val systemId: String = "",
    val systemName: String = "",
    val supplementalScan: Boolean = false,
    val logRetention: Int = LogRetention.DEFAULT,
) {
    val signalSourceKey: String
        get() = "${mode.name}:${if (mode == MonitoringMode.WIFI) homeSsid else bluetoothDeviceAddress}"
}

/** Milliseconds are monotonic since boot, never wall-clock time. */
data class WifiEvidence(
    val batchId: Long,
    val observedAt: Long,
    val present: Boolean,
    val signalAt: Long? = if (present) observedAt else null,
)

data class MonitorStatus(
    val running: Boolean = false,
    val presence: Presence = Presence.UNKNOWN,
    val deadline: Long? = null,
    val lastScanAt: Long? = null,
    val note: String = "Monitoring stopped",
    val lastSignalAtEpochMillis: Long? = null,
    val lastSignalSource: String? = null,
)

data class ErrorDetails(
    val operation: String = "",
    val httpMethod: String = "",
    val url: String = "",
    val httpStatus: String = "",
    val exceptionType: String = "",
    val exceptionMessage: String = "",
    val causes: String = "",
    val stackTrace: String = "",
)

data class LogEntry(
    val at: Long,
    val message: String,
    val isError: Boolean = false,
    val errorDetails: ErrorDetails? = null,
    val type: LogType = LogType.INFO,
)

object LogRetention {
    const val DEFAULT = 500
    const val MIN = 50
    const val MAX = 5_000

    fun validate(value: Int) {
        require(value in MIN..MAX) { "Log retention must be between $MIN and $MAX entries" }
    }
}

/** Assigned at the event source; historical text is never used to infer sensor evidence. */
enum class LogType {
    INFO,
    SIGNAL_DETECTED,
    SIGNAL_LOST,
    ABSENCE_CONFIRMED,
    SIGNAL_UNKNOWN,
    WAITING_CONFIRMATION,
    MONITORING_STARTED,
    MONITORING_STOPPED,
    BLINK_ARMED,
    BLINK_DISARMED,
    ACCOUNT,
    ERROR,
}

enum class LoginResult {
    CONNECTED,
    TWO_FACTOR_REQUIRED,
}

data class BlinkErrorContext(
    val operation: String = "",
    val httpMethod: String = "",
    val url: String = "",
    val httpStatus: Int? = null,
)

class BlinkException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
    val context: BlinkErrorContext? = null,
) : Exception(message, cause) {
    enum class Kind {
        AUTH,
        NETWORK,
        RATE_LIMIT,
        PROTOCOL,
        REMOTE,
    }
}
