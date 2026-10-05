package org.fikohc.espkbd.keys

import org.jetbrains.compose.resources.StringResource

// System-wide keyboard capture (desktop): every key goes to the device, none reach local apps,
// so shortcuts like Meta+Tab can be sent to the host.
interface KeyboardGrabber {
    // How the release combination is spelled on this OS
    val releaseCombo: StringResource

    // Start capturing; [listener] is called on a hook thread.
    // Throws [GrabUnavailableException] if the OS doesn't allow it.
    fun start(listener: GrabListener)

    fun stop()
}

// Called on the OS hook thread: must return quickly (hand off, don't block)
interface GrabListener {
    fun onKey(name: String, down: Boolean)

    // A key the OS reports only as a single event (macOS Caps Lock toggles): the receiver
    // presses it and releases it shortly after
    fun onInstantKey(name: String)

    // The user pressed the release combination; capturing has already stopped (call stop to clean up)
    fun onReleased()
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
class GrabFilter(private val listener: GrabListener) {
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
            listener.onReleased()
            return true
        }
        if (down) held += name else held -= name
        listener.onKey(name, down)
        return true
    }

    // A key reported as one event (see GrabListener.onInstantKey). Returns true to swallow it.
    fun handleInstant(name: String): Boolean {
        if (!active) return false
        listener.onInstantKey(name)
        return true
    }
}
