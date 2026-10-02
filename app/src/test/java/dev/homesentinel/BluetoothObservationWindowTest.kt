package dev.homesentinel

import dev.homesentinel.data.bluetooth.BluetoothObservationWindow
import dev.homesentinel.domain.model.*
import dev.homesentinel.domain.usecase.PresenceMachine
import org.junit.Assert.*
import org.junit.Test

class BluetoothObservationWindowTest {
    private val settings = Settings(enabled = true, mode = MonitoringMode.BLUETOOTH,
        bluetoothDeviceAddress = "AA:BB:CC:DD:EE:FF", delaySeconds = 30)

    @Test fun windowClosureDoesNotInventANewSignalTimestamp() {
        val window = BluetoothObservationWindow(0)
        window.advertisement(1_000, 1_000)
        val present = window.complete(10_000)!!
        assertEquals(10_000L, present.observedAt)
        assertEquals(1_000L, present.signalAt)
        assertNull(window.complete(20_000)!!.signalAt)
    }

    @Test fun initialSilenceIsUnknownUntilThePeripheralWasActuallyDetected() {
        val window = BluetoothObservationWindow(0)
        assertNull(window.complete(10_000))
        window.advertisement(11_000, 11_000)
        assertTrue(window.complete(20_000)!!.present)
        assertFalse(window.complete(30_000)!!.present)
    }

    @Test fun cachedAndFutureAdvertisementsDoNotEstablishPresence() {
        val window = BluetoothObservationWindow(100)
        window.advertisement(99, 200)
        window.advertisement(201, 200)
        assertNull(window.complete(10_100))
    }

    @Test fun duplicateAdvertisementsAreIgnoredEvenWhenDeliveredAgain() {
        val window = BluetoothObservationWindow(100)
        assertTrue(window.advertisement(200, 200))
        assertFalse(window.advertisement(200, 201))
        assertTrue(window.complete(10_100)!!.present)
        assertFalse(window.advertisement(200, 10_101))
        assertFalse(window.complete(20_100)!!.present)
    }

    @Test fun delayedWakeupInvalidatesTheWindowInsteadOfConfirmingDeparture() {
        val window = BluetoothObservationWindow(0)
        window.advertisement(1_000, 1_000)
        assertTrue(window.complete(10_000)!!.present)
        assertNull(window.complete(100_000))
        assertNull(window.complete(110_000))
    }

    @Test fun temporaryLossAndRapidReturnDoNotArm() {
        val window = BluetoothObservationWindow(0)
        val machine = PresenceMachine()
        window.advertisement(1_000, 1_000)
        machine.observe(window.complete(10_000)!!, settings, 10_000)
        machine.observe(window.complete(20_000)!!, settings, 20_000)
        assertEquals(Presence.AWAY_PENDING, machine.state)
        window.advertisement(21_000, 21_000)
        machine.observe(window.complete(30_000)!!, settings, 30_000)
        assertEquals(Presence.HOME, machine.state)
        assertNull(machine.deadline)
    }

    @Test fun absenceNeedsAnotherLiveWindowAfterTheDelayAndDuplicatesDoNotAdvance() {
        val window = BluetoothObservationWindow(0)
        val machine = PresenceMachine()
        window.advertisement(1_000, 1_000)
        machine.observe(window.complete(10_000)!!, settings, 10_000)
        val absent = window.complete(20_000)!!
        machine.observe(absent, settings, 20_000)
        machine.observe(absent, settings, 50_000)
        assertEquals(Presence.AWAY_PENDING, machine.state)
        assertNull(machine.observe(window.complete(30_000)!!, settings, 30_000))
        assertNull(machine.observe(window.complete(40_000)!!, settings, 40_000))
        assertEquals(BlinkStatus.ARMED, machine.observe(window.complete(50_000)!!, settings, 50_000))
        machine.reset()
        assertEquals(Presence.UNKNOWN, machine.state)
    }
}
