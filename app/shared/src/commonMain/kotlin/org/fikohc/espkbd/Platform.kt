package org.fikohc.espkbd

import org.fikohc.espkbd.keys.KeyboardGrabber

// True where text comes from an on-screen keyboard (IME) rather than only hardware key events
expect val usesSoftKeyboard: Boolean

// Null where the whole keyboard can't be grabbed (mobile)
expect fun createKeyboardGrabber(): KeyboardGrabber?
