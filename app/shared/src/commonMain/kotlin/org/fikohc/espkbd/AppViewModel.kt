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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fikohc.espkbd.ble.BleClient
import org.fikohc.espkbd.ble.BleDevice
import org.fikohc.espkbd.ble.KbdChar
import org.fikohc.espkbd.ble.KbdLink
import org.fikohc.espkbd.ble.KbdSession
import org.fikohc.espkbd.keys.HeldKeys
import org.fikohc.espkbd.keys.charToKeys
import org.fikohc.espkbd.keys.parseKeyCsv
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class LinkStatus { NONE, CONNECTING, CONNECTED }

// An error to show: the exception's message if it has one, otherwise a generic text
data class UiError(val fallback: StringResource, val detail: String? = null)

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
)

class AppViewModel(
    private val ble: BleClient,
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

    init {
        viewModelScope.launch { scanLoop() }
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

    // Hardware key down/up, by firmware key name
    fun keyDown(name: String) {
        if (held.press(name)) session?.setHeld(held.current)
    }

    fun keyUp(name: String) {
        if (held.release(name)) session?.setHeld(held.current)
    }

    // Focus moved away: nothing we know is held stays held
    fun releaseAll() {
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
            _state.update { it.copy(link = LinkStatus.NONE, usbHostConnected = null, heldKeys = emptyList()) }
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
