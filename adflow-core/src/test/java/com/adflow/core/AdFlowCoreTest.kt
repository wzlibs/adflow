package com.adflow.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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

    @Test
    fun `runOnFirstForeground does not run before the process reaches the foreground`() {
        var runCount = 0
        AdFlowCore.runOnFirstForeground { runCount++ }

        assertEquals(0, runCount)
    }

    @Test
    fun `runOnFirstForeground runs once the process reaches the foreground`() {
        var runCount = 0
        AdFlowCore.runOnFirstForeground { runCount++ }

        AdFlowCore.onForegroundStart()

        assertEquals(1, runCount)
    }

    @Test
    fun `runOnFirstForeground does not run again on a later foreground transition`() {
        var runCount = 0
        AdFlowCore.runOnFirstForeground { runCount++ }

        AdFlowCore.onForegroundStart()
        AdFlowCore.onForegroundStart() // app quay lại background rồi foreground lần nữa

        assertEquals(1, runCount)
    }

    @Test
    fun `a second call before the first fires is a no-op`() {
        var firstRunCount = 0
        var secondRunCount = 0
        AdFlowCore.runOnFirstForeground { firstRunCount++ }
        AdFlowCore.runOnFirstForeground { secondRunCount++ }

        AdFlowCore.onForegroundStart()

        assertEquals(1, firstRunCount)
        assertEquals(0, secondRunCount)
    }

    @Test
    fun `consentAllowsAdRequests defaults to true so apps without a ConsentManager are unaffected`() {
        assertTrue(AdFlowCore.consentAllowsAdRequests)
    }

    @Test
    fun `updateConsent changes consentAllowsAdRequests`() {
        AdFlowCore.updateConsent(false)
        assertFalse(AdFlowCore.consentAllowsAdRequests)

        AdFlowCore.updateConsent(true)
        assertTrue(AdFlowCore.consentAllowsAdRequests)
    }

    @Test
    fun `reset() restores consentAllowsAdRequests back to true`() {
        AdFlowCore.updateConsent(false)
        AdFlowCore.reset()
        assertTrue(AdFlowCore.consentAllowsAdRequests)
    }
}
