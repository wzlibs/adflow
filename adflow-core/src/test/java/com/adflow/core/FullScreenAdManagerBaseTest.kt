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
        AdFlowCore.reset() // also resets AdShowIntervalPolicy internally
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
    fun `successful show consumes the cached ad and preloads again once the ad is dismissed`() {
        val config = PlacementConfig(
            placementId = "p1",
            adUnitIds = listOf("A"),
            preloadEnabled = true,
        )
        var loadCount = 0
        var deferredDismiss: (() -> Unit)? = null
        val manager = object : FullScreenAdManagerBase<String>(config, AdType.INTERSTITIAL) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                loadCount += 1
                onResult(Result.success("ad-$loadCount"))
            }
            override fun performShow(ad: String, activity: Activity, callback: ShowCallback) {
                callback.onAdShown()
                deferredDismiss = { callback.onAdDismissed() } // simulate the ad still being on screen
            }
        }
        manager.load {}
        assertEquals(1, loadCount)

        manager.show(activity, ShowCallback.NONE)
        assertEquals(1, loadCount) // ad is still on screen (not dismissed yet) - no preload yet

        deferredDismiss?.invoke()
        assertEquals(2, loadCount) // preload triggered only once the ad was actually dismissed
    }

    @Test
    fun `preload also triggers when the ad fails to show, not only on dismiss`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"), preloadEnabled = true)
        var loadCount = 0
        var deferredFail: (() -> Unit)? = null
        val manager = object : FullScreenAdManagerBase<String>(config, AdType.INTERSTITIAL) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                loadCount += 1
                onResult(Result.success("ad-$loadCount"))
            }
            override fun performShow(ad: String, activity: Activity, callback: ShowCallback) {
                deferredFail = { callback.onAdFailedToShow(AdFlowError(-1, "boom")) }
            }
        }
        manager.load {}
        assertEquals(1, loadCount)

        manager.show(activity, ShowCallback.NONE)
        assertEquals(1, loadCount) // failure hasn't actually happened yet - no preload yet

        deferredFail?.invoke()
        assertEquals(2, loadCount) // preload triggered once the failure was actually reported
    }

    @Test
    fun `show-interval is recorded when the ad is dismissed, not when show() is invoked`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        var deferredDismiss: (() -> Unit)? = null
        val manager = object : FullScreenAdManagerBase<String>(config, AdType.INTERSTITIAL) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                onResult(Result.success("ad-A"))
            }
            override fun performShow(ad: String, activity: Activity, callback: ShowCallback) {
                deferredDismiss = { callback.onAdDismissed() } // simulate the ad still being on screen
            }
        }
        manager.nowProvider = { 1_000L }
        manager.load {}
        manager.show(activity, ShowCallback.NONE)

        // The ad is still on screen - display duration is out of our control, so the interval clock
        // must not start until the ad is actually dismissed, not the instant show() was called.
        assertTrue(AdShowIntervalPolicy.canShow(AdType.INTERSTITIAL, now = 1_000L))

        deferredDismiss?.invoke()
        assertFalse(AdShowIntervalPolicy.canShow(AdType.INTERSTITIAL, now = 1_000L))
    }

    @Test
    fun `onAdShown and onAdDismissed are forwarded to the caller's callback`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        val manager = FakeManager(config, mutableMapOf("A" to Result.success("ad-A")))
        manager.load {}

        var shown = false
        var dismissed = false
        manager.show(
            activity,
            object : ShowCallback {
                override fun onAdShown() { shown = true }
                override fun onAdDismissed() { dismissed = true }
            },
        )
        assertTrue(shown)
        assertTrue(dismissed)
    }

    @Test
    fun `AdFlowCore isShowingFullScreenAd is true only while the ad is actually on screen`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        var deferredDismiss: (() -> Unit)? = null
        var showingWhileOnScreen = false
        val manager = object : FullScreenAdManagerBase<String>(config, AdType.INTERSTITIAL) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                onResult(Result.success("ad-A"))
            }
            override fun performShow(ad: String, activity: Activity, callback: ShowCallback) {
                showingWhileOnScreen = AdFlowCore.isShowingFullScreenAd
                deferredDismiss = { callback.onAdDismissed() }
            }
        }
        manager.load {}

        assertFalse(AdFlowCore.isShowingFullScreenAd) // not shown yet
        manager.show(activity, ShowCallback.NONE)
        assertTrue(showingWhileOnScreen) // set before performShow() hands off to the SDK
        assertTrue(AdFlowCore.isShowingFullScreenAd) // still on screen - dismiss hasn't fired yet

        deferredDismiss?.invoke()
        assertFalse(AdFlowCore.isShowingFullScreenAd) // cleared once dismissed
    }

    @Test
    fun `AdFlowCore isShowingFullScreenAd is cleared when the ad fails to show`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        val manager = object : FullScreenAdManagerBase<String>(config, AdType.INTERSTITIAL) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                onResult(Result.success("ad-A"))
            }
            override fun performShow(ad: String, activity: Activity, callback: ShowCallback) {
                callback.onAdFailedToShow(AdFlowError(-1, "boom"))
            }
        }
        manager.load {}
        manager.show(activity, ShowCallback.NONE)
        assertFalse(AdFlowCore.isShowingFullScreenAd)
    }

    @Test
    fun `show blocks a second full-screen ad from a different manager while one is already on screen`() {
        val configA = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        val configB = PlacementConfig(placementId = "p2", adUnitIds = listOf("B"))
        val managerA = object : FullScreenAdManagerBase<String>(configA, AdType.INTERSTITIAL) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                onResult(Result.success("ad-A"))
            }
            override fun performShow(ad: String, activity: Activity, callback: ShowCallback) {
                // Deliberately never dismisses - ad A stays on screen for the rest of this test.
            }
        }
        var managerBShown = false
        val managerB = object : FullScreenAdManagerBase<String>(configB, AdType.APP_OPEN) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                onResult(Result.success("ad-B"))
            }
            override fun performShow(ad: String, activity: Activity, callback: ShowCallback) {
                managerBShown = true
            }
        }
        managerA.load {}
        managerB.load {}
        managerA.show(activity, ShowCallback.NONE)

        var blockedReason: BlockReason? = null
        managerB.show(activity, object : ShowCallback {
            override fun onShowBlocked(reason: BlockReason) { blockedReason = reason }
        })

        assertEquals(BlockReason.ANOTHER_AD_SHOWING, blockedReason)
        assertFalse(managerBShown) // must never be displayed on top of ad A
        assertTrue(managerB.isReady()) // the blocked attempt must not have consumed/lost its cached ad
    }

    @Test
    fun `AdFlowCore isShowingFullScreenAd is cleared even if performShow throws synchronously`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        val manager = object : FullScreenAdManagerBase<String>(config, AdType.INTERSTITIAL) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                onResult(Result.success("ad-A"))
            }
            override fun performShow(ad: String, activity: Activity, callback: ShowCallback) {
                throw IllegalStateException("SDK blew up")
            }
        }
        manager.load {}

        try {
            manager.show(activity, ShowCallback.NONE)
            org.junit.Assert.fail("expected the exception to propagate")
        } catch (e: IllegalStateException) {
            // expected - the exception must still propagate, not be swallowed
        }

        assertFalse(AdFlowCore.isShowingFullScreenAd) // must not be left stuck true
    }

    @Test
    fun `a synchronous throw from performShow triggers a fresh load so the placement recovers`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        var loadCount = 0
        val manager = object : FullScreenAdManagerBase<String>(config, AdType.INTERSTITIAL) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                loadCount += 1
                onResult(Result.success("ad-$loadCount"))
            }
            override fun performShow(ad: String, activity: Activity, callback: ShowCallback) {
                throw IllegalStateException("SDK blew up")
            }
        }
        manager.load {}
        assertEquals(1, loadCount)

        try {
            manager.show(activity, ShowCallback.NONE)
            org.junit.Assert.fail("expected the exception to propagate")
        } catch (e: IllegalStateException) {
            // expected - the exception must still propagate, not be swallowed
        }

        // The consumed ad is gone (and its state after a synchronous SDK throw is unknown), so the
        // placement must self-heal with a fresh load instead of being stuck reporting not-ready
        // until some unrelated caller happens to call load() again.
        assertEquals(2, loadCount)
        assertTrue(manager.isReady())
    }
}
