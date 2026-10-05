package org.fikohc.espkbd

import androidx.lifecycle.viewModelScope
import espkbd.shared.generated.resources.Res
import espkbd.shared.generated.resources.grab_failed
import espkbd.shared.generated.resources.release_combo_linux
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.fikohc.espkbd.ble.BleClient
import org.fikohc.espkbd.ble.BleDevice
import org.fikohc.espkbd.ble.FakeLink
import org.fikohc.espkbd.ble.KbdChar
import org.fikohc.espkbd.ble.KbdLink
import org.fikohc.espkbd.keys.GrabListener
import org.fikohc.espkbd.keys.GrabUnavailableException
import org.fikohc.espkbd.keys.KeyboardGrabber
import org.jetbrains.compose.resources.StringResource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private val DEVICE = BleDevice("id", "BLE-KBD")

private class FakeBle(val link: FakeLink) : BleClient {
    override fun scan(): Flow<List<BleDevice>> = flowOf(listOf(DEVICE))
    override suspend fun connect(id: String): KbdLink = link
}

private class FakeGrabber(private val refuse: StringResource? = null) : KeyboardGrabber {
    override val releaseCombo = Res.string.release_combo_linux
    var listener: GrabListener? = null
    var running = false

    override fun start(listener: GrabListener) {
        refuse?.let { throw GrabUnavailableException(it) }
        this.listener = listener
        running = true
    }

    override fun stop() {
        running = false
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelGrabTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // Runs [block] against a connected view model, then cancels it: its heartbeat and scan
    // loops never finish on their own, and runTest would wait for them forever
    private fun TestScope.connected(link: FakeLink, grabber: KeyboardGrabber, block: (AppViewModel) -> Unit) {
        val vm = AppViewModel(FakeBle(link), grabber)
        try {
            vm.select(DEVICE)
            runCurrent()
            assertEquals(LinkStatus.CONNECTED, vm.state.value.link)
            block(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private fun FakeLink.held() = writesTo(KbdChar.KEYPRESS)

    @Test
    fun windowKeysAreIgnoredUntilGrabbed() = runTest(dispatcher) {
        val link = FakeLink()
        connected(link, FakeGrabber()) { vm ->
            vm.keyDown("a")
            runCurrent()
            assertEquals(emptyList(), link.held())
        }
    }

    @Test
    fun systemGrabSendsMetaTabAndReleasesOnCombo() = runTest(dispatcher) {
        val link = FakeLink()
        val grabber = FakeGrabber()
        connected(link, grabber) { vm ->
            vm.startGrab()
            assertEquals(GrabMode.SYSTEM, vm.state.value.grab)
            val keys = grabber.listener!!
            keys.onKey("gui", true)
            keys.onKey("tab", true)
            keys.onKey("tab", false)
            runCurrent()
            assertEquals(listOf("gui", "gui,tab", "gui"), link.held())

            keys.onReleased()
            runCurrent()
            assertEquals(GrabMode.OFF, vm.state.value.grab)
            assertEquals(false, grabber.running)
            assertEquals("", link.held().last(), "everything is released with the grab")
        }
    }

    @Test
    fun instantKeyIsPressedThenReleasedAfterHold() = runTest(dispatcher) {
        val link = FakeLink()
        val grabber = FakeGrabber()
        connected(link, grabber) { vm ->
            vm.startGrab()
            grabber.listener!!.onInstantKey("capslock")
            runCurrent()
            assertEquals(listOf("capslock"), link.held())

            advanceTimeBy(INSTANT_KEY_HOLD.inWholeMilliseconds + 1)
            assertEquals(listOf("capslock", ""), link.held())
        }
    }

    @Test
    fun quickSecondToggleIsItsOwnTap() = runTest(dispatcher) {
        val link = FakeLink()
        val grabber = FakeGrabber()
        connected(link, grabber) { vm ->
            vm.startGrab()
            grabber.listener!!.onInstantKey("capslock")
            runCurrent()
            advanceTimeBy(INSTANT_KEY_HOLD.inWholeMilliseconds / 2)
            grabber.listener!!.onInstantKey("capslock")
            runCurrent()
            // The second tap waits for the first to finish
            advanceTimeBy(2 * INSTANT_KEY_HOLD.inWholeMilliseconds)
            assertEquals(listOf("capslock", "", "capslock", ""), link.held())
        }
    }

    @Test
    fun refusedGrabFallsBackToWindowKeys() = runTest(dispatcher) {
        val link = FakeLink()
        val reason = Res.string.grab_failed
        connected(link, FakeGrabber(refuse = reason)) { vm ->
            vm.startGrab()
            assertEquals(GrabMode.WINDOW, vm.state.value.grab)
            assertEquals(reason, vm.state.value.grabError?.fallback)

            vm.keyDown("a")
            runCurrent()
            assertEquals(listOf("a"), link.held())

            listOf("ctrl", "alt", "gui").forEach(vm::keyDown)
            vm.keyDown("esc")
            runCurrent()
            assertEquals(GrabMode.OFF, vm.state.value.grab)
            assertEquals("", link.held().last())
        }
    }

    @Test
    fun windowFocusLossEndsTheGrab() = runTest(dispatcher) {
        val link = FakeLink()
        val grabber = FakeGrabber()
        connected(link, grabber) { vm ->
            vm.startGrab()
            grabber.listener!!.onKey("a", true)
            runCurrent()

            vm.windowFocusLost()
            runCurrent()
            assertEquals(GrabMode.OFF, vm.state.value.grab)
            assertEquals(false, grabber.running)
            assertEquals(listOf("a", ""), link.held())
        }
    }

    @Test
    fun panelFocusLossKeepsSystemGrab() = runTest(dispatcher) {
        val grabber = FakeGrabber()
        connected(FakeLink(), grabber) { vm ->
            vm.startGrab()
            vm.panelFocusLost()
            assertEquals(GrabMode.SYSTEM, vm.state.value.grab)
        }
    }

    @Test
    fun clickTogglesTheGrab() = runTest(dispatcher) {
        val grabber = FakeGrabber()
        connected(FakeLink(), grabber) { vm ->
            vm.toggleGrab()
            assertEquals(GrabMode.SYSTEM, vm.state.value.grab)
            vm.toggleGrab()
            assertEquals(GrabMode.OFF, vm.state.value.grab)
            assertEquals(false, grabber.running)
        }
    }

    @Test
    fun disconnectEndsTheGrab() = runTest(dispatcher) {
        val link = FakeLink()
        val grabber = FakeGrabber()
        connected(link, grabber) { vm ->
            vm.startGrab()
            link.disconnected.complete(Unit)
            runCurrent()
            assertEquals(GrabMode.OFF, vm.state.value.grab)
            assertEquals(false, grabber.running)
        }
    }
}
