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
