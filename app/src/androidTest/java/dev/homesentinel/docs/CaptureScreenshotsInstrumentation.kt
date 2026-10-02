package dev.homesentinel.docs

import android.app.Activity
import android.app.Instrumentation
import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowManager
import android.view.inspector.WindowInspector
import dev.homesentinel.MainActivity
import dev.homesentinel.SentinelApplication
import dev.homesentinel.data.preferences.EventLog
import dev.homesentinel.domain.model.BlinkException
import dev.homesentinel.domain.model.BlinkErrorContext
import dev.homesentinel.domain.model.LogType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException

/** Captures the real Compose UI without removing the production window's FLAG_SECURE. */
class CaptureScreenshotsInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        var demonstrationLog: EventLog? = null
        try {
            check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") {
                "Documentation capture must run on an emulator, never a personal phone"
            }
            check(Build.VERSION.SDK_INT >= 31) { "Screenshot generation requires an Android 12+ emulator" }
            // Instrumentation starts before Application.onCreate; wait for application initialization.
            waitForIdleSync()
            val application = targetContext.applicationContext as SentinelApplication
            runBlocking {
                application.graph.ready.await()
                check(!application.graph.blink.connected.value) { "Use an emulator without a Blink account" }
                check(!application.graph.settings.snapshot().enabled) { "Monitoring must be disabled" }
                check(application.graph.logs.entries.value.isEmpty()) { "Use an emulator with an empty event log" }
            }
            demonstrationLog = application.graph.logs
            seedDemonstrationLogs(demonstrationLog)
            val destination = File(targetContext.filesDir, "documentation-screenshots").apply { mkdirs() }
            val nightMode = targetContext.getSystemService(UiModeManager::class.java)
            captureLauncher(File(destination, "launcher-icon.png"))
            for ((theme, mode) in listOf("light" to UiModeManager.MODE_NIGHT_NO, "dark" to UiModeManager.MODE_NIGHT_YES)) {
                runOnMainSync { nightMode.setApplicationNightMode(mode) }
                val activity = startActivitySync(
                    Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                awaitText("Blink Sentinel")
                capture(activity, File(destination, "home-$theme.png"), mode)
                for ((tab, name) in listOf("Network" to "network", "Blink" to "blink", "Event log" to "event-log")) {
                    clickTab(tab)
                    capture(activity, File(destination, "$name-$theme.png"), mode)
                }
                clickTab("View error details")
                awaitText("Error details")
                capture(activity, File(destination, "error-details-$theme.png"), mode, includeDialogs = true)
                clickTab("Copy")
                runOnMainSync {
                    val copied = targetContext.getSystemService(android.content.ClipboardManager::class.java)
                        .primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                    check(copied.contains("Full stack trace:") && copied.contains("[DEMO] Read Blink status"))
                }
                clickTab("Close")
                // Exercise the real settings dialog and DataStore save without enabling monitoring.
                runBlocking { application.graph.settings.update { it.copy(logRetention = 1_000) } }
                clickTab("Log settings")
                awaitText("Log retention")
                clickTab("Use default (500)")
                clickTab("Save")
                runBlocking {
                    withTimeout(5_000) { application.graph.settings.settings.first { it.logRetention == 500 } }
                }
                runOnMainSync { activity.finish() }
            }
            clearDemonstrationLog(demonstrationLog)
            result.putString("stream", "Captured 10 real application views with DEMO log fixtures in ${destination.absolutePath}\n")
            finish(Activity.RESULT_OK, result)
        } catch (error: Exception) {
            demonstrationLog?.let(::clearDemonstrationLog)
            result.putString("stream", "Documentation capture failed: ${error.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun clearDemonstrationLog(log: EventLog) {
        log.clear()
        // Instrumentation may be killed immediately by finish(); flush only these test fixtures
        // on the instrumentation worker thread, never the application's main thread.
        check(targetContext.getSharedPreferences("events", android.content.Context.MODE_PRIVATE)
            .edit().putString("entries", "[]").commit()) { "Cannot clear documentation fixtures" }
    }

    private fun captureLauncher(destination: File) {
        val drawable = targetContext.packageManager.getApplicationIcon(targetContext.packageName)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        destination.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    private fun seedDemonstrationLogs(log: EventLog) {
        // Documentation-only fixtures: no sensor evidence, network calls, or monitoring state changes.
        log.add("[DEMO] Monitoring started; waiting for a fresh signal", type = LogType.MONITORING_STARTED)
        log.add("[DEMO] Home Wi-Fi detected", type = LogType.SIGNAL_DETECTED)
        log.add("[DEMO] Home Wi-Fi not detected; awaiting confirmation", type = LogType.SIGNAL_LOST)
        log.addError(
            "[DEMO] Blink status unavailable: connection timed out",
            BlinkException(
                BlinkException.Kind.NETWORK,
                "[DEMO] Connection timed out",
                IOException("[DEMO] No response received", SocketTimeoutException("[DEMO] Request timed out")),
                BlinkErrorContext("[DEMO] Read Blink status", "GET", "https://blink.example.invalid/network/123/status"),
            ),
        )
    }

    private fun awaitText(text: String) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (findNodes(text).isNotEmpty()) {
                waitForIdleSync()
                return
            }
            SystemClock.sleep(100)
        }
        error("The application did not display $text")
    }

    private fun clickTab(label: String) {
        awaitText(label)
        val matches = findNodes(label)
        val match = matches.lastOrNull { it.text?.toString() == label || it.contentDescription?.toString() == label }
            ?: error("Navigation item not found: $label")
        var node: AccessibilityNodeInfo? = match
        while (node != null && !node.isClickable) node = node.parent
        check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "Cannot open $label" }
        waitForIdleSync()
    }

    private fun findNodes(text: String): List<AccessibilityNodeInfo> {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        uiAutomation.rootInActiveWindow?.let(pending::add)
        val matches = mutableListOf<AccessibilityNodeInfo>()
        // Compose exposes virtual nodes; walk them instead of using framework View text lookup.
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.text?.toString() == text || node.contentDescription?.toString() == text) matches.add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let(pending::add)
        }
        return matches
    }

    private fun capture(activity: Activity, destination: File, mode: Int, includeDialogs: Boolean = false) {
        // Let Compose finish the frame after navigation. No account or fabricated monitoring data is used.
        SystemClock.sleep(700)
        waitForIdleSync()
        var bitmap: Bitmap? = null
        runOnMainSync {
            val actual = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            check(actual == if (mode == UiModeManager.MODE_NIGHT_YES) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            val view = activity.window.decorView
            check(view.width > 0 && view.height > 0)
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                // Drawing within the test process retains screenshot protection on the app's secure window.
                val canvas = Canvas(it)
                view.draw(canvas)
                if (includeDialogs) {
                    val dialogs = WindowInspector.getGlobalWindowViews().filter { root -> root !== view && root.isShown }
                    check(dialogs.isNotEmpty()) { "No visible error dialog to capture" }
                    for (dialog in dialogs) {
                        val params = dialog.layoutParams as? WindowManager.LayoutParams
                        if (params != null && params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                            canvas.drawColor(android.graphics.Color.argb((255 * params.dimAmount).toInt(), 0, 0, 0))
                        }
                        val position = IntArray(2)
                        dialog.getLocationOnScreen(position)
                        canvas.save()
                        canvas.translate(position[0].toFloat(), position[1].toFloat())
                        dialog.draw(canvas)
                        canvas.restore()
                    }
                }
            }
        }
        val captured = checkNotNull(bitmap)
        destination.outputStream().use { check(captured.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        captured.recycle()
    }
}
