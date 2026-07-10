package com.adflow.core.engine

import com.adflow.core.AdType
import com.adflow.core.config.ShowIntervalConfig
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdShowIntervalPolicyTest {

    @After
    fun tearDown() {
        AdShowIntervalPolicy.reset()
    }

    @Test
    fun `interstitial after interstitial uses the same-type gap, as one global clock`() {
        AdShowIntervalPolicy.configure(ShowIntervalConfig(interstitialAfterInterstitialMs = 5_000))
        AdShowIntervalPolicy.recordShown(AdType.INTERSTITIAL, now = 0L)
        assertFalse(AdShowIntervalPolicy.canShow(AdType.INTERSTITIAL, now = 4_999L))
        assertTrue(AdShowIntervalPolicy.canShow(AdType.INTERSTITIAL, now = 5_000L))
    }

    @Test
    fun `interstitial after app open uses the cross-type gap`() {
        AdShowIntervalPolicy.configure(ShowIntervalConfig(interstitialAfterAppOpenMs = 3_000))
        AdShowIntervalPolicy.recordShown(AdType.APP_OPEN, now = 0L)
        assertFalse(AdShowIntervalPolicy.canShow(AdType.INTERSTITIAL, now = 2_999L))
        assertTrue(AdShowIntervalPolicy.canShow(AdType.INTERSTITIAL, now = 3_000L))
    }

    @Test
    fun `app open after app open uses the same-type gap, as one global clock`() {
        AdShowIntervalPolicy.configure(ShowIntervalConfig(appOpenAfterAppOpenMs = 4_000))
        AdShowIntervalPolicy.recordShown(AdType.APP_OPEN, now = 0L)
        assertFalse(AdShowIntervalPolicy.canShow(AdType.APP_OPEN, now = 3_999L))
        assertTrue(AdShowIntervalPolicy.canShow(AdType.APP_OPEN, now = 4_000L))
    }

    @Test
    fun `app open after interstitial uses the cross-type gap`() {
        AdShowIntervalPolicy.configure(ShowIntervalConfig(appOpenAfterInterstitialMs = 2_000))
        AdShowIntervalPolicy.recordShown(AdType.INTERSTITIAL, now = 0L)
        assertFalse(AdShowIntervalPolicy.canShow(AdType.APP_OPEN, now = 1_999L))
        assertTrue(AdShowIntervalPolicy.canShow(AdType.APP_OPEN, now = 2_000L))
    }

    @Test
    fun `types other than interstitial and app open are never capped`() {
        AdShowIntervalPolicy.recordShown(AdType.INTERSTITIAL, now = 0L)
        assertTrue(AdShowIntervalPolicy.canShow(AdType.REWARDED, now = 1L))
        assertTrue(AdShowIntervalPolicy.canShow(AdType.NATIVE, now = 1L))
        assertTrue(AdShowIntervalPolicy.canShow(AdType.BANNER, now = 1L))
    }
}
