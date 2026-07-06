package com.adflow.admob.fullscreen

import android.content.Context
import com.adflow.core.AdLoadResult
import com.adflow.core.PlacementConfig
import com.adflow.core.RetryPolicy
import com.google.android.gms.ads.rewarded.RewardedAd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Proves that AdMobRewardedAdManager's load/retry plumbing - now delegated to the shared
 * [com.adflow.core.RetryingAdLoader] - retries the waterfall with an injectable scheduler,
 * exactly like the full-screen managers built on [com.adflow.core.FullScreenAdManagerBase].
 * Before this refactor this behavior was unit-untestable because the retry scheduler was a
 * hardcoded `Handler(...).postDelayed(...)` call.
 */
@RunWith(RobolectricTestRunner::class)
class AdMobRewardedAdManagerTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private class TestableRewardedAdManager(
        context: Context,
        config: PlacementConfig,
        private val loadResults: MutableList<Result<RewardedAd>>,
    ) : AdMobRewardedAdManager(context, config) {
        var attemptCount = 0

        override fun requestAd(adUnitId: String, onResult: (Result<RewardedAd>) -> Unit) {
            attemptCount += 1
            onResult(if (loadResults.isNotEmpty()) loadResults.removeAt(0) else Result.failure(RuntimeException("no fill")))
        }
    }

    @Test
    fun `falls through the waterfall then retries with backoff before exhausting retries`() {
        val config = PlacementConfig(
            placementId = "p1",
            adUnitIds = listOf("A", "B"),
            retryPolicy = RetryPolicy(initialDelayMs = 1, multiplier = 1.0, maxDelayMs = 1, maxRetries = 1),
        )
        val manager = TestableRewardedAdManager(context, config, mutableListOf())
        val fakeScheduler = mutableListOf<() -> Unit>()
        manager.scheduleRetry = { _, action -> fakeScheduler += action }

        var result: AdLoadResult? = null
        manager.load { result = it }

        // First waterfall pass tried both ad units and failed; scheduled exactly one retry.
        assertEquals(2, manager.attemptCount)
        assertEquals(null, result) // still retrying, waiting on the scheduler
        assertEquals(1, fakeScheduler.size)

        fakeScheduler.removeAt(0).invoke() // run the retry synchronously

        assertEquals(4, manager.attemptCount)
        assertTrue(result is AdLoadResult.Failure)
        assertFalse(manager.isReady())
    }
}
