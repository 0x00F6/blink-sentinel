package dev.homesentinel

import dev.homesentinel.domain.model.*
import dev.homesentinel.domain.usecase.PresenceMachine
import org.junit.Assert.*
import org.junit.Test

class PresenceMachineTest {
    private val settings = Settings(enabled = true, homeSsid = "Home", delaySeconds = 30)

    private fun evidence(t: Long, present: Boolean) = WifiEvidence(t + 1, t, present)

    @Test
    fun presentWifiEntersHomeAndRequestsDisarm() {
        val m = PresenceMachine()
        assertEquals(BlinkStatus.DISARMED, m.observe(evidence(0, true), settings, 0))
        assertEquals(Presence.HOME, m.state)
    }

    @Test
    fun absentWifiStartsDelay() {
        val m = PresenceMachine()
        assertNull(m.observe(evidence(0, false), settings, 0))
        assertEquals(Presence.AWAY_PENDING, m.state)
        assertEquals(30_000L, m.deadline)
    }

    @Test
    fun firstAbsentScanNeverArmsEvenAfterLongGap() {
        val m = PresenceMachine()
        assertNull(m.observe(evidence(90_000, false), settings, 90_000))
        assertEquals(Presence.AWAY_PENDING, m.state)
    }

    @Test
    fun earlyConfirmationDoesNotArm() {
        val m = PresenceMachine()
        m.observe(evidence(0, false), settings, 0)
        assertNull(m.observe(evidence(29_999, false), settings, 29_999))
        assertEquals(Presence.AWAY_PENDING, m.state)
    }

    @Test
    fun newAbsenceAtDeadlineArms() {
        val m = PresenceMachine()
        m.observe(evidence(0, false), settings, 0)
        assertEquals(BlinkStatus.ARMED, m.observe(evidence(30_000, false), settings, 30_000))
        assertEquals(Presence.AWAY, m.state)
    }

    @Test
    fun rapidReturnCancelsPendingArm() {
        val m = PresenceMachine()
        m.observe(evidence(0, true), settings, 0)
        m.observe(evidence(1_000, false), settings, 1_000)
        m.observe(evidence(15_000, true), settings, 15_000)
        assertEquals(Presence.HOME, m.state)
        assertNull(m.deadline)
    }

    @Test
    fun repeatedAbsenceDoesNotIssueDuplicateCommand() {
        val m = PresenceMachine()
        m.observe(evidence(0, false), settings, 0)
        m.observe(evidence(30_000, false), settings, 30_000)
        assertNull(m.observe(evidence(40_000, false), settings, 40_000))
    }

    @Test
    fun repeatedPresenceDoesNotIssueDuplicateCommand() {
        val m = PresenceMachine()
        m.observe(evidence(0, true), settings, 0)
        assertNull(m.observe(evidence(1_000, true), settings, 1_000))
    }

    @Test
    fun automaticDisarmCanBeDisabled() {
        val m = PresenceMachine()
        assertNull(m.observe(evidence(0, true), settings.copy(autoDisarm = false), 0))
        assertEquals(Presence.HOME, m.state)
    }

    @Test
    fun duplicateBatchCannotConfirmAbsence() {
        val m = PresenceMachine()
        m.observe(WifiEvidence(1, 0, false), settings, 0)
        assertNull(m.observe(WifiEvidence(1, 30_000, false), settings, 30_000))
        assertEquals(Presence.AWAY_PENDING, m.state)
    }

    @Test
    fun staleOrFutureEvidenceCannotChangeState() {
        val m = PresenceMachine()
        assertNull(m.observe(evidence(0, false), settings, 60_001))
        assertNull(m.observe(evidence(20_000, false), settings, 10_000))
        assertEquals(Presence.UNKNOWN, m.state)
    }

    @Test
    fun expirationReturnsUnknown() {
        val m = PresenceMachine()
        m.observe(evidence(0, true), settings, 0)
        assertFalse(m.expire(PresenceMachine.STALE_AFTER_MS - 1))
        assertTrue(m.expire(PresenceMachine.STALE_AFTER_MS))
        assertEquals(Presence.UNKNOWN, m.state)
    }

    @Test
    fun disabledAutomationStaysUnknown() {
        val m = PresenceMachine()
        assertNull(m.observe(evidence(0, false), settings.copy(enabled = false), 0))
        assertEquals(Presence.UNKNOWN, m.state)
    }

    @Test
    fun restartDoesNotRestorePreviousAwayDecision() {
        val before = PresenceMachine()
        before.observe(evidence(0, false), settings, 0)
        before.observe(evidence(30_000, false), settings, 30_000)
        assertEquals(Presence.AWAY, before.state)
        val after = PresenceMachine()
        assertEquals(Presence.UNKNOWN, after.state)
        assertNull(after.observe(evidence(40_000, false), settings, 40_000))
        assertEquals(Presence.AWAY_PENDING, after.state)
    }

    @Test
    fun missingSsidCannotTriggerAnAction() {
        val m = PresenceMachine()
        assertNull(m.observe(evidence(0, true), settings.copy(homeSsid = ""), 0))
        assertEquals(Presence.UNKNOWN, m.state)
    }
}
