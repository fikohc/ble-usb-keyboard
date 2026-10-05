package org.fikohc.espkbd.grab

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import espkbd.shared.generated.resources.Res
import espkbd.shared.generated.resources.grab_failed
import espkbd.shared.generated.resources.grab_needs_accessibility
import espkbd.shared.generated.resources.release_combo_macos
import org.fikohc.espkbd.keys.GrabFilter
import org.fikohc.espkbd.keys.GrabListener
import org.fikohc.espkbd.keys.GrabUnavailableException
import org.fikohc.espkbd.keys.KeyboardGrabber
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Suppress("FunctionName")
private interface CoreGraphics : Library {
    fun interface EventTapCallback : Callback {
        fun invoke(proxy: Pointer?, type: Int, event: Pointer?, refcon: Pointer?): Pointer?
    }

    fun CGEventTapCreate(tap: Int, place: Int, options: Int, mask: Long, callback: EventTapCallback, refcon: Pointer?): Pointer?
    fun CGEventTapEnable(tap: Pointer, enable: Boolean)
    fun CGEventGetIntegerValueField(event: Pointer, field: Int): Long
    fun CGEventGetFlags(event: Pointer): Long
}

@Suppress("FunctionName")
private interface CoreFoundation : Library {
    fun CFMachPortCreateRunLoopSource(allocator: Pointer?, port: Pointer, order: Long): Pointer
    fun CFMachPortInvalidate(port: Pointer)
    fun CFRunLoopGetCurrent(): Pointer
    fun CFRunLoopAddSource(loop: Pointer, source: Pointer, mode: Pointer)
    fun CFRunLoopRun()
    fun CFRunLoopStop(loop: Pointer)
    fun CFStringCreateWithCString(allocator: Pointer?, str: String, encoding: Int): Pointer
    fun CFDictionaryCreate(allocator: Pointer?, keys: Pointer, values: Pointer, count: Long, keyCallbacks: Pointer, valueCallbacks: Pointer): Pointer
    fun CFRelease(ref: Pointer)
}

@Suppress("FunctionName")
private interface ApplicationServices : Library {
    // C bool: only the low byte is defined
    fun AXIsProcessTrustedWithOptions(options: Pointer?): Byte
}

private const val SESSION_EVENT_TAP = 1
private const val HEAD_INSERT = 0
private const val TAP_DEFAULT = 0 // active filter: may swallow events
private const val KEY_DOWN = 10
private const val KEY_UP = 11
private const val FLAGS_CHANGED = 12
private const val TAP_DISABLED_BY_TIMEOUT = 0xFFFFFFFE.toInt()
private const val TAP_DISABLED_BY_USER_INPUT = 0xFFFFFFFF.toInt()
private const val KEYCODE_FIELD = 9
private const val CAPS_LOCK = 57
private const val UTF8 = 0x08000100

// CGEventTap at session level: needs the Accessibility permission, can swallow Cmd+Tab and friends
internal class MacKeyboardGrabber : KeyboardGrabber {
    override val releaseCombo = Res.string.release_combo_macos

    private val cg = Native.load("CoreGraphics", CoreGraphics::class.java)
    private val cf = Native.load("CoreFoundation", CoreFoundation::class.java)
    private val cfLib = NativeLibrary.getInstance("CoreFoundation")

    private var thread: Thread? = null

    @Volatile private var runLoop: Pointer? = null

    // JNA callbacks must stay strongly reachable while installed
    private var callback: CoreGraphics.EventTapCallback? = null

    override fun start(listener: GrabListener) {
        if (thread != null) return
        if (!isTrusted(prompt = true)) throw GrabUnavailableException(Res.string.grab_needs_accessibility)

        val filter = GrabFilter(listener)
        val started = CompletableFuture<Unit>()
        thread = Thread({ runTap(filter, started) }, "keyboard-grab").apply {
            isDaemon = true
            start()
        }
        try {
            started.get(2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            stop()
            val cause = e.cause ?: e
            throw cause as? GrabUnavailableException ?: GrabUnavailableException(Res.string.grab_failed, cause)
        }
    }

    override fun stop() {
        val t = thread ?: return
        // The run loop may not be running yet; keep asking until the thread is gone
        val deadline = System.currentTimeMillis() + 1000
        while (t.isAlive && System.currentTimeMillis() < deadline) {
            runLoop?.let { cf.CFRunLoopStop(it) }
            t.join(20)
        }
        thread = null
        runLoop = null
        callback = null
    }

    private fun runTap(filter: GrabFilter, started: CompletableFuture<Unit>) {
        var tap: Pointer? = null
        val cb = CoreGraphics.EventTapCallback { _, type, event, _ ->
            when (type) {
                // The system turns slow taps off; turn it back on
                TAP_DISABLED_BY_TIMEOUT, TAP_DISABLED_BY_USER_INPUT -> {
                    tap?.let { cg.CGEventTapEnable(it, true) }
                    event
                }
                KEY_DOWN, KEY_UP -> {
                    val code = cg.CGEventGetIntegerValueField(event!!, KEYCODE_FIELD).toInt()
                    if (filter.handle(MAC_KEYS[code], type == KEY_DOWN)) null else event
                }
                FLAGS_CHANGED -> {
                    val code = cg.CGEventGetIntegerValueField(event!!, KEYCODE_FIELD).toInt()
                    val swallow = if (code == CAPS_LOCK) {
                        // Caps Lock only reports toggles, not presses and releases
                        filter.handleInstant("capslock")
                    } else {
                        val bit = MAC_MODIFIER_BITS[code]
                        val down = bit != null && cg.CGEventGetFlags(event) and bit != 0L
                        filter.handle(MAC_KEYS[code], down)
                    }
                    if (swallow) null else event
                }
                else -> event
            }
        }
        callback = cb

        val mask = (1L shl KEY_DOWN) or (1L shl KEY_UP) or (1L shl FLAGS_CHANGED)
        tap = cg.CGEventTapCreate(SESSION_EVENT_TAP, HEAD_INSERT, TAP_DEFAULT, mask, cb, null)
        if (tap == null) {
            started.completeExceptionally(GrabUnavailableException(Res.string.grab_needs_accessibility))
            return
        }
        val source = cf.CFMachPortCreateRunLoopSource(null, tap, 0)
        val loop = cf.CFRunLoopGetCurrent()
        cf.CFRunLoopAddSource(loop, source, cfLib.getGlobalVariableAddress("kCFRunLoopCommonModes").getPointer(0))
        cg.CGEventTapEnable(tap, true)
        runLoop = loop
        started.complete(Unit)

        cf.CFRunLoopRun()

        cg.CGEventTapEnable(tap, false)
        cf.CFMachPortInvalidate(tap)
        cf.CFRelease(source)
        cf.CFRelease(tap)
    }

    // AXIsProcessTrustedWithOptions({kAXTrustedCheckOptionPrompt: prompt})
    private fun isTrusted(prompt: Boolean): Boolean {
        val ax = Native.load("ApplicationServices", ApplicationServices::class.java)
        val key = cf.CFStringCreateWithCString(null, "AXTrustedCheckOptionPrompt", UTF8)
        val value = cfLib.getGlobalVariableAddress(if (prompt) "kCFBooleanTrue" else "kCFBooleanFalse").getPointer(0)
        val keys = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, key) }
        val values = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, value) }
        val options = cf.CFDictionaryCreate(
            null, keys, values, 1,
            cfLib.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks"),
            cfLib.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks"),
        )
        try {
            return ax.AXIsProcessTrustedWithOptions(options) != 0.toByte()
        } finally {
            cf.CFRelease(options)
            cf.CFRelease(key)
        }
    }
}
