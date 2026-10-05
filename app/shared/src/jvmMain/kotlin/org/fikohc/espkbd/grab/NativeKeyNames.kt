package org.fikohc.espkbd.grab

// Native key codes → firmware key names. All three tables use physical key positions
// (like HID usages), so the result doesn't depend on the local keyboard layout.

private val LETTERS = "abcdefghijklmnopqrstuvwxyz"

// macOS virtual key codes (Carbon kVK_*, ANSI positions)
internal val MAC_KEYS: Map<Int, String> = buildMap {
    val letterCodes = intArrayOf(0, 11, 8, 2, 14, 3, 5, 4, 34, 38, 40, 37, 46, 45, 31, 35, 12, 15, 1, 17, 32, 9, 13, 7, 16, 6)
    letterCodes.forEachIndexed { i, code -> put(code, LETTERS[i].toString()) }
    val digitCodes = intArrayOf(29, 18, 19, 20, 21, 23, 22, 26, 28, 25) // 0..9
    digitCodes.forEachIndexed { i, code -> put(code, i.toString()) }
    val fKeys = intArrayOf(122, 120, 99, 118, 96, 97, 98, 100, 101, 109, 103, 111) // F1..F12
    fKeys.forEachIndexed { i, code -> put(code, "f${i + 1}") }
    putAll(
        mapOf(
            36 to "enter", 76 to "enter", 53 to "esc", 51 to "backspace", 48 to "tab", 49 to "space",
            27 to "minus", 24 to "equal", 33 to "leftbrace", 30 to "rightbrace", 42 to "backslash",
            41 to "semicolon", 39 to "quote", 50 to "grave", 43 to "comma", 47 to "dot", 44 to "slash",
            57 to "capslock",
            105 to "printscreen", 107 to "scrolllock", 113 to "pause", // F13..F15 on Apple keyboards
            114 to "insert", 115 to "home", 116 to "pageup", 117 to "delete", 119 to "end", 121 to "pagedown",
            124 to "right", 123 to "left", 125 to "down", 126 to "up",
            59 to "ctrl", 56 to "shift", 58 to "alt", 55 to "gui",
            62 to "rctrl", 60 to "rshift", 61 to "ralt", 54 to "rgui",
        ),
    )
}

// Device-dependent modifier bits in CGEventFlags (IOKit NX_DEVICE*KEYMASK), by key code
internal val MAC_MODIFIER_BITS: Map<Int, Long> = mapOf(
    59 to 0x0001L, 56 to 0x0002L, 60 to 0x0004L, 55 to 0x0008L,
    54 to 0x0010L, 58 to 0x0020L, 61 to 0x0040L, 62 to 0x2000L,
)

// PC scan codes, set 1 — also the Linux evdev codes for this range
private val SET1_KEYS: Map<Int, String> = buildMap {
    val letterCodes = intArrayOf(
        0x1E, 0x30, 0x2E, 0x20, 0x12, 0x21, 0x22, 0x23, 0x17, 0x24, 0x25, 0x26, 0x32,
        0x31, 0x18, 0x19, 0x10, 0x13, 0x1F, 0x14, 0x16, 0x2F, 0x11, 0x2D, 0x15, 0x2C,
    )
    letterCodes.forEachIndexed { i, code -> put(code, LETTERS[i].toString()) }
    for (d in 1..9) put(0x01 + d, d.toString())
    put(0x0B, "0")
    for (f in 1..10) put(0x3A + f, "f$f")
    put(0x57, "f11")
    put(0x58, "f12")
    putAll(
        mapOf(
            0x1C to "enter", 0x01 to "esc", 0x0E to "backspace", 0x0F to "tab", 0x39 to "space",
            0x0C to "minus", 0x0D to "equal", 0x1A to "leftbrace", 0x1B to "rightbrace", 0x2B to "backslash",
            0x27 to "semicolon", 0x28 to "quote", 0x29 to "grave", 0x33 to "comma", 0x34 to "dot",
            0x35 to "slash", 0x3A to "capslock", 0x46 to "scrolllock",
            0x1D to "ctrl", 0x2A to "shift", 0x38 to "alt", 0x36 to "rshift",
        ),
    )
}

// Windows low-level hook: modifiers and odd keys by virtual key, the rest by scan code
private val WIN_VK_KEYS = mapOf(
    0xA2 to "ctrl", 0xA0 to "shift", 0xA4 to "alt", 0x5B to "gui",
    0xA3 to "rctrl", 0xA1 to "rshift", 0xA5 to "ralt", 0x5C to "rgui",
    0x13 to "pause", 0x2C to "printscreen",
)

private val WIN_EXTENDED_KEYS = mapOf(
    0x1C to "enter", 0x52 to "insert", 0x47 to "home", 0x49 to "pageup", 0x53 to "delete",
    0x4F to "end", 0x51 to "pagedown", 0x4D to "right", 0x4B to "left", 0x50 to "down", 0x48 to "up",
)

internal fun windowsKeyName(vkCode: Int, scanCode: Int, extended: Boolean): String? =
    WIN_VK_KEYS[vkCode] ?: if (extended) WIN_EXTENDED_KEYS[scanCode] else SET1_KEYS[scanCode]

// Linux evdev codes (X11 keycode - 8)
internal val LINUX_KEYS: Map<Int, String> = SET1_KEYS + mapOf(
    96 to "enter", 97 to "rctrl", 99 to "printscreen", 100 to "ralt", 102 to "home", 103 to "up",
    104 to "pageup", 105 to "left", 106 to "right", 107 to "end", 108 to "down", 109 to "pagedown",
    110 to "insert", 111 to "delete", 119 to "pause", 125 to "gui", 126 to "rgui",
)
