package org.fikohc.espkbd.ble

import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

private val CONNECT_TIMEOUT = 15.seconds

class KableBleClient : BleClient {
    // Kable builds peripherals from advertisements, so remember the latest one per device
    private val advertisements = mutableMapOf<String, Advertisement>()

    private val scanner = Scanner {
        filters {
            match { services = listOf(KbdUuids.SERVICE) }
        }
    }

    override fun scan(): Flow<BleDevice> = scanner.advertisements
        .onEach { adv -> advertisements[adv.identifier.toString()] = adv }
        .map { adv ->
            val id = adv.identifier.toString()
            BleDevice(id, adv.name ?: adv.peripheralName ?: id)
        }

    override suspend fun connect(id: String): KbdLink {
        val adv = advertisements[id] ?: error("Device $id was not found by the scan")
        val peripheral = Peripheral(adv)
        try {
            withTimeout(CONNECT_TIMEOUT) { peripheral.connect() }
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                peripheral.disconnect()
                peripheral.close()
            }
            throw e
        }
        return KableLink(peripheral)
    }
}

private class KableLink(private val peripheral: Peripheral) : KbdLink {
    private fun KbdChar.toKable() = characteristicOf(KbdUuids.SERVICE, uuid)

    override suspend fun awaitDisconnect() {
        peripheral.state.first { it is State.Disconnected }
    }

    override suspend fun read(chr: KbdChar): String =
        peripheral.read(chr.toKable()).decodeToString()

    override suspend fun write(chr: KbdChar, value: String) {
        // The firmware only accepts writes with response
        peripheral.write(chr.toKable(), value.encodeToByteArray(), WriteType.WithResponse)
    }

    override fun observe(chr: KbdChar): Flow<String> =
        peripheral.observe(chr.toKable()).map { it.decodeToString() }

    override suspend fun close() {
        peripheral.disconnect()
        peripheral.close()
    }
}
