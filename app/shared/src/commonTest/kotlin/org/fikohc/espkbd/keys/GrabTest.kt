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
        val keys = mutableListOf<Pair<String, Boolean>>()
        var released = 0
        val filter = GrabFilter({ n, d -> keys += n to d }, { released++ })

        assertTrue(filter.handle("gui", true))
        assertTrue(filter.handle("tab", true))
        assertTrue(filter.handle("tab", false))
        assertTrue(filter.handle(null, true), "unknown keys are swallowed too")
        assertTrue(filter.handle("ctrl", true))
        assertTrue(filter.handle("alt", true))
        assertTrue(filter.handle("esc", true), "the combo's Esc is swallowed")

        assertEquals(1, released)
        assertEquals(listOf("gui" to true, "tab" to true, "tab" to false, "ctrl" to true, "alt" to true), keys)
        assertFalse(filter.active)
        assertFalse(filter.handle("esc", false), "after release, keys go to the local OS")
    }
}
