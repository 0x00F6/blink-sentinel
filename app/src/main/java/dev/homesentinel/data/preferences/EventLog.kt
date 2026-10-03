package dev.homesentinel.data.preferences

import android.content.Context
import androidx.core.content.edit
import dev.homesentinel.domain.model.BlinkException
import dev.homesentinel.domain.model.ErrorDetails
import dev.homesentinel.domain.model.LogEntry
import dev.homesentinel.domain.model.LogRetention
import dev.homesentinel.domain.model.LogType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Bounded local log with error technical details and secret sanitization. */
class EventLog internal constructor(
    private val read: () -> String,
    private val write: (String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    initialRetention: Int = LogRetention.DEFAULT,
) {
    constructor(context: Context, initialRetention: Int = LogRetention.DEFAULT) : this(
        read = { context.getSharedPreferences("events", Context.MODE_PRIVATE).getString("entries", "[]") ?: "[]" },
        write = { contents ->
            context.getSharedPreferences("events", Context.MODE_PRIVATE).edit { putString("entries", contents) }
        },
        initialRetention = initialRetention,
    )

    private var retention = initialRetention.also(LogRetention::validate)
    private val mutable = MutableStateFlow(load().take(retention))
    val entries = mutable.asStateFlow()

    private fun load(): List<LogEntry> =
        try {
            val j = JSONArray(read())
            (0 until minOf(j.length(), LogRetention.MAX)).map { i ->
                val row = j.getJSONObject(i)
                val detailsObj = row.optJSONObject("errorDetails")
                val type = LogType.entries.firstOrNull { it.name == row.optString("type") } ?: LogType.INFO
                val isError = row.optBoolean("isError", false) || detailsObj != null || type == LogType.ERROR
                val details =
                    detailsObj?.let { d ->
                        ErrorDetails(
                            operation = d.optString("operation", ""),
                            httpMethod = d.optString("httpMethod", ""),
                            url = d.optString("url", ""),
                            httpStatus = d.optString("httpStatus", ""),
                            exceptionType = d.optString("exceptionType", ""),
                            exceptionMessage = d.optString("exceptionMessage", ""),
                            causes = d.optString("causes", ""),
                            stackTrace = d.optString("stackTrace", ""),
                        )
                    }
                LogEntry(
                    row.getLong("at"),
                    sanitizeSecrets(row.getString("message")),
                    isError,
                    details?.let {
                        it.copy(
                            operation = sanitizeSecrets(it.operation),
                            httpMethod = sanitizeSecrets(it.httpMethod),
                            httpStatus = sanitizeSecrets(it.httpStatus),
                            exceptionType = sanitizeSecrets(it.exceptionType),
                            url = sanitizeSecrets(it.url),
                            exceptionMessage = sanitizeSecrets(it.exceptionMessage),
                            causes = sanitizeSecrets(it.causes),
                            stackTrace = sanitizeSecrets(it.stackTrace),
                        )
                    },
                    if (isError) LogType.ERROR else type,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

    @Synchronized
    fun add(
        message: String,
        isError: Boolean = false,
        errorDetails: ErrorDetails? = null,
        type: LogType = LogType.INFO,
    ) {
        val sanitizedMsg = sanitizeSecrets(message)
        val sanitizedDetails =
            errorDetails?.let { d ->
                ErrorDetails(
                    operation = sanitizeSecrets(d.operation),
                    httpMethod = sanitizeSecrets(d.httpMethod),
                    url = sanitizeSecrets(d.url),
                    httpStatus = sanitizeSecrets(d.httpStatus),
                    exceptionType = sanitizeSecrets(d.exceptionType),
                    exceptionMessage = sanitizeSecrets(d.exceptionMessage),
                    causes = sanitizeSecrets(d.causes),
                    stackTrace = sanitizeSecrets(d.stackTrace),
                )
            }
        val entry =
            LogEntry(
                at = now(),
                message = sanitizedMsg,
                isError = isError || errorDetails != null || type == LogType.ERROR,
                errorDetails = sanitizedDetails,
                type = if (isError || errorDetails != null) LogType.ERROR else type,
            )
        mutable.value = (listOf(entry) + mutable.value).take(retention)
        save()
    }

    @Synchronized
    fun setRetention(value: Int) {
        LogRetention.validate(value)
        retention = value
        val retained = mutable.value.take(value)
        if (retained != mutable.value) {
            mutable.value = retained
            save()
        }
    }

    @Synchronized
    fun addError(
        message: String,
        throwable: Throwable? = null,
        operation: String = "",
        httpMethod: String = "",
        url: String = "",
        httpStatus: Int? = null,
    ) {
        var op = operation
        var method = httpMethod
        var callUrl = url
        var statusText = if (httpStatus != null) httpStatus.toString() else "No HTTP status received"

        if (throwable is BlinkException && throwable.context != null) {
            val ctx = throwable.context
            if (ctx.operation.isNotBlank()) op = ctx.operation
            if (method.isBlank()) method = ctx.httpMethod
            if (callUrl.isBlank()) callUrl = ctx.url
            if (ctx.httpStatus != null) statusText = ctx.httpStatus.toString()
        }

        val type = throwable?.javaClass?.name ?: "Error"
        val exMsg = throwable?.message.orEmpty()
        val causesList = mutableListOf<String>()
        var curr = throwable?.cause
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        while (curr != null && seen.add(curr)) {
            causesList.add("${curr.javaClass.name}: ${curr.message.orEmpty()}")
            curr = curr.cause
        }
        val causesStr = causesList.joinToString("\n")
        val trace = throwable?.stackTraceToString().orEmpty()

        val details =
            ErrorDetails(
                operation = op.ifBlank { "Blink / monitoring operation" },
                httpMethod = method.ifBlank { "N/A" },
                url = callUrl.ifBlank { "N/A" },
                httpStatus = statusText,
                exceptionType = type,
                exceptionMessage = exMsg,
                causes = causesStr.ifBlank { "No underlying cause" },
                stackTrace = trace,
            )
        add(message, isError = true, errorDetails = details)
    }

    @Synchronized
    fun clear() {
        mutable.value = emptyList()
        save()
    }

    private fun save() {
        val j = JSONArray()
        mutable.value.forEach { entry ->
            val row =
                JSONObject()
                    .put("at", entry.at)
                    .put("message", entry.message)
                    .put("isError", entry.isError)
                    .put("type", entry.type.name)
            entry.errorDetails?.let { d ->
                val detailsObj =
                    JSONObject()
                        .put("operation", d.operation)
                        .put("httpMethod", d.httpMethod)
                        .put("url", d.url)
                        .put("httpStatus", d.httpStatus)
                        .put("exceptionType", d.exceptionType)
                        .put("exceptionMessage", d.exceptionMessage)
                        .put("causes", d.causes)
                        .put("stackTrace", d.stackTrace)
                row.put("errorDetails", detailsObj)
            }
            j.put(row)
        }
        write(j.toString())
    }

    companion object {
        fun sanitizeSecrets(input: String): String {
            if (input.isBlank()) return input
            var s = input
            // Never retain header values, query values, credentials, or credential-shaped fields.
            s = s.replace(Regex("(?im)(authorization|proxy-authorization|cookie|set-cookie)\\s*:\\s*[^\\r\\n]+"), "$1: [REDACTED]")
            s = s.replace(Regex("(?i)Bearer\\s+[^\\s,;]+"), "Bearer [REDACTED]")
            s = s.replace(Regex("(https?://)[^/\\s@]+@"), "$1[REDACTED]@")
            s = s.replace(Regex("([?&][^=&#\\s]+=)[^&#\\s]*"), "$1[REDACTED]")
            val keys =
                "password|passwd|pwd|access[_-]?token|refresh[_-]?token|id[_-]?token|token|" +
                    "csrf[_-]?token|csrf|2fa[_-]?code|code|otp|code[_-]?verifier|" +
                    "client[_-]?secret|secret|cookie|authorization"
            s =
                s.replace(
                    Regex("""(?i)(["']?(?:$keys)["']?\s*[:=]\s*)("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[^&;\s,}]+)"""),
                    "$1[REDACTED]",
                )
            return s
        }
    }
}
