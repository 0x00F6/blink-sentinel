package dev.homesentinel

import dev.homesentinel.data.blink.*
import dev.homesentinel.domain.model.*
import dev.homesentinel.domain.usecase.EnsureBlinkState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

/**
 * Entire network flow is replayed against a loopback server; no real Blink account is contacted.
 */
class BlinkApiServiceTest {
    @Test
    fun httpErrorsIncludeTheOperationStatusAndActualUrl() = runTest {
        savedSession()
        api.restore()
        reply(503)
        try {
            api.listSystems()
            fail("Expected remote failure")
        } catch (e: BlinkException) {
            assertEquals(503, e.context!!.httpStatus)
            assertEquals("GET", e.context!!.httpMethod)
            assertEquals("Systems list", e.context!!.operation)
            assertEquals(server.url("/api/v3/accounts/123/homescreen").toString(), e.context!!.url)
        }
    }

    @Test
    fun malformedSystemResponseKeepsItsHttpContext() = runTest {
        savedSession()
        api.restore()
        reply(body = "{}")
        try {
            api.listSystems()
            fail("Expected protocol failure")
        } catch (e: BlinkException) {
            assertEquals(BlinkException.Kind.PROTOCOL, e.kind)
            assertEquals(200, e.context!!.httpStatus)
            assertEquals("Systems list", e.context!!.operation)
            assertNotNull(e.cause)
        }
    }

    @Test
    fun explicitCommandForAnObsoleteSelectionSendsNoRequest() = runTest {
        savedSession()
        api.restore()
        selected = "2"
        try {
            api.arm("1")
            fail("Must not retarget an obsolete command")
        } catch (e: BlinkException) {
            assertEquals(BlinkException.Kind.PROTOCOL, e.kind)
        }
        assertEquals(0, server.requestCount)
    }

    private lateinit var server: MockWebServer
    private lateinit var vault: MemoryVault
    private lateinit var api: BlinkApiService
    private var selected = "1"
    private var time = 1_000_000L

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        vault = MemoryVault()
        api = service()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun service() =
        BlinkApiService(
            vault,
            { selected },
            BlinkEndpoints(server.url("/"), server.url("/"), { server.url("/") }),
            { time },
        )

    private fun reply(code: Int = 200, body: String = "{}") {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    private fun prepareLogin(twoFactor: Boolean = false) {
        server.enqueue(
            MockResponse()
                .setBody("<html>OAuth ready</html>")
                .addHeader("Set-Cookie", "sid=abc; Path=/; HttpOnly")
        )
        reply(
            body =
                """<script type="application/json" id="oauth-args">{"csrf-token":"csrf123"}</script>"""
        )
        if (twoFactor) reply(202, """{"tsv_state":"pending","next_time_in_secs":60}""")
        else
            server.enqueue(
                MockResponse().setResponseCode(302).addHeader("Location", "/oauth/v2/authorize")
            )
    }

    private fun prepareFinish() {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader(
                    "Location",
                    "immedia-blink://applinks.blink.com/signin/callback?code=code123",
                )
        )
        reply(
            body = """{"access_token":"access123","refresh_token":"refresh123","expires_in":3600}"""
        )
        reply(body = """{"tier":"rest-prod","account_id":123}""")
    }

    private fun status(armed: Boolean) {
        reply(body = """{"networks":[{"id":1,"name":"House","armed":$armed}]}""")
    }

    private fun savedSession(expiry: Long = time + 3_600_000) {
        vault.text =
            JSONObject()
                .put("hardware", "8B0F56C8-BCB6-4C26-9052-38548A2D3987")
                .put("access_token", "old-access")
                .put("refresh_token", "old-refresh")
                .put("expiry", expiry)
                .put("region", "rest-prod")
                .put("account", "123")
                .toString()
    }

    @Test
    fun loginUsesPkceCookiesAndStoresNoPassword() = runTest {
        prepareLogin()
        prepareFinish()
        assertEquals(LoginResult.CONNECTED, api.login("user@example.test", "SECRET_PASSWORD"))
        assertTrue(api.connected.value)
        val authorize = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("S256", authorize.requestUrl!!.queryParameter("code_challenge_method"))
        assertTrue(
            authorize.requestUrl!!.queryParameter("hardware_id")!!.matches(Regex("[A-F0-9-]{36}"))
        )
        val signinPage = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertTrue(signinPage.getHeader("Cookie")!!.contains("sid=abc"))
        val signin = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertTrue(signin.body.readUtf8().contains("csrf-token=csrf123"))
        assertFalse(vault.text!!.contains("SECRET_PASSWORD"))
        assertFalse(vault.text!!.contains("user@example.test"))
        assertFalse(vault.text!!.contains("csrf123"))
    }

    @Test
    fun loginAndTwoFactorCanCompleteInTwoCalls() = runTest {
        prepareLogin(true)
        assertEquals(LoginResult.TWO_FACTOR_REQUIRED, api.login("user@example.test", "password"))
        assertFalse(api.connected.value)
        reply(201, """{"status":"auth-completed"}""")
        prepareFinish()
        api.verifyCode("123456")
        assertTrue(api.connected.value)
        assertFalse(vault.text!!.contains("123456"))
    }

    @Test
    fun restartRestoresSessionWithoutResubmittingCredentials() = runTest {
        savedSession()
        val restarted = service()
        restarted.restore()
        status(true)
        assertTrue(restarted.connected.value)
        assertEquals(BlinkStatus.ARMED, restarted.getStatus())
        assertEquals(1, server.requestCount)
        assertEquals("Bearer old-access", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun expiredSessionRefreshesAndPersistsRotatedToken() = runTest {
        savedSession(time - 1)
        api.restore()
        reply(
            body =
                """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600}"""
        )
        status(false)
        assertEquals(BlinkStatus.DISARMED, api.getStatus())
        assertEquals("/oauth/token", server.takeRequest().path)
        assertEquals("Bearer new-access", server.takeRequest().getHeader("Authorization"))
        assertTrue(vault.text!!.contains("new-refresh"))
    }

    @Test
    fun unauthorizedGetRefreshesOnceAndRetries() = runTest {
        savedSession()
        api.restore()
        reply(401)
        reply(
            body =
                """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600}"""
        )
        status(true)
        assertEquals(BlinkStatus.ARMED, api.getStatus())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun invalidRefreshMarksSessionDisconnected() = runTest {
        savedSession(time - 1)
        api.restore()
        reply(401)
        try {
            api.getStatus()
            fail("Must reject expired refresh token")
        } catch (e: BlinkException) {
            assertEquals(BlinkException.Kind.AUTH, e.kind)
        }
        assertFalse(api.connected.value)
        assertEquals(BlinkStatus.UNKNOWN, api.status.value)
    }

    @Test
    fun remoteErrorIsReportedAndStatusBecomesUnknown() = runTest {
        savedSession()
        api.restore()
        reply(503)
        try {
            api.getStatus()
            fail("Must propagate server error")
        } catch (e: BlinkException) {
            assertEquals(BlinkException.Kind.REMOTE, e.kind)
        }
        assertEquals(BlinkStatus.UNKNOWN, api.status.value)
    }

    @Test
    fun armIsConfirmedByReadingActualStateAfterPost() = runTest {
        savedSession()
        api.restore()
        status(false)
        reply(body = """{"id":101,"network_id":1}""")
        status(true)
        assertTrue(EnsureBlinkState(api)(BlinkStatus.ARMED))
        val get = server.takeRequest()
        val post = server.takeRequest()
        assertEquals("GET", get.method)
        assertEquals("POST", post.method)
        assertEquals("/api/v1/accounts/123/networks/1/state/arm", post.path)
        assertEquals(BlinkStatus.ARMED, api.status.value)
    }

    @Test
    fun disarmIsConfirmedByReadingActualStateAfterPost() = runTest {
        savedSession()
        api.restore()
        status(true)
        reply()
        status(false)
        assertTrue(EnsureBlinkState(api)(BlinkStatus.DISARMED))
        server.takeRequest()
        assertEquals("/api/v1/accounts/123/networks/1/state/disarm", server.takeRequest().path)
        assertEquals(BlinkStatus.DISARMED, api.status.value)
    }

    @Test
    fun alreadyArmedSkipsAnyPost() = runTest {
        savedSession()
        api.restore()
        status(true)
        assertFalse(EnsureBlinkState(api)(BlinkStatus.ARMED))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun unconfirmedCommandIsNotPresentedAsSuccess() = runTest {
        savedSession()
        api.restore()
        reply()
        repeat(5) { status(false) }
        try {
            api.arm()
            fail("Must not claim a successful arm")
        } catch (e: BlinkException) {
            assertEquals(BlinkException.Kind.REMOTE, e.kind)
        }
        assertEquals(BlinkStatus.UNKNOWN, api.status.value)
    }

    @Test
    fun logoutDeletesTheSavedSession() = runTest {
        savedSession()
        api.restore()
        api.logout()
        assertNull(vault.text)
        assertFalse(api.connected.value)
        assertEquals(BlinkStatus.UNKNOWN, api.status.value)
    }

    @Test
    fun missingArmedFieldRemainsUnknown() = runTest {
        savedSession()
        api.restore()
        reply(body = """{"networks":[{"id":1,"name":"House"}]}""")
        assertEquals(BlinkStatus.UNKNOWN, api.getStatus())
    }

    @Test
    fun redirectToAnotherOriginIsRejectedWithoutForwardingCookies() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(302).addHeader("Location", "https://malicious.invalid/")
        )
        try {
            api.login("user@example.test", "password")
            fail("Must reject external redirect")
        } catch (e: BlinkException) {
            assertEquals(BlinkException.Kind.PROTOCOL, e.kind)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun corruptSavedSessionRequiresNewLogin() = runTest {
        vault.text = "invalid-json"
        try {
            api.restore()
            fail("Must reject corrupt session")
        } catch (e: BlinkException) {
            assertEquals(BlinkException.Kind.AUTH, e.kind)
        }
        assertFalse(api.connected.value)
    }
}
