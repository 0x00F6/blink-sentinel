package dev.homesentinel.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.homesentinel.domain.model.MonitoringMode
import dev.homesentinel.domain.model.AppThemeMode
import dev.homesentinel.domain.model.LogRetention
import dev.homesentinel.domain.model.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("automation")

class SettingsRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.settingsStore)
    private val enabled = booleanPreferencesKey("enabled")
    private val mode = stringPreferencesKey("mode")
    private val ssid = stringPreferencesKey("ssid")
    private val btAddress = stringPreferencesKey("bt_address")
    private val btName = stringPreferencesKey("bt_name")
    private val delay = intPreferencesKey("delay")
    private val disarm = booleanPreferencesKey("disarm")
    private val system = stringPreferencesKey("system")
    private val systemName = stringPreferencesKey("system_name")
    private val supplemental = booleanPreferencesKey("supplemental")
    private val logRetention = intPreferencesKey("log_retention")
    private val themeMode = stringPreferencesKey("theme_mode")

    private fun decode(p: Preferences) =
        Settings(
            themeMode = try {
                AppThemeMode.valueOf(p[themeMode] ?: AppThemeMode.DARK.name)
            } catch (_: Exception) {
                AppThemeMode.DARK
            },
            enabled = p[enabled] ?: false,
            mode = try {
                MonitoringMode.valueOf(p[mode] ?: "WIFI")
            } catch (_: Exception) {
                MonitoringMode.WIFI
            },
            homeSsid = p[ssid] ?: "",
            bluetoothDeviceAddress = p[btAddress] ?: "",
            bluetoothDeviceName = p[btName] ?: "",
            delaySeconds = (p[delay] ?: 30).coerceIn(10, 600),
            autoDisarm = p[disarm] ?: true,
            systemId = p[system] ?: "",
            systemName = p[systemName] ?: "",
            supplementalScan = p[supplemental] ?: false,
            logRetention = (p[logRetention] ?: LogRetention.DEFAULT).coerceIn(LogRetention.MIN, LogRetention.MAX),
        )

    val settings = store.data.map(::decode)

    suspend fun snapshot() = decode(store.data.first())

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p ->
            val s = transform(decode(p))
            LogRetention.validate(s.logRetention)
            p[enabled] = s.enabled
            p[themeMode] = s.themeMode.name
            p[mode] = s.mode.name
            p[ssid] = s.homeSsid
            p[btAddress] = s.bluetoothDeviceAddress
            p[btName] = s.bluetoothDeviceName
            p[delay] = s.delaySeconds.coerceIn(10, 600)
            p[disarm] = s.autoDisarm
            p[system] = s.systemId
            p[systemName] = s.systemName
            p[supplemental] = s.supplementalScan
            p[logRetention] = s.logRetention
        }
    }
}
