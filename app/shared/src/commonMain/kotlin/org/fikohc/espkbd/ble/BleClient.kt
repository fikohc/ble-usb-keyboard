package org.fikohc.espkbd.ble

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

// GATT layout of the firmware's keyboard service (firmware/main/ble_svc.c)
object KbdUuids {
    val SERVICE: Uuid = Uuid.parse("4b9d0000-6a3e-4f8b-9c21-7d5e3a1b8c00")
}

enum class KbdChar(val uuid: Uuid) {
    // Write "shift,a": press all keys in order, then release them
    KEYS(Uuid.parse("4b9d0001-6a3e-4f8b-9c21-7d5e3a1b8c00")),

    // Write: keys that must be held right now ("" releases all, also a heartbeat).
    // Read/notify: keys the device is holding.
    KEYPRESS(Uuid.parse("4b9d0002-6a3e-4f8b-9c21-7d5e3a1b8c00")),

    // Write anything; held keys are released after 1 s without one
    HEARTBEAT(Uuid.parse("4b9d0003-6a3e-4f8b-9c21-7d5e3a1b8c00")),

    // Read/notify: "1" while the USB host has the keyboard mounted
    STATE(Uuid.parse("4b9d0004-6a3e-4f8b-9c21-7d5e3a1b8c00")),
}

data class BleDevice(val id: String, val name: String)

// Seam between the app logic and the BLE stack, so the protocol can be tested with fakes
interface BleClient {
    // Emits devices advertising the keyboard service; may emit the same device repeatedly
    fun scan(): Flow<BleDevice>

    // Connect to a previously scanned device. Throws if the connection can't be established.
    suspend fun connect(id: String): KbdLink
}

interface KbdLink {
    // Suspends until the link drops
    suspend fun awaitDisconnect()

    suspend fun read(chr: KbdChar): String

    suspend fun write(chr: KbdChar, value: String)

    fun observe(chr: KbdChar): Flow<String>

    suspend fun close()
}
