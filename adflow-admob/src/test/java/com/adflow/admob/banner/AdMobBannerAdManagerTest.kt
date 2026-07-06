package com.adflow.admob.banner

import android.content.Context
import com.adflow.core.AdLoadResult
import com.adflow.core.PlacementConfig
import com.adflow.core.RetryPolicy
import com.google.android.gms.ads.AdView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Chứng minh AdMobBannerAdManager giờ đã retry waterfall với backoff khi no-fill (qua
 * com.adflow.core.RetryingAdLoader dùng chung) thay vì bỏ cuộc sau 1 lượt duy nhất, trong khi
 * isReady() vẫn chỉ là 1 check non-null đơn giản, không có expiry - khác với mọi loại ad khác,
 * banner được thiết kế để hiển thị và tự refresh bởi SDK ngay khi load xong, không cache trước rồi
 * show sau, nên nó không bao giờ bị cũ (stale) trong lúc chờ.
 */
@RunWith(RobolectricTestRunner::class)
class AdMobBannerAdManagerTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private class TestableBannerAdManager(
        context: Context,
        config: PlacementConfig,
    ) : AdMobBannerAdManager(context, config) {
        var attemptCount = 0

        override fun requestAd(adUnitId: String, onResult: (Result<AdView>) -> Unit) {
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
        val manager = TestableBannerAdManager(context, config)
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
}
