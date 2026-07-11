package com.adflow.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenSlotTest {

    @Test
    fun `first claim succeeds, second claim is rejected until released`() {
        val slot = FullScreenSlot()

        assertTrue(slot.tryClaim())
        assertFalse(slot.tryClaim())

        slot.release()
        assertTrue(slot.tryClaim())
    }

    @Test
    fun `release without a prior claim is safe and idempotent`() {
        val slot = FullScreenSlot()
        slot.release()
        slot.release()
        assertTrue(slot.tryClaim())
    }
}
