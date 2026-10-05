package org.fikohc.espkbd

import android.view.KeyCharacterMap
import androidx.compose.ui.input.key.KeyEvent
import org.fikohc.espkbd.keys.KeyboardGrabber

actual val usesSoftKeyboard: Boolean = true

actual fun createKeyboardGrabber(): KeyboardGrabber? = null

actual fun KeyEvent.isFromSoftKeyboard(): Boolean =
    nativeKeyEvent.flags and android.view.KeyEvent.FLAG_SOFT_KEYBOARD != 0 ||
        nativeKeyEvent.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD
