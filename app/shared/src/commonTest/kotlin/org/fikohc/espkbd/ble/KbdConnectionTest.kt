package org.fikohc.espkbd.ble

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeLink : KbdLink {
    val writes = mutableListOf<Pair<KbdChar, String>>()
    var failWrites = false
    val disconnected = CompletableDeferred<Unit>()
    val reads = mutableMapOf<KbdChar, String>()
    val notifications = mutableMapOf<KbdChar, MutableSharedFlow<String>>()
    var rssi: Int? = null
    var closed = false

    override suspend fun awaitDisconnect() = disconnected.await()
    override suspend fun read(chr: KbdChar) = reads[chr] ?: ""
    override suspend fun write(chr: KbdChar, value: String) {
        if (failWrites) error("rejected")
        writes += chr to value
    }
    override fun observe(chr: KbdChar): Flow<String> = notifications[chr] ?: emptyFlow()
    override suspend fun rssi(): Int? = rssi
    override suspend fun close() {
        closed = true
    }

    fun writesTo(chr: KbdChar) = writes.filter { it.first == chr }.map { it.second }
}

private class SingleLinkBle(private val link: () -> KbdLink) : BleClient {
    override fun scan(): Flow<List<BleDevice>> = emptyFlow()
    override suspend fun connect(id: String): KbdLink = link()
}

@OptIn(ExperimentalCoroutinesApi::class)
class KbdConnectionTest {
    // Connection on a scope the test cancels at the end: its loops never finish on their own
    private fun TestScope.connect(link: FakeLink): Pair<KbdConnection, CoroutineScope> =
        connect { link }

    private fun TestScope.connect(link: () -> KbdLink): Pair<KbdConnection, CoroutineScope> {
        val scope = CoroutineScope(coroutineContext + Job())
        val c = BleKbdConnection(SingleLinkBle(link), "id", scope)
        runCurrent()
        return c to scope
    }

    @Test
    fun connectsAndReportsInitialDeviceState() = runTest {
        val link = FakeLink().apply {
            reads[KbdChar.STATE] = "1"
            reads[KbdChar.KEYPRESS] = "shift"
            rssi = -60
        }
        val (c, scope) = connect(link)
        val s = c.observe().value
        assertEquals(LinkStatus.CONNECTED, s.status)
        assertEquals(UsbState.CONNECTED, s.usb)
        assertEquals("shift", s.keysHeld)
        assertEquals(-60, s.rssi)
        assertNull(s.error)
        scope.cancel()
    }

    @Test
    fun notificationsAndRssiUpdateState() = runTest {
        val usb = MutableSharedFlow<String>()
        val keys = MutableSharedFlow<String>()
        val link = FakeLink().apply {
            notifications[KbdChar.STATE] = usb
            notifications[KbdChar.KEYPRESS] = keys
        }
        val (c, scope) = connect(link)
        usb.emit("0")
        keys.emit("ctrl,k")
        link.rssi = -70
        advanceTimeBy(RSSI_POLL_INTERVAL + RSSI_POLL_INTERVAL / 2)
        val s = c.observe().value
        assertEquals(UsbState.DISCONNECTED, s.usb)
        assertEquals("ctrl,k", s.keysHeld)
        assertEquals(-70, s.rssi)
        scope.cancel()
    }

    @Test
    fun failedConnectEndsDisconnectedWithTheReason() = runTest {
        val (c, scope) = connect { throw BleException.DeviceNotFound("id") }
        val s = c.observe().value
        assertEquals(LinkStatus.DISCONNECTED, s.status)
        assertIs<BleException.DeviceNotFound>(s.error)
        scope.cancel()
    }

    @Test
    fun droppedLinkEndsDisconnectedAndClosesTheLink() = runTest {
        val link = FakeLink()
        val (c, scope) = connect(link)
        link.disconnected.complete(Unit)
        runCurrent()
        assertEquals(LinkStatus.DISCONNECTED, c.observe().value.status)
        assertIs<BleException.Disconnected>(c.observe().value.error)
        assertTrue(link.closed)
        scope.cancel()
    }

    @Test
    fun disconnectEndsWithoutErrorAndDropsLaterCommands() = runTest {
        val link = FakeLink()
        val (c, scope) = connect(link)
        c.disconnect()
        runCurrent()
        c.tap("a")
        runCurrent()
        assertEquals(LinkStatus.DISCONNECTED, c.observe().value.status)
        assertNull(c.observe().value.error)
        assertTrue(link.closed)
        assertEquals(emptyList(), link.writesTo(KbdChar.KEYS))
        scope.cancel()
    }

    @Test
    fun sendsHeartbeatsWellInsideTheFirmwareTimeout() = runTest {
        val link = FakeLink()
        val (_, scope) = connect(link)
        advanceTimeBy(1000)
        // t = 0, 300, 600, 900
        assertEquals(4, link.writesTo(KbdChar.HEARTBEAT).size)
        scope.cancel()
    }

    @Test
    fun longPressIsOneHeldWriteUntilRelease() = runTest {
        val link = FakeLink()
        val (c, scope) = connect(link)
        c.key("a", down = true)
        c.key("a", down = true) // key repeat
        runCurrent()
        advanceTimeBy(2000)
        c.key("a", down = false)
        runCurrent()
        assertEquals(listOf("a", ""), link.writesTo(KbdChar.KEYPRESS))
        scope.cancel()
    }

    @Test
    fun heldSetFollowsPressOrderAndReleaseAll() = runTest {
        val link = FakeLink()
        val (c, scope) = connect(link)
        c.key("ctrl", down = true)
        c.key("k", down = true)
        assertEquals(listOf("ctrl", "k"), c.pressed)
        c.releaseAll()
        c.releaseAll() // nothing held: no write
        runCurrent()
        assertEquals(listOf("ctrl", "ctrl,k", ""), link.writesTo(KbdChar.KEYPRESS))
        scope.cancel()
    }

    @Test
    fun quickKeystrokesAreNotDropped() = runTest {
        val link = FakeLink()
        val (c, scope) = connect(link)
        c.key("a", down = true)
        c.key("a", down = false)
        c.key("b", down = true)
        c.key("b", down = false)
        c.tap("shift,c")
        runCurrent()
        assertEquals(
            listOf(KbdChar.KEYPRESS to "a", KbdChar.KEYPRESS to "", KbdChar.KEYPRESS to "b", KbdChar.KEYPRESS to "", KbdChar.KEYS to "shift,c"),
            link.writes.filter { it.first != KbdChar.HEARTBEAT },
        )
        scope.cancel()
    }

    @Test
    fun failedWritesAreReportedAndConnectionKeepsRunning() = runTest {
        val link = FakeLink()
        val (c, scope) = connect(link)
        link.failWrites = true
        c.tap("a")
        runCurrent()
        assertIs<BleException.Rejected>(c.observe().value.error)
        link.failWrites = false
        c.tap("b")
        advanceTimeBy(301)
        assertEquals(listOf("b"), link.writesTo(KbdChar.KEYS))
        assertEquals(LinkStatus.CONNECTED, c.observe().value.status)
        assertTrue(link.writesTo(KbdChar.HEARTBEAT).isNotEmpty())
        scope.cancel()
    }
}
