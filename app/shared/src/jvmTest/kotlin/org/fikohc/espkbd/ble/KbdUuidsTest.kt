package org.fikohc.espkbd.ble

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KbdUuidsTest {
    // Must match firmware/main/ble_svc.c
    @Test
    fun loadsFirmwareUuidsFromResources() = runTest {
        val uuids = KbdUuids.load()
        assertEquals("4b9d0000-6a3e-4f8b-9c21-7d5e3a1b8c00", uuids.service.toString())
        assertEquals("4b9d0001-6a3e-4f8b-9c21-7d5e3a1b8c00", uuids[KbdChar.KEYS].toString())
        assertEquals("4b9d0002-6a3e-4f8b-9c21-7d5e3a1b8c00", uuids[KbdChar.KEYPRESS].toString())
        assertEquals("4b9d0003-6a3e-4f8b-9c21-7d5e3a1b8c00", uuids[KbdChar.HEARTBEAT].toString())
        assertEquals("4b9d0004-6a3e-4f8b-9c21-7d5e3a1b8c00", uuids[KbdChar.STATE].toString())
    }
}
