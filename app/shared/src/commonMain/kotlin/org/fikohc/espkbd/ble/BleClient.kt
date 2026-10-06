package org.fikohc.espkbd.ble

import espkbd.shared.generated.resources.Res
import espkbd.shared.generated.resources.uuid_kbd_heartbeat
import espkbd.shared.generated.resources.uuid_kbd_keypress
import espkbd.shared.generated.resources.uuid_kbd_keys
import espkbd.shared.generated.resources.uuid_kbd_service
import espkbd.shared.generated.resources.uuid_kbd_state
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

enum class KbdChar(val uuidRes: StringResource) {
    KEYS(Res.string.uuid_kbd_keys),
    KEYPRESS(Res.string.uuid_kbd_keypress),
    HEARTBEAT(Res.string.uuid_kbd_heartbeat),
    STATE(Res.string.uuid_kbd_state),
}

// Service and characteristic UUIDs, loaded from composeResources/values/ble.xml
class KbdUuids(val service: Uuid, private val chars: Map<KbdChar, Uuid>) {
    operator fun get(chr: KbdChar): Uuid = chars.getValue(chr)

    companion object {
        suspend fun load(): KbdUuids = KbdUuids(
            service = Uuid.parse(getString(Res.string.uuid_kbd_service)),
            chars = KbdChar.entries.associateWith { Uuid.parse(getString(it.uuidRes)) },
        )
    }
}

// rssi: signal strength of the latest advertisement in dBm, null if unknown
data class BleDevice(val id: String, val name: String, val rssi: Int? = null)

// A device that hasn't advertised for this long is treated as gone (devices advertise every ~100 ms)
val DEVICE_STALE_AFTER = 10.seconds

// Everything BleClient / KbdLink throw, besides cancellation. Each one means the current
// connection attempt or link is over; the caller resets its state and may retry.
sealed class BleException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    // Bluetooth is off, missing permission, or not supported
    class BluetoothUnavailable(cause: Throwable? = null) : BleException("Bluetooth unavailable", cause)

    // The device isn't advertising (switched off, out of range, or not scanned yet)
    class DeviceNotFound(id: String) : BleException("Device $id not found")

    class ConnectTimeout(id: String) : BleException("Connecting to $id timed out")

    class ConnectFailed(cause: Throwable) : BleException("Connection failed", cause)

    // The link dropped (or was never up) during an operation
    class Disconnected(cause: Throwable? = null) : BleException("Device disconnected", cause)

    // The device rejected a read or write, e.g. an unknown key name
    class Rejected(cause: Throwable) : BleException("Request rejected by the device", cause)
}

// Seam between the app logic and the BLE stack, so the protocol can be tested with fakes
interface BleClient {
    // While collected, scans and emits the devices currently advertising the keyboard service
    // (seen within DEVICE_STALE_AFTER) whenever that list changes.
    // Fails with BleException.BluetoothUnavailable.
    fun scan(): Flow<List<BleDevice>>

    // Connect to a device in the current scan list.
    // Fails with DeviceNotFound, ConnectTimeout, ConnectFailed or BluetoothUnavailable.
    suspend fun connect(id: String): KbdLink
}

// Operations fail with BleException.Disconnected or BleException.Rejected
interface KbdLink {
    // Suspends until the link drops
    suspend fun awaitDisconnect()

    suspend fun read(chr: KbdChar): String

    suspend fun write(chr: KbdChar, value: String)

    fun observe(chr: KbdChar): Flow<String>

    // Signal strength of the connection in dBm, null if the platform can't tell
    suspend fun rssi(): Int?

    suspend fun close()
}
