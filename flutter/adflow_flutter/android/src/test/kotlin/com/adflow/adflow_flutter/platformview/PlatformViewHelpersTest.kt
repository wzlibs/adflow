package com.adflow.adflow_flutter.platformview

import android.app.Application
import android.content.Context
import com.adflow.adflow_flutter.PlacementRegistry
import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.PBlockReason
import com.adflow.adflow_flutter.generated.PShowEventKind
import com.adflow.core.BlockReason
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PlatformViewHelpersTest {

    private fun newRegistry(): PlacementRegistry {
        val application = mock<Application>()
        val context = mock<Context> { whenever(it.applicationContext).thenReturn(application) }
        return PlacementRegistry(context)
    }

    @Test
    fun `enabledManager returns the manager when the placement is enabled`() {
        val registry = newRegistry()
        val managers = mapOf("p1" to "manager-1")

        assertSame("manager-1", enabledManager(registry, "p1", managers))
    }

    @Test
    fun `enabledManager returns null when the placement is disabled`() {
        val registry = newRegistry()
        registry.setEnabled("p1", false)
        val managers = mapOf("p1" to "manager-1")

        assertNull(enabledManager(registry, "p1", managers))
    }

    @Test
    fun `enabledManager returns null when placementId is null or has no manager`() {
        val registry = newRegistry()
        val managers = mapOf("p1" to "manager-1")

        assertNull(enabledManager(registry, null, managers))
        assertNull(enabledManager(registry, "unregistered", managers))
    }

    @Test
    fun `showBlockedReporter forwards the block reason as a SHOW_BLOCKED onShowEvent`() {
        val flutterApi = mock<AdFlowFlutterApi>()

        showBlockedReporter("p1", flutterApi).invoke(BlockReason.RULE_REJECTED)

        verify(flutterApi).onShowEvent(
            eq("p1"),
            eq(PShowEventKind.SHOW_BLOCKED),
            eq(null),
            eq(PBlockReason.RULE_REJECTED),
            eq(null),
            any(),
        )
    }
}
