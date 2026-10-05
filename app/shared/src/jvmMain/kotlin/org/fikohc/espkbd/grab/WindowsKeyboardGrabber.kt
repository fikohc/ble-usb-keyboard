package org.fikohc.espkbd.grab

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import espkbd.shared.generated.resources.Res
import espkbd.shared.generated.resources.grab_failed
import espkbd.shared.generated.resources.release_combo_windows
import org.fikohc.espkbd.keys.GrabFilter
import org.fikohc.espkbd.keys.GrabListener
import org.fikohc.espkbd.keys.GrabUnavailableException
import org.fikohc.espkbd.keys.KeyboardGrabber
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private const val WM_QUIT = 0x0012
private const val LLKHF_EXTENDED = 0x01
private const val LLKHF_INJECTED = 0x10

// WH_KEYBOARD_LL hook: sees keys before Windows handles Win, Alt+Tab etc. (not Ctrl+Alt+Del)
internal class WindowsKeyboardGrabber : KeyboardGrabber {
    override val releaseCombo = Res.string.release_combo_windows

    private var thread: Thread? = null

    @Volatile private var threadId = 0

    // JNA callbacks must stay strongly reachable while installed
    private var proc: WinUser.LowLevelKeyboardProc? = null

    override fun start(listener: GrabListener) {
        if (thread != null) return
        val filter = GrabFilter(listener)
        val started = CompletableFuture<Unit>()
        thread = Thread({ runHook(filter, started) }, "keyboard-grab").apply {
            isDaemon = true
            start()
        }
        try {
            started.get(2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            stop()
            throw GrabUnavailableException(Res.string.grab_failed, e.cause ?: e)
        }
    }

    override fun stop() {
        val t = thread ?: return
        val deadline = System.currentTimeMillis() + 1000
        while (t.isAlive && System.currentTimeMillis() < deadline) {
            if (threadId != 0) User32.INSTANCE.PostThreadMessage(threadId, WM_QUIT, WPARAM(0), LPARAM(0))
            t.join(20)
        }
        thread = null
        threadId = 0
        proc = null
    }

    private fun runHook(filter: GrabFilter, started: CompletableFuture<Unit>) {
        val user32 = User32.INSTANCE
        var hook: WinUser.HHOOK? = null
        val p = WinUser.LowLevelKeyboardProc { nCode, wParam, info ->
            val down = when (wParam.toInt()) {
                WinUser.WM_KEYDOWN, WinUser.WM_SYSKEYDOWN -> true
                WinUser.WM_KEYUP, WinUser.WM_SYSKEYUP -> false
                else -> null
            }
            val ours = nCode >= 0 && down != null && info.flags and LLKHF_INJECTED == 0
            val name = windowsKeyName(info.vkCode, info.scanCode, info.flags and LLKHF_EXTENDED != 0)
            if (ours && filter.handle(name, down)) {
                LRESULT(1)
            } else {
                user32.CallNextHookEx(hook, nCode, wParam, LPARAM(Pointer.nativeValue(info.pointer)))
            }
        }
        proc = p
        hook = user32.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, p, Kernel32.INSTANCE.GetModuleHandle(null), 0)
        if (hook == null) {
            started.completeExceptionally(IllegalStateException("SetWindowsHookEx failed: ${Kernel32.INSTANCE.GetLastError()}"))
            return
        }
        threadId = Kernel32.INSTANCE.GetCurrentThreadId()
        started.complete(Unit)

        // Low-level hooks are called through this thread's message loop
        val msg = WinUser.MSG()
        while (user32.GetMessage(msg, null, 0, 0) > 0) {
            user32.TranslateMessage(msg)
            user32.DispatchMessage(msg)
        }
        user32.UnhookWindowsHookEx(hook)
    }
}
