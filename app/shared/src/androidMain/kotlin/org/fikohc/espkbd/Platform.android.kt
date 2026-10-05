package org.fikohc.espkbd

import org.fikohc.espkbd.keys.KeyboardGrabber

actual val usesSoftKeyboard: Boolean = true

actual fun createKeyboardGrabber(): KeyboardGrabber? = null
