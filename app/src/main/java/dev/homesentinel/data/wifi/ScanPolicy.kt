package dev.homesentinel.data.wifi

import dev.homesentinel.domain.model.WifiEvidence
import dev.homesentinel.domain.model.WifiNetwork
import dev.homesentinel.domain.usecase.PresenceMachine
import dev.homesentinel.domain.usecase.SignalDistanceEstimator

data class ScanAccessPoint(
    val ssid: String,
    val timestampMs: Long,
    val level: Int,
)

object ScanPolicy {
    fun freshNetworks(
        results: List<ScanAccessPoint>,
        now: Long,
    ): List<WifiNetwork> = networks(results.filter { it.timestampMs in (now - PresenceMachine.MAX_AGE_MS)..now })

    /** Individual timestamps matter: Android may return a mix of old and new results. */
    fun evidence(
        results: List<ScanAccessPoint>,
        home: String,
        now: Long,
    ): WifiEvidence? {
        if (results.isEmpty()) return WifiEvidence(now, now, false)
        val fresh = results.filter { it.timestampMs in (now - PresenceMachine.MAX_AGE_MS)..now }
        if (fresh.isEmpty()) return null
        val batch = fresh.maxOf { it.timestampMs }
        val signalAt = fresh.filter { it.ssid == home }.maxOfOrNull { it.timestampMs }
        return WifiEvidence(batch, batch, signalAt != null, signalAt)
    }

    fun networks(results: List<ScanAccessPoint>): List<WifiNetwork> =
        results
            .filter { it.ssid.isNotBlank() }
            .groupBy { it.ssid }
            .map { (ssid, points) ->
                val strongestRssi =
                    points
                        .filter { SignalDistanceEstimator.wifiMeters(it.level) != null }
                        .maxOfOrNull { it.level } ?: points.maxOf { it.level }
                WifiNetwork(ssid, strongestRssi, points.size, SignalDistanceEstimator.wifiMeters(strongestRssi))
            }.sortedWith(
                compareBy<WifiNetwork> { it.estimatedDistanceMeters ?: Double.POSITIVE_INFINITY }
                    .thenBy { it.ssid },
            )
}
