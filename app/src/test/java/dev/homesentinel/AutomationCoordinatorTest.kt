package dev.homesentinel

import dev.homesentinel.domain.model.*
import dev.homesentinel.domain.usecase.EnsureBlinkState
import dev.homesentinel.service.BlinkAutomationService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationCoordinatorTest {
    private val config = Settings(enabled = true, homeSsid = "Home", systemId = "1")

    private class Fixture(
        val test: TestScope,
        val blink: FakeBlink = FakeBlink(),
        checkSettings: suspend (Settings) -> Boolean = { true },
    ) {
        val scanner = FakeScanner()
        var monitor = MonitorStatus()
        val logs = mutableListOf<String>()
        val logTypes = mutableListOf<LogType>()
        val errors = mutableListOf<Throwable>()
        val coordinator =
            BlinkAutomationService(
                test.backgroundScope,
                scanner,
                EnsureBlinkState(blink),
                { test.testScheduler.currentTime },
                { monitor = it },
                { message, type -> logs.add(message); logTypes.add(type) },
                settingsStillCurrent = checkSettings,
                logError = { _, error -> errors.add(error) },
                wallNow = { 1_700_000_000_000L + test.testScheduler.currentTime },
            )

        fun evidence(present: Boolean) {
            val t = test.testScheduler.currentTime
            coordinator.evidence(WifiEvidence(t + 1, t, present))
        }
    }

    @Test
    fun logTypesDescribeActualTransitionsWithoutDuplicateOrTimerBasedAbsence() = runTest {
        val f = Fixture(this)
        f.coordinator.configure(config.copy(autoDisarm = false))
        f.evidence(true)
        f.evidence(true)
        advanceTimeBy(1)
        f.evidence(false)
        assertEquals(listOf(LogType.SIGNAL_DETECTED, LogType.SIGNAL_LOST), f.logTypes)
        advanceTimeBy(30_001)
        runCurrent()
        assertEquals(LogType.WAITING_CONFIRMATION, f.logTypes.last())
        assertFalse(f.logTypes.contains(LogType.ABSENCE_CONFIRMED))
        f.evidence(false)
        runCurrent()
        assertEquals(listOf(LogType.ABSENCE_CONFIRMED, LogType.BLINK_ARMED), f.logTypes.takeLast(2))
        f.coordinator.invalid("Sensor unavailable")
        assertEquals(LogType.SIGNAL_UNKNOWN, f.logTypes.last())
        f.coordinator.close()
    }

    @Test
    fun changingLogRetentionDoesNotResetPendingDeparture() = runTest {
        val f = Fixture(this)
        f.coordinator.configure(config)
        f.evidence(false)
        advanceTimeBy(15_000)
        f.coordinator.configure(config.copy(logRetention = 1_000))
        assertEquals(Presence.AWAY_PENDING, f.monitor.presence)
        advanceTimeBy(15_001)
        f.evidence(false)
        runCurrent()
        assertEquals(1, f.blink.arms)
        f.coordinator.close()
    }

    @Test
    fun lastSignalUsesThePeripheralTimestampRatherThanTheBatchTimestamp() = runTest {
        val f = Fixture(this)
        f.coordinator.configure(config)
        advanceTimeBy(10_000)
        f.coordinator.evidence(WifiEvidence(10_000, 10_000, true, signalAt = 9_000))
        assertEquals(1_700_000_009_000L, f.monitor.lastSignalAtEpochMillis)
        assertEquals(config.signalSourceKey, f.monitor.lastSignalSource)
        f.coordinator.close()
    }

    @Test
    fun absenceUnknownAndRepeatedCachedSignalsDoNotChangeTheLastSignalDate() = runTest {
        val f = Fixture(this)
        f.coordinator.configure(config)
        advanceTimeBy(1_000)
        f.evidence(true)
        val timestamp = f.monitor.lastSignalAtEpochMillis
        advanceTimeBy(1_000)
        f.evidence(false)
        assertEquals(timestamp, f.monitor.lastSignalAtEpochMillis)
        advanceTimeBy(1_000)
        f.coordinator.evidence(WifiEvidence(3_001, 3_000, true, signalAt = 1_000))
        assertEquals(timestamp, f.monitor.lastSignalAtEpochMillis)
        f.coordinator.invalid("Sensor unavailable")
        assertEquals(timestamp, f.monitor.lastSignalAtEpochMillis)
        f.coordinator.close()
    }

    @Test
    fun newTargetAndRestartClearTheSignalDateButBlinkSelectionDoesNot() = runTest {
        val f = Fixture(this)
        f.coordinator.configure(config)
        f.evidence(true)
        val timestamp = f.monitor.lastSignalAtEpochMillis
        assertNotNull(timestamp)
        f.coordinator.configure(config.copy(systemId = "2"))
        assertEquals(timestamp, f.monitor.lastSignalAtEpochMillis)
        val bluetooth = config.copy(mode = MonitoringMode.BLUETOOTH, bluetoothDeviceAddress = "AA:BB:CC:DD:EE:FF")
        f.coordinator.configure(bluetooth)
        assertNull(f.monitor.lastSignalAtEpochMillis)
        val restarted = Fixture(this)
        restarted.coordinator.configure(config)
        assertNull(restarted.monitor.lastSignalAtEpochMillis)
        restarted.coordinator.close()
        f.coordinator.close()
    }

    @Test
    fun automaticErrorsRetainTheirOriginalThrowableForDiagnostics() = runTest {
        val blink = FakeBlink().apply {
            error = BlinkException(BlinkException.Kind.NETWORK, "Network error")
            failuresLeft = 1
        }
        val f = Fixture(this, blink)
        f.coordinator.configure(config)
        f.evidence(false)
        advanceTimeBy(30_001)
        f.evidence(false)
        runCurrent()
        assertEquals(1, f.errors.size)
        assertSame(blink.error, f.errors.single())
        f.coordinator.close()
    }

    @Test
    fun bluetoothTargetOrModeChangeCancelsDepartureAndResetsEvidence() = runTest {
        val f = Fixture(this)
        val bluetooth = config.copy(mode = MonitoringMode.BLUETOOTH, bluetoothDeviceAddress = "AA:BB:CC:DD:EE:FF")
        f.coordinator.configure(bluetooth)
        f.evidence(false)
        assertEquals(Presence.AWAY_PENDING, f.monitor.presence)
        f.coordinator.configure(bluetooth.copy(bluetoothDeviceAddress = "11:22:33:44:55:66"))
        assertEquals(Presence.UNKNOWN, f.monitor.presence)
        advanceTimeBy(40_000)
        runCurrent()
        assertEquals(0, f.blink.arms)
        f.evidence(true)
        f.coordinator.configure(config)
        assertEquals(Presence.UNKNOWN, f.monitor.presence)
        f.coordinator.close()
    }

    @Test
    fun changedPersistedSettingsCancelCommandBeforeFlowDelivery() = runTest {
        var unchanged = true
        val blink = FakeBlink(BlinkStatus.ARMED).apply { onGet = { unchanged = false } }
        val f = Fixture(this, blink) { unchanged }
        f.coordinator.configure(config)
        f.evidence(true)
        runCurrent()
        assertEquals(0, blink.disarms)
        assertFalse(f.logs.any { it.startsWith("Blink already") })
        f.coordinator.close()
    }

    @Test
    fun deadlineRequestsOneScanWithoutArmingFromATimer() = runTest {
        val f = Fixture(this)
        f.coordinator.configure(config)
        f.evidence(false)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(0, f.blink.arms)
        assertEquals(Presence.AWAY_PENDING, f.monitor.presence)
        assertEquals(2, f.scanner.requests)
        f.coordinator.close()
    }

    @Test
    fun newScanAfterDeadlineArmsExactlyOnce() = runTest {
        val f = Fixture(this)
        f.coordinator.configure(config)
        f.evidence(false)
        advanceTimeBy(30_001)
        f.evidence(false)
        runCurrent()
        assertEquals(1, f.blink.arms)
        advanceTimeBy(1_000)
        f.evidence(false)
        runCurrent()
        assertEquals(1, f.blink.arms)
        f.coordinator.close()
    }

    @Test
    fun rapidReturnCancelsTheDepartureTimer() = runTest {
        val f = Fixture(this)
        f.coordinator.configure(config)
        f.evidence(false)
        advanceTimeBy(10_000)
        f.evidence(true)
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(0, f.blink.arms)
        assertEquals(Presence.HOME, f.monitor.presence)
        f.coordinator.close()
    }

    @Test
    fun returnHomeDisarmsAnArmedSystem() = runTest {
        val f = Fixture(this, FakeBlink(BlinkStatus.ARMED))
        f.coordinator.configure(config)
        f.evidence(true)
        runCurrent()
        assertEquals(1, f.blink.disarms)
        f.coordinator.close()
    }

    @Test
    fun failedScanNeverConfirmsDeparture() = runTest {
        val f = Fixture(this)
        f.coordinator.configure(config)
        f.evidence(false)
        advanceTimeBy(31_000)
        f.coordinator.scanFailed()
        runCurrent()
        assertEquals(0, f.blink.arms)
        assertEquals(Presence.AWAY_PENDING, f.monitor.presence)
        f.coordinator.close()
    }

    @Test
    fun disabledWifiInvalidatesEvidenceInsteadOfArming() = runTest {
        val f = Fixture(this)
        f.coordinator.configure(config)
        f.evidence(false)
        f.coordinator.invalid("Wi-Fi disabled")
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(Presence.UNKNOWN, f.monitor.presence)
        assertEquals(0, f.blink.arms)
        f.coordinator.close()
    }

    @Test
    fun networkRetrySucceedsWhileEvidenceIsFresh() = runTest {
        val f = Fixture(this, FakeBlink(BlinkStatus.ARMED).apply { failuresLeft = 1 })
        f.coordinator.configure(config)
        f.evidence(true)
        runCurrent()
        assertEquals(0, f.blink.disarms)
        advanceTimeBy(15_000)
        runCurrent()
        assertEquals(1, f.blink.disarms)
        f.coordinator.close()
    }

    @Test
    fun returnCancelsAnOutstandingArmRetry() = runTest {
        val f = Fixture(this, FakeBlink().apply { failuresLeft = 1 })
        f.coordinator.configure(config)
        f.evidence(false)
        advanceTimeBy(30_000)
        f.evidence(false)
        runCurrent()
        advanceTimeBy(1_000)
        f.evidence(true)
        runCurrent()
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(0, f.blink.arms)
        f.coordinator.close()
    }

    @Test
    fun disablingAutomationCancelsOutstandingActions() = runTest {
        val f = Fixture(this)
        f.coordinator.configure(config)
        f.evidence(false)
        f.coordinator.configure(config.copy(enabled = false))
        advanceTimeBy(40_000)
        f.evidence(false)
        runCurrent()
        assertEquals(0, f.blink.arms)
        assertEquals(Presence.UNKNOWN, f.monitor.presence)
        f.coordinator.close()
    }

    @Test
    fun restartRequiresNewEvidenceBeforeAnyBlinkCall() = runTest {
        val f = Fixture(this)
        f.coordinator.configure(config)
        runCurrent()
        assertEquals(Presence.UNKNOWN, f.monitor.presence)
        assertEquals(0, f.blink.gets)
        f.coordinator.close()
    }
}
