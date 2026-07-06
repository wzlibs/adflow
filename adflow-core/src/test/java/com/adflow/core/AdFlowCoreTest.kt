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
    fun `tryClaimFullScreenSlot succeeds when free and reflects in isShowingFullScreenAd`() {
        assertFalse(AdFlowCore.isShowingFullScreenAd)
        assertTrue(AdFlowCore.tryClaimFullScreenSlot())
        assertTrue(AdFlowCore.isShowingFullScreenAd)
        AdFlowCore.releaseFullScreenSlot()
        assertFalse(AdFlowCore.isShowingFullScreenAd)
    }

    @Test
    fun `tryClaimFullScreenSlot fails while the slot is already claimed, until released`() {
        assertTrue(AdFlowCore.tryClaimFullScreenSlot())

        assertFalse(AdFlowCore.tryClaimFullScreenSlot()) // đã bị chiếm - không được cướp hoặc claim đôi
        assertTrue(AdFlowCore.isShowingFullScreenAd) // vẫn do người claim đầu tiên giữ

        AdFlowCore.releaseFullScreenSlot()
        assertTrue(AdFlowCore.tryClaimFullScreenSlot()) // trống lại, nên 1 lần claim mới có thể thành công
    }

    @Test
    fun `reset() clears isShowingFullScreenAd back to false`() {
        AdFlowCore.tryClaimFullScreenSlot()
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
