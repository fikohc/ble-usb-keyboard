package org.fikohc.espkbd

import androidx.compose.ui.input.key.KeyEvent
import org.fikohc.espkbd.keys.KeyboardGrabber

actual val usesSoftKeyboard: Boolean = true

actual fun createKeyboardGrabber(): KeyboardGrabber? = null

actual fun KeyEvent.isFromSoftKeyboard(): Boolean = false
