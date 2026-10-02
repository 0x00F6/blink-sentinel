package dev.homesentinel

import dev.homesentinel.data.bluetooth.BluetoothDeviceCatalog
import dev.homesentinel.data.wifi.ScanAccessPoint
import dev.homesentinel.data.wifi.ScanPolicy
import dev.homesentinel.domain.usecase.SignalDistanceEstimator
import org.junit.Assert.*
import org.junit.Test

class SignalDistanceEstimatorTest {
    @Test fun bluetoothSortsClassicAndBleTogetherByDistanceWithUnknownLast() {
        val catalog = BluetoothDeviceCatalog()
        catalog.update("AA:BB:CC:DD:EE:01", androidName = "BLE loin", rssi = -84, bleObserved = true)
        catalog.update("AA:BB:CC:DD:EE:02", androidName = "Distance inconnue", radioObserved = true)
        catalog.update("AA:BB:CC:DD:EE:03", androidName = "Classique proche", rssi = -59, classic = true)
        assertEquals(listOf("Classique proche", "BLE loin", "Distance inconnue"), catalog.items().map { it.name })
        catalog.update("AA:BB:CC:DD:EE:01", rssi = -49, bleObserved = true)
        assertEquals("BLE loin", catalog.items().first().name)
    }

    @Test fun wifiSortsByDistanceAndPutsInvalidSignalsLast() {
        val networks = ScanPolicy.networks(listOf(
            ScanAccessPoint("Far", 10, -80),
            ScanAccessPoint("Unknown", 10, 0),
            ScanAccessPoint("Near", 10, -40),
        ))
        assertEquals(listOf("Near", "Far", "Unknown"), networks.map { it.ssid })
    }

    @Test fun invalidWifiSignalCannotHideAValidAccessPointInTheSameGroup() {
        val network = ScanPolicy.networks(listOf(
            ScanAccessPoint("Home", 10, 0), ScanAccessPoint("Home", 10, -40),
        )).single()
        assertEquals(-40, network.signalDbm)
        assertEquals(1.0, network.estimatedDistanceMeters!!, 0.0001)
    }

    @Test fun referenceSignalsProduceOneMeter() {
        assertEquals(1.0, SignalDistanceEstimator.wifiMeters(-40)!!, 0.0001)
        assertEquals(1.0, SignalDistanceEstimator.bluetoothMeters(-59)!!, 0.0001)
    }

    @Test fun weakerSignalsProduceLargerDistances() {
        assertTrue(SignalDistanceEstimator.wifiMeters(-80)!! > SignalDistanceEstimator.wifiMeters(-50)!!)
        assertTrue(SignalDistanceEstimator.bluetoothMeters(-85)!! > SignalDistanceEstimator.bluetoothMeters(-65)!!)
        assertEquals(10.0, SignalDistanceEstimator.bluetoothMeters(-84)!!, 0.0001)
    }

    @Test fun absentOrInvalidSignalsNeverBecomeDistances() {
        listOf(null, 0, 127, -127, Int.MIN_VALUE, Int.MAX_VALUE).forEach {
            assertNull(SignalDistanceEstimator.wifiMeters(it))
            assertNull(SignalDistanceEstimator.bluetoothMeters(it))
        }
    }

    @Test fun wifiGroupUsesTheStrongestAccessPointForItsEstimate() {
        val network = ScanPolicy.networks(listOf(
            ScanAccessPoint("Home", 10, -80), ScanAccessPoint("Home", 10, -40),
        )).single()
        assertEquals(1.0, network.estimatedDistanceMeters!!, 0.0001)
        assertEquals(2, network.accessPoints)
    }

    @Test fun bondedDeviceWithoutRssiHasNoDistanceButClassicDiscoveryCanSupplyOne() {
        val catalog = BluetoothDeviceCatalog()
        catalog.update("AA:BB:CC:DD:EE:FF", androidName = "Enceinte", bonded = true, classic = true)
        assertTrue(catalog.items().isEmpty())
        catalog.update("AA:BB:CC:DD:EE:FF", rssi = -59, classic = true)
        assertEquals(1.0, catalog.items().single().estimatedDistanceMeters!!, 0.0001)
        catalog.update("AA:BB:CC:DD:EE:FF", androidName = "Enceinte salon")
        assertEquals(1.0, catalog.items().single().estimatedDistanceMeters!!, 0.0001)
    }

    @Test fun newBleSignalUpdatesTheDistanceInsteadOfKeepingThePreviousEstimate() {
        val catalog = BluetoothDeviceCatalog()
        catalog.update("AA:BB:CC:DD:EE:FF", rssi = -59, bleObserved = true)
        catalog.update("AA:BB:CC:DD:EE:FF", rssi = -84, bleObserved = true)
        assertEquals(10.0, catalog.items().single().estimatedDistanceMeters!!, 0.0001)
    }
}
