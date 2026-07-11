package com.adflow.adflow_flutter.callbacks

import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.PAdFlowError
import com.adflow.adflow_flutter.generated.PBlockReason
import com.adflow.adflow_flutter.generated.PShowEventKind
import com.adflow.core.AdFlowError
import com.adflow.core.BlockReason
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ShowCallbackBridgeTest {

    @Test
    fun `onAdShowed forwards SHOWN with no error, blockReason or reward`() {
        val flutterApi = mock<AdFlowFlutterApi>()
        val bridge = ShowCallbackBridge("p1", flutterApi)

        bridge.onAdShowed()

        verify(flutterApi).onShowEvent(eq("p1"), eq(PShowEventKind.SHOWN), eq(null), eq(null), eq(null), any())
    }

    @Test
    fun `onAdFailedToShow forwards the mapped error`() {
        val flutterApi = mock<AdFlowFlutterApi>()
        val bridge = ShowCallbackBridge("p1", flutterApi)

        bridge.onAdFailedToShow(AdFlowError(3, "no fill"))

        verify(flutterApi).onShowEvent(
            eq("p1"),
            eq(PShowEventKind.FAILED_TO_SHOW),
            argThat<PAdFlowError> { code == 3L && message == "no fill" },
            eq(null),
            eq(null),
            any(),
        )
    }

    @Test
    fun `onAdDismissed forwards DISMISSED`() {
        val flutterApi = mock<AdFlowFlutterApi>()
        val bridge = ShowCallbackBridge("p1", flutterApi)

        bridge.onAdDismissed()

        verify(flutterApi).onShowEvent(eq("p1"), eq(PShowEventKind.DISMISSED), eq(null), eq(null), eq(null), any())
    }

    @Test
    fun `onAdBlocked forwards the mapped block reason`() {
        val flutterApi = mock<AdFlowFlutterApi>()
        val bridge = ShowCallbackBridge("p1", flutterApi)

        bridge.onAdBlocked(BlockReason.INTERVAL_NOT_ELAPSED)

        verify(flutterApi).onShowEvent(
            eq("p1"),
            eq(PShowEventKind.SHOW_BLOCKED),
            eq(null),
            eq(PBlockReason.INTERVAL_NOT_ELAPSED),
            eq(null),
            any(),
        )
    }
}
