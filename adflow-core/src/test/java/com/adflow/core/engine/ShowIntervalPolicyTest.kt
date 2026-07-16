package com.adflow.core.engine

import com.adflow.core.AdType
import com.adflow.core.config.ShowIntervalConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowIntervalPolicyTest {

    @Test
    fun `canShow is true with no prior shown ad of either type`() {
        val policy = ShowIntervalPolicy(ShowIntervalConfig(), clock = { 0L })
        assertTrue(policy.canShow(AdType.INTERSTITIAL))
        assertTrue(policy.canShow(AdType.APP_OPEN))
    }

    @Test
    fun `same-type gap blocks a second Interstitial before the gap elapses`() {
        var now = 0L
        val policy = ShowIntervalPolicy(ShowIntervalConfig(interstitialAfterInterstitialMs = 30_000), clock = { now })

        policy.recordDismissed(AdType.INTERSTITIAL)
        now = 10_000
        assertFalse(policy.canShow(AdType.INTERSTITIAL))

        now = 30_000
        assertTrue(policy.canShow(AdType.INTERSTITIAL))
    }

    @Test
    fun `cross-type gap blocks App Open shortly after an Interstitial was dismissed`() {
        var now = 0L
        val policy = ShowIntervalPolicy(ShowIntervalConfig(appOpenAfterInterstitialMs = 6_000), clock = { now })

        policy.recordDismissed(AdType.INTERSTITIAL)
        now = 3_000
        assertFalse(policy.canShow(AdType.APP_OPEN))

        now = 6_000
        assertTrue(policy.canShow(AdType.APP_OPEN))
    }

    @Test
    fun `Rewarded Native Banner are never gated by the interval policy`() {
        var now = 0L
        val policy = ShowIntervalPolicy(ShowIntervalConfig(), clock = { now })
        policy.recordDismissed(AdType.INTERSTITIAL)
        policy.recordDismissed(AdType.APP_OPEN)

        assertTrue(policy.canShow(AdType.REWARDED))
        assertTrue(policy.canShow(AdType.NATIVE))
        assertTrue(policy.canShow(AdType.BANNER))
    }

    @Test
    fun `updateConfig takes effect immediately without resetting in-flight cooldown`() {
        var now = 0L
        val policy = ShowIntervalPolicy(ShowIntervalConfig(interstitialAfterInterstitialMs = 30_000), clock = { now })

        policy.recordDismissed(AdType.INTERSTITIAL)
        now = 2_000
        assertFalse(policy.canShow(AdType.INTERSTITIAL))

        // Dữ liệu remote mới về giữa chừng: gap rút xuống còn 2s - không cần khởi tạo lại policy.
        policy.updateConfig(ShowIntervalConfig(interstitialAfterInterstitialMs = 2_000))
        assertTrue(policy.canShow(AdType.INTERSTITIAL))
    }
}
