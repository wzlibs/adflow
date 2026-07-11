@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.adflow.core.nativead

import com.adflow.core.AdState
import com.adflow.core.AdType
import com.adflow.core.config.PlacementConfig
import com.adflow.core.config.RetryPolicy
import com.adflow.core.fullscreen.newTestRuntime
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NativeAdControllerTest {

    private fun config(expiryMs: Long? = null) = PlacementConfig(
        placementId = "native",
        adType = AdType.NATIVE,
        adUnitIds = listOf("unit-a"),
        preload = true,
        expiryMs = expiryMs,
        retryPolicy = RetryPolicy.DEFAULT,
        loadRule = null,
        showRule = null,
    )

    @Test
    fun `bind does not consume the cache - two views can bind to the same ad`() = runTest {
        val runtime = newTestRuntime()
        val source = FakeNativeAdSource { FakeLoadedNativeAd(RuntimeEnvironment.getApplication()) }
        val controller = NativeAdControllerImpl("native", config(), source, runtime, this)
        controller.load()
        advanceUntilIdle()

        val boundToViewA = controller.bind()
        val boundToViewB = controller.bind()

        assertSame(boundToViewA, boundToViewB)
    }

    @Test
    fun `reload keeps the old ad alive while a view is still bound, then destroys it once unbound`() = runTest {
        val runtime = newTestRuntime()
        var nextAd = FakeLoadedNativeAd(RuntimeEnvironment.getApplication())
        val source = FakeNativeAdSource { nextAd }
        val controller = NativeAdControllerImpl("native", config(), source, runtime, this)
        controller.load()
        advanceUntilIdle()

        val oldAd = controller.bind() as FakeLoadedNativeAd

        nextAd = FakeLoadedNativeAd(RuntimeEnvironment.getApplication())
        controller.reload()
        advanceUntilIdle()

        assertFalse(oldAd.destroyed) // view vẫn đang bind vào oldAd - chưa được destroy
        assertTrue(controller.state.value is AdState.Loaded)

        // Đúng hợp đồng của view: unbind ad cũ TRƯỚC rồi bind lại để lấy ad mới - net bindCount
        // không đổi qua 1 lần chuyển ad, nhưng unbind() ngay lập tức đưa bindCount về 0 và flush
        // pendingDestroy vì lúc này không còn view nào khác bind vào oldAd.
        controller.unbind()
        assertTrue(oldAd.destroyed)

        val rebound = controller.bind()
        assertSame(nextAd, rebound)
    }

    @Test
    fun `reload with no view bound destroys the old ad immediately on success`() = runTest {
        val runtime = newTestRuntime()
        var nextAd = FakeLoadedNativeAd(RuntimeEnvironment.getApplication())
        val source = FakeNativeAdSource { nextAd }
        val controller = NativeAdControllerImpl("native", config(), source, runtime, this)
        controller.load()
        advanceUntilIdle()
        val oldAd = nextAd

        nextAd = FakeLoadedNativeAd(RuntimeEnvironment.getApplication())
        controller.reload()
        advanceUntilIdle()

        assertTrue(oldAd.destroyed) // không ai bind - destroy ngay
    }

    @Test
    fun `expiry destroys the ad immediately when nothing is bound`() = runTest {
        val runtime = newTestRuntime()
        val ad = FakeLoadedNativeAd(RuntimeEnvironment.getApplication())
        val source = FakeNativeAdSource { ad }
        val controller = NativeAdControllerImpl("native", config(expiryMs = 1_000), source, runtime, this)
        controller.load()
        runCurrent()
        assertTrue(controller.state.value is AdState.Loaded)

        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(AdState.Idle, controller.state.value)
        assertTrue(ad.destroyed)
    }

    @Test
    fun `bind returns null once no ad is cached`() = runTest {
        val runtime = newTestRuntime()
        val source = FakeNativeAdSource { error("never called in this test") }
        val controller = NativeAdControllerImpl("native", config(), source, runtime, this)

        assertNull(controller.bind())
    }
}
