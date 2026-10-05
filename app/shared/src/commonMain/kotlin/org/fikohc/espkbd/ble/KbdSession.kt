package org.fikohc.espkbd.ble

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// Firmware releases held keys after 1000 ms without a heartbeat; stay well inside that
val HEARTBEAT_INTERVAL = 300.milliseconds

// Keyboard protocol on top of a connected link. All work runs in [scope]; cancel it to stop.
class KbdSession(
    private val link: KbdLink,
    scope: CoroutineScope,
    heartbeatInterval: Duration = HEARTBEAT_INTERVAL,
    private val onError: (Throwable) -> Unit = {},
) {
    private class Write(val chr: KbdChar, val value: String)

    // Key writes keep their order: dropping an intermediate held set would lose a quick keystroke
    private val writes = Channel<Write>(Channel.UNLIMITED)
    private var lastHeld: List<String> = emptyList()

    init {
        scope.launch {
            while (true) {
                safeWrite(KbdChar.HEARTBEAT, "1")
                delay(heartbeatInterval)
            }
        }
        scope.launch {
            for (write in writes) safeWrite(write.chr, write.value)
        }
    }

    // Make exactly these keys held on the host (long press = keep them in the set)
    fun hold(keys: List<String>) {
        if (keys == lastHeld) return
        lastHeld = keys
        writes.trySend(Write(KbdChar.KEYPRESS, keys.joinToString(",")))
    }

    fun releaseAll() = hold(emptyList())

    // Press and release keys, e.g. "shift,a"
    fun tap(csv: String) {
        writes.trySend(Write(KbdChar.KEYS, csv))
    }

    private suspend fun safeWrite(chr: KbdChar, value: String) {
        try {
            link.write(chr, value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A rejected write shouldn't end the session; a dead link is detected separately
            onError(e)
        }
    }
}
