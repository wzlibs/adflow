@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.adflow.core.engine

import android.content.Context
import com.adflow.core.AdFlowError
import com.adflow.core.AdListener
import com.adflow.core.AdState
import com.adflow.core.AdType
import com.adflow.core.BlockReason
import com.adflow.core.config.AdRule
import com.adflow.core.config.PlacementConfig
import com.adflow.core.config.RetryPolicy
import com.adflow.core.consent.ConsentDebugConfig
import com.adflow.core.consent.ConsentManager
import com.adflow.core.logging.AdFlowLogger
import com.adflow.core.network.AdLoadException
import com.adflow.core.network.AdNetwork
import com.adflow.core.network.BannerAdSource
import com.adflow.core.network.FullScreenAdSource
import com.adflow.core.network.NativeAdSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAd

private object NoOpAdNetwork : AdNetwork {
    override val name: String = "fake"
    override fun initialize(context: Context, onComplete: () -> Unit) = error("not used in engine tests")
    override fun createConsentManager(
        context: Context,
        debug: ConsentDebugConfig?,
        onConsentChanged: (Boolean) -> Unit,
    ): ConsentManager = error("not used in engine tests")
    override fun interstitialSource(context: Context): FullScreenAdSource = error("not used in engine tests")
    override fun appOpenSource(context: Context): FullScreenAdSource = error("not used in engine tests")
    override fun rewardedSource(context: Context): FullScreenAdSource = error("not used in engine tests")
    override fun bannerSource(context: Context): BannerAdSource = error("not used in engine tests")
    override fun nativeSource(context: Context): NativeAdSource = error("not used in engine tests")
}

private class RecordingListener : AdListener {
    val loadingCalls = mutableListOf<Unit>()
    val loadedCalls = mutableListOf<Unit>()
    val failedCalls = mutableListOf<Pair<AdFlowError, Boolean>>()
    val blockedCalls = mutableListOf<BlockReason>()

    override fun onAdLoading() { loadingCalls += Unit }
    override fun onAdLoaded() { loadedCalls += Unit }
    override fun onAdFailedToLoad(error: AdFlowError, willRetry: Boolean) { failedCalls += error to willRetry }
    override fun onAdBlocked(reason: BlockReason) { blockedCalls += reason }
}

class AdLoadEngineTest {

    private fun TestScope.newRuntime(): AdFlowRuntime =
        AdFlowRuntime(
            network = NoOpAdNetwork,
            logger = AdFlowLogger { _, _, _, _ -> },
            scope = this,
            clock = { testScheduler.currentTime }, // đồng bộ với thời gian ảo của TestScope, không dùng wall clock thật
        )

    private fun config(
        adUnitIds: List<String> = listOf("unit-a"),
        loadRule: AdRule? = null,
        expiryMs: Long? = null,
        retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
    ) = PlacementConfig(
        placementId = "p",
        adType = AdType.INTERSTITIAL,
        adUnitIds = adUnitIds,
        preload = true,
        expiryMs = expiryMs,
        retryPolicy = retryPolicy,
        loadRule = loadRule,
        showRule = null,
    )

    @Test
    fun `waterfall tries ad units in order and stops at the first success`() = runTest {
        val runtime = newRuntime()
        val calls = mutableListOf<String>()
        val ad = FakeAd()
        val engine = AdLoadEngine<FakeAd>(
            config = config(adUnitIds = listOf("a", "b", "c")),
            loadOne = { adUnitId ->
                calls += adUnitId
                if (adUnitId == "b") ad else throw AdLoadException(AdFlowError(1, "no fill"))
            },
            onDrop = {},
            runtime = runtime,
            scope = this,
        )

        engine.ensureLoaded()
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), calls)
        assertSame(ad, engine.peek())
        assertTrue(engine.state.value is AdState.Loaded)
    }

    @Test
    fun `waterfall exhaustion retries with backoff then stops after maxRetries`() = runTest {
        val runtime = newRuntime()
        val engine = AdLoadEngine<FakeAd>(
            config = config(
                adUnitIds = listOf("a"),
                retryPolicy = RetryPolicy(initialDelayMs = 5_000, multiplier = 2.0, maxDelayMs = 60_000, maxRetries = 3),
            ),
            loadOne = { throw AdLoadException(AdFlowError(1, "no fill")) },
            onDrop = {},
            runtime = runtime,
            scope = this,
        )

        engine.ensureLoaded()
        runCurrent()
        var failed = engine.state.value as AdState.Failed
        assertTrue(failed.willRetry)
        assertEquals(5_000L, failed.nextRetryDelayMs)

        advanceTimeBy(5_000)
        runCurrent()
        failed = engine.state.value as AdState.Failed
        assertTrue(failed.willRetry)
        assertEquals(10_000L, failed.nextRetryDelayMs)

        advanceTimeBy(10_000)
        runCurrent()
        failed = engine.state.value as AdState.Failed
        assertFalse(failed.willRetry)
        assertNull(failed.nextRetryDelayMs)
    }

    @Test
    fun `ensureLoaded coalesces concurrent calls into one waterfall cycle`() = runTest {
        val runtime = newRuntime()
        var callCount = 0
        val ad = FakeAd()
        val engine = AdLoadEngine<FakeAd>(
            config = config(),
            loadOne = { callCount++; ad },
            onDrop = {},
            runtime = runtime,
            scope = this,
        )

        engine.ensureLoaded()
        engine.ensureLoaded()
        engine.ensureLoaded()
        advanceUntilIdle()

        assertEquals(1, callCount)
    }

    @Test
    fun `ensureLoaded after a fully-failed cycle opens a fresh cycle with reset retry count`() = runTest {
        val runtime = newRuntime()
        var loadOneCallCount = 0
        val engine = AdLoadEngine<FakeAd>(
            config = config(retryPolicy = RetryPolicy(maxRetries = 1, initialDelayMs = 1_000)),
            loadOne = { loadOneCallCount++; throw AdLoadException(AdFlowError(1, "no fill")) },
            onDrop = {},
            runtime = runtime,
            scope = this,
        )
        val listener = RecordingListener()
        engine.addListener(listener)

        engine.ensureLoaded()
        advanceUntilIdle()
        val first = engine.state.value as AdState.Failed
        assertFalse(first.willRetry)
        assertEquals(1, loadOneCallCount)

        // Lượt mới: đếm retry lại từ 0, phải chạy lại đúng 1 chu kỳ mới (1 lệnh loadOne mới, 1
        // Loading mới, 1 Failed mới) - không kế thừa bộ đếm cũ hay coi như đã "dùng hết" vĩnh viễn.
        engine.ensureLoaded()
        advanceUntilIdle()
        val second = engine.state.value as AdState.Failed
        assertFalse(second.willRetry)
        assertEquals(2, loadOneCallCount)
        assertEquals(2, listener.loadingCalls.size)
        assertEquals(2, listener.failedCalls.size)
    }

    @Test
    fun `a Loaded ad auto-drops to Idle once expiryMs elapses without any external access`() = runTest {
        val runtime = newRuntime()
        val ad = FakeAd()
        var dropped: FakeAd? = null
        val engine = AdLoadEngine<FakeAd>(
            config = config(expiryMs = 1_000),
            loadOne = { ad },
            onDrop = { dropped = it },
            runtime = runtime,
            scope = this,
        )

        // runCurrent() (không phải advanceUntilIdle()) - advanceUntilIdle() sẽ chạy luôn tới cùng
        // cả job hẹn giờ expiry đã được lên lịch bên trong lần load này, khiến ad bị drop ngay
        // trong bước "load xong" thay vì đúng lúc 1000ms sau như bài test này muốn kiểm chứng.
        engine.ensureLoaded()
        runCurrent()
        assertTrue(engine.state.value is AdState.Loaded)

        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(AdState.Idle, engine.state.value)
        assertSame(ad, dropped)
    }

    @Test
    fun `forceReload keeps the old ad on failure and only swaps on success`() = runTest {
        val runtime = newRuntime()
        val adA = FakeAd()
        val adB = FakeAd()
        var nextResult: (() -> FakeAd)? = { adA }
        val dropped = mutableListOf<FakeAd>()
        val engine = AdLoadEngine<FakeAd>(
            config = config(retryPolicy = RetryPolicy(maxRetries = 1, initialDelayMs = 1_000)),
            loadOne = {
                val provide = nextResult ?: throw AdLoadException(AdFlowError(1, "no fill"))
                provide()
            },
            onDrop = { dropped += it },
            runtime = runtime,
            scope = this,
        )

        engine.ensureLoaded()
        advanceUntilIdle()
        assertSame(adA, engine.peek())
        val loadedAtA = (engine.state.value as AdState.Loaded).loadedAtMs

        // forceReload thất bại - ad cũ + state cũ giữ nguyên, không báo lỗi.
        nextResult = null
        engine.forceReload()
        advanceUntilIdle()
        assertSame(adA, engine.peek())
        assertEquals(loadedAtA, (engine.state.value as AdState.Loaded).loadedAtMs)
        assertTrue(dropped.isEmpty())

        // forceReload thành công - ad mới thay ad cũ, state phát Loaded mới, onDrop chạy trên ad cũ.
        nextResult = { adB }
        engine.forceReload()
        advanceUntilIdle()
        assertSame(adB, engine.peek())
        assertEquals(listOf(adA), dropped)
    }

    @Test
    fun `loadWhen rule rejection blocks load with RULE_REJECTED`() = runTest {
        val runtime = newRuntime()
        val engine = AdLoadEngine<FakeAd>(
            config = config(loadRule = AdRule { false }),
            loadOne = { FakeAd() },
            onDrop = {},
            runtime = runtime,
            scope = this,
        )
        val listener = RecordingListener()
        engine.addListener(listener)

        engine.ensureLoaded()
        advanceUntilIdle()

        assertEquals(listOf(BlockReason.RULE_REJECTED), listener.blockedCalls)
    }

    @Test
    fun `a newly-added listener replays the current state instead of missing it`() = runTest {
        val runtime = newRuntime()
        val ad = FakeAd()
        val engine = AdLoadEngine<FakeAd>(
            config = config(),
            loadOne = { ad },
            onDrop = {},
            runtime = runtime,
            scope = this,
        )

        engine.ensureLoaded()
        advanceUntilIdle()

        val lateListener = RecordingListener()
        engine.addListener(lateListener)

        assertEquals(1, lateListener.loadedCalls.size)
    }

    @Test
    fun `markShowing and markIdle drive the Showing state used by full-screen controllers`() = runTest {
        val runtime = newRuntime()
        val ad = FakeAd()
        val engine = AdLoadEngine<FakeAd>(
            config = config(),
            loadOne = { ad },
            onDrop = {},
            runtime = runtime,
            scope = this,
        )

        engine.ensureLoaded()
        advanceUntilIdle()
        assertSame(ad, engine.take())
        engine.markShowing()
        assertEquals(AdState.Showing, engine.state.value)

        engine.markIdle()
        assertEquals(AdState.Idle, engine.state.value)
    }
}
