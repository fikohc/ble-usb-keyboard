package org.fikohc.espkbd.grab

import kotlin.test.Test
import kotlin.test.assertEquals

class NativeKeyNamesTest {
    @Test
    fun macKeyCodes() {
        assertEquals("a", MAC_KEYS[0])
        assertEquals("z", MAC_KEYS[6])
        assertEquals("tab", MAC_KEYS[48])
        assertEquals("esc", MAC_KEYS[53])
        assertEquals("gui", MAC_KEYS[55])
        assertEquals("rgui", MAC_KEYS[54])
        assertEquals("f12", MAC_KEYS[111])
    }

    @Test
    fun windowsScanCodes() {
        assertEquals("a", windowsKeyName(vkCode = 0x41, scanCode = 0x1E, extended = false))
        assertEquals("gui", windowsKeyName(vkCode = 0x5B, scanCode = 0x5B, extended = true))
        assertEquals("ralt", windowsKeyName(vkCode = 0xA5, scanCode = 0x38, extended = true))
        assertEquals("up", windowsKeyName(vkCode = 0x26, scanCode = 0x48, extended = true))
        assertEquals("tab", windowsKeyName(vkCode = 0x09, scanCode = 0x0F, extended = false))
        assertEquals(null, windowsKeyName(vkCode = 0x68, scanCode = 0x48, extended = false)) // numpad 8
    }

    @Test
    fun linuxEvdevCodes() {
        assertEquals("a", LINUX_KEYS[30])
        assertEquals("esc", LINUX_KEYS[1])
        assertEquals("gui", LINUX_KEYS[125])
        assertEquals("rctrl", LINUX_KEYS[97])
        assertEquals("up", LINUX_KEYS[103])
    }
}
