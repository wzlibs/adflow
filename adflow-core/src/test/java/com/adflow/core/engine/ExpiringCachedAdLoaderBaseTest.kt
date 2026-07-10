package com.adflow.core.engine

import com.adflow.core.AdFlowCore
import com.adflow.core.AdLoadResult
import com.adflow.core.AdType
import com.adflow.core.config.PlacementConfig
import com.adflow.core.config.RetryPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExpiringCachedAdLoaderBaseTest {

    @After
    fun tearDown() {
        AdFlowCore.reset()
    }

    @Test
    fun `loads successfully and is ready while fresh`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"), expiryMs = 1_000)
        val manager = object : ExpiringCachedAdLoaderBase<String>(config, AdType.NATIVE) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                onResult(Result.success("ad-A"))
            }
        }
        var result: AdLoadResult? = null
        manager.load { result = it }
        assertEquals(AdLoadResult.Success, result)
        assertTrue(manager.isReady())
    }

    @Test
    fun `drops the cached ad and reloads once it goes past expiryMs`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"), expiryMs = 1_000)
        var loadCount = 0
        val manager = object : ExpiringCachedAdLoaderBase<String>(config, AdType.NATIVE) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                loadCount += 1
                onResult(Result.success("ad-$loadCount"))
            }
        }
        var now = 0L
        manager.nowProvider = { now }
        manager.load {}
        assertEquals(1, loadCount)
        assertTrue(manager.isReady())

        now = 2_000 // quá expiryMs
        assertFalse(manager.isReady()) // đã cũ (stale), dù dropIfExpired() chưa chạy

        var result: AdLoadResult? = null
        manager.load { result = it }
        assertEquals(AdLoadResult.Success, result)
        assertEquals(2, loadCount) // isReady() là false nên load() tiếp tục chạy 1 waterfall mới
    }

    @Test
    fun `onLoaded is not fired again by the isReady() short-circuit on a fresh ad`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"), expiryMs = 1_000)
        var onLoadedCallCount = 0
        val manager = object : ExpiringCachedAdLoaderBase<String>(config, AdType.NATIVE) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                onResult(Result.success("ad-A"))
            }
            override fun onLoaded(ad: String) {
                super.onLoaded(ad)
                onLoadedCallCount += 1
            }
        }
        manager.load {}
        assertEquals(1, onLoadedCallCount)

        manager.load {} // vẫn còn fresh - isReady() shortcut, không được đụng lại load timestamp
        assertEquals(1, onLoadedCallCount)
    }

    private class ReloadableManager(config: PlacementConfig, val events: MutableList<String>) :
        ExpiringCachedAdLoaderBase<String>(config, AdType.NATIVE) {
        var loadCount = 0

        override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
            loadCount += 1
            onResult(Result.success("ad-$loadCount"))
        }

        override fun onLoaded(ad: String) {
            super.onLoaded(ad)
            events += "onLoaded:$ad"
        }

        override fun onDrop(ad: String) {
            events += "onDrop:$ad"
        }

        fun reload(onResult: (AdLoadResult) -> Unit = {}) = forceLoad(onResult)

        fun dropIfExpiredForTest() = dropIfExpired()

        val cachedAdForTest: String? get() = cachedAd
    }

    @Test
    fun `reload() triggers a real fetch even while the cached ad is still fresh`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"), expiryMs = 1_000)
        val manager = ReloadableManager(config, mutableListOf())
        manager.load {}
        assertEquals(1, manager.loadCount)
        assertTrue(manager.isReady())

        manager.reload {}
        assertEquals(2, manager.loadCount)
    }

    @Test
    fun `reload() success swaps cachedAd and calls onDrop on the previous ad only, after onLoaded`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"), expiryMs = 1_000)
        val events = mutableListOf<String>()
        val manager = ReloadableManager(config, events)

        manager.load {}
        var result: AdLoadResult? = null
        manager.reload { result = it }

        assertEquals(AdLoadResult.Success, result)
        assertEquals("ad-2", manager.cachedAdForTest)
        assertEquals(listOf("onLoaded:ad-1", "onLoaded:ad-2", "onDrop:ad-1"), events)
    }

    private class FlakyReloadManager(config: PlacementConfig, val events: MutableList<String>) :
        ExpiringCachedAdLoaderBase<String>(config, AdType.NATIVE) {
        var loadCount = 0
        var failReload = false

        override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
            loadCount += 1
            if (failReload && loadCount > 1) {
                onResult(Result.failure(RuntimeException("no fill")))
            } else {
                onResult(Result.success("ad-$loadCount"))
            }
        }

        override fun onDrop(ad: String) {
            events += "onDrop:$ad"
        }

        fun reload(onResult: (AdLoadResult) -> Unit = {}) = forceLoad(onResult)

        val cachedAdForTest: String? get() = cachedAd
    }

    @Test
    fun `reload() failure leaves the previous cached ad in place`() {
        val config = PlacementConfig(
            placementId = "p1",
            adUnitIds = listOf("A"),
            expiryMs = 1_000,
            retryPolicy = RetryPolicy(initialDelayMs = 1, multiplier = 1.0, maxDelayMs = 1, maxRetries = 1),
        )
        val manager = FlakyReloadManager(config, mutableListOf())
        val fakeScheduler = mutableListOf<() -> Unit>()
        manager.scheduleRetry = { _, action -> fakeScheduler += action }

        manager.load {}
        assertEquals("ad-1", manager.cachedAdForTest)

        manager.failReload = true
        var result: AdLoadResult? = null
        manager.reload { result = it }
        fakeScheduler.removeAt(0).invoke() // exhaust the single retry -> waterfall fails

        assertTrue(result is AdLoadResult.Failure)
        assertEquals("ad-1", manager.cachedAdForTest)
        assertTrue(manager.isReady())
        assertEquals(emptyList<String>(), manager.events)
    }

    private class AsyncReloadManager(config: PlacementConfig, val events: MutableList<String>) :
        ExpiringCachedAdLoaderBase<String>(config, AdType.NATIVE) {
        val pending = mutableListOf<(Result<String>) -> Unit>()

        override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
            pending += onResult
        }

        override fun onDrop(ad: String) {
            events += "onDrop:$ad"
        }

        fun resolveNext(value: String) = pending.removeAt(0).invoke(Result.success(value))

        fun reload(onResult: (AdLoadResult) -> Unit = {}) = forceLoad(onResult)

        val cachedAdForTest: String? get() = cachedAd
    }

    @Test
    fun `two reload() calls that coalesce onto the same in-flight cycle call onDrop exactly once`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"), expiryMs = 1_000)
        val manager = AsyncReloadManager(config, mutableListOf())
        manager.load {}
        manager.resolveNext("ad-1")
        assertEquals("ad-1", manager.cachedAdForTest)

        val results = mutableListOf<AdLoadResult>()
        manager.reload { results += it }
        manager.reload { results += it }
        assertEquals(1, manager.pending.size) // 2 lệnh reload() coalesce vào cùng 1 network call

        manager.resolveNext("ad-2")

        assertEquals(2, results.size)
        assertTrue(results.all { it == AdLoadResult.Success })
        assertEquals(1, manager.events.count { it.startsWith("onDrop:") })
        assertEquals("ad-2", manager.cachedAdForTest)
    }

    @Test
    fun `dropIfExpired() calls onDrop before nulling cachedAd`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"), expiryMs = 1_000)
        val events = mutableListOf<String>()
        val manager = ReloadableManager(config, events)
        var now = 0L
        manager.nowProvider = { now }
        manager.load {}
        assertEquals("ad-1", manager.cachedAdForTest)

        now = 2_000 // quá expiryMs
        manager.dropIfExpiredForTest()

        assertEquals(null, manager.cachedAdForTest)
        assertEquals(listOf("onLoaded:ad-1", "onDrop:ad-1"), events)
    }
}
