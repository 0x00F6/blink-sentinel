package dev.homesentinel.data.blink

import dev.homesentinel.domain.model.BlinkErrorContext
import dev.homesentinel.domain.model.BlinkException
import dev.homesentinel.domain.model.BlinkStatus
import dev.homesentinel.domain.model.BlinkSystem
import dev.homesentinel.domain.model.LoginResult
import dev.homesentinel.domain.repository.BlinkService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Endpoint injection is for local mock-server tests only; never configured from user input. */
data class BlinkEndpoints(
    val oauth: HttpUrl = "https://api.oauth.blink.com/".toHttpUrl(),
    val tier: HttpUrl = "https://rest-prod.immedia-semi.com/".toHttpUrl(),
    val region: (String) -> HttpUrl = { r ->
        // Persisted older sessions may contain e005 rather than rest-e005.
        val tier = r.removePrefix("rest-")
        require(tier.matches(Regex("[a-z][a-z0-9-]*"))) { "Invalid Blink region" }
        val host = "rest-$tier"
        "https://$host.immedia-semi.com/".toHttpUrl()
    },
)

/**
 * Unofficial mobile OAuth v2 / PKCE adapter, informed by blinkpy 0.25.9. No password, OTP, HTTP
 * body or cookie is persisted or logged. No insecure TLS bypass.
 */
class BlinkApiService(
    private val vault: SessionVault,
    private val selectedSystem: suspend () -> String,
    private val endpoints: BlinkEndpoints = BlinkEndpoints(),
    private val now: () -> Long = System::currentTimeMillis,
) : BlinkService {
    private val lock = Mutex()
    private var lastHttpContext: BlinkErrorContext? = null

    /** Keep parsing and verification failures tied to the response actually received. */
    private suspend fun <T> withDiagnostics(block: suspend () -> T): T =
        lock.withLock {
            lastHttpContext = null
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: BlinkException) {
                if (e.context != null || lastHttpContext == null) throw e
                throw BlinkException(e.kind, e.message.orEmpty(), e, lastHttpContext)
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: Exception) {
                throw BlinkException(
                    BlinkException.Kind.PROTOCOL,
                    "Blink operation failed: ${e.javaClass.simpleName}",
                    e,
                    lastHttpContext,
                )
            }
        }

    private val mutableConnected = MutableStateFlow(false)
    private val mutableStatus = MutableStateFlow(BlinkStatus.UNKNOWN)
    override val connected = mutableConnected.asStateFlow()
    override val status = mutableStatus.asStateFlow()
    private var session = JSONObject()
    private var verifier = ""
    private var csrf = ""
    private var authStarted = 0L
    private val cookies = mutableListOf<Cookie>()
    private val client =
        OkHttpClient
            .Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .cookieJar(
                object : CookieJar {
                    override fun saveFromResponse(
                        url: HttpUrl,
                        newCookies: List<Cookie>,
                    ) = synchronized(cookies) {
                        newCookies.forEach { n ->
                            cookies.removeAll {
                                it.name == n.name && it.domain == n.domain && it.path == n.path
                            }
                            cookies.add(n)
                        }
                    }

                    override fun loadForRequest(url: HttpUrl): List<Cookie> =
                        synchronized(cookies) {
                            cookies.removeAll { it.expiresAt < now() }
                            cookies.filter { it.matches(url) }
                        }
                },
            ).build()
    private val browserAgent =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.1 Mobile/15E148 Safari/604.1"
    private val tokenAgent = "Blink/2511191620 CFNetwork/3860.200.71 Darwin/25.1.0"
    private val redirect = "immedia-blink://applinks.blink.com/signin/callback"

    suspend fun restore() =
        withDiagnostics {
            try {
                session = JSONObject(vault.read() ?: "{}")
                mutableConnected.value =
                    session.optString("refresh_token").isNotBlank() &&
                    session.has("account")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                session = JSONObject()
                mutableConnected.value = false
                throw BlinkException(
                    BlinkException.Kind.AUTH,
                    "Encrypted session unreadable: sign in to Blink again",
                    cause = e,
                    context = BlinkErrorContext(operation = "Session restoration"),
                )
            }
        }

    private suspend fun hardware(): String {
        if (!session.has("hardware")) {
            session.put("hardware", UUID.randomUUID().toString().uppercase())
            vault.write(session.toString())
        }
        return session.getString("hardware")
    }

    private fun oauth(path: String) = endpoints.oauth.resolve(path)!!

    private fun form(vararg pairs: Pair<String, String>): RequestBody =
        FormBody.Builder().apply { pairs.forEach { add(it.first, it.second) } }.build()

    private suspend fun request(
        url: HttpUrl,
        body: RequestBody? = null,
        bearer: String? = null,
        token: Boolean = false,
        operationName: String = "",
    ): HttpReply {
        val builder =
            Request.Builder().url(url).header("User-Agent", if (token) tokenAgent else browserAgent)
        bearer?.let { builder.header("Authorization", "Bearer $it") }
        if (body != null) {
            builder.post(body)
            if (url.host == endpoints.oauth.host) {
                builder
                    .header("Origin", endpoints.oauth.toString().trimEnd('/'))
                    .header("Referer", oauth("oauth/v2/signin").toString())
            }
        }
        val request = builder.build()
        lastHttpContext = BlinkErrorContext(operationName, request.method, request.url.toString())
        return client.fetch(request, operationName).also {
            lastHttpContext = lastHttpContext?.copy(httpStatus = it.code)
        }
    }

    private fun accepted(
        reply: HttpReply,
        operationName: String,
        method: String,
        url: HttpUrl,
    ): JSONObject {
        if (reply.code !in 200..299) fail(reply.code, operationName, method, url)
        return try {
            BlinkProtocol.json(reply.body)
        } catch (e: BlinkException) {
            throw BlinkException(
                e.kind,
                e.message.orEmpty(),
                e,
                BlinkErrorContext(operationName, method, url.toString(), reply.code),
            )
        }
    }

    private fun fail(
        code: Int,
        operationName: String,
        method: String,
        url: HttpUrl,
    ): Nothing =
        throw when (code) {
            401 ->
                BlinkException(
                    BlinkException.Kind.AUTH,
                    "Blink session expired or credentials incorrect: sign in again",
                    context = BlinkErrorContext(operationName, method, url.toString(), code),
                )
            403,
            406,
            ->
                BlinkException(
                    BlinkException.Kind.AUTH,
                    "Blink refused the connection (HTTP $code). Try without a VPN or on another network.",
                    context = BlinkErrorContext(operationName, method, url.toString(), code),
                )
            429 ->
                BlinkException(
                    BlinkException.Kind.RATE_LIMIT,
                    "Blink is rate-limiting requests: try again later",
                    context = BlinkErrorContext(operationName, method, url.toString(), code),
                )
            else ->
                BlinkException(
                    BlinkException.Kind.REMOTE,
                    "Blink error (HTTP $code)",
                    context = BlinkErrorContext(operationName, method, url.toString(), code),
                )
        }

    override suspend fun login(
        email: String,
        password: String,
    ): LoginResult =
        withDiagnostics {
            require(email.isNotBlank() && password.isNotBlank()) {
                "Enter your Blink email address and password"
            }
            mutableConnected.value = false
            mutableStatus.value = BlinkStatus.UNKNOWN
            clearPending()
            val hardware = session.optString("hardware")
            session = JSONObject()
            if (hardware.isNotBlank()) session.put("hardware", hardware)
            vault.write(session.toString())
            val pair = BlinkProtocol.pkce()
            verifier = pair.first
            authStarted = now()
            val authUrl =
                oauth("oauth/v2/authorize")
                    .newBuilder()
                    .apply {
                        mapOf(
                            "app_brand" to "blink",
                            "app_version" to "50.1",
                            "client_id" to "ios",
                            "code_challenge" to pair.second,
                            "code_challenge_method" to "S256",
                            "device_brand" to "Apple",
                            "device_model" to "iPhone16,1",
                            "device_os_version" to "26.1",
                            "hardware_id" to hardware(),
                            "redirect_uri" to redirect,
                            "response_type" to "code",
                            "scope" to "client",
                        ).forEach { (k, v) -> addQueryParameter(k, v) }
                    }.build()
            var reply = request(authUrl, operationName = "OAuth authorization")
            var current = authUrl
            repeat(5) {
                if (reply.code in 300..399) {
                    current =
                        current.resolve(reply.location ?: "")
                            ?: throw BlinkException(
                                BlinkException.Kind.PROTOCOL,
                                "Invalid Blink redirect",
                                context = lastHttpContext,
                            )
                    if (
                        current.scheme != endpoints.oauth.scheme ||
                        current.host != endpoints.oauth.host ||
                        current.port != endpoints.oauth.port
                    ) {
                        throw BlinkException(
                            BlinkException.Kind.PROTOCOL,
                            "Unexpected Blink redirect",
                            context = lastHttpContext,
                        )
                    }
                    reply = request(current, operationName = "OAuth redirect")
                }
            }
            if (reply.code != 200) fail(reply.code, "OAuth authorization", "GET", current)
            val page = request(oauth("oauth/v2/signin"), operationName = "Sign-in page")
            if (page.code != 200) fail(page.code, "Sign-in page", "GET", oauth("oauth/v2/signin"))
            csrf = BlinkProtocol.csrf(page.body)
            val signinUrl = oauth("oauth/v2/signin")
            reply =
                request(
                    signinUrl,
                    form("username" to email.trim(), "password" to password, "csrf-token" to csrf),
                    operationName = "Credential submission",
                )
            if (BlinkProtocol.twoFactor(reply.code, reply.body)) {
                return@withDiagnostics LoginResult.TWO_FACTOR_REQUIRED
            }
            if (reply.code !in 300..399) fail(reply.code, "Credential submission", "POST", signinUrl)
            finishLogin()
            LoginResult.CONNECTED
        }

    override suspend fun verifyCode(code: String) =
        withDiagnostics {
            if (csrf.isBlank() || verifier.isBlank() || now() - authStarted > 600_000) {
                throw BlinkException(
                    BlinkException.Kind.AUTH,
                    "Verification expired: start signing in again",
                    context = BlinkErrorContext("2FA code verification", "POST", oauth("oauth/v2/2fa/verify").toString()),
                )
            }
            require(code.matches(Regex("[0-9]{4,10}"))) { "Invalid verification code" }
            val verifyUrl = oauth("oauth/v2/2fa/verify")
            val reply =
                request(
                    verifyUrl,
                    form("2fa_code" to code, "csrf-token" to csrf, "remember_me" to "false"),
                    operationName = "2FA code verification",
                )
            val j = accepted(reply, "2FA code verification", "POST", verifyUrl)
            if (reply.code != 201 || j.optString("status") != "auth-completed") {
                throw BlinkException(
                    BlinkException.Kind.AUTH,
                    "Blink rejected the code",
                    context = BlinkErrorContext("2FA code verification", "POST", verifyUrl.toString(), reply.code),
                )
            }
            finishLogin()
        }

    private suspend fun finishLogin() {
        val authUrl = oauth("oauth/v2/authorize")
        val reply = request(authUrl, operationName = "OAuth sign-in completion")
        if (reply.code !in 300..399) fail(reply.code, "OAuth sign-in completion", "GET", authUrl)
        val location =
            reply.location
                ?: throw BlinkException(
                    BlinkException.Kind.PROTOCOL,
                    "Missing OAuth code",
                    context = BlinkErrorContext("OAuth sign-in completion", "GET", authUrl.toString(), reply.code),
                )
        // Parse the callback query without invoking its custom URL scheme.
        val code =
            ("https://callback.invalid/?" + location.substringAfter('?', ""))
                .toHttpUrl()
                .queryParameter("code")
                ?.takeIf { it.isNotBlank() }
                ?: throw BlinkException(
                    BlinkException.Kind.PROTOCOL,
                    "Missing Blink OAuth code",
                    context = BlinkErrorContext("OAuth sign-in completion", "GET", authUrl.toString(), reply.code),
                )
        val tokenUrl = oauth("oauth/token")
        saveTokens(
            accepted(
                request(
                    tokenUrl,
                    form(
                        "app_brand" to "blink",
                        "client_id" to "ios",
                        "code" to code,
                        "code_verifier" to verifier,
                        "grant_type" to "authorization_code",
                        "hardware_id" to hardware(),
                        "redirect_uri" to redirect,
                        "scope" to "client",
                    ),
                    token = true,
                    operationName = "OAuth token exchange",
                ),
                "OAuth token exchange",
                "POST",
                tokenUrl,
            ),
        )
        val tierUrl = endpoints.tier.resolve("api/v1/users/tier_info")!!
        val tier =
            accepted(
                request(
                    tierUrl,
                    bearer = session.getString("access_token"),
                    operationName = "Region lookup",
                ),
                "Region lookup",
                "GET",
                tierUrl,
            )
        val region = tier.optString("tier")
        if (!region.matches(Regex("[a-zA-Z0-9-]+"))) {
            throw BlinkException(
                BlinkException.Kind.PROTOCOL,
                "Invalid Blink region",
                context = BlinkErrorContext("Region lookup", "GET", tierUrl.toString(), lastHttpContext?.httpStatus),
            )
        }
        session.put("region", region).put("account", BlinkProtocol.id(tier.opt("account_id")))
        vault.write(session.toString())
        mutableConnected.value = true
        clearPending()
    }

    private suspend fun saveTokens(j: JSONObject) {
        val token = j.optString("access_token")
        val refresh = j.optString("refresh_token", session.optString("refresh_token"))
        if (token.isBlank() || refresh.isBlank()) {
            throw BlinkException(BlinkException.Kind.PROTOCOL, "Missing Blink tokens")
        }
        session
            .put("access_token", token)
            .put("refresh_token", refresh)
            .put("expiry", now() + j.optLong("expires_in", 3600).coerceIn(60, 604800) * 1000)
        vault.write(session.toString())
    }

    private suspend fun refresh() {
        try {
            val tokenUrl = oauth("oauth/token")
            saveTokens(
                accepted(
                    request(
                        tokenUrl,
                        form(
                            "grant_type" to "refresh_token",
                            "refresh_token" to session.getString("refresh_token"),
                            "client_id" to "ios",
                            "scope" to "client",
                            "hardware_id" to hardware(),
                        ),
                        token = true,
                        operationName = "Token refresh",
                    ),
                    "Token refresh",
                    "POST",
                    tokenUrl,
                ),
            )
        } catch (e: BlinkException) {
            if (e.kind == BlinkException.Kind.AUTH) {
                mutableConnected.value = false
                mutableStatus.value = BlinkStatus.UNKNOWN
            }
            throw e
        }
    }

    private suspend fun authorized(
        path: String,
        post: Boolean = false,
        operationName: String = "",
    ): JSONObject {
        if (!mutableConnected.value) {
            throw BlinkException(BlinkException.Kind.AUTH, "Sign in to your Blink account")
        }
        if (session.optLong("expiry") < now() + 60_000) refresh()
        val url = endpoints.region(session.getString("region")).resolve(path)!!
        val body = if (post) "{}".toRequestBody("application/json".toMediaType()) else null
        val method = if (post) "POST" else "GET"
        var reply = request(url, body, session.getString("access_token"), operationName = operationName)
        // An explicit 401 means the command was not authorized. Never retry an ambiguous network
        // POST.
        if (reply.code == 401) {
            refresh()
            reply = request(url, body, session.getString("access_token"), operationName = operationName)
        }
        return accepted(reply, operationName.ifBlank { "Blink request" }, method, url)
    }

    private suspend fun systemsUnlocked(): List<BlinkSystem> {
        if (!mutableConnected.value) {
            throw BlinkException(BlinkException.Kind.AUTH, "Sign in to your Blink account")
        }
        return BlinkProtocol.systems(
            authorized(
                "api/v3/accounts/${session.getString("account")}/homescreen",
                operationName = "Systems list",
            ),
        )
    }

    override suspend fun listSystems(): List<BlinkSystem> = withDiagnostics { systemsUnlocked() }

    private suspend fun statusUnlocked(id: String): BlinkStatus {
        if (id.isBlank()) {
            throw BlinkException(BlinkException.Kind.PROTOCOL, "Select a Blink system")
        }
        return systemsUnlocked().firstOrNull { it.id == id }?.status
            ?: throw BlinkException(
                BlinkException.Kind.PROTOCOL,
                "The selected Blink system no longer exists",
            )
    }

    override suspend fun getStatus(systemId: String?): BlinkStatus =
        withDiagnostics {
            try {
                val id = systemId ?: selectedSystem()
                statusUnlocked(id).also { if (id == selectedSystem()) mutableStatus.value = it }
            } catch (e: Exception) {
                mutableStatus.value = BlinkStatus.UNKNOWN
                throw e
            }
        }

    override suspend fun arm(systemId: String?) {
        change(BlinkStatus.ARMED, systemId)
    }

    override suspend fun disarm(systemId: String?) {
        change(BlinkStatus.DISARMED, systemId)
    }

    private suspend fun change(
        desired: BlinkStatus,
        systemId: String?,
    ) = withDiagnostics {
        val id = BlinkProtocol.id(systemId ?: selectedSystem())
        if (id != selectedSystem()) {
            throw BlinkException(
                BlinkException.Kind.PROTOCOL,
                "Blink selection changed: command canceled",
            )
        }
        val actionWord = if (desired == BlinkStatus.ARMED) "arm" else "disarm"
        try {
            authorized(
                "api/v1/accounts/${session.getString(
                    "account",
                )}/networks/$id/state/${if (desired == BlinkStatus.ARMED) "arm" else "disarm"}",
                true,
                operationName = "$actionWord command",
            )
            // Cloud command acceptance is not confirmation. Bound the convergence check to 10
            // seconds.
            repeat(5) {
                delay(2_000)
                val actual = statusUnlocked(id)
                if (actual == desired) {
                    if (id == selectedSystem()) mutableStatus.value = actual
                    return@withDiagnostics
                }
            }
            mutableStatus.value = BlinkStatus.UNKNOWN
            throw BlinkException(
                BlinkException.Kind.REMOTE,
                "Command sent, but Blink state unconfirmed: refresh the status",
                context = lastHttpContext,
            )
        } catch (e: Exception) {
            mutableStatus.value = BlinkStatus.UNKNOWN
            throw e
        }
    }

    private fun clearPending() {
        csrf = ""
        verifier = ""
        synchronized(cookies) { cookies.clear() }
    }

    override suspend fun logout() =
        withDiagnostics {
            vault.clear()
            session = JSONObject()
            mutableConnected.value = false
            mutableStatus.value = BlinkStatus.UNKNOWN
            clearPending()
        }
}
