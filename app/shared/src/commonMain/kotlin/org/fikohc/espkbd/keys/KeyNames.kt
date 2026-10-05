package org.fikohc.espkbd.keys

import androidx.compose.ui.input.key.Key

// Firmware key names (firmware/main/keymap.c), canonical spelling
val MODIFIERS = setOf("ctrl", "shift", "alt", "gui", "rctrl", "rshift", "ralt", "rgui")

// Boot keyboard report: at most 6 non-modifier keys at once
const val MAX_REGULAR_KEYS = 6

private val keyNames: Map<Key, String> = buildMap {
    put(Key.CtrlLeft, "ctrl")
    put(Key.ShiftLeft, "shift")
    put(Key.AltLeft, "alt")
    put(Key.MetaLeft, "gui")
    put(Key.CtrlRight, "rctrl")
    put(Key.ShiftRight, "rshift")
    put(Key.AltRight, "ralt")
    put(Key.MetaRight, "rgui")

    val letters = listOf(
        Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
        Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z,
    )
    letters.forEachIndexed { i, key -> put(key, ('a' + i).toString()) }

    val digits = listOf(
        Key.Zero, Key.One, Key.Two, Key.Three, Key.Four,
        Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
    )
    digits.forEachIndexed { i, key -> put(key, i.toString()) }

    val functionKeys = listOf(
        Key.F1, Key.F2, Key.F3, Key.F4, Key.F5, Key.F6,
        Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12,
    )
    functionKeys.forEachIndexed { i, key -> put(key, "f${i + 1}") }

    put(Key.Enter, "enter")
    put(Key.NumPadEnter, "enter")
    put(Key.Escape, "esc")
    put(Key.Backspace, "backspace")
    put(Key.Tab, "tab")
    put(Key.Spacebar, "space")
    put(Key.Minus, "minus")
    put(Key.Equals, "equal")
    put(Key.LeftBracket, "leftbrace")
    put(Key.RightBracket, "rightbrace")
    put(Key.Backslash, "backslash")
    put(Key.Semicolon, "semicolon")
    put(Key.Apostrophe, "quote")
    put(Key.Grave, "grave")
    put(Key.Comma, "comma")
    put(Key.Period, "dot")
    put(Key.Slash, "slash")
    put(Key.CapsLock, "capslock")

    put(Key.PrintScreen, "printscreen")
    put(Key.ScrollLock, "scrolllock")
    put(Key.Break, "pause")
    put(Key.Insert, "insert")
    put(Key.MoveHome, "home")
    put(Key.PageUp, "pageup")
    put(Key.Delete, "delete")
    put(Key.MoveEnd, "end")
    put(Key.PageDown, "pagedown")
    put(Key.DirectionRight, "right")
    put(Key.DirectionLeft, "left")
    put(Key.DirectionDown, "down")
    put(Key.DirectionUp, "up")
}

// Firmware name for a hardware key, or null if the firmware can't send it
fun Key.firmwareName(): String? = keyNames[this]

private val displayNames = mapOf(
    "ctrl" to "Ctrl", "shift" to "Shift", "alt" to "Alt", "gui" to "Meta",
    "rctrl" to "Right Ctrl", "rshift" to "Right Shift", "ralt" to "Right Alt", "rgui" to "Right Meta",
    "esc" to "Esc", "backspace" to "Backspace", "pageup" to "Page Up", "pagedown" to "Page Down",
    "printscreen" to "Print Screen", "scrolllock" to "Scroll Lock", "capslock" to "Caps Lock",
    "leftbrace" to "[", "rightbrace" to "]", "backslash" to "\\", "semicolon" to ";",
    "quote" to "'", "grave" to "`", "comma" to ",", "dot" to ".", "slash" to "/",
    "minus" to "-", "equal" to "=", "right" to "→", "left" to "←", "down" to "↓", "up" to "↑",
)

// "ctrl,shift,k" style names → "Ctrl + Shift + K"
fun formatKeys(names: List<String>): String = names.joinToString(" + ") { name ->
    displayNames[name] ?: if (name.length == 1) name.uppercase() else name.replaceFirstChar { it.uppercase() }
}

// Parse the device's held-keys CSV
fun parseKeyCsv(csv: String): List<String> =
    csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
