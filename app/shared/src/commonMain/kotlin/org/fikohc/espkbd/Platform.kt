package org.fikohc.espkbd

import androidx.compose.ui.input.key.KeyEvent
import org.fikohc.espkbd.keys.KeyboardGrabber

// True where text comes from an on-screen keyboard (IME) rather than only hardware key events
expect val usesSoftKeyboard: Boolean

// Null where the whole keyboard can't be grabbed (mobile)
expect fun createKeyboardGrabber(): KeyboardGrabber?

// Key events an on-screen keyboard generates (e.g. Backspace on Android). These are taps:
// IMEs auto-repeat them as down/up pairs, so they can't be treated as held keys.
expect fun KeyEvent.isFromSoftKeyboard(): Boolean
