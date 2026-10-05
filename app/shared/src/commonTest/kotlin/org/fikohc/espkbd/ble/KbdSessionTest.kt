package org.fikohc.espkbd.ble

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeLink : KbdLink {
    val writes = mutableListOf<Pair<KbdChar, String>>()
    var failWrites = false
    val disconnected = CompletableDeferred<Unit>()

    override suspend fun awaitDisconnect() = disconnected.await()
    override suspend fun read(chr: KbdChar) = ""
    override suspend fun write(chr: KbdChar, value: String) {
        if (failWrites) error("rejected")
        writes += chr to value
    }
    override fun observe(chr: KbdChar): Flow<String> = emptyFlow()
    override suspend fun close() {}

    fun writesTo(chr: KbdChar) = writes.filter { it.first == chr }.map { it.second }
}

@OptIn(ExperimentalCoroutinesApi::class)
class KbdSessionTest {
    private fun TestScope.session(link: FakeLink, onError: (Throwable) -> Unit = {}): Pair<KbdSession, CoroutineScope> {
        val scope = CoroutineScope(coroutineContext + Job())
        return KbdSession(link, scope, onError = onError) to scope
    }

    @Test
    fun sendsHeartbeatsWellInsideTheFirmwareTimeout() = runTest {
        val link = FakeLink()
        val (_, scope) = session(link)
        runCurrent()
        advanceTimeBy(1000)
        // t = 0, 300, 600, 900
        assertEquals(4, link.writesTo(KbdChar.HEARTBEAT).size)
        scope.cancel()
    }

    @Test
    fun longPressIsOneHeldWriteUntilRelease() = runTest {
        val link = FakeLink()
        val (s, scope) = session(link)
        s.setHeld(listOf("a"))
        s.setHeld(listOf("a")) // key repeat
        runCurrent()
        advanceTimeBy(2000)
        s.releaseAll()
        runCurrent()
        assertEquals(listOf("a", ""), link.writesTo(KbdChar.KEYPRESS))
        scope.cancel()
    }

    @Test
    fun quickKeystrokesAreNotDropped() = runTest {
        val link = FakeLink()
        val (s, scope) = session(link)
        s.setHeld(listOf("a"))
        s.setHeld(emptyList())
        s.setHeld(listOf("b"))
        s.setHeld(emptyList())
        s.tap("shift,c")
        runCurrent()
        assertEquals(
            listOf(KbdChar.KEYPRESS to "a", KbdChar.KEYPRESS to "", KbdChar.KEYPRESS to "b", KbdChar.KEYPRESS to "", KbdChar.KEYS to "shift,c"),
            link.writes.filter { it.first != KbdChar.HEARTBEAT },
        )
        scope.cancel()
    }

    @Test
    fun failedWritesAreReportedAndSessionKeepsRunning() = runTest {
        val link = FakeLink().apply { failWrites = true }
        val errors = mutableListOf<Throwable>()
        val (s, scope) = session(link) { errors += it }
        s.tap("a")
        runCurrent()
        link.failWrites = false
        s.tap("b")
        advanceTimeBy(301)
        assertEquals(listOf("b"), link.writesTo(KbdChar.KEYS))
        assertEquals(true, errors.isNotEmpty())
        assertEquals(true, link.writesTo(KbdChar.HEARTBEAT).isNotEmpty())
        scope.cancel()
    }
}
