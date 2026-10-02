package dev.homesentinel.data.bluetooth

import dev.homesentinel.domain.model.BluetoothDeviceItem
import dev.homesentinel.domain.usecase.SignalDistanceEstimator

/** Merge Android's names without losing a previously resolved name on an unnamed advertisement. */
class BluetoothDeviceCatalog(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val lastSeen = mutableMapOf<String, Long>()
    private val devices = linkedMapOf<String, BluetoothDeviceItem>()

    fun update(
        address: String,
        advertisedName: String? = null,
        androidName: String? = null,
        rssi: Int? = null,
        bonded: Boolean = false,
        bleObserved: Boolean = false,
        classic: Boolean = false,
        radioObserved: Boolean = bleObserved || rssi != null,
    ) {
        val key = address.uppercase(java.util.Locale.ROOT)
        val previous = devices[key]
        // Bond/name metadata must not keep an out-of-date radio observation visible.
        if (radioObserved) lastSeen[key] = now()
        val name = sequenceOf(advertisedName, androidName, previous?.name)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull() ?: "Name not provided"
        val signal = rssi ?: previous?.rssi
        devices[key] = BluetoothDeviceItem(
            name, key, signal,
            bonded || previous?.bonded == true,
            bleObserved || previous?.bleObserved == true,
            classic || previous?.classic == true,
            SignalDistanceEstimator.bluetoothMeters(signal),
        )
    }

    fun items(): List<BluetoothDeviceItem> = devices.values.filter { device ->
        lastSeen[device.address]?.let { now() - it in 0 until VISIBLE_FOR_MS } == true
    }.sortedWith(
        compareBy<BluetoothDeviceItem> { it.estimatedDistanceMeters ?: Double.POSITIVE_INFINITY }
            .thenBy { it.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.address },
    )
    companion object {
        const val VISIBLE_FOR_MS = 30_000L
    }
}
