package dev.homesentinel.domain.usecase

import kotlin.math.pow

/** Indicative indoor log-distance model, without transmitter calibration or physical ranging. */
object SignalDistanceEstimator {
    private const val PATH_LOSS_EXPONENT = 2.5

    fun wifiMeters(rssiDbm: Int?): Double? = meters(rssiDbm, referenceAtOneMeterDbm = -40)

    fun bluetoothMeters(rssiDbm: Int?): Double? = meters(rssiDbm, referenceAtOneMeterDbm = -59)

    private fun meters(
        rssiDbm: Int?,
        referenceAtOneMeterDbm: Int,
    ): Double? {
        // Missing RSSI and Android's unknown/invalid sentinel values must not become distances.
        if (rssiDbm == null || rssiDbm !in -126..-1) return null
        return 10.0.pow((referenceAtOneMeterDbm - rssiDbm) / (10.0 * PATH_LOSS_EXPONENT))
    }
}
