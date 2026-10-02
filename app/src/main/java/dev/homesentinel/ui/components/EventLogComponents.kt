package dev.homesentinel.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.homesentinel.R
import dev.homesentinel.domain.model.LogEntry
import dev.homesentinel.domain.model.LogRetention
import dev.homesentinel.domain.model.LogType

private fun LogType.label(): String = when (this) {
    LogType.INFO -> "Information"
    LogType.SIGNAL_DETECTED -> "Signal detected"
    LogType.SIGNAL_LOST -> "Signal not detected"
    LogType.ABSENCE_CONFIRMED -> "Absence confirmed"
    LogType.SIGNAL_UNKNOWN -> "Signal unknown"
    LogType.WAITING_CONFIRMATION -> "Awaiting confirmation"
    LogType.MONITORING_STARTED -> "Monitoring started"
    LogType.MONITORING_STOPPED -> "Monitoring stopped"
    LogType.BLINK_ARMED -> "Blink armed"
    LogType.BLINK_DISARMED -> "Blink disarmed"
    LogType.ACCOUNT -> "Blink account"
    LogType.ERROR -> "Error"
}

private fun LogType.icon(): Int = when (this) {
    LogType.INFO -> R.drawable.ic_log_info
    LogType.SIGNAL_DETECTED -> R.drawable.ic_network
    LogType.SIGNAL_LOST -> R.drawable.ic_signal_lost
    LogType.ABSENCE_CONFIRMED -> R.drawable.ic_home_away
    LogType.SIGNAL_UNKNOWN -> R.drawable.ic_signal_unknown
    LogType.WAITING_CONFIRMATION -> R.drawable.ic_log_clock
    LogType.MONITORING_STARTED -> R.drawable.ic_log_play
    LogType.MONITORING_STOPPED -> R.drawable.ic_log_stop
    LogType.BLINK_ARMED -> R.drawable.ic_log_lock
    LogType.BLINK_DISARMED -> R.drawable.ic_log_unlock
    LogType.ACCOUNT -> R.drawable.ic_log_account
    LogType.ERROR -> R.drawable.ic_log_error
}

@Composable
fun EventLogCard(entry: LogEntry, date: String, onDetails: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val type = if (entry.isError) LogType.ERROR else entry.type
    val accent = when (type) {
        LogType.ERROR -> colors.error
        LogType.SIGNAL_LOST, LogType.ABSENCE_CONFIRMED, LogType.WAITING_CONFIRMATION -> colors.tertiary
        LogType.SIGNAL_UNKNOWN -> colors.secondary
        LogType.MONITORING_STOPPED, LogType.INFO -> colors.onSurfaceVariant
        else -> colors.primary
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = entry.isError, onClick = onDetails),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (entry.isError) colors.errorContainer else colors.surface),
        border = BorderStroke(1.dp, if (entry.isError) colors.error.copy(alpha = 0.6f) else colors.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(type.icon()), contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(type.label(), color = accent, style = MaterialTheme.typography.labelLarge)
                Text(date, color = accent, style = MaterialTheme.typography.labelSmall)
                Text(
                    entry.message,
                    color = if (entry.isError) colors.error else colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (entry.isError) {
                    TextButton(onClick = onDetails, contentPadding = PaddingValues(vertical = 4.dp)) {
                        Text("View error details", color = colors.error)
                    }
                }
            }
        }
    }
}

@Composable
fun LogRetentionDialog(current: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var input by remember(current) { mutableStateOf(current.toString()) }
    val value = input.toIntOrNull()
    val valid = value != null && value in LogRetention.MIN..LogRetention.MAX
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log retention") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Entries to keep") },
                    supportingText = { Text("${LogRetention.MIN}–${LogRetention.MAX} entries; default ${LogRetention.DEFAULT}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !valid,
                )
                Text(
                    "Lowering the limit removes the oldest entries immediately. Increasing it cannot restore deleted entries.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { input = LogRetention.DEFAULT.toString() }) { Text("Use default (500)") }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { value?.let(onSave) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun LogErrorDialog(entry: LogEntry, date: String, onDismiss: () -> Unit) {
    val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
    val details = entry.errorDetails
    val text = "Date: $date\n${entry.message}\n\n" + (details?.let {
        "Operation: ${it.operation}\nHTTP method: ${it.httpMethod}\nURL: ${it.url}\nHTTP status: ${it.httpStatus}" +
            "\nType: ${it.exceptionType}\nMessage: ${it.exceptionMessage}\nCauses:\n${it.causes}" +
            "\n\nFull stack trace:\n${it.stackTrace}"
    } ?: "No technical details available for this older event")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(R.drawable.ic_log_error), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("Error details", color = MaterialTheme.colorScheme.error)
            }
        },
        text = {
            SelectionContainer {
                Text(
                    text,
                    modifier = Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("Blink Sentinel diagnostics", text)) }) {
                Text("Copy")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
