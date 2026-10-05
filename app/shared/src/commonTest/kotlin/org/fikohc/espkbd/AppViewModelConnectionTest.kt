package org.fikohc.espkbd

import androidx.lifecycle.viewModelScope
import espkbd.shared.generated.resources.Res
import espkbd.shared.generated.resources.error_device_not_found
import espkbd.shared.generated.resources.error_disconnected
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.fikohc.espkbd.ble.BleClient
import org.fikohc.espkbd.ble.BleDevice
import org.fikohc.espkbd.ble.BleException
import org.fikohc.espkbd.ble.FakeLink
import org.fikohc.espkbd.ble.KbdChar
import org.fikohc.espkbd.ble.KbdLink
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val KBD_A = BleDevice("a", "BLE-KBD A")
private val KBD_B = BleDevice("b", "BLE-KBD B")

// Scan lists are pushed by the test; connect() follows [connectResult]
private class ScriptedBle : BleClient {
    val adverts = MutableSharedFlow<List<BleDevice>>(extraBufferCapacity = 16)
    var connectResult: () -> KbdLink = { FakeLink() }
    var connects = 0

    override fun scan(): Flow<List<BleDevice>> = adverts
    override suspend fun connect(id: String): KbdLink {
        connects++
        return connectResult()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelConnectionTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // The view model's loops never finish on their own; cancel it so runTest can complete
    private fun TestScope.withVm(ble: ScriptedBle, block: (AppViewModel) -> Unit) {
        val vm = AppViewModel(ble)
        try {
            vm.setScanAllowed(true)
            runCurrent()
            block(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun scanListIsShownAsIs() = runTest(dispatcher) {
        val ble = ScriptedBle()
        withVm(ble) { vm ->
            ble.adverts.tryEmit(listOf(KBD_A, KBD_B))
            runCurrent()
            assertEquals(listOf(KBD_A, KBD_B), vm.state.value.devices)
            ble.adverts.tryEmit(listOf(KBD_A))
            runCurrent()
            assertEquals(listOf(KBD_A), vm.state.value.devices)
        }
    }

    @Test
    fun failedConnectResetsSelectionWithoutRetrying() = runTest(dispatcher) {
        val ble = ScriptedBle().apply { connectResult = { throw BleException.DeviceNotFound("a") } }
        withVm(ble) { vm ->
            vm.select(KBD_A)
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()

            val s = vm.state.value
            assertNull(s.selected)
            assertEquals(LinkStatus.NONE, s.link)
            assertEquals(Res.string.error_device_not_found, s.linkError?.fallback)
            assertEquals(1, ble.connects)
        }
    }

    @Test
    fun softKeyEventsAreTaps() = runTest(dispatcher) {
        val link = FakeLink()
        val ble = ScriptedBle().apply { connectResult = { link } }
        withVm(ble) { vm ->
            vm.select(KBD_A)
            runCurrent()
            vm.softKey("a", shift = true)
            vm.softKey("enter", shift = false)
            vm.softKey("shift", shift = true) // modifier alone: nothing
            vm.softKey("backspace", shift = false)
            runCurrent()
            assertEquals(listOf("shift,a", "enter", "backspace"), link.writesTo(KbdChar.KEYS))
            assertEquals(emptyList(), link.writesTo(KbdChar.KEYPRESS))
        }
    }

    @Test
    fun droppedLinkResetsState() = runTest(dispatcher) {
        val link = FakeLink()
        val ble = ScriptedBle().apply { connectResult = { link } }
        withVm(ble) { vm ->
            vm.select(KBD_A)
            runCurrent()
            assertEquals(LinkStatus.CONNECTED, vm.state.value.link)

            link.disconnected.complete(Unit)
            runCurrent()
            val s = vm.state.value
            assertNull(s.selected)
            assertEquals(LinkStatus.NONE, s.link)
            assertNull(s.usbHostConnected)
            assertEquals(Res.string.error_disconnected, s.linkError?.fallback)
        }
    }
}
