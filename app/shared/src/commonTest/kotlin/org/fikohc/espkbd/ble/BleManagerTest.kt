package org.fikohc.espkbd.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

private val KBD_A = BleDevice("a", "BLE-KBD A")
private val KBD_B = BleDevice("b", "BLE-KBD B")
private val RETRY = 3.seconds

// Each scan() either fails (while [failScans]) or follows [adverts]
private class ScriptedBle(private val link: KbdLink = FakeLink()) : BleClient {
    val adverts = MutableSharedFlow<List<BleDevice>>()
    var failScans = false
    var scans = 0

    override fun scan(): Flow<List<BleDevice>> = flow {
        scans++
        if (failScans) throw BleException.BluetoothUnavailable()
        adverts.collect { emit(it) }
    }

    override suspend fun connect(id: String): KbdLink = link
}

@OptIn(ExperimentalCoroutinesApi::class)
class BleManagerTest {
    // Manager on a scope the test cancels at the end: a running scan never finishes on its own
    private fun TestScope.manager(ble: BleClient): Pair<BleManager, CoroutineScope> {
        val scope = CoroutineScope(coroutineContext + Job())
        return BleManager(ble, scope, RETRY) to scope
    }

    @Test
    fun devicesAreReported() = runTest {
        val ble = ScriptedBle()
        val (m, scope) = manager(ble)
        m.startScanJob()
        runCurrent()
        ble.adverts.emit(listOf(KBD_A, KBD_B))
        runCurrent()
        assertEquals(BleScanState(listOf(KBD_A, KBD_B)), m.observeDevices().value)
        scope.cancel()
    }

    @Test
    fun failedScanKeepsDevicesAndRetriesAfterDelay() = runTest {
        val ble = ScriptedBle()
        val (m, scope) = manager(ble)
        m.startScanJob()
        runCurrent()
        ble.adverts.emit(listOf(KBD_A))
        runCurrent()

        // Restart with Bluetooth off: every attempt fails until it comes back
        ble.failScans = true
        m.stopScanJob()
        m.startScanJob()
        runCurrent()
        assertEquals(2, ble.scans)
        assertIs<BleException.BluetoothUnavailable>(m.observeDevices().value.error)
        assertEquals(listOf(KBD_A), m.observeDevices().value.devices)

        advanceTimeBy(RETRY - 1.seconds)
        assertEquals(2, ble.scans)
        advanceTimeBy(2.seconds)
        assertEquals(3, ble.scans)

        ble.failScans = false
        advanceTimeBy(RETRY + 1.seconds)
        ble.adverts.emit(listOf(KBD_B))
        runCurrent()
        assertEquals(BleScanState(listOf(KBD_B)), m.observeDevices().value)
        scope.cancel()
    }

    @Test
    fun stoppedScanDoesNotRestart() = runTest {
        val ble = ScriptedBle().apply { failScans = true }
        val (m, scope) = manager(ble)
        m.startScanJob()
        runCurrent()
        m.stopScanJob()
        advanceTimeBy(RETRY * 3)
        assertEquals(1, ble.scans)
        scope.cancel()
    }

    @Test
    fun startingTwiceRunsOneScan() = runTest {
        val ble = ScriptedBle()
        val (m, scope) = manager(ble)
        m.startScanJob()
        m.startScanJob()
        runCurrent()
        assertEquals(1, ble.scans)
        scope.cancel()
    }

    @Test
    fun connectReturnsAConnectedConnection() = runTest {
        val link = FakeLink()
        val (m, scope) = manager(ScriptedBle(link))
        val c = m.connect(KBD_A)
        runCurrent()
        assertEquals(LinkStatus.CONNECTED, c.observe().value.status)
        c.tap("a")
        runCurrent()
        assertEquals(listOf("a"), link.writesTo(KbdChar.KEYS))
        assertNull(c.observe().value.error)
        scope.cancel()
    }
}
