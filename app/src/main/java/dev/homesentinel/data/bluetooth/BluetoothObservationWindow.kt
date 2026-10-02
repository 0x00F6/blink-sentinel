package dev.homesentinel.data.bluetooth

import dev.homesentinel.domain.model.WifiEvidence

/** Only advertisements received during this live window count; paired devices never count. */
class BluetoothObservationWindow(private val startedAt: Long) {
    private var lastSeen = -1L
    private var lastWindow = startedAt
    private var detected = false

    fun advertisement(timestampMillis: Long, now: Long): Boolean {
        if (timestampMillis >= lastWindow && timestampMillis <= now && timestampMillis > lastSeen) {
            detected = true
            lastSeen = timestampMillis
            return true
        }
        return false
    }

    fun complete(now: Long): WifiEvidence? {
        if (now <= lastWindow) return null
        // Startup silence and long delivery gaps cannot distinguish absence from suspended scanning.
        // Require a real advertisement before allowing subsequent empty windows to confirm departure.
        if (now - lastWindow > 20_000 || !detected) {
            lastWindow = now
            detected = false
            lastSeen = -1
            return null
        }
        val present = lastSeen >= lastWindow
        val evidence = WifiEvidence(now, now, present, if (present) lastSeen else null)
        lastWindow = now
        return evidence
    }
}
