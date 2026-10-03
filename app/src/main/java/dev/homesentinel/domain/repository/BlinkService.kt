package dev.homesentinel.domain.repository

import dev.homesentinel.domain.model.BlinkStatus
import dev.homesentinel.domain.model.BlinkSystem
import dev.homesentinel.domain.model.LoginResult
import kotlinx.coroutines.flow.StateFlow

interface BlinkService {
    val connected: StateFlow<Boolean>
    val status: StateFlow<BlinkStatus>

    suspend fun login(
        email: String,
        password: String,
    ): LoginResult

    suspend fun verifyCode(code: String)

    suspend fun logout()

    suspend fun listSystems(): List<BlinkSystem>

    suspend fun getStatus(systemId: String? = null): BlinkStatus

    suspend fun arm(systemId: String? = null)

    suspend fun disarm(systemId: String? = null)
}
