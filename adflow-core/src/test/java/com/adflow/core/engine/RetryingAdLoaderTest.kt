package com.adflow.core.engine

import com.adflow.core.AdFlowCore
import com.adflow.core.AdLoadResult
import com.adflow.core.AdType
import com.adflow.core.config.PlacementConfig
import com.adflow.core.config.RetryPolicy
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

        // Lượt waterfall đầu tiên thử cả 2 ad unit và fail; đã schedule đúng 1 lần retry.
        assertEquals(2, attempts)
        assertEquals(null, result) // vẫn đang retry, chờ scheduler
        assertEquals(1, fakeScheduler.size)

        fakeScheduler.removeAt(0).invoke() // chạy lần retry đồng bộ

        // Retry chạy lại toàn bộ waterfall (2 attempt nữa) rồi hết retry.
        assertEquals(4, attempts)
        assertTrue(result is AdLoadResult.Failure)
    }

    @Test
    fun `a second start() call while one is already in flight is coalesced onto it, not dropped`() {
        val fakeScheduler = mutableListOf<() -> Unit>()
        val config = PlacementConfig(
            placementId = "p1",
            adUnitIds = listOf("A"),
            retryPolicy = RetryPolicy(initialDelayMs = 1, multiplier = 1.0, maxDelayMs = 1, maxRetries = 1),
        )
        var attempts = 0
        val loader = RetryingAdLoader<String>(config, AdType.INTERSTITIAL) { _, onResult ->
            attempts += 1
            onResult(Result.failure(RuntimeException("no fill")))
        }
        loader.scheduleRetry = { _, action -> fakeScheduler += action }

        var firstResult: AdLoadResult? = null
        var secondResult: AdLoadResult? = null
        loader.start { r, _ -> firstResult = r }
        assertEquals(1, fakeScheduler.size) // lượt đầu fail, đã schedule 1 retry, vẫn đang chạy

        // Một caller thứ 2 bắt đầu load trong lúc caller đầu vẫn đang retry - không được chạy
        // thêm 1 waterfall độc lập, nhưng vẫn phải được thông báo khi cycle đang chạy đó kết thúc,
        // không được âm thầm rơi mất vĩnh viễn.
        loader.start { r, _ -> secondResult = r }
        assertEquals(1, fakeScheduler.size) // vẫn không có retry độc lập thứ 2 nào được schedule

        fakeScheduler.removeAt(0).invoke() // chạy lần retry đồng bộ; hết retry sau lần này

        assertTrue(firstResult is AdLoadResult.Failure)
        assertTrue(secondResult is AdLoadResult.Failure) // callback được coalesce cũng được gọi, không bị rơi mất
        assertEquals(2, attempts) // vẫn chỉ CHẠY 1 waterfall pass (1 ad unit) x 2 attempt (ban đầu + 1 retry)
    }

    @Test
    fun `a fresh start() after a cycle finishes is not ignored`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"))
        var attempts = 0
        val loader = RetryingAdLoader<String>(config, AdType.INTERSTITIAL) { _, onResult ->
            attempts += 1
            onResult(Result.success("ad-$attempts"))
        }

        var firstResult: AdLoadResult? = null
        loader.start { r, _ -> firstResult = r }
        assertEquals(AdLoadResult.Success, firstResult)

        // isRunning phải reset về false khi cycle kết thúc, để 1 lần start() độc lập sau đó được
        // tự do chạy waterfall riêng của nó, không bị nhầm là vẫn đang chạy.
        var secondResult: AdLoadResult? = null
        loader.start { r, _ -> secondResult = r }
        assertEquals(AdLoadResult.Success, secondResult)
        assertEquals(2, attempts)
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
