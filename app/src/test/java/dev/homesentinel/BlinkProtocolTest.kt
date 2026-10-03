package dev.homesentinel

import dev.homesentinel.data.blink.BlinkProtocol
import dev.homesentinel.data.blink.fetch
import dev.homesentinel.domain.model.BlinkException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

class BlinkProtocolTest {
    @Test
    fun pkceChallengeMatchesSha256AndIsUrlSafe() {
        val (verifier, challenge) = BlinkProtocol.pkce()
        assertTrue(verifier.matches(Regex("[A-Za-z0-9_-]{43}")))
        assertEquals(
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
                ),
            challenge,
        )
    }

    @Test
    fun csrfAllowsDifferentHtmlAttributeOrder() {
        assertEquals(
            "abc",
            BlinkProtocol.csrf(
                """<script type='application/json' data-id='x' id='oauth-args'>{"csrf-token":"abc"}</script>""",
            ),
        )
    }

    @Test
    fun modernAndLegacyTwoFactorResponsesAreRecognized() {
        assertTrue(BlinkProtocol.twoFactor(412, ""))
        assertTrue(BlinkProtocol.twoFactor(202, """{"tsv_methods":["sms"]}"""))
        assertFalse(BlinkProtocol.twoFactor(202, "{}"))
    }

    @Test
    fun malformedJsonIsTypedProtocolFailure() {
        try {
            BlinkProtocol.json("not-json")
            fail("Must reject response")
        } catch (e: BlinkException) {
            assertEquals(BlinkException.Kind.PROTOCOL, e.kind)
        }
    }

    @Test
    fun actualNetworkFailureIsTypedAndContainsNoCredentials() =
        runTest {
            val server = MockWebServer().apply { start() }
            val url = server.url("/")
            server.shutdown()
            val client =
                OkHttpClient
                    .Builder()
                    .retryOnConnectionFailure(false)
                    .callTimeout(1, TimeUnit.SECONDS)
                    .build()
            try {
                client.fetch(Request.Builder().url(url).build())
                fail("Must report refused connection")
            } catch (e: BlinkException) {
                assertEquals(BlinkException.Kind.NETWORK, e.kind)
                assertFalse(e.message!!.contains(url.toString()))
            }
        }
}
