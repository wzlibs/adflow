package com.adflow.adflow_flutter.callbacks

import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.PAdRevenueEvent
import com.adflow.core.revenue.AdRevenueEvent
import com.adflow.core.AdType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class RevenueLoggerBridgeTest {

    @Test
    fun `onRevenuePaid forwards the mapped event to the Flutter side`() {
        val flutterApi = mock<AdFlowFlutterApi>()
        val bridge = RevenueLoggerBridge(flutterApi)

        bridge.onRevenuePaid(
            AdRevenueEvent(
                placementId = "p1",
                adType = AdType.INTERSTITIAL,
                adUnitId = "unit-1",
                valueMicros = 1_000_000,
                currencyCode = "USD",
                precision = "ESTIMATED",
                adNetwork = "AdMob",
            ),
        )

        verify(flutterApi).onRevenuePaid(
            argThat<PAdRevenueEvent> {
                placementId == "p1" && adType.name == "INTERSTITIAL" && valueMicros == 1_000_000L && adNetwork == "AdMob"
            },
            org.mockito.kotlin.any(),
        )
    }
}
