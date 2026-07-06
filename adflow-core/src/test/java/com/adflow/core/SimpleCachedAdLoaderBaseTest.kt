package com.adflow.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SimpleCachedAdLoaderBaseTest {

    private class FakeManager(
        config: PlacementConfig,
        private val loadResults: MutableMap<String, Result<String>>,
    ) : SimpleCachedAdLoaderBase<String>(config, AdType.BANNER) {
        override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
            onResult(loadResults[adUnitId] ?: Result.failure(RuntimeException("no fill")))
        }
    }

    @After
    fun tearDown() {
        AdFlowCore.reset()
    }

    @Test
    fun `loads successfully and becomes ready, with no expiry`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        val manager = FakeManager(config, mutableMapOf("A" to Result.success("ad-A")))
        var result: AdLoadResult? = null
        manager.load { result = it }
        assertEquals(AdLoadResult.Success, result)
        assertTrue(manager.isReady())
    }

    @Test
    fun `load is a no-op success when an ad is already cached`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        var requestCount = 0
        val manager = object : SimpleCachedAdLoaderBase<String>(config, AdType.BANNER) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                requestCount += 1
                onResult(Result.success("ad-$requestCount"))
            }
        }
        manager.load {}
        assertEquals(1, requestCount)

        var result: AdLoadResult? = null
        manager.load { result = it }
        assertEquals(1, requestCount) // still cached - load() must not start a second waterfall
        assertEquals(AdLoadResult.Success, result)
    }

    @Test
    fun `falls through the waterfall then reports failure once retries are exhausted`() {
        val fakeScheduler = mutableListOf<() -> Unit>()
        val config = PlacementConfig(
            placementId = "p1",
            adUnitIds = listOf("A", "B"),
            retryPolicy = RetryPolicy(initialDelayMs = 1, multiplier = 1.0, maxDelayMs = 1, maxRetries = 1),
        )
        val manager = FakeManager(config, mutableMapOf())
        manager.scheduleRetry = { _, action -> fakeScheduler += action }

        var result: AdLoadResult? = null
        manager.load { result = it }
        assertEquals(null, result) // still retrying, waiting on the scheduler
        assertEquals(1, fakeScheduler.size)

        fakeScheduler.removeAt(0).invoke() // run the retry synchronously
        assertTrue(result is AdLoadResult.Failure)
        assertFalse(manager.isReady())
    }

    @Test
    fun `load reports disabled and loadRule-rejected failures without touching the network`() {
        var requestCount = 0
        val disabledManager = object : SimpleCachedAdLoaderBase<String>(
            PlacementConfig(placementId = "p1", adUnitIds = listOf("A"), enabled = false),
            AdType.BANNER,
        ) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                requestCount += 1
                onResult(Result.success("ad"))
            }
        }
        var result: AdLoadResult? = null
        disabledManager.load { result = it }
        assertTrue(result is AdLoadResult.Failure)
        assertEquals(0, requestCount)

        val ruleRejectedManager = object : SimpleCachedAdLoaderBase<String>(
            PlacementConfig(placementId = "p1", adUnitIds = listOf("A"), loadRule = AdRule { false }),
            AdType.BANNER,
        ) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                requestCount += 1
                onResult(Result.success("ad"))
            }
        }
        var ruleResult: AdLoadResult? = null
        ruleRejectedManager.load { ruleResult = it }
        assertTrue(ruleResult is AdLoadResult.Failure)
        assertEquals(0, requestCount)
    }

    @Test
    fun `onLoaded fires exactly once per genuine new load, after cachedAd is already set`() {
        var onLoadedCallCount = 0
        var cachedAdWhenOnLoadedFired: String? = null
        val manager = object : SimpleCachedAdLoaderBase<String>(
            PlacementConfig(placementId = "p1", adUnitIds = listOf("A")),
            AdType.BANNER,
        ) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                onResult(Result.success("ad-A"))
            }

            override fun onLoaded(ad: String) {
                onLoadedCallCount += 1
                cachedAdWhenOnLoadedFired = cachedAd // must already reflect the just-loaded ad
            }
        }

        manager.load {}
        assertEquals(1, onLoadedCallCount)
        assertEquals("ad-A", cachedAdWhenOnLoadedFired)

        // isReady() shortcut - a redundant load() while already cached must not fire onLoaded again.
        manager.load {}
        assertEquals(1, onLoadedCallCount)
    }
}
