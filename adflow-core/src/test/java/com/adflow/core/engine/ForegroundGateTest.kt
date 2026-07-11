package com.adflow.core.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForegroundGateTest {

    @Test
    fun `action does not run before the process reaches the foreground`() {
        var runCount = 0
        ForegroundGate().runOnFirstForeground { runCount++ }

        assertEquals(0, runCount)
    }

    @Test
    fun `action runs once the process reaches the foreground`() {
        var runCount = 0
        val gate = ForegroundGate()
        gate.runOnFirstForeground { runCount++ }

        gate.onForegroundStart()

        assertEquals(1, runCount)
    }

    @Test
    fun `action does not run again on a later foreground transition`() {
        var runCount = 0
        val gate = ForegroundGate()
        gate.runOnFirstForeground { runCount++ }

        gate.onForegroundStart()
        gate.onForegroundStart() // app quay lại background rồi foreground lần nữa

        assertEquals(1, runCount)
    }

    @Test
    fun `a second registration before the first fires is a no-op`() {
        var firstRunCount = 0
        var secondRunCount = 0
        val gate = ForegroundGate()
        gate.runOnFirstForeground { firstRunCount++ }
        gate.runOnFirstForeground { secondRunCount++ }

        gate.onForegroundStart()

        assertEquals(1, firstRunCount)
        assertEquals(0, secondRunCount)
    }
}
