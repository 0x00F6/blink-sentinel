package dev.homesentinel.domain.usecase

import dev.homesentinel.domain.model.BlinkException
import dev.homesentinel.domain.model.BlinkStatus
import dev.homesentinel.domain.repository.BlinkService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes manual and automatic actions and checks current state before a POST. */
class EnsureBlinkState(
    private val blink: BlinkService,
    private val selectedSystem: (suspend () -> String)? = null,
) {
    private val mutex = Mutex()

    suspend operator fun invoke(
        desired: BlinkStatus,
        stillWanted: suspend () -> Boolean = { true },
    ): Boolean =
        mutex.withLock {
            require(desired != BlinkStatus.UNKNOWN)
            // Capture the ID once: a mutable selection must never retarget an in-flight action.
            val target = selectedSystem?.invoke()
            val current = blink.getStatus(target)
            if (target != null && selectedSystem?.invoke() != target) return@withLock false
            if (!stillWanted()) return@withLock false
            if (current == desired) return@withLock false
            // UNKNOWN from an unreadable server response is not permission to change it.
            if (current == BlinkStatus.UNKNOWN) {
                throw BlinkException(
                    BlinkException.Kind.PROTOCOL,
                    "Blink state unknown: action deferred",
                )
            }
            if (desired == BlinkStatus.ARMED) blink.arm(target) else blink.disarm(target)
            true
        }
}
