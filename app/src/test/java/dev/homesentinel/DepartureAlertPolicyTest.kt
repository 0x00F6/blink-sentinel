package dev.homesentinel

import dev.homesentinel.domain.model.MonitorStatus
import dev.homesentinel.domain.model.Presence
import dev.homesentinel.domain.usecase.DepartureAlertPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepartureAlertPolicyTest {
    private fun status(
        presence: Presence,
        running: Boolean = true,
    ) = MonitorStatus(running = running, presence = presence)

    @Test fun confirmedDepartureAlertsOnceAfterHome() {
        val policy = DepartureAlertPolicy()
        assertFalse(policy.onStatus(status(Presence.HOME)))
        assertFalse(policy.onStatus(status(Presence.AWAY_PENDING)))
        assertTrue(policy.onStatus(status(Presence.AWAY)))
        repeat(10) { assertFalse(policy.onStatus(status(Presence.AWAY))) }
    }

    @Test fun temporaryLossAndRapidReturnAreSilent() {
        val policy = DepartureAlertPolicy()
        policy.onStatus(status(Presence.HOME))
        assertFalse(policy.onStatus(status(Presence.AWAY_PENDING)))
        assertFalse(policy.onStatus(status(Presence.HOME)))
    }

    @Test fun startingWhileAlreadyAwayDoesNotAlert() {
        val policy = DepartureAlertPolicy()
        assertFalse(policy.onStatus(status(Presence.AWAY_PENDING)))
        assertFalse(policy.onStatus(status(Presence.AWAY)))
    }

    @Test fun disabledSensorOrExpiredEvidenceDoNotBecomeDepartureAlerts() {
        val policy = DepartureAlertPolicy()
        policy.onStatus(status(Presence.HOME))
        assertFalse(policy.onStatus(status(Presence.UNKNOWN)))
        assertFalse(policy.onStatus(status(Presence.AWAY_PENDING)))
        assertFalse(policy.onStatus(status(Presence.AWAY)))
    }

    @Test fun stoppingAndRestartingDoNotReplayAnAlert() {
        val policy = DepartureAlertPolicy()
        policy.onStatus(status(Presence.HOME))
        assertFalse(policy.onStatus(status(Presence.UNKNOWN, running = false)))
        assertFalse(policy.onStatus(status(Presence.AWAY)))
        assertFalse(DepartureAlertPolicy().onStatus(status(Presence.AWAY)))
    }

    @Test fun returningHomeAllowsAnAlertForTheNextConfirmedDeparture() {
        val policy = DepartureAlertPolicy()
        policy.onStatus(status(Presence.HOME))
        assertTrue(policy.onStatus(status(Presence.AWAY)))
        assertFalse(policy.onStatus(status(Presence.HOME)))
        assertFalse(policy.onStatus(status(Presence.AWAY_PENDING)))
        assertTrue(policy.onStatus(status(Presence.AWAY)))
    }
}
