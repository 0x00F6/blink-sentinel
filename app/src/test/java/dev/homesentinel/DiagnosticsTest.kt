package dev.homesentinel

import dev.homesentinel.data.blink.*
import dev.homesentinel.data.preferences.EventLog
import dev.homesentinel.domain.model.BlinkException
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsTest {
    @Test fun regionalUrlsAlwaysUseRestPrefix() {
        val endpoints = BlinkEndpoints()
        assertEquals("https://rest-e005.immedia-semi.com/", endpoints.region("e005").toString())
        assertEquals("https://rest-e005.immedia-semi.com/", endpoints.region("rest-e005").toString())
    }

    @Test fun networkMessageIncludesExceptionTypeAndMessage() {
        assertEquals("UnknownHostException: host unreachable", networkErrorDetail(UnknownHostException("host unreachable")))
        assertEquals("IOException", networkErrorDetail(IOException()))
        assertEquals("IOException", networkErrorDetail(IOException("IOException")))
    }

    @Test fun secretsAreMaskedInHeadersFormsJsonUrlsAndTraceMessages() {
        val text = """
            Authorization: Bearer hidden1
            Cookie: sid=hidden2; other=hidden3
            Set-Cookie: sid=hidden4
            {"password":"hidden5 with spaces", "access_token":"hidden6", "refresh-token":"hidden7"}
            password=hidden8&otp=hidden9&code_verifier=hidden10
            https://user:hidden11@host/path?code=hidden12&state=hidden13
            java.io.IOException: client_secret='hidden14 with spaces' token=hidden15
        """.trimIndent()
        val safe = EventLog.sanitizeSecrets(text)
        (1..15).forEach { assertFalse("Leaked secret $it: $safe", safe.contains("hidden$it")) }
        assertTrue(safe.contains("java.io.IOException"))
        assertTrue(safe.contains("host/path"))
    }

    @Test fun escapedQuotesDoNotExposeTheRestOfASecret() {
        val safe = EventLog.sanitizeSecrets("""{"password":"hidden\\\"stillhidden", "token":"private"}""")
        assertFalse(safe.contains("hidden"))
        assertFalse(safe.contains("private"))
    }

    @Test fun failureBeforeResponseKeepsTheOriginalCauseAndRealRequestContext() = runTest {
        val cause = UnknownHostException("Test DNS failure")
        val client = OkHttpClient.Builder().dns(object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> = throw cause
        }).build()
        try {
            client.fetch(Request.Builder().url("https://blink.invalid/status").build(), "Read status")
            fail("Expected network failure")
        } catch (e: BlinkException) {
            assertSame(cause, e.cause)
            assertEquals("GET", e.context!!.httpMethod)
            assertEquals("https://blink.invalid/status", e.context!!.url)
            assertEquals("Read status", e.context!!.operation)
            assertNull(e.context!!.httpStatus)
            assertTrue(e.message!!.contains("UnknownHostException"))
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    @Test fun interruptedBodyRetainsTheReceivedHttpStatusAndOriginalCause() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("x".repeat(1000)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        server.start()
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()
        try {
            client.fetch(Request.Builder().url(server.url("/status")).build(), "Read status")
            fail("Expected interrupted body")
        } catch (e: BlinkException) {
            assertTrue(e.cause is IOException)
            assertEquals(200, e.context!!.httpStatus)
            assertEquals(server.url("/status").toString(), e.context!!.url)
        } finally {
            server.shutdown()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
