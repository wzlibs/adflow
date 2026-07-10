package com.adflow.admob.fullscreen

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.adflow.core.AdFlowCore
import com.adflow.core.AdLoadResult
import com.adflow.core.BlockReason
import com.adflow.core.config.PlacementConfig
import com.adflow.core.config.RetryPolicy
import com.adflow.core.rewarded.RewardedAdCallback
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
 * Chứng minh phần load/retry của AdMobRewardedAdManager - giờ đã giao cho
 * [com.adflow.core.RetryingAdLoader] dùng chung - retry waterfall với scheduler có thể inject
 * được, giống hệt các full-screen manager xây trên [com.adflow.core.FullScreenAdManagerBase].
 * Trước khi refactor này, hành vi này không thể unit-test được vì retry scheduler là 1 lệnh
 * `Handler(...).postDelayed(...)` hardcode.
 */
@RunWith(RobolectricTestRunner::class)
class AdMobRewardedAdManagerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()

    // RewardedAd là abstract với constructor public không tham số và chỉ có member abstract, nên
    // fake được trực tiếp ở đây mà không cần Mockito (khác với NativeAd, xem AdMobNativeAdManagerTest).
    private class FakeRewardedAd(private val throwOnShow: Boolean = false) : RewardedAd() {
        override fun setServerSideVerificationOptions(options: ServerSideVerificationOptions?) {}
        override fun setOnAdMetadataChangedListener(listener: OnAdMetadataChangedListener?) {}
        override fun getOnAdMetadataChangedListener(): OnAdMetadataChangedListener? = null
        override fun getAdMetadata(): Bundle = Bundle()
        override fun show(activity: Activity, listener: OnUserEarnedRewardListener) {
            if (throwOnShow) throw IllegalStateException("SDK blew up")
        }
        override fun getRewardItem(): RewardItem = RewardItem.DEFAULT_REWARD
        // ResponseInfo không có constructor public và cũng không cần cho luồng blocked-show mà
        // fake này tồn tại để phục vụ; chỉ để thỏa mãn yêu cầu override kiểu trả về NonNull lúc compile.
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

        // Lượt waterfall đầu tiên thử cả 2 ad unit và fail; đã schedule đúng 1 lần retry.
        assertEquals(2, manager.attemptCount)
        assertEquals(null, result) // vẫn đang retry, chờ scheduler
        assertEquals(1, fakeScheduler.size)

        fakeScheduler.removeAt(0).invoke() // chạy lần retry đồng bộ

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
        AdFlowCore.tryClaimFullScreenSlot() // giả lập 1 Interstitial/AppOpen/Rewarded khác đang hiển thị

        var blockedReason: BlockReason? = null
        manager.show(activity, object : RewardedAdCallback {
            override fun onShowBlocked(reason: BlockReason) { blockedReason = reason }
        })

        assertEquals(BlockReason.ANOTHER_AD_SHOWING, blockedReason)
        assertTrue(manager.isReady()) // lần bị chặn không được làm mất/tiêu thụ cached ad của nó
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
            // đúng như mong đợi - exception vẫn phải propagate, không bị nuốt
        }

        // Ad đã bị consume (và trạng thái của nó sau khi SDK throw đồng bộ là không xác định),
        // nên placement phải tự phục hồi bằng 1 lần load mới, thay vì bị kẹt ở not-ready.
        assertEquals(2, manager.attemptCount)
    }

    @Test
    fun `show on a never-loaded placement blocks as not-ready and triggers a fresh load`() {
        // Regression test: trước đây show() chỉ báo NOT_READY rồi dừng, để placement bị kẹt vĩnh
        // viễn nếu caller không tự tay gọi lại load(). Giờ nó phải tự kích hoạt 1 lần load mới -
        // cùng cách fix đã áp dụng cho luồng ad hết hạn của FullScreenAdManagerBase.
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        val manager = TestableRewardedAdManager(context, config, mutableListOf())

        var blockedReason: BlockReason? = null
        manager.show(activity, object : RewardedAdCallback {
            override fun onShowBlocked(reason: BlockReason) { blockedReason = reason }
        })

        assertEquals(BlockReason.NOT_READY, blockedReason)
        assertEquals(1, manager.attemptCount) // show() đã kích hoạt load(), thử ad unit "A"
    }
}
