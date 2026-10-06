package org.fikohc.espkbd.ble

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fikohc.espkbd.keys.HeldKeys
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// Firmware releases held keys after 1000 ms without a heartbeat; stay well inside that
val HEARTBEAT_INTERVAL = 300.milliseconds

val RSSI_POLL_INTERVAL = 2.seconds

enum class LinkStatus { CONNECTING, CONNECTED, DISCONNECTED }

enum class UsbState { CONNECTED, DISCONNECTED }

data class LinkState(
    val status: LinkStatus = LinkStatus.CONNECTING,
    // Signal strength in dBm; null until read or where unsupported
    val rssi: Int? = null,
    // Whether the ESP32 is mounted by its USB host; null until read
    val usb: UsbState? = null,
    // Keys the device reports as held, as the firmware's CSV ("ctrl,k")
    val keysHeld: String = "",
    // DISCONNECTED: why the link ended (null after disconnect()).
    // CONNECTED: the last write the device rejected.
    val error: BleException? = null,
)

// One connection to a keyboard device: keyboard commands in, link state out.
// Commands are dropped unless the link is CONNECTED. Once DISCONNECTED it stays so; make a new one to reconnect.
interface KbdConnection {
    fun observe(): StateFlow<LinkState>

    // Hardware key down/up by firmware name; auto-repeat downs are ignored
    fun key(key: String, down: Boolean)

    // Press and release keys, e.g. "shift,a"
    fun tap(keys: String)

    // Release everything key() holds
    fun releaseAll()

    // Keys held through key(), in press order
    val pressed: List<String>

    fun disconnect()
}

// Connects to [deviceId] right away. All work runs in a child job of [parent].
class BleKbdConnection(
    private val ble: BleClient,
    private val deviceId: String,
    parent: CoroutineScope,
    private val heartbeatInterval: Duration = HEARTBEAT_INTERVAL,
    private val rssiInterval: Duration = RSSI_POLL_INTERVAL,
) : KbdConnection {
    private class Write(val chr: KbdChar, val value: String)

    private val state = MutableStateFlow(LinkState())
    private val job = Job(parent.coroutineContext[Job])

    // Key writes keep their order: dropping an intermediate held set would lose a quick keystroke
    private val writes = Channel<Write>(Channel.UNLIMITED)
    private val held = HeldKeys()

    init {
        CoroutineScope(parent.coroutineContext + job).launch { run() }
        // Finish once run() does
        job.complete()
    }

    override fun observe(): StateFlow<LinkState> = state.asStateFlow()

    override val pressed: List<String> get() = held.current

    override fun key(key: String, down: Boolean) {
        val changed = if (down) held.press(key) else held.release(key)
        if (changed) sendHeld()
    }

    override fun releaseAll() {
        if (held.clear()) sendHeld()
    }

    override fun tap(keys: String) {
        if (state.value.status != LinkStatus.CONNECTED) return
        writes.trySend(Write(KbdChar.KEYS, keys))
    }

    override fun disconnect() {
        if (state.value.status == LinkStatus.DISCONNECTED) return
        state.update { it.copy(status = LinkStatus.DISCONNECTED, error = null) }
        // run() closes the link on the way out
        job.cancel()
    }

    // Make exactly the held keys held on the host (long press = keep them in the set).
    // Called only when the held set changed.
    private fun sendHeld() {
        if (state.value.status != LinkStatus.CONNECTED) return
        writes.trySend(Write(KbdChar.KEYPRESS, held.current.joinToString(",")))
    }

    private suspend fun run() {
        var link: KbdLink? = null
        try {
            link = ble.connect(deviceId)
            session(link)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e as? BleException
                ?: if (link == null) BleException.ConnectFailed(e) else BleException.Disconnected(e)
            state.update { it.copy(status = LinkStatus.DISCONNECTED, error = error) }
        } finally {
            link?.let { withContext(NonCancellable) { runCatching { it.close() } } }
        }
    }

    private suspend fun session(link: KbdLink): Nothing = coroutineScope {
        launch {
            link.observe(KbdChar.STATE).collect { v -> state.update { it.copy(usb = usbState(v)) } }
        }
        launch {
            link.observe(KbdChar.KEYPRESS).collect { v -> state.update { it.copy(keysHeld = v) } }
        }
        val usb = usbState(link.read(KbdChar.STATE))
        val keys = link.read(KbdChar.KEYPRESS)
        state.update { it.copy(status = LinkStatus.CONNECTED, usb = usb, keysHeld = keys, error = null) }

        launch {
            while (true) {
                safeWrite(link, KbdChar.HEARTBEAT, "1")
                delay(heartbeatInterval)
            }
        }
        launch {
            for (write in writes) safeWrite(link, write.chr, write.value)
        }
        // The scan pauses while connected, so RSSI comes from the link
        launch {
            while (true) {
                val rssi = link.rssi()
                state.update { it.copy(rssi = rssi) }
                delay(rssiInterval)
            }
        }
        link.awaitDisconnect()
        throw BleException.Disconnected()
    }

    private suspend fun safeWrite(link: KbdLink, chr: KbdChar, value: String) {
        try {
            link.write(chr, value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A rejected write doesn't end the connection; a dead link is detected by awaitDisconnect
            if (e !is BleException.Disconnected) {
                state.update { it.copy(error = e as? BleException ?: BleException.Rejected(e)) }
            }
        }
    }

    private fun usbState(value: String) = if (value == "1") UsbState.CONNECTED else UsbState.DISCONNECTED
}
