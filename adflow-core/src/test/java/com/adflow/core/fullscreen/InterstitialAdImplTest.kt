@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.adflow.core.fullscreen

import android.app.Activity
import com.adflow.core.AdFlowError
import com.adflow.core.AdState
import com.adflow.core.AdType
import com.adflow.core.BlockReason
import com.adflow.core.config.AdRule
import com.adflow.core.config.PlacementConfig
import com.adflow.core.config.RetryPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InterstitialAdImplTest {

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).create().get()

    private fun config(
        showRule: AdRule? = null,
        preload: Boolean = true,
    ) = PlacementConfig(
        placementId = "p",
        adType = AdType.INTERSTITIAL,
        adUnitIds = listOf("unit-a"),
        preload = preload,
        expiryMs = null,
        retryPolicy = RetryPolicy.DEFAULT,
        loadRule = null,
        showRule = showRule,
    )

    private class RecordingCallback : FullScreenCallback {
        var shown = false
        var dismissed = false
        var failedError: AdFlowError? = null
        var blockedReason: BlockReason? = null
        override fun onAdShowed() { shown = true }
        override fun onAdDismissed() { dismissed = true }
        override fun onAdFailedToShow(error: AdFlowError) { failedError = error }
        override fun onAdBlocked(reason: BlockReason) { blockedReason = reason }
    }

    @Test
    fun `show blocked by showRule reports RULE_REJECTED without touching the slot`() = runTest {
        val runtime = newTestRuntime()
        val source = FakeFullScreenAdSource { FakeLoadedFullScreenAd { _, _ -> } }
        val interstitial = InterstitialAdImpl("p", config(showRule = AdRule { false }), source, runtime, this)
        interstitial.load()
        advanceUntilIdle()

        val callback = RecordingCallback()
        interstitial.show(activity, callback)

        assertEquals(BlockReason.RULE_REJECTED, callback.blockedReason)
        assertFalse(runtime.fullScreenSlot.isShowing)
        assertTrue(interstitial.isReady) // ad cache không bị đụng tới
    }

    @Test
    fun `show blocked by not-ready reports STILL_LOADING while a load is in flight`() = runTest {
        val runtime = newTestRuntime()
        val pending = CompletableDeferred<com.adflow.core.network.LoadedFullScreenAd>()
        val source = FakeFullScreenAdSource { pending.await() }
        val interstitial = InterstitialAdImpl("p", config(), source, runtime, this)

        interstitial.load()
        runCurrent() // để lượt load bắt đầu (state=Loading) nhưng chưa hoàn tất (pending chưa resolve)
        assertTrue(interstitial.state.value is AdState.Loading)

        val callback = RecordingCallback()
        interstitial.show(activity, callback)

        assertEquals(BlockReason.STILL_LOADING, callback.blockedReason)

        // runTest() yêu cầu mọi coroutine trong scope phải hoàn tất trước khi test kết thúc -
        // resolve nốt lượt load đang treo để job nền không bị bỏ dở.
        pending.complete(FakeLoadedFullScreenAd { _, _ -> })
        advanceUntilIdle()
    }

    @Test
    fun `show blocked by not-ready reports NO_AD_AVAILABLE once the load has fully failed`() = runTest {
        val runtime = newTestRuntime()
        val source = FakeFullScreenAdSource { throw com.adflow.core.network.AdLoadException(AdFlowError(1, "no fill")) }
        val interstitial = InterstitialAdImpl("p", config(preload = false), source, runtime, this)

        interstitial.load()
        advanceUntilIdle()
        assertTrue(interstitial.state.value is AdState.Failed)

        val callback = RecordingCallback()
        interstitial.show(activity, callback)

        assertEquals(BlockReason.NO_AD_AVAILABLE, callback.blockedReason)
    }

    @Test
    fun `show blocked by interval reports INTERVAL_NOT_ELAPSED and does not consume the cached ad`() = runTest {
        val runtime = newTestRuntime()
        val source = FakeFullScreenAdSource { FakeLoadedFullScreenAd { _, _ -> } }
        val interstitial = InterstitialAdImpl("p", config(), source, runtime, this)
        interstitial.load()
        advanceUntilIdle()
        runtime.showIntervalPolicy.recordDismissed(AdType.INTERSTITIAL) // giả lập vừa show 1 lần

        val callback = RecordingCallback()
        interstitial.show(activity, callback)

        assertEquals(BlockReason.INTERVAL_NOT_ELAPSED, callback.blockedReason)
        assertTrue(interstitial.isReady)
    }

    @Test
    fun `show blocked by another full-screen ad already showing reports ANOTHER_AD_SHOWING`() = runTest {
        val runtime = newTestRuntime()
        val source = FakeFullScreenAdSource { FakeLoadedFullScreenAd { _, _ -> } }
        val interstitial = InterstitialAdImpl("p", config(), source, runtime, this)
        interstitial.load()
        advanceUntilIdle()
        runtime.fullScreenSlot.tryClaim() // 1 full-screen ad khác đang show

        val callback = RecordingCallback()
        interstitial.show(activity, callback)

        assertEquals(BlockReason.ANOTHER_AD_SHOWING, callback.blockedReason)
        assertTrue(interstitial.isReady)
    }

    @Test
    fun `a successful show claims the slot, transitions to Showing, and records the interval only at dismiss`() = runTest {
        val runtime = newTestRuntime()
        var listenerRef: com.adflow.core.network.LoadedFullScreenAd.ShowListener? = null
        val source = FakeFullScreenAdSource { FakeLoadedFullScreenAd { _, listener -> listenerRef = listener } }
        val interstitial = InterstitialAdImpl("p", config(preload = false), source, runtime, this)
        interstitial.load()
        advanceUntilIdle()

        val callback = RecordingCallback()
        interstitial.show(activity, callback)

        assertTrue(runtime.fullScreenSlot.isShowing)
        assertEquals(AdState.Showing, interstitial.state.value)
        assertNull(callback.dismissed.takeIf { it }) // chưa dismiss

        listenerRef!!.onShowed()
        assertTrue(callback.shown)

        listenerRef!!.onDismissed()
        assertTrue(callback.dismissed)
        assertFalse(runtime.fullScreenSlot.isShowing) // slot được giải phóng
        assertEquals(AdState.Idle, interstitial.state.value) // preload=false -> về Idle, không tự load lại
    }

    @Test
    fun `preload true triggers a fresh load right after dismiss`() = runTest {
        val runtime = newTestRuntime()
        var listenerRef: com.adflow.core.network.LoadedFullScreenAd.ShowListener? = null
        var loadCount = 0
        val source = FakeFullScreenAdSource {
            loadCount++
            FakeLoadedFullScreenAd { _, listener -> listenerRef = listener }
        }
        val interstitial = InterstitialAdImpl("p", config(preload = true), source, runtime, this)
        interstitial.load()
        advanceUntilIdle()
        assertEquals(1, loadCount)

        interstitial.show(activity, RecordingCallback())
        listenerRef!!.onDismissed()
        advanceUntilIdle()

        assertEquals(2, loadCount) // preload tự load lại ad tiếp theo
    }

    @Test
    fun `onFailedToShow releases the slot without recording a show interval`() = runTest {
        val runtime = newTestRuntime()
        var listenerRef: com.adflow.core.network.LoadedFullScreenAd.ShowListener? = null
        val source = FakeFullScreenAdSource { FakeLoadedFullScreenAd { _, listener -> listenerRef = listener } }
        val interstitial = InterstitialAdImpl("p", config(preload = false), source, runtime, this)
        interstitial.load()
        advanceUntilIdle()

        val callback = RecordingCallback()
        interstitial.show(activity, callback)
        listenerRef!!.onFailedToShow(AdFlowError(99, "boom"))

        assertEquals(99, callback.failedError?.code)
        assertFalse(runtime.fullScreenSlot.isShowing)
        assertTrue(runtime.showIntervalPolicy.canShow(AdType.INTERSTITIAL)) // không tính là đã show
    }

    @Test
    fun `a synchronous throw from the SDK still releases the slot and self-heals`() = runTest {
        val runtime = newTestRuntime()
        var loadCount = 0
        val source = FakeFullScreenAdSource {
            loadCount++
            FakeLoadedFullScreenAd { _, _ -> throw IllegalStateException("SDK bug") }
        }
        val interstitial = InterstitialAdImpl("p", config(), source, runtime, this)
        interstitial.load()
        advanceUntilIdle()
        assertEquals(1, loadCount)

        var threw = false
        try {
            interstitial.show(activity, RecordingCallback())
        } catch (e: IllegalStateException) {
            threw = true
        }
        advanceUntilIdle()

        assertTrue(threw)
        assertFalse(runtime.fullScreenSlot.isShowing)
        assertEquals(2, loadCount) // self-heal: 1 lần load mới được kích hoạt
    }
}
