package org.fikohc.espkbd.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GrabTest {
    @Test
    fun releaseComboNeedsCtrlAltMetaThenEsc() {
        assertTrue(isReleaseCombo(listOf("ctrl", "alt", "gui"), "esc"))
        assertTrue(isReleaseCombo(listOf("rctrl", "ralt", "rgui"), "esc"))
        assertFalse(isReleaseCombo(listOf("ctrl", "alt"), "esc"))
        assertFalse(isReleaseCombo(listOf("ctrl", "alt", "gui"), "tab"))
    }

    @Test
    fun filterForwardsKeysUntilReleaseCombo() {
        val listener = RecordingListener()
        val filter = GrabFilter(listener)

        assertTrue(filter.handle("gui", true))
        assertTrue(filter.handle("tab", true))
        assertTrue(filter.handle("tab", false))
        assertTrue(filter.handle(null, true), "unknown keys are swallowed too")
        assertTrue(filter.handle("ctrl", true))
        assertTrue(filter.handle("alt", true))
        assertTrue(filter.handle("esc", true), "the combo's Esc is swallowed")

        assertEquals(1, listener.released)
        assertEquals(listOf("gui" to true, "tab" to true, "tab" to false, "ctrl" to true, "alt" to true), listener.keys)
        assertFalse(filter.active)
        assertFalse(filter.handle("esc", false), "after release, keys go to the local OS")
        assertFalse(filter.handleInstant("capslock"))
    }

    @Test
    fun instantKeysAreForwardedSeparately() {
        val listener = RecordingListener()
        val filter = GrabFilter(listener)
        assertTrue(filter.handleInstant("capslock"))
        assertEquals(listOf("capslock"), listener.instant)
        assertEquals(emptyList(), listener.keys)
    }
}

private class RecordingListener : GrabListener {
    val keys = mutableListOf<Pair<String, Boolean>>()
    val instant = mutableListOf<String>()
    var released = 0

    override fun onKey(name: String, down: Boolean) {
        keys += name to down
    }

    override fun onInstantKey(name: String) {
        instant += name
    }

    override fun onReleased() {
        released++
    }
}
