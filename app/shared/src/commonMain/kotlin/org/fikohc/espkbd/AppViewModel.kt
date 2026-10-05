package org.fikohc.espkbd

import androidx.lifecycle.ViewModel
import espkbd.shared.generated.resources.Res
import espkbd.shared.generated.resources.error_connect
import espkbd.shared.generated.resources.error_disconnected
import espkbd.shared.generated.resources.error_scan
import espkbd.shared.generated.resources.error_write
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fikohc.espkbd.ble.BleClient
import org.fikohc.espkbd.ble.BleDevice
import org.fikohc.espkbd.ble.KbdChar
import org.fikohc.espkbd.ble.KbdLink
import org.fikohc.espkbd.ble.KbdSession
import org.fikohc.espkbd.keys.GrabUnavailableException
import org.fikohc.espkbd.keys.HeldKeys
import org.fikohc.espkbd.keys.KeyboardGrabber
import org.fikohc.espkbd.keys.isReleaseCombo
import org.fikohc.espkbd.keys.charToKeys
import org.fikohc.espkbd.keys.parseKeyCsv
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class LinkStatus { NONE, CONNECTING, CONNECTED }

// An error to show: the exception's message if it has one, otherwise a generic text
data class UiError(val fallback: StringResource, val detail: String? = null)

enum class GrabMode {
    OFF,

    // OS hook: every key, including system shortcuts, goes to the device
    SYSTEM,

    // OS refused the hook: only keys typed into the app window are sent
    WINDOW,
}

data class UiState(
    val devices: List<BleDevice> = emptyList(),
    val selected: BleDevice? = null,
    val link: LinkStatus = LinkStatus.NONE,
    // Whether the ESP32 is mounted by its USB host; null until read
    val usbHostConnected: Boolean? = null,
    // Keys the device reports as held
    val heldKeys: List<String> = emptyList(),
    // Last keys sent as a tap (soft keyboard), for feedback
    val lastTyped: String? = null,
    val scanError: UiError? = null,
    val linkError: UiError? = null,
    val grab: GrabMode = GrabMode.OFF,
    // Why grab fell back to WINDOW mode
    val grabError: UiError? = null,
)

class AppViewModel(
    private val ble: BleClient,
    // Null where the keyboard isn't grabbed (mobile): keys are sent whenever the app has focus
    private val grabber: KeyboardGrabber? = null,
    private val reconnectDelay: Duration = 2.seconds,
    private val scanRetryDelay: Duration = 3.seconds,
) : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val scanAllowed = MutableStateFlow(false)
    private val pickerOpen = MutableStateFlow(false)
    private val held = HeldKeys()
    private var session: KbdSession? = null
    private var connectJob: Job? = null

    // Hook threads hand events to the main thread through this
    private val grabEvents = Channel<GrabEvent>(Channel.UNLIMITED)

    private sealed interface GrabEvent {
        data class Key(val name: String, val down: Boolean) : GrabEvent
        data object Released : GrabEvent
    }

    val releaseCombo: StringResource? = grabber?.releaseCombo

    init {
        viewModelScope.launch { scanLoop() }
        viewModelScope.launch {
            for (event in grabEvents) {
                when (event) {
                    is GrabEvent.Key -> if (_state.value.grab == GrabMode.SYSTEM) sendKey(event.name, event.down)
                    GrabEvent.Released -> stopGrab()
                }
            }
        }
        // Never keep the keyboard captured with nothing to send it to
        viewModelScope.launch {
            _state.map { it.link }.distinctUntilChanged().collect { if (it != LinkStatus.CONNECTED) stopGrab() }
        }
    }

    override fun onCleared() {
        stopGrab()
    }

    // Capture the whole keyboard (desktop). Falls back to window-only capture if the OS refuses.
    fun startGrab() {
        val g = grabber ?: return
        val current = _state.value
        if (current.link != LinkStatus.CONNECTED || current.grab == GrabMode.SYSTEM) return
        releaseAll()
        try {
            g.start(
                onKey = { name, down -> grabEvents.trySend(GrabEvent.Key(name, down)) },
                onReleased = { grabEvents.trySend(GrabEvent.Released) },
            )
            _state.update { it.copy(grab = GrabMode.SYSTEM, grabError = null) }
        } catch (e: GrabUnavailableException) {
            _state.update { it.copy(grab = GrabMode.WINDOW, grabError = UiError(e.reason)) }
        }
    }

    fun stopGrab() {
        if (_state.value.grab == GrabMode.OFF) return
        grabber?.stop()
        _state.update { it.copy(grab = GrabMode.OFF) }
        releaseAll()
    }

    // Scanning needs Bluetooth permission on Android; call once it's granted
    fun setScanAllowed(allowed: Boolean) {
        scanAllowed.value = allowed
    }

    fun setPickerOpen(open: Boolean) {
        pickerOpen.value = open
    }

    fun select(device: BleDevice?) {
        connectJob?.cancel()
        session = null
        held.clear()
        _state.update {
            it.copy(
                selected = device, link = LinkStatus.NONE, usbHostConnected = null,
                heldKeys = emptyList(), lastTyped = null, linkError = null,
            )
        }
        if (device != null) {
            connectJob = viewModelScope.launch { connectLoop(device) }
        }
    }

    // Hardware key down/up from the app window, by firmware key name
    fun keyDown(name: String) = windowKey(name, down = true)

    fun keyUp(name: String) = windowKey(name, down = false)

    private fun windowKey(name: String, down: Boolean) {
        if (grabber != null) {
            // Desktop sends keys only while grabbed; in SYSTEM mode the hook delivers them instead
            if (_state.value.grab != GrabMode.WINDOW) return
            if (down && isReleaseCombo(held.current, name)) {
                stopGrab()
                return
            }
        }
        sendKey(name, down)
    }

    private fun sendKey(name: String, down: Boolean) {
        val changed = if (down) held.press(name) else held.release(name)
        if (changed) session?.setHeld(held.current)
    }

    // Focus moved away: nothing we know is held stays held.
    // A system grab keeps capturing regardless of focus, so it's left alone.
    fun windowFocusLost() {
        if (_state.value.grab != GrabMode.SYSTEM) releaseAll()
    }

    private fun releaseAll() {
        held.clear()
        session?.releaseAll()
    }

    // Text from the soft keyboard: each character becomes a tap
    fun typeText(text: String) {
        val s = session ?: return
        for (c in text) {
            val keys = charToKeys(c) ?: continue
            s.tap(keys)
            _state.update { it.copy(lastTyped = keys) }
        }
    }

    fun tapKey(name: String) {
        val s = session ?: return
        s.tap(name)
        _state.update { it.copy(lastTyped = name) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun scanLoop() {
        // Scan while there's no connection, or while the picker is open to show alternatives
        combine(scanAllowed, pickerOpen, _state) { allowed, open, s ->
            allowed && (open || s.link != LinkStatus.CONNECTED)
        }.distinctUntilChanged().collectLatest { wanted ->
            if (!wanted) return@collectLatest
            while (true) {
                try {
                    ble.scan().collect { device ->
                        _state.update { s ->
                            val others = s.devices.filter { it.id != device.id }
                            s.copy(devices = (others + device).sortedBy { it.name }, scanError = null)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(scanError = UiError(Res.string.error_scan, e.message)) }
                }
                delay(scanRetryDelay)
            }
        }
    }

    // Keep the selected device connected, reconnecting after drops
    private suspend fun connectLoop(device: BleDevice) {
        while (viewModelScope.isActive) {
            _state.update { it.copy(link = LinkStatus.CONNECTING) }
            var link: KbdLink? = null
            try {
                link = ble.connect(device.id)
                runSession(link)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LinkLostException) {
                _state.update { it.copy(linkError = UiError(Res.string.error_disconnected)) }
            } catch (e: Exception) {
                _state.update { it.copy(linkError = UiError(Res.string.error_connect, e.message)) }
            } finally {
                session = null
                held.clear()
                link?.let { withContext(NonCancellable) { runCatching { it.close() } } }
            }
            _state.update { it.copy(selected = null, link = LinkStatus.NONE, usbHostConnected = null, heldKeys = emptyList()) }
            delay(reconnectDelay)
        }
    }

    private suspend fun runSession(link: KbdLink): Nothing = coroutineScope {
        launch {
            link.observe(KbdChar.STATE).collect { v -> _state.update { it.copy(usbHostConnected = v == "1") } }
        }
        launch {
            link.observe(KbdChar.KEYPRESS).collect { v -> _state.update { it.copy(heldKeys = parseKeyCsv(v)) } }
        }
        val usb = link.read(KbdChar.STATE) == "1"
        val keys = parseKeyCsv(link.read(KbdChar.KEYPRESS))
        session = KbdSession(link, this, onError = { e ->
            _state.update { it.copy(linkError = UiError(Res.string.error_write, e.message)) }
        })
        _state.update {
            it.copy(link = LinkStatus.CONNECTED, usbHostConnected = usb, heldKeys = keys, linkError = null)
        }
        link.awaitDisconnect()
        throw LinkLostException()
    }

    private class LinkLostException : Exception()
}
