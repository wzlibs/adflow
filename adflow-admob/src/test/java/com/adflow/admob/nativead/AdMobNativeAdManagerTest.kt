package com.adflow.admob.nativead

import android.content.Context
import android.view.View
import com.adflow.core.AdLoadResult
import com.adflow.core.AdRule
import com.adflow.core.BlockReason
import com.adflow.core.NativeAdAssets
import com.adflow.core.NativeAdRenderer
import com.adflow.core.PlacementConfig
import com.adflow.core.RetryPolicy
import com.google.android.gms.ads.nativead.NativeAd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Chứng minh AdMobNativeAdManager giờ đã retry waterfall với backoff khi no-fill (qua
 * com.adflow.core.RetryingAdLoader dùng chung) thay vì bỏ cuộc sau 1 lượt duy nhất. Hành vi expiry
 * của Native (drop cached ad cũ sau khi quá PlacementConfig.expiryMs) được kế thừa từ
 * com.adflow.core.ExpiringCachedAdLoaderBase và đã được test trực tiếp bởi test riêng của class
 * đó, vì một NativeAd load thành công thật không thể tạo được ở đây nếu không có Mockito.
 */
@RunWith(RobolectricTestRunner::class)
class AdMobNativeAdManagerTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private class TestableNativeAdManager(
        context: Context,
        config: PlacementConfig,
    ) : AdMobNativeAdManager(context, config) {
        var attemptCount = 0

        override fun requestAd(adUnitId: String, onResult: (Result<NativeAd>) -> Unit) {
            attemptCount += 1
            onResult(Result.failure(RuntimeException("no fill")))
        }
    }

    @Test
    fun `retries the waterfall with backoff on no-fill before exhausting retries`() {
        val config = PlacementConfig(
            placementId = "p1",
            adUnitIds = listOf("A", "B"),
            retryPolicy = RetryPolicy(initialDelayMs = 1, multiplier = 1.0, maxDelayMs = 1, maxRetries = 1),
        )
        val manager = TestableNativeAdManager(context, config)
        val fakeScheduler = mutableListOf<() -> Unit>()
        manager.scheduleRetry = { _, action -> fakeScheduler += action }

        var result: AdLoadResult? = null
        manager.load { result = it }

        assertEquals(2, manager.attemptCount)
        assertEquals(null, result)
        assertEquals(1, fakeScheduler.size)

        fakeScheduler.removeAt(0).invoke()

        assertEquals(4, manager.attemptCount)
        assertTrue(result is AdLoadResult.Failure)
        assertFalse(manager.isReady())
    }

    private class MockNativeAdManager(
        context: Context,
        config: PlacementConfig,
        private val ads: MutableList<NativeAd>,
    ) : AdMobNativeAdManager(context, config) {
        override fun requestAd(adUnitId: String, onResult: (Result<NativeAd>) -> Unit) {
            onResult(Result.success(ads.removeAt(0)))
        }
    }

    @Test
    fun `reload() destroys the previous NativeAd once the replacement loads successfully`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        val ad1 = mock<NativeAd>()
        val ad2 = mock<NativeAd>()
        val manager = MockNativeAdManager(context, config, mutableListOf(ad1, ad2))

        manager.load {}
        manager.reload {}

        verify(ad1).destroy()
        verify(ad2, never()).destroy()
    }

    @Test
    fun `createView() reports RULE_REJECTED and returns a GONE view instead of throwing when showRule rejects`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"), showRule = AdRule { false })
        val manager = MockNativeAdManager(context, config, mutableListOf(mock<NativeAd>()))
        manager.load {}
        assertTrue(manager.isReady())

        var blockedReason: BlockReason? = null
        val view = manager.createView(context, DefaultMediumNativeAdRenderer()) { blockedReason = it }

        assertEquals(BlockReason.RULE_REJECTED, blockedReason)
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `createView() reports NOT_READY and returns a GONE view instead of throwing when no ad is cached`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        val manager = MockNativeAdManager(context, config, mutableListOf())
        assertFalse(manager.isReady())

        var blockedReason: BlockReason? = null
        val view = manager.createView(context, DefaultMediumNativeAdRenderer()) { blockedReason = it }

        assertEquals(BlockReason.NOT_READY, blockedReason)
        assertEquals(View.GONE, view.visibility)
    }

    // Renderer thuần (không dùng MediaView thật) để tránh chạm vào
    // GooglePlayServicesUtil.isGooglePlayServicesAvailable() - DefaultMediumNativeAdRenderer tạo
    // MediaView thật, kiểm tra manifest meta-data không có sẵn dưới Robolectric.
    private class FakeNativeAdRenderer : NativeAdRenderer {
        override fun createView(context: Context): View = View(context)
        override fun bind(view: View, assets: NativeAdAssets) {}
    }

    @Test
    fun `createView() binds the real ad and never invokes onShowBlocked when ready and showRule allows`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        val manager = MockNativeAdManager(context, config, mutableListOf(mock<NativeAd>()))
        manager.load {}

        var blockedReason: BlockReason? = null
        val view = manager.createView(context, FakeNativeAdRenderer()) { blockedReason = it }

        assertEquals(null, blockedReason)
        assertFalse(view.visibility == View.GONE)
    }
}
