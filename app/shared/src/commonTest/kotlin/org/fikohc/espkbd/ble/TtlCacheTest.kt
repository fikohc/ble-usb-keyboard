package org.fikohc.espkbd.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class TtlCacheTest {
    @Test
    fun entriesExpireAfterTheirLastSet() {
        val time = TestTimeSource()
        val cache = TtlCache<String, String>(10.seconds, time)
        cache["a"] = "A"
        cache["b"] = "B"

        time += 6.seconds
        cache["a"] = "A" // refreshed
        time += 6.seconds

        assertEquals("A", cache["a"])
        assertNull(cache["b"])
        assertEquals(listOf("A"), cache.values())
    }
}
