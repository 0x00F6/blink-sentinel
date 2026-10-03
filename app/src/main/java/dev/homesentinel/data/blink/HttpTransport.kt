package dev.homesentinel.data.blink

import dev.homesentinel.domain.model.BlinkErrorContext
import dev.homesentinel.domain.model.BlinkException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class HttpReply(
    val code: Int,
    val body: String,
    val location: String?,
)

fun networkErrorDetail(e: IOException): String {
    val type = e.javaClass.simpleName.ifBlank { "IOException" }
    val msg = (e.localizedMessage ?: e.message).orEmpty().trim()
    return if (msg.isNotBlank() && msg != type) {
        "$type: $msg"
    } else {
        type
    }
}

/** Cancellation closes the underlying call, important when network changes during an action. */
suspend fun OkHttpClient.fetch(
    request: Request,
    operationName: String = "",
): HttpReply =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    if (continuation.isActive) {
                        val detail = networkErrorDetail(e)
                        continuation.resumeWithException(
                            BlinkException(
                                BlinkException.Kind.NETWORK,
                                "Network connection failed ($detail). Check your Internet connection and try again.",
                                // Preserve the original exception for sanitized cause/stack diagnostics.
                                cause = e,
                                context =
                                    BlinkErrorContext(
                                        operation = operationName,
                                        httpMethod = request.method,
                                        url = request.url.toString(),
                                        httpStatus = null,
                                    ),
                            ),
                        )
                    }
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    response.use { r ->
                        try {
                            val source = r.body?.source()
                            if (source?.request(1_048_577L) == true) {
                                throw BlinkException(
                                    BlinkException.Kind.PROTOCOL,
                                    "Blink response too large",
                                    context =
                                        BlinkErrorContext(
                                            operation = operationName,
                                            httpMethod = request.method,
                                            url = request.url.toString(),
                                            httpStatus = r.code,
                                        ),
                                )
                            }
                            val reply =
                                HttpReply(
                                    r.code,
                                    source?.readUtf8().orEmpty(),
                                    r.header("Location"),
                                )
                            if (continuation.isActive) continuation.resume(reply)
                        } catch (e: IOException) {
                            if (continuation.isActive) {
                                val detail = networkErrorDetail(e)
                                continuation.resumeWithException(
                                    BlinkException(
                                        BlinkException.Kind.NETWORK,
                                        "Network read interrupted ($detail)",
                                        cause = e,
                                        context =
                                            BlinkErrorContext(
                                                operation = operationName,
                                                httpMethod = request.method,
                                                url = request.url.toString(),
                                                httpStatus = r.code,
                                            ),
                                    ),
                                )
                            }
                        } catch (e: BlinkException) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                }
            },
        )
    }
