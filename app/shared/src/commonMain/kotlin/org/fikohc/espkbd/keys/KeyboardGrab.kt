package org.fikohc.espkbd.keys

import org.jetbrains.compose.resources.StringResource

// System-wide keyboard capture (desktop): every key goes to the device, none reach local apps,
// so shortcuts like Meta+Tab can be sent to the host.
interface KeyboardGrabber {
    // How the release combination is spelled on this OS
    val releaseCombo: StringResource

    // Start capturing. Callbacks run on a hook thread. [onReleased] fires when the user presses the
    // release combination; capturing has already stopped by then (call [stop] to clean up).
    // Throws [GrabUnavailableException] if the OS doesn't allow it.
    fun start(onKey: (name: String, down: Boolean) -> Unit, onReleased: () -> Unit)

    fun stop()
}

class GrabUnavailableException(val reason: StringResource, cause: Throwable? = null) : Exception(cause)

// Ctrl + Alt + Meta + Esc (any side) gives the keyboard back. No desktop OS assigns it and it's
// unlikely to be needed on the host; Esc is never sent.
fun isReleaseCombo(held: Collection<String>, pressed: String): Boolean =
    pressed == "esc" &&
        held.any { it == "ctrl" || it == "rctrl" } &&
        held.any { it == "alt" || it == "ralt" } &&
        held.any { it == "gui" || it == "rgui" }

// Tracks keys on the hook thread and spots the release combination there, so the keyboard comes
// back even if the UI thread is stuck.
class GrabFilter(
    private val onKey: (name: String, down: Boolean) -> Unit,
    private val onReleased: () -> Unit,
) {
    private val held = mutableSetOf<String>()

    @kotlin.concurrent.Volatile
    var active = true
        private set

    // Returns true if the event should be swallowed. [name] is null for keys the device can't send.
    fun handle(name: String?, down: Boolean): Boolean {
        if (!active) return false
        if (name == null) return true
        if (down && isReleaseCombo(held, name)) {
            active = false
            onReleased()
            return true
        }
        if (down) held += name else held -= name
        onKey(name, down)
        return true
    }
}
