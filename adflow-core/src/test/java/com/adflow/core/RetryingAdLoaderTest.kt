package com.adflow.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RetryingAdLoaderTest {

    @After
    fun tearDown() {
        AdFlowCore.reset()
    }

    @Test
    fun `loads successfully on the first ad unit`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A", "B"))
        val loader = RetryingAdLoader(config, AdType.INTERSTITIAL) { adUnitId, onResult ->
            onResult(if (adUnitId == "A") Result.success("ad-A") else Result.failure(RuntimeException("no fill")))
        }

        var result: AdLoadResult? = null
        var loadedAd: String? = null
        loader.start { r, ad -> result = r; loadedAd = ad }

        assertEquals(AdLoadResult.Success, result)
        assertEquals("ad-A", loadedAd)
    }

    @Test
    fun `falls through the waterfall then retries the whole waterfall again after a backoff delay`() {
        val fakeScheduler = mutableListOf<() -> Unit>()
        val config = PlacementConfig(
            placementId = "p1",
            adUnitIds = listOf("A", "B"),
            retryPolicy = RetryPolicy(initialDelayMs = 1, multiplier = 1.0, maxDelayMs = 1, maxRetries = 1),
        )
        var attempts = 0
        val loader = RetryingAdLoader<String>(config, AdType.INTERSTITIAL) { _, onResult ->
            attempts += 1
            onResult(Result.failure(RuntimeException("no fill")))
        }
        loader.scheduleRetry = { _, action -> fakeScheduler += action }

        var result: AdLoadResult? = null
        loader.start { r, _ -> result = r }

        // First waterfall pass tried both ad units and failed; scheduled exactly one retry.
        assertEquals(2, attempts)
        assertEquals(null, result) // still retrying, waiting on the scheduler
        assertEquals(1, fakeScheduler.size)

        fakeScheduler.removeAt(0).invoke() // run the retry synchronously

        // Retry re-ran the whole waterfall (2 more attempts) then exhausted retries.
        assertEquals(4, attempts)
        assertTrue(result is AdLoadResult.Failure)
    }

    @Test
    fun `retry delay follows the configured retry policy backoff schedule`() {
        val delays = mutableListOf<Long>()
        val fakeScheduler = mutableListOf<() -> Unit>()
        val config = PlacementConfig(
            placementId = "p1",
            adUnitIds = listOf("A"),
            retryPolicy = RetryPolicy(initialDelayMs = 100, multiplier = 2.0, maxDelayMs = 10_000, maxRetries = 3),
        )
        val loader = RetryingAdLoader<String>(config, AdType.INTERSTITIAL) { _, onResult ->
            onResult(Result.failure(RuntimeException("no fill")))
        }
        loader.scheduleRetry = { delayMs, action -> delays += delayMs; fakeScheduler += action }

        loader.start { _, _ -> }
        while (fakeScheduler.isNotEmpty()) {
            fakeScheduler.removeAt(0).invoke()
        }

        assertEquals(listOf(100L, 200L, 400L), delays)
    }
}
