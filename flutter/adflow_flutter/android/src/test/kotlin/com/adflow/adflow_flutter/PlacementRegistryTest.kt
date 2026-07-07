package com.adflow.adflow_flutter

import android.app.Application
import android.content.Context
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PlacementRegistryTest {

    private fun newRegistry(): PlacementRegistry {
        val application = mock<Application>()
        val context = mock<Context> { whenever(it.applicationContext).thenReturn(application) }
        return PlacementRegistry(context)
    }

    @Test
    fun `isEnabled defaults to true for a placement that was never toggled`() {
        val registry = newRegistry()

        assertTrue(registry.isEnabled("never_touched"))
    }

    @Test
    fun `setEnabled false overrides isEnabled to false`() {
        val registry = newRegistry()

        registry.setEnabled("p1", false)

        assertFalse(registry.isEnabled("p1"))
        assertTrue(registry.isEnabled("other_placement"))
    }

    @Test
    fun `setEnabled true after false restores isEnabled to true`() {
        val registry = newRegistry()

        registry.setEnabled("p1", false)
        registry.setEnabled("p1", true)

        assertTrue(registry.isEnabled("p1"))
    }
}
