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
        assertEquals(1, requestCount) // vẫn còn cached - load() không được chạy thêm 1 waterfall
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
        assertEquals(null, result) // vẫn đang retry, chờ scheduler
        assertEquals(1, fakeScheduler.size)

        fakeScheduler.removeAt(0).invoke() // chạy lần retry đồng bộ
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
    fun `load reports consent-blocked failure without touching the network when canRequestAds is false`() {
        var requestCount = 0
        val manager = object : SimpleCachedAdLoaderBase<String>(
            PlacementConfig(placementId = "p1", adUnitIds = listOf("A")),
            AdType.BANNER,
        ) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                requestCount += 1
                onResult(Result.success("ad"))
            }
        }

        AdFlowCore.updateConsent(false)
        var result: AdLoadResult? = null
        manager.load { result = it }

        assertTrue(result is AdLoadResult.Failure)
        assertEquals(0, requestCount)

        AdFlowCore.updateConsent(true)
        manager.load { result = it }
        assertEquals(AdLoadResult.Success, result)
        assertEquals(1, requestCount)
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
                cachedAdWhenOnLoadedFired = cachedAd // phải đã phản ánh đúng ad vừa load xong
            }
        }

        manager.load {}
        assertEquals(1, onLoadedCallCount)
        assertEquals("ad-A", cachedAdWhenOnLoadedFired)

        // isReady() shortcut - 1 lần load() dư thừa trong khi đã cached không được gọi lại onLoaded.
        manager.load {}
        assertEquals(1, onLoadedCallCount)
    }

    @Test
    fun `onLoaded fires exactly once even when two load() calls coalesce onto the same in-flight cycle`() {
        val fakeScheduler = mutableListOf<() -> Unit>()
        var onLoadedCallCount = 0
        var attempts = 0
        val manager = object : SimpleCachedAdLoaderBase<String>(
            PlacementConfig(
                placementId = "p1",
                adUnitIds = listOf("A"),
                retryPolicy = RetryPolicy(initialDelayMs = 1, multiplier = 1.0, maxDelayMs = 1, maxRetries = 1),
            ),
            AdType.BANNER,
        ) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                attempts += 1
                // Cho attempt đầu fail để cycle vẫn đang chạy (retry đã schedule), tạo khoảng thời
                // gian cho 1 lần load() thứ 2 coalesce vào trước khi nó resolve.
                if (attempts == 1) onResult(Result.failure(RuntimeException("no fill"))) else onResult(Result.success("ad-A"))
            }

            override fun onLoaded(ad: String) {
                onLoadedCallCount += 1
            }
        }
        manager.scheduleRetry = { _, action -> fakeScheduler += action }

        var firstResult: AdLoadResult? = null
        var secondResult: AdLoadResult? = null
        manager.load { firstResult = it }
        assertEquals(1, fakeScheduler.size) // attempt đầu fail, đã schedule retry, vẫn đang chạy

        manager.load { secondResult = it } // coalesce vào cycle retry đang chạy, theo cơ chế RetryingAdLoader

        fakeScheduler.removeAt(0).invoke() // chạy lần retry đồng bộ - lần này thành công

        assertEquals(AdLoadResult.Success, firstResult)
        assertEquals(AdLoadResult.Success, secondResult)
        // Cả 2 callback được coalesce đều chạy chung nhánh "cache ad" - onLoaded vẫn chỉ được gọi
        // đúng 1 lần cho 1 lần load thật, không phải 1 lần cho mỗi caller được coalesce.
        assertEquals(1, onLoadedCallCount)
    }
}
