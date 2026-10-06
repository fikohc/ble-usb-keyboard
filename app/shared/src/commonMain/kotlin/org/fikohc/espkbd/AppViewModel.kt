package org.fikohc.espkbd

import androidx.lifecycle.ViewModel
import espkbd.shared.generated.resources.Res
import espkbd.shared.generated.resources.error_bluetooth_unavailable
import espkbd.shared.generated.resources.error_connect
import espkbd.shared.generated.resources.error_connect_timeout
import espkbd.shared.generated.resources.error_device_not_found
import espkbd.shared.generated.resources.error_disconnected
import espkbd.shared.generated.resources.error_rejected
import espkbd.shared.generated.resources.error_scan
import espkbd.shared.generated.resources.error_write
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fikohc.espkbd.ble.BleClient
import org.fikohc.espkbd.ble.BleException
import org.fikohc.espkbd.ble.BleDevice
import org.fikohc.espkbd.ble.BleManager
import org.fikohc.espkbd.ble.KbdConnection
import org.fikohc.espkbd.ble.LinkState
import org.fikohc.espkbd.ble.LinkStatus
import org.fikohc.espkbd.keys.GrabListener
import org.fikohc.espkbd.keys.GrabUnavailableException
import org.fikohc.espkbd.keys.KeyboardGrabber
import org.fikohc.espkbd.keys.MODIFIERS
import org.fikohc.espkbd.keys.isReleaseCombo
import org.fikohc.espkbd.keys.charToKeys
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// An error to show: [detail] if set, otherwise the resource text
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
    // State of the connection to the selected device; null when none is selected
    val link: LinkState? = null,
    // Last keys sent as a tap (soft keyboard), for feedback
    val lastTyped: String? = null,
    val scanError: UiError? = null,
    val linkError: UiError? = null,
    val grab: GrabMode = GrabMode.OFF,
    // Why grab fell back to WINDOW mode
    val grabError: UiError? = null,
)

class AppViewModel(
    ble: BleClient,
    // Null where the keyboard isn't grabbed (mobile): keys are sent whenever the app has focus
    private val grabber: KeyboardGrabber? = null,
    scanRetryDelay: Duration = 3.seconds,
) : ViewModel() {
    private val manager = BleManager(ble, viewModelScope, scanRetryDelay)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val scanAllowed = MutableStateFlow(false)
    private val pickerOpen = MutableStateFlow(false)
    private var connection: KbdConnection? = null
    private var connectionJob: Job? = null

    // Hook threads hand events to the main thread through this
    private val grabEvents = Channel<GrabEvent>(Channel.UNLIMITED)

    private sealed interface GrabEvent {
        data class Key(val name: String, val down: Boolean) : GrabEvent

        // Reported as a single event: pressed now, released after INSTANT_KEY_HOLD
        data class InstantKey(val name: String) : GrabEvent
        data object Released : GrabEvent
    }


    val releaseCombo: StringResource? = grabber?.releaseCombo

    init {
        viewModelScope.launch { scanGate() }
        viewModelScope.launch {
            manager.observeDevices().collect { scan ->
                _state.update { it.copy(devices = scan.devices, scanError = scan.error?.toUiError(Res.string.error_scan)) }
            }
        }
        viewModelScope.launch {
            for (event in grabEvents) {
                when (event) {
                    is GrabEvent.Key -> if (_state.value.grab == GrabMode.SYSTEM) sendKeyEvent(event.name, event.down)
                    is GrabEvent.InstantKey -> if (_state.value.grab == GrabMode.SYSTEM) tapInstantKey(event.name)
                    GrabEvent.Released -> stopGrab()
                }
            }
        }
        // Never keep the keyboard captured with nothing to send it to
        viewModelScope.launch {
            _state.map { it.link?.status }.distinctUntilChanged().collect { if (it != LinkStatus.CONNECTED) stopGrab() }
        }
    }

    override fun onCleared() {
        stopGrab()
    }

    // Capture the whole keyboard (desktop). Falls back to window-only capture if the OS refuses.
    fun startGrab() {
        val g = grabber ?: return
        val current = _state.value
        if (current.link?.status != LinkStatus.CONNECTED || current.grab == GrabMode.SYSTEM) return
        releaseAll()
        try {
            g.start(object : GrabListener {
                override fun onKey(name: String, down: Boolean) {
                    grabEvents.trySend(GrabEvent.Key(name, down))
                }

                override fun onInstantKey(name: String) {
                    grabEvents.trySend(GrabEvent.InstantKey(name))
                }

                override fun onReleased() {
                    grabEvents.trySend(GrabEvent.Released)
                }
            })
            _state.update { it.copy(grab = GrabMode.SYSTEM, grabError = null) }
        } catch (e: GrabUnavailableException) {
            _state.update { it.copy(grab = GrabMode.WINDOW, grabError = UiError(e.reason)) }
        }
    }

    // Panel click: grab, or give the keyboard back if already grabbed
    fun toggleGrab() {
        if (_state.value.grab == GrabMode.OFF) startGrab() else stopGrab()
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
        connectionJob?.cancel()
        connection?.disconnect()
        connection = null
        _state.update { it.copy(selected = device, link = null, lastTyped = null, linkError = null) }
        if (device == null) return

        val c = manager.connect(device)
        connection = c
        connectionJob = viewModelScope.launch {
            c.observe()
                .transformWhile { emit(it); it.status != LinkStatus.DISCONNECTED }
                .collect(::onLinkState)
        }
    }

    // A link that ends on its own resets the selection; the user picks again.
    // (disconnect() only comes from select(), which stops collecting first.)
    private fun onLinkState(link: LinkState) {
        if (link.status == LinkStatus.DISCONNECTED) {
            connection = null
            _state.update {
                it.copy(selected = null, link = null, linkError = link.error?.toUiError(Res.string.error_connect))
            }
            return
        }
        _state.update {
            it.copy(
                link = link,
                // While connected, the error is a rejected write
                linkError = link.error?.toUiError(Res.string.error_write),
            )
        }
    }

    // The connection, if it can take keys now
    private fun connected(): KbdConnection? =
        connection?.takeIf { _state.value.link?.status == LinkStatus.CONNECTED }

    // Hardware key down/up from the app window, by firmware key name
    fun keyDown(name: String) = windowKey(name, down = true)

    fun keyUp(name: String) = windowKey(name, down = false)

    private fun windowKey(name: String, down: Boolean) {
        if (grabber != null) {
            // Desktop sends keys only while grabbed; in SYSTEM mode the hook delivers them instead
            if (_state.value.grab != GrabMode.WINDOW) return
            if (down && isReleaseCombo(connection?.pressed.orEmpty(), name)) {
                stopGrab()
                return
            }
        }
        sendKeyEvent(name, down)
    }

    // Press, hold briefly so the host sees a real key press, release.
    // Pauses the grab event loop meanwhile; later keys just wait their turn.
    private suspend fun tapInstantKey(name: String) {
        sendKeyEvent(name, down = true)
        delay(INSTANT_KEY_HOLD)
        sendKeyEvent(name, down = false)
    }

    private fun sendKeyEvent(name: String, down: Boolean) {
        connection?.key(name, down)
    }

    // The app window lost focus: give the keyboard back, and release everything
    // (keys held now would never see their key up)
    fun windowFocusLost() {
        stopGrab()
        releaseAll()
    }

    // Focus moved elsewhere inside the window (e.g. the device picker): release held keys.
    // A system grab keeps capturing regardless.
    fun panelFocusLost() {
        if (_state.value.grab != GrabMode.SYSTEM) releaseAll()
    }

    private fun releaseAll() {
        connection?.releaseAll()
    }

    // Text from the soft keyboard: each character becomes a tap
    fun typeText(text: String) {
        val s = connected() ?: return
        for (c in text) {
            val keys = charToKeys(c) ?: continue
            s.tap(keys)
            _state.update { it.copy(lastTyped = keys) }
        }
    }

    // Soft-keyboard Backspace: one tap per deleted character
    fun softBackspace(times: Int) {
        val s = connected() ?: return
        if (times <= 0) return
        repeat(times) { s.tap("backspace") }
        _state.update { it.copy(lastTyped = "backspace") }
    }

    // A key event from the on-screen keyboard (key down only): sent as a tap, not a held key
    fun softKey(name: String, shift: Boolean) {
        if (name in MODIFIERS) return
        val s = connected() ?: return
        val keys = if (shift) "shift,$name" else name
        s.tap(keys)
        _state.update { it.copy(lastTyped = keys) }
    }

    // Scan while there's no connection, or while the picker is open to show alternatives
    private suspend fun scanGate() {
        combine(scanAllowed, pickerOpen, _state.map { it.link?.status }) { allowed, open, status ->
            allowed && (open || status != LinkStatus.CONNECTED)
        }.distinctUntilChanged().collect { wanted ->
            if (wanted) manager.startScanJob() else manager.stopScanJob()
        }
    }
}

// How long an instant key (macOS Caps Lock) is held before its release
val INSTANT_KEY_HOLD = 100.milliseconds

private fun Throwable.toUiError(fallback: StringResource): UiError = UiError(
    when (this) {
        is BleException.BluetoothUnavailable -> Res.string.error_bluetooth_unavailable
        is BleException.DeviceNotFound -> Res.string.error_device_not_found
        is BleException.ConnectTimeout -> Res.string.error_connect_timeout
        is BleException.ConnectFailed -> Res.string.error_connect
        is BleException.Disconnected -> Res.string.error_disconnected
        is BleException.Rejected -> Res.string.error_rejected
        else -> fallback
    },
)
