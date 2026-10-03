package dev.homesentinel.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dev.homesentinel.SentinelApplication
import dev.homesentinel.domain.model.LogType
import dev.homesentinel.domain.model.MonitoringMode
import dev.homesentinel.service.MonitorNotifications
import dev.homesentinel.service.WifiMonitorService
import kotlinx.coroutines.launch

/**
 * Boot is a background start: precise + background location are required for a location FGS.
 * Without them, offer a notification that launches the activity; never bypass Android restrictions.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        val graph = (context.applicationContext as SentinelApplication).graph
        graph.scope.launch {
            try {
                val settings = graph.settings.snapshot()
                if (!settings.enabled) return@launch
                val background =
                    context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                if (if (settings.mode == MonitoringMode.BLUETOOTH) {
                        graph.bluetoothScanner.available()
                    } else {
                        background && graph.scanner.available()
                    }
                ) {
                    try {
                        context.startForegroundService(
                            Intent(context, WifiMonitorService::class.java),
                        )
                    } catch (e: RuntimeException) {
                        graph.logs.addError("Boot recovery denied", e, "Boot recovery")
                        MonitorNotifications.resumeReminder(context)
                    }
                } else {
                    MonitorNotifications.resumeReminder(context)
                }
                graph.logs.add("Device restarted — fresh presence evidence required", type = LogType.SIGNAL_UNKNOWN)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                graph.logs.addError("Boot recovery unavailable: open the app", e, "Boot recovery")
                MonitorNotifications.resumeReminder(context)
            } finally {
                pending.finish()
            }
        }
    }
}
