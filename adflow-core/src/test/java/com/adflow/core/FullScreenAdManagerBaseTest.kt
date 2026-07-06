package com.adflow.core

import android.app.Activity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FullScreenAdManagerBaseTest {

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()

    private class FakeManager(
        config: PlacementConfig,
        private val loadResults: MutableMap<String, Result<String>>,
    ) : FullScreenAdManagerBase<String>(config, AdType.INTERSTITIAL) {
        var shownAd: String? = null

        override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
            onResult(loadResults[adUnitId] ?: Result.failure(RuntimeException("no fill")))
        }

        override fun performShow(ad: String, activity: Activity, callback: ShowCallback) {
            shownAd = ad
            callback.onAdShown()
            callback.onAdDismissed()
        }
    }

    @After
    fun tearDown() {
        AdFlowCore.reset()
    }

    @Test
    fun `loads successfully on the first ad unit`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A", "B"))
        val manager = FakeManager(config, mutableMapOf("A" to Result.success("ad-A")))
        var result: AdLoadResult? = null
        manager.load { result = it }
        assertEquals(AdLoadResult.Success, result)
        assertTrue(manager.isReady())
    }

    @Test
    fun `load is a no-op success when a fresh ad is already cached`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        var requestCount = 0
        val manager = object : FullScreenAdManagerBase<String>(config, AdType.INTERSTITIAL) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                requestCount += 1
                onResult(Result.success("ad-$requestCount"))
            }
            override fun performShow(ad: String, activity: Activity, callback: ShowCallback) {}
        }
        manager.load {}
        assertEquals(1, requestCount)

        var result: AdLoadResult? = null
        manager.load { result = it }
        assertEquals(1, requestCount) // still fresh - load() must not start a second waterfall
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
    fun `show is blocked when the placement is not ready`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        val manager = FakeManager(config, mutableMapOf())
        var blockedReason: BlockReason? = null
        manager.show(activity, object : ShowCallback {
            override fun onShowBlocked(reason: BlockReason) { blockedReason = reason }
        })
        assertEquals(BlockReason.NOT_READY, blockedReason)
    }

    @Test
    fun `show is blocked by a rejecting showRule even when ready`() {
        val config = PlacementConfig(
            placementId = "p1",
            adUnitIds = listOf("A"),
            showRule = AdRule { false },
        )
        val manager = FakeManager(config, mutableMapOf("A" to Result.success("ad-A")))
        manager.load {}
        var blockedReason: BlockReason? = null
        manager.show(activity, object : ShowCallback {
            override fun onShowBlocked(reason: BlockReason) { blockedReason = reason }
        })
        assertEquals(BlockReason.RULE_REJECTED, blockedReason)
        assertEquals(null, manager.shownAd)
    }

    @Test
    fun `show is blocked when the show-interval has not elapsed`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        val manager = FakeManager(config, mutableMapOf("A" to Result.success("ad-A")))
        manager.nowProvider = { 0L }
        manager.load {}
        AdShowIntervalPolicy.recordShown(AdType.INTERSTITIAL, now = 0L)

        var blockedReason: BlockReason? = null
        manager.show(activity, object : ShowCallback {
            override fun onShowBlocked(reason: BlockReason) { blockedReason = reason }
        })
        assertEquals(BlockReason.INTERVAL_NOT_ELAPSED, blockedReason)
    }

    @Test
    fun `show on an expired ad drops the stale ad and triggers a fresh load instead of staying stuck`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"), expiryMs = 1_000)
        var loadCount = 0
        val manager = object : FullScreenAdManagerBase<String>(config, AdType.INTERSTITIAL) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                loadCount += 1
                onResult(Result.success("ad-$loadCount"))
            }
            override fun performShow(ad: String, activity: Activity, callback: ShowCallback) {}
        }
        var now = 0L
        manager.nowProvider = { now }
        manager.load {}
        assertEquals(1, loadCount)
        assertTrue(manager.isReady())

        now = 2_000 // past expiryMs, the cached ad is now stale
        var blockedReason: BlockReason? = null
        manager.show(activity, object : ShowCallback {
            override fun onShowBlocked(reason: BlockReason) { blockedReason = reason }
        })

        assertEquals(BlockReason.NOT_READY, blockedReason) // the stale ad itself must never be shown
        assertEquals(2, loadCount) // a fresh load was kicked off automatically, not left stuck forever
        assertTrue(manager.isReady()) // and the placement actually recovers instead of staying blocked
    }

    @Test
    fun `successful show consumes the cached ad and preloads again when enabled`() {
        val config = PlacementConfig(
            placementId = "p1",
            adUnitIds = listOf("A"),
            preloadEnabled = true,
        )
        var loadCount = 0
        val manager = object : FullScreenAdManagerBase<String>(config, AdType.INTERSTITIAL) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                loadCount += 1
                onResult(Result.success("ad-$loadCount"))
            }
            override fun performShow(ad: String, activity: Activity, callback: ShowCallback) {
                callback.onAdShown()
            }
        }
        manager.load {}
        assertEquals(1, loadCount)
        manager.show(activity, ShowCallback.NONE)
        assertEquals(2, loadCount) // preload triggered a second load
    }
}
