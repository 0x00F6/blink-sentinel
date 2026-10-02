package dev.homesentinel

import dev.homesentinel.data.blink.SessionVault
import dev.homesentinel.domain.model.*
import dev.homesentinel.domain.repository.*
import kotlinx.coroutines.flow.MutableStateFlow

class MemoryVault(var text: String? = null) : SessionVault {
    override suspend fun read() = text

    override suspend fun write(value: String) {
        text = value
    }

    override suspend fun clear() {
        text = null
    }
}

class FakeBlink(initial: BlinkStatus = BlinkStatus.DISARMED) : BlinkService {
    override val connected = MutableStateFlow(true)
    override val status = MutableStateFlow(initial)
    var gets = 0
    var arms = 0
    var disarms = 0
    var error: BlinkException? = null
    var failuresLeft = 0
    var onGet: (() -> Unit)? = null
    var lastGetSystem: String? = null
    var lastCommandSystem: String? = null

    override suspend fun login(email: String, password: String) = LoginResult.CONNECTED

    override suspend fun verifyCode(code: String) = Unit

    override suspend fun logout() {
        connected.value = false
    }

    override suspend fun listSystems() = listOf(BlinkSystem("1", "Home", status.value))

    override suspend fun getStatus(systemId: String?): BlinkStatus {
        lastGetSystem = systemId
        gets++
        if (failuresLeft-- > 0)
            throw error ?: BlinkException(BlinkException.Kind.NETWORK, "Network unavailable")
        onGet?.invoke()
        return status.value
    }

    override suspend fun arm(systemId: String?) {
        lastCommandSystem = systemId
        arms++
        status.value = BlinkStatus.ARMED
    }

    override suspend fun disarm(systemId: String?) {
        lastCommandSystem = systemId
        disarms++
        status.value = BlinkStatus.DISARMED
    }
}

class FakeScanner : WifiScanner {
    override val networks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    var requests = 0
    var enabled = true

    override fun requestScan(): Boolean {
        requests++
        return enabled
    }

    override fun readSuccessfulScan(homeSsid: String): WifiEvidence? = null

    override fun refreshNetworks() = Unit

    override fun available() = enabled
}
