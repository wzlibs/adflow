package com.adflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterfallLoaderTest {
    @Test
    fun `succeeds on first ad unit without trying others`() {
        val attempted = mutableListOf<String>()
        val loader = WaterfallLoader<String>(listOf("A", "B", "C")) { id, onResult ->
            attempted += id
            onResult(Result.success("ad-$id"))
        }
        var finalResult: Result<String>? = null
        loader.start { finalResult = it }
        assertEquals(listOf("A"), attempted)
        assertEquals("ad-A", finalResult?.getOrNull())
    }

    @Test
    fun `falls through to next ad unit on failure`() {
        val attempted = mutableListOf<String>()
        val loader = WaterfallLoader<String>(listOf("A", "B", "C")) { id, onResult ->
            attempted += id
            if (id == "B") onResult(Result.success("ad-B")) else onResult(Result.failure(RuntimeException("no fill")))
        }
        var finalResult: Result<String>? = null
        loader.start { finalResult = it }
        assertEquals(listOf("A", "B"), attempted)
        assertEquals("ad-B", finalResult?.getOrNull())
    }

    @Test
    fun `fails when every ad unit in the list fails`() {
        val attempted = mutableListOf<String>()
        val loader = WaterfallLoader<String>(listOf("A", "B")) { id, onResult ->
            attempted += id
            onResult(Result.failure(RuntimeException("no fill $id")))
        }
        var finalResult: Result<String>? = null
        loader.start { finalResult = it }
        assertEquals(listOf("A", "B"), attempted)
        assertTrue(finalResult?.isFailure == true)
    }
}
