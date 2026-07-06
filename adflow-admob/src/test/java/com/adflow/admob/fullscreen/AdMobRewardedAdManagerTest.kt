package com.adflow.admob.fullscreen

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.adflow.core.AdFlowCore
import com.adflow.core.AdLoadResult
import com.adflow.core.BlockReason
import com.adflow.core.PlacementConfig
import com.adflow.core.RetryPolicy
import com.adflow.core.RewardedAdCallback
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.rewarded.OnAdMetadataChangedListener
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()

    // RewardedAd is abstract with a public no-arg constructor and only abstract members, so it's
    // directly fakeable here without Mockito (unlike NativeAd, see AdMobNativeAdManagerTest).
    private class FakeRewardedAd(private val throwOnShow: Boolean = false) : RewardedAd() {
        override fun setServerSideVerificationOptions(options: ServerSideVerificationOptions?) {}
        override fun setOnAdMetadataChangedListener(listener: OnAdMetadataChangedListener?) {}
        override fun getOnAdMetadataChangedListener(): OnAdMetadataChangedListener? = null
        override fun getAdMetadata(): Bundle = Bundle()
        override fun show(activity: Activity, listener: OnUserEarnedRewardListener) {
            if (throwOnShow) throw IllegalStateException("SDK blew up")
        }
        override fun getRewardItem(): RewardItem = RewardItem.DEFAULT_REWARD
        // ResponseInfo has no public constructor and isn't needed by the blocked-show path this
        // fake exists for; only satisfies the NonNull-return override requirement at compile time.
        override fun getResponseInfo(): ResponseInfo = error("ResponseInfo not available on this fake")
        override fun setOnPaidEventListener(listener: OnPaidEventListener?) {}
        override fun getOnPaidEventListener(): OnPaidEventListener? = null
        override fun setFullScreenContentCallback(callback: FullScreenContentCallback?) {}
        override fun getFullScreenContentCallback(): FullScreenContentCallback? = null
        override fun getAdUnitId(): String = "unit"
        override fun setImmersiveMode(immersive: Boolean) {}
        override fun getPlacementId(): Long = 0L
        override fun setPlacementId(id: Long) {}
    }

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

    @After
    fun tearDown() {
        AdFlowCore.releaseFullScreenSlot()
    }

    @Test
    fun `show is blocked when another full-screen ad is already showing, and keeps its cached ad`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        val manager = TestableRewardedAdManager(context, config, mutableListOf(Result.success(FakeRewardedAd())))
        manager.load {}
        assertTrue(manager.isReady())
        AdFlowCore.tryClaimFullScreenSlot() // simulates an Interstitial/AppOpen/other Rewarded already on screen

        var blockedReason: BlockReason? = null
        manager.show(activity, object : RewardedAdCallback {
            override fun onShowBlocked(reason: BlockReason) { blockedReason = reason }
        })

        assertEquals(BlockReason.ANOTHER_AD_SHOWING, blockedReason)
        assertTrue(manager.isReady()) // the blocked attempt must not have consumed/lost its cached ad
    }

    @Test
    fun `a synchronous throw from ad show() triggers a fresh load so the placement recovers`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        val manager = TestableRewardedAdManager(context, config, mutableListOf(Result.success(FakeRewardedAd(throwOnShow = true))))
        manager.load {}
        assertEquals(1, manager.attemptCount)

        try {
            manager.show(activity, RewardedAdCallback.NONE)
            org.junit.Assert.fail("expected the exception to propagate")
        } catch (e: IllegalStateException) {
            // expected - the exception must still propagate, not be swallowed
        }

        // The consumed ad is gone (and its state after a synchronous SDK throw is unknown), so the
        // placement must self-heal with a fresh load instead of being stuck reporting not-ready.
        assertEquals(2, manager.attemptCount)
    }

    @Test
    fun `show on a never-loaded placement blocks as not-ready and triggers a fresh load`() {
        // Regression test: show() used to just report NOT_READY and stop, leaving the placement
        // permanently stuck if the caller never manually called load() again. It must now kick off
        // a fresh load itself - same fix applied to FullScreenAdManagerBase's expired-ad path.
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        val manager = TestableRewardedAdManager(context, config, mutableListOf())

        var blockedReason: BlockReason? = null
        manager.show(activity, object : RewardedAdCallback {
            override fun onShowBlocked(reason: BlockReason) { blockedReason = reason }
        })

        assertEquals(BlockReason.NOT_READY, blockedReason)
        assertEquals(1, manager.attemptCount) // show() triggered load(), which attempted ad unit "A"
    }
}
