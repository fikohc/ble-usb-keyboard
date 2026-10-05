package org.fikohc.espkbd.ble

import com.juul.kable.Advertisement
import com.juul.kable.GattRequestRejectedException
import com.juul.kable.GattStatusException
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.UnmetRequirementException
import com.juul.kable.WriteType
import com.juul.kable.characteristicOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val CONNECT_TIMEOUT = 15.seconds
private val EXPIRY_CHECK_INTERVAL = 1.seconds

class KableBleClient(timeSource: TimeSource = TimeSource.Monotonic) : BleClient {
    // Devices seen by the scan, with the advertisement Kable needs to connect
    private val devices = TtlCache<String, Pair<BleDevice, Advertisement>>(DEVICE_STALE_AFTER, timeSource)

    private var uuids: KbdUuids? = null

    private suspend fun uuids(): KbdUuids = uuids ?: KbdUuids.load().also { uuids = it }

    override fun scan(): Flow<List<BleDevice>> = channelFlow {
        val service = uuids().service
        val scanner = Scanner {
            filters {
                match { services = listOf(service) }
            }
        }
        // Re-check regularly so devices that went quiet drop out
        launch {
            while (true) {
                delay(EXPIRY_CHECK_INTERVAL)
                send(currentDevices())
            }
        }
        scanner.advertisements.collect { adv ->
            val id = adv.identifier.toString()
            devices[id] = BleDevice(id, adv.name ?: adv.peripheralName ?: id) to adv
            send(currentDevices())
        }
    }
        .catch { e -> throw e as? CancellationException ?: BleException.BluetoothUnavailable(e) }
        .distinctUntilChanged()

    private fun currentDevices() = devices.values().map { it.first }.sortedBy { it.name }

    override suspend fun connect(id: String): KbdLink {
        // An expired advertisement would only lead to a long connect timeout
        val (_, advertisement) = devices[id] ?: throw BleException.DeviceNotFound(id)
        val uuids = uuids()
        val peripheral = Peripheral(advertisement)
        try {
            withTimeout(CONNECT_TIMEOUT) { peripheral.connect() }
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                runCatching { peripheral.disconnect() }
                peripheral.close()
            }
            throw when (e) {
                is TimeoutCancellationException -> BleException.ConnectTimeout(id)
                is CancellationException -> e
                is UnmetRequirementException -> BleException.BluetoothUnavailable(e)
                else -> BleException.ConnectFailed(e)
            }
        }
        return KableLink(peripheral, uuids)
    }
}

private class KableLink(private val peripheral: Peripheral, private val uuids: KbdUuids) : KbdLink {
    private fun KbdChar.toKable() = characteristicOf(uuids.service, uuids[this])

    override suspend fun awaitDisconnect() {
        peripheral.state.first { it is State.Disconnected }
    }

    override suspend fun read(chr: KbdChar): String = mapErrors {
        peripheral.read(chr.toKable()).decodeToString()
    }

    override suspend fun write(chr: KbdChar, value: String) = mapErrors {
        // The firmware only accepts writes with response
        peripheral.write(chr.toKable(), value.encodeToByteArray(), WriteType.WithResponse)
    }

    override fun observe(chr: KbdChar): Flow<String> =
        peripheral.observe(chr.toKable())
            .map { it.decodeToString() }
            .catch { e -> throw if (e is CancellationException) e else e.toBleException() }

    override suspend fun close() {
        withContext(NonCancellable) {
            runCatching { peripheral.disconnect() }
            peripheral.close()
        }
    }

    private inline fun <T> mapErrors(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw e.toBleException()
    }
}

private fun Throwable.toBleException(): BleException = when (this) {
    is BleException -> this
    is GattStatusException, is GattRequestRejectedException -> BleException.Rejected(this)
    // NotConnectedException, I/O errors, a lost characteristic: the link isn't usable
    else -> BleException.Disconnected(this)
}
