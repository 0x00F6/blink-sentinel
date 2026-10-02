package dev.homesentinel

import dev.homesentinel.domain.model.MonitoringMode
import dev.homesentinel.domain.model.Settings
import dev.homesentinel.domain.usecase.ActivateMonitoring
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ActivateMonitoringTest {
    private class Fixture {
        var settings = Settings(homeSsid = "Old", systemId = "system")
        var connected = true
        var available = true
        var started: Settings? = null
        var stopped = false
        var startFailure: RuntimeException? = null
        val activate = ActivateMonitoring(
            snapshot = { settings },
            update = { settings = it(settings) },
            connected = { connected },
            sensorAvailable = { available },
            startService = {
                startFailure?.let { throw it }
                started = settings
            },
            stopService = { stopped = true },
        )
    }

    @Test
    fun wifiSelectionIsStoredAndEnabledBeforeServiceStarts() = runTest {
        val f = Fixture()
        f.activate { it.copy(homeSsid = "New") }
        assertEquals("New", f.started!!.homeSsid)
        assertTrue(f.started!!.enabled)
        assertFalse(f.stopped)
    }

    @Test
    fun bluetoothSelectionStartsWithTheChosenAddressAndMode() = runTest {
        val f = Fixture()
        f.activate { it.copy(mode = MonitoringMode.BLUETOOTH, bluetoothDeviceAddress = "AA:BB:CC:DD:EE:FF") }
        assertEquals(MonitoringMode.BLUETOOTH, f.started!!.mode)
        assertEquals("AA:BB:CC:DD:EE:FF", f.started!!.bluetoothDeviceAddress)
        assertTrue(f.settings.enabled)
    }

    @Test
    fun missingBlinkRetainsSelectionButStopsOldMonitoring() = runTest {
        val f = Fixture()
        f.settings = f.settings.copy(enabled = true)
        f.connected = false
        try {
            f.activate { it.copy(homeSsid = "New") }
            fail("Expected prerequisite failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Sign in to Blink"))
        }
        assertEquals("New", f.settings.homeSsid)
        assertFalse(f.settings.enabled)
        assertNull(f.started)
        assertTrue(f.stopped)
    }

    @Test
    fun unavailableSensorDoesNotEnableMonitoring() = runTest {
        val f = Fixture()
        f.available = false
        try {
            f.activate()
            fail("Expected prerequisite failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("precise location"))
        }
        assertFalse(f.settings.enabled)
        assertNull(f.started)
    }

    @Test
    fun bluetoothRequiresItsOwnTargetEvenIfWifiHasBeenConfigured() = runTest {
        val f = Fixture()
        try {
            f.activate { it.copy(mode = MonitoringMode.BLUETOOTH) }
            fail("Expected missing target")
        } catch (_: IllegalArgumentException) {
            assertFalse(f.settings.enabled)
            assertNull(f.started)
        }
    }

    @Test
    fun serviceFailureRollsBackEnabledAndKeepsOriginalException() = runTest {
        val f = Fixture()
        val failure = IllegalStateException("Foreground start refused")
        f.startFailure = failure
        try {
            f.activate { it.copy(homeSsid = "New") }
            fail("Expected service failure")
        } catch (e: RuntimeException) {
            assertSame(failure, e)
        }
        assertEquals("New", f.settings.homeSsid)
        assertFalse(f.settings.enabled)
        assertTrue(f.stopped)
    }
}
