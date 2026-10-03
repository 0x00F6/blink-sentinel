package dev.homesentinel

import dev.homesentinel.data.bluetooth.BluetoothDeviceCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothDeviceCatalogTest {
    @Test fun missingAdvertisementNameUsesAndroidName() {
        val catalog = BluetoothDeviceCatalog()
        catalog.update("AA:BB:CC:DD:EE:FF", androidName = "Living room lamp", bleObserved = true)
        assertEquals("Living room lamp", catalog.items().single().name)
    }

    @Test fun unnamedPacketsPreservePreviouslyDiscoveredNames() {
        val catalog = BluetoothDeviceCatalog()
        catalog.update("AA:BB:CC:DD:EE:FF", advertisedName = "Capteur", bleObserved = true)
        catalog.update("aa:bb:cc:dd:ee:ff", advertisedName = " ", rssi = -55)
        assertEquals(1, catalog.items().size)
        assertEquals("Capteur", catalog.items().single().name)
        assertTrue(catalog.items().single().bleObserved)
        assertEquals(-55, catalog.items().single().rssi)
    }

    @Test fun bondedClassicDeviceIsListedOnlyAfterRadioDetection() {
        val catalog = BluetoothDeviceCatalog()
        catalog.update("AA:BB:CC:DD:EE:FF", androidName = "Enceinte", bonded = true, classic = true)
        assertTrue(catalog.items().isEmpty())
        catalog.update("AA:BB:CC:DD:EE:FF", classic = true, radioObserved = true)
        val device = catalog.items().single()
        assertEquals("Enceinte", device.name)
        assertTrue(device.classic)
        assertTrue(device.bonded)
        assertFalse(device.bleObserved)
    }

    @Test fun resolvedNameReplacesUnknownNameWithoutInventingRadioPresence() {
        val catalog = BluetoothDeviceCatalog()
        catalog.update("AA:BB:CC:DD:EE:FF", bonded = true)
        assertTrue(catalog.items().isEmpty())
        catalog.update("AA:BB:CC:DD:EE:FF", advertisedName = "Entrance beacon", bleObserved = true)
        assertEquals("Entrance beacon", catalog.items().single().name)
        assertTrue(catalog.items().single().bonded)
        assertTrue(catalog.items().single().bleObserved)
    }

    @Test fun expiredDevicesDisappearAndFreshDetectionMakesThemVisibleAgain() {
        var time = 0L
        val catalog = BluetoothDeviceCatalog { time }
        catalog.update("AA:BB:CC:DD:EE:FF", bleObserved = true)
        time = 29_999
        assertEquals(1, catalog.items().size)
        time = 30_000
        assertTrue(catalog.items().isEmpty())
        catalog.update("AA:BB:CC:DD:EE:FF", radioObserved = true)
        assertEquals(1, catalog.items().size)
    }

    @Test fun nameChangesDoNotExtendRadioVisibility() {
        var time = 0L
        val catalog = BluetoothDeviceCatalog { time }
        catalog.update("AA:BB:CC:DD:EE:FF", rssi = -55, classic = true)
        time = 30_000
        catalog.update("AA:BB:CC:DD:EE:FF", androidName = "Enceinte salon")
        assertTrue(catalog.items().isEmpty())
    }

    @Test fun onlyTheDeviceWithFreshEvidenceRemainsVisible() {
        var time = 0L
        val catalog = BluetoothDeviceCatalog { time }
        catalog.update("AA:BB:CC:DD:EE:01", rssi = -55)
        time = 20_000
        catalog.update("AA:BB:CC:DD:EE:02", rssi = -60)
        time = 30_000
        assertEquals("AA:BB:CC:DD:EE:02", catalog.items().single().address)
    }
}
