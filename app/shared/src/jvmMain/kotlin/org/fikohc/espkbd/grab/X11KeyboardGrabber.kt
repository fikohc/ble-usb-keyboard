package org.fikohc.espkbd.grab

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.platform.unix.X11
import espkbd.shared.generated.resources.Res
import espkbd.shared.generated.resources.grab_x11_failed
import espkbd.shared.generated.resources.release_combo_linux
import org.fikohc.espkbd.keys.GrabFilter
import org.fikohc.espkbd.keys.GrabListener
import org.fikohc.espkbd.keys.GrabUnavailableException
import org.fikohc.espkbd.keys.KeyboardGrabber
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Suppress("FunctionName")
private interface Xkb : Library {
    fun XkbSetDetectableAutoRepeat(display: X11.Display, detectable: Boolean, supported: Pointer?): Boolean
}

private const val KEY_PRESS = 2
private const val KEY_RELEASE = 3
private const val GRAB_MODE_ASYNC = 1
private const val GRAB_SUCCESS = 0
private const val X_KEYCODE_OFFSET = 8

// XGrabKeyboard on the root window: all keys come to us while the grab lasts.
// Works on X11 sessions; under Wayland (via XWayland) the compositor usually refuses it.
internal class X11KeyboardGrabber : KeyboardGrabber {
    override val releaseCombo = Res.string.release_combo_linux

    private var thread: Thread? = null

    @Volatile private var stopping = false

    override fun start(listener: GrabListener) {
        if (thread != null) return
        stopping = false
        val filter = GrabFilter(listener)
        val started = CompletableFuture<Unit>()
        thread = Thread({ runGrab(filter, started) }, "keyboard-grab").apply {
            isDaemon = true
            start()
        }
        try {
            started.get(2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            stop()
            throw GrabUnavailableException(Res.string.grab_x11_failed, e.cause ?: e)
        }
    }

    override fun stop() {
        val t = thread ?: return
        stopping = true
        t.join(1000)
        thread = null
    }

    // Xlib isn't thread-safe: everything on this display happens on this thread
    private fun runGrab(filter: GrabFilter, started: CompletableFuture<Unit>) {
        val x = X11.INSTANCE
        val display = x.XOpenDisplay(null)
        if (display == null) {
            started.completeExceptionally(IllegalStateException("No X display"))
            return
        }
        try {
            val result = x.XGrabKeyboard(
                display, x.XDefaultRootWindow(display), 0, GRAB_MODE_ASYNC, GRAB_MODE_ASYNC, NativeLong(0),
            )
            if (result != GRAB_SUCCESS) {
                started.completeExceptionally(IllegalStateException("XGrabKeyboard returned $result"))
                return
            }
            // Report auto-repeat as repeated presses, not release+press pairs
            runCatching { Native.load("X11", Xkb::class.java).XkbSetDetectableAutoRepeat(display, true, null) }
            started.complete(Unit)

            val event = X11.XEvent()
            while (!stopping) {
                while (x.XPending(display) > 0) {
                    x.XNextEvent(display, event)
                    if (event.type == KEY_PRESS || event.type == KEY_RELEASE) {
                        event.readField("xkey")
                        val code = event.xkey.keycode - X_KEYCODE_OFFSET
                        filter.handle(LINUX_KEYS[code], event.type == KEY_PRESS)
                        // After the release combination, hand the keyboard back right away
                        if (!filter.active) {
                            x.XUngrabKeyboard(display, NativeLong(0))
                        }
                    }
                }
                Thread.sleep(5)
            }
            x.XUngrabKeyboard(display, NativeLong(0))
        } finally {
            x.XCloseDisplay(display)
        }
    }
}
