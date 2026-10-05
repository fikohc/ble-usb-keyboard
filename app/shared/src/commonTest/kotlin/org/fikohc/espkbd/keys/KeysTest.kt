package org.fikohc.espkbd.keys

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeysTest {
    @Test
    fun hardwareKeysMapToFirmwareNames() {
        assertEquals("a", Key.A.firmwareName())
        assertEquals("0", Key.Zero.firmwareName())
        assertEquals("f12", Key.F12.firmwareName())
        assertEquals("rshift", Key.ShiftRight.firmwareName())
        assertEquals("gui", Key.MetaLeft.firmwareName())
        assertEquals("up", Key.DirectionUp.firmwareName())
        assertNull(Key.VolumeUp.firmwareName())
    }

    @Test
    fun charactersMapToUsLayoutCombos() {
        assertEquals("a", charToKeys('a'))
        assertEquals("shift,a", charToKeys('A'))
        assertEquals("shift,1", charToKeys('!'))
        assertEquals("shift,0", charToKeys(')'))
        assertEquals("shift,quote", charToKeys('"'))
        assertEquals("enter", charToKeys('\n'))
        assertEquals("space", charToKeys(' '))
        assertNull(charToKeys('é'))
    }

    @Test
    fun heldKeysIgnoreRepeatsAndCapRegularKeys() {
        val held = HeldKeys()
        assertTrue(held.press("ctrl"))
        assertFalse(held.press("ctrl"), "auto-repeat must not change the set")
        listOf("a", "b", "c", "d", "e", "f").forEach { assertTrue(held.press(it)) }
        assertFalse(held.press("g"), "a 7th regular key doesn't fit the report")
        assertTrue(held.press("shift"), "modifiers don't count against the limit")
        assertEquals(listOf("ctrl", "a", "b", "c", "d", "e", "f", "shift"), held.current)
        assertTrue(held.release("a"))
        assertFalse(held.release("g"))
        assertTrue(held.clear())
        assertFalse(held.clear())
    }

    @Test
    fun formatsAndParsesKeyLists() {
        assertEquals("Ctrl + Shift + K", formatKeys(listOf("ctrl", "shift", "k")))
        assertEquals(listOf("ctrl", "k"), parseKeyCsv("ctrl,k"))
        assertEquals(emptyList(), parseKeyCsv(""))
    }

    @Test
    fun softInputDiff() {
        assertEquals(SoftEdit(0, "a"), diffSoftInput(SOFT_INPUT_SENTINEL + "a"))
        assertEquals(SoftEdit(1, ""), diffSoftInput(SOFT_INPUT_SENTINEL.dropLast(1)))
        assertEquals(SoftEdit(0, ""), diffSoftInput(SOFT_INPUT_SENTINEL))
        assertEquals(SoftEdit(2, "yz"), diffSoftInput("xxxxxxyz", base = "xxxxxxxx"))
        // Diffed against the previous text, not the sentinel
        assertEquals(SoftEdit(0, "B"), diffSoftInput(SOFT_INPUT_SENTINEL + "AB", base = SOFT_INPUT_SENTINEL + "A"))
    }

    @Test
    fun softInputResetsOnlyAtTheEdges() {
        assertFalse(softInputNeedsReset(SOFT_INPUT_SENTINEL))
        assertFalse(softInputNeedsReset(SOFT_INPUT_SENTINEL + "hello"))
        assertTrue(softInputNeedsReset("xxx"))
        assertTrue(softInputNeedsReset("x".repeat(300)))
    }
}
