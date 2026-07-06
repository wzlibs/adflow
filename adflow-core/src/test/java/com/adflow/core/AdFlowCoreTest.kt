package com.adflow.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdFlowCoreTest {

    @After
    fun tearDown() {
        AdFlowCore.reset()
    }

    @Test
    fun `isShowingFullScreenAd reflects the most recent setShowingFullScreenAd call`() {
        assertFalse(AdFlowCore.isShowingFullScreenAd)
        AdFlowCore.setShowingFullScreenAd(true)
        assertTrue(AdFlowCore.isShowingFullScreenAd)
        AdFlowCore.setShowingFullScreenAd(false)
        assertFalse(AdFlowCore.isShowingFullScreenAd)
    }

    @Test
    fun `reset() clears isShowingFullScreenAd back to false`() {
        AdFlowCore.setShowingFullScreenAd(true)
        AdFlowCore.reset()
        assertFalse(AdFlowCore.isShowingFullScreenAd)
    }

    @Test
    fun `dispatches revenue events to every registered logger`() {
        val received = mutableListOf<AdRevenueEvent>()
        val loggerA = RevenueLogger { received += it }
        val loggerB = RevenueLogger { received += it }
        AdFlowCore.addRevenueLogger(loggerA)
        AdFlowCore.addRevenueLogger(loggerB)

        val event = AdRevenueEvent(
            placementId = "splash_interstitial",
            adType = AdType.INTERSTITIAL,
            adUnitId = "unit-1",
            valueMicros = 1_000_000,
            currencyCode = "USD",
            precision = "ESTIMATED",
            adNetwork = "AdMob",
        )
        AdFlowCore.dispatchRevenue(event)

        assertEquals(listOf(event, event), received)
    }

    @Test
    fun `removed logger no longer receives events`() {
        val received = mutableListOf<AdRevenueEvent>()
        val logger = RevenueLogger { received += it }
        AdFlowCore.addRevenueLogger(logger)
        AdFlowCore.removeRevenueLogger(logger)

        AdFlowCore.dispatchRevenue(
            AdRevenueEvent("p", AdType.BANNER, "u", 1, "USD", "ESTIMATED", null)
        )

        assertEquals(0, received.size)
    }
}
