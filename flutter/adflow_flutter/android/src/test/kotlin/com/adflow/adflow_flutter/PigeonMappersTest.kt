package com.adflow.adflow_flutter

import com.adflow.adflow_flutter.generated.PAdStateKind
import com.adflow.adflow_flutter.generated.PBlockReason
import com.adflow.core.AdFlowError
import com.adflow.core.AdState
import com.adflow.core.BlockReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PigeonMappersTest {
    @Test
    fun `loaded state preserves timestamp`() {
        val mapped = AdState.Loaded(42L).toPigeon()
        assertEquals(PAdStateKind.LOADED, mapped.kind)
        assertEquals(42L, mapped.loadedAtMs)
        assertNull(mapped.error)
    }

    @Test
    fun `failed state preserves retry metadata and error`() {
        val mapped = AdState.Failed(
            error = AdFlowError(1, "no fill"),
            willRetry = true,
            nextRetryDelayMs = 5_000L,
        ).toPigeon()
        assertEquals(PAdStateKind.FAILED, mapped.kind)
        assertEquals(1L, mapped.error?.code)
        assertEquals("no fill", mapped.error?.message)
        assertTrue(mapped.willRetry == true)
        assertEquals(5_000L, mapped.nextRetryDelayMs)
    }

    @Test
    fun `terminal failed state has no retry delay`() {
        val mapped = AdState.Failed(AdFlowError(1, "no fill"), false, null).toPigeon()
        assertFalse(mapped.willRetry == true)
        assertNull(mapped.nextRetryDelayMs)
    }

    @Test
    fun `all v2 block reasons map by name`() {
        BlockReason.entries.forEach { reason ->
            assertEquals(PBlockReason.valueOf(reason.name), reason.toPigeon())
        }
    }
}
