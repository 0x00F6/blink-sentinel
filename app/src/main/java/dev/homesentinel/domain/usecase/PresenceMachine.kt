package dev.homesentinel.domain.usecase

import dev.homesentinel.domain.model.BlinkStatus
import dev.homesentinel.domain.model.MonitoringMode
import dev.homesentinel.domain.model.Presence
import dev.homesentinel.domain.model.Settings
import dev.homesentinel.domain.model.WifiEvidence

/**
 * Only a second, fresh, successful scan after the deadline may confirm AWAY. A timer, a lost
 * connection, stale data, and a failed scan are never proof of absence. Instances are scoped to one
 * service lifetime: restart deliberately returns UNKNOWN.
 */
class PresenceMachine {
    var state: Presence = Presence.UNKNOWN
        private set

    var deadline: Long? = null
        private set

    private var pendingAt: Long? = null
    private var lastBatch: Long = -1
    var lastObservation: Long? = null
        private set

    fun reset() {
        state = Presence.UNKNOWN
        deadline = null
        pendingAt = null
        lastBatch = -1
        lastObservation = null
    }

    fun observe(
        evidence: WifiEvidence,
        settings: Settings,
        now: Long,
    ): BlinkStatus? {
        if (!settings.enabled) {
            reset()
            return null
        }
        if (settings.mode == MonitoringMode.WIFI && settings.homeSsid.isBlank()) {
            reset()
            return null
        }
        if (settings.mode == MonitoringMode.BLUETOOTH && settings.bluetoothDeviceAddress.isBlank()) {
            reset()
            return null
        }
        if (evidence.observedAt > now || now - evidence.observedAt > MAX_AGE_MS) return null
        if (evidence.batchId <= lastBatch) return null
        lastBatch = evidence.batchId
        lastObservation = evidence.observedAt
        if (evidence.present) {
            val changed = state != Presence.HOME
            state = Presence.HOME
            deadline = null
            pendingAt = null
            return if (changed && settings.autoDisarm) BlinkStatus.DISARMED else null
        }
        return when (state) {
            Presence.UNKNOWN,
            Presence.HOME,
            -> {
                state = Presence.AWAY_PENDING
                pendingAt = evidence.observedAt
                deadline = evidence.observedAt + settings.delaySeconds * 1_000L
                null
            }
            Presence.AWAY_PENDING -> {
                if (evidence.observedAt >= deadline!! && evidence.observedAt > pendingAt!!) {
                    state = Presence.AWAY
                    deadline = null
                    pendingAt = null
                    BlinkStatus.ARMED
                } else {
                    null
                }
            }
            Presence.AWAY -> null
        }
    }

    fun expire(now: Long): Boolean {
        val last = lastObservation ?: return false
        if (now - last >= STALE_AFTER_MS) {
            reset()
            return true
        }
        return false
    }

    companion object {
        const val MAX_AGE_MS = 60_000L
        const val STALE_AFTER_MS = 300_000L
    }
}
