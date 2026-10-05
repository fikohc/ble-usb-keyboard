package org.fikohc.espkbd

import org.fikohc.espkbd.grab.MacKeyboardGrabber
import org.fikohc.espkbd.grab.WindowsKeyboardGrabber
import org.fikohc.espkbd.grab.X11KeyboardGrabber
import androidx.compose.ui.input.key.KeyEvent
import org.fikohc.espkbd.keys.KeyboardGrabber

actual val usesSoftKeyboard: Boolean = false

actual fun createKeyboardGrabber(): KeyboardGrabber? {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.startsWith("mac") -> MacKeyboardGrabber()
        os.startsWith("windows") -> WindowsKeyboardGrabber()
        else -> X11KeyboardGrabber()
    }
}

actual fun KeyEvent.isFromSoftKeyboard(): Boolean = false
