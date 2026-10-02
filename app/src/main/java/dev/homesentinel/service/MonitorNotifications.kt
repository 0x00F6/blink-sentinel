package dev.homesentinel.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.homesentinel.MainActivity
import dev.homesentinel.R

object MonitorNotifications {
    // Channel sound settings are immutable after registration. A new ID migrates old installs.
    const val CHANNEL = "presence-silent-v2"
    const val ID = 101

    fun createChannel(context: Context) {
        context
            .getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Silent Wi-Fi / Bluetooth monitoring",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
    }

    fun notification(context: Context, text: String, ongoing: Boolean = true): Notification {
        createChannel(context)
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val builder =
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Blink Sentinel")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
        if (ongoing) {
            val stop =
                PendingIntent.getService(
                    context,
                    1,
                    Intent(context, WifiMonitorService::class.java)
                        .setAction(WifiMonitorService.STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            builder.addAction(Notification.Action.Builder(null, "Stop", stop).build())
        }
        return builder.build()
    }

    /** Drive only the handset vibrator; never request audio playback or route an alert to a headset. */
    @Suppress("DEPRECATION")
    fun vibrateDeparture(context: Context) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        if (notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL ||
            audio.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        val vibrator = if (Build.VERSION.SDK_INT >= 31)
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        else context.getSystemService(Vibrator::class.java)
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_NOTIFICATION).build())
        } else {
            vibrator.vibrate(effect, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        }
    }

    fun resumeReminder(context: Context) {
        if (
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 33
        ) {
            context
                .getSystemService(NotificationManager::class.java)
                .notify(
                    102,
                    notification(
                        context,
                        "Open the app to resume monitoring",
                        false,
                    ),
                )
        }
    }
}
