package com.adflow.adflow_flutter

import com.adflow.adflow_flutter.generated.PPlacementConfig
import com.adflow.adflow_flutter.generated.PRetryPolicy
import com.adflow.adflow_flutter.generated.PShowIntervalConfig
import com.adflow.core.AdFlowError
import com.adflow.core.AdLoadResult
import com.adflow.core.revenue.AdRevenueEvent
import com.adflow.core.AdType
import com.adflow.core.BlockReason
import com.adflow.core.consent.ConsentStatus
import com.adflow.core.consent.PrivacyOptionsRequirement
import com.adflow.core.rewarded.RewardItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PigeonMappersTest {

    @Test
    fun `PPlacementConfig toCore never carries loadRule or showRule`() {
        val pConfig = PPlacementConfig(
            placementId = "p1",
            enabled = true,
            preloadEnabled = true,
            adUnitIds = listOf("unit-1", "unit-2"),
            retryPolicy = PRetryPolicy(initialDelayMs = 1000, multiplier = 2.0, maxDelayMs = 8000, maxRetries = 3),
            expiryMs = 1000,
        )

        val core = pConfig.toCore()

        assertEquals("p1", core.placementId)
        assertEquals(listOf("unit-1", "unit-2"), core.adUnitIds)
        assertEquals(3, core.retryPolicy.maxRetries)
        assertNull(core.loadRule)
        assertNull(core.showRule)
    }

    @Test
    fun `PShowIntervalConfig toCore maps every field`() {
        val core = PShowIntervalConfig(
            interstitialAfterInterstitialMs = 1,
            appOpenAfterAppOpenMs = 2,
            interstitialAfterAppOpenMs = 3,
            appOpenAfterInterstitialMs = 4,
        ).toCore()

        assertEquals(1, core.interstitialAfterInterstitialMs)
        assertEquals(2, core.appOpenAfterAppOpenMs)
        assertEquals(3, core.interstitialAfterAppOpenMs)
        assertEquals(4, core.appOpenAfterInterstitialMs)
    }

    @Test
    fun `BlockReason and AdType map by enum name`() {
        assertEquals("DISABLED", BlockReason.DISABLED.toPigeon().name)
        assertEquals("ANOTHER_AD_SHOWING", BlockReason.ANOTHER_AD_SHOWING.toPigeon().name)
        assertEquals("APP_OPEN", AdType.APP_OPEN.toPigeon().name)
    }

    @Test
    fun `ConsentStatus and PrivacyOptionsRequirement map by enum name`() {
        assertEquals("OBTAINED", ConsentStatus.OBTAINED.toPigeon().name)
        assertEquals("NOT_REQUIRED", ConsentStatus.NOT_REQUIRED.toPigeon().name)
        assertEquals("REQUIRED", PrivacyOptionsRequirement.REQUIRED.toPigeon().name)
    }

    @Test
    fun `AdLoadResult Success and Failure map to PLoadResult`() {
        val success = (AdLoadResult.Success as AdLoadResult).toPigeon()
        assertEquals(true, success.success)
        assertNull(success.error)

        val failure = AdLoadResult.Failure(AdFlowError(7, "boom")).toPigeon()
        assertEquals(false, failure.success)
        assertEquals(7, failure.error?.code)
        assertEquals("boom", failure.error?.message)
    }

    @Test
    fun `RewardItem and AdRevenueEvent map every field`() {
        val pReward = RewardItem(type = "coins", amount = 10).toPigeon()
        assertEquals("coins", pReward.type)
        assertEquals(10, pReward.amount)

        val pEvent = AdRevenueEvent(
            placementId = "p1",
            adType = AdType.REWARDED,
            adUnitId = "unit-1",
            valueMicros = 1_000_000,
            currencyCode = "USD",
            precision = "ESTIMATED",
            adNetwork = "AdMob",
        ).toPigeon()
        assertEquals("p1", pEvent.placementId)
        assertEquals("REWARDED", pEvent.adType.name)
        assertEquals(1_000_000, pEvent.valueMicros)
        assertEquals("AdMob", pEvent.adNetwork)
    }
}
