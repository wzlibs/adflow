@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.adflow.core.banner

import com.adflow.core.AdState
import com.adflow.core.AdType
import com.adflow.core.config.PlacementConfig
import com.adflow.core.config.RetryPolicy
import com.adflow.core.fullscreen.newTestRuntime
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BannerAdControllerTest {

    private fun config(preload: Boolean = true) = PlacementConfig(
        placementId = "banner",
        adType = AdType.BANNER,
        adUnitIds = listOf("unit-a"),
        preload = preload,
        expiryMs = null,
        retryPolicy = RetryPolicy.DEFAULT,
        loadRule = null,
        showRule = null,
    )

    @Test
    fun `lease consumes the cached ad and a second concurrent lease is rejected`() = runTest {
        val runtime = newTestRuntime()
        val source = FakeBannerAdSource { _, _ -> FakeLoadedBannerAd(RuntimeEnvironment.getApplication()) }
        val controller = BannerAdControllerImpl("banner", config(), source, runtime, this)
        controller.load()
        advanceUntilIdle()

        val first = controller.lease()
        assertTrue(first != null)

        val second = controller.lease()
        assertNull(second)
    }

    @Test
    fun `release destroys the leased ad and re-preloads when preload is enabled`() = runTest {
        val runtime = newTestRuntime()
        var loadCount = 0
        val source = FakeBannerAdSource { _, _ -> loadCount++; FakeLoadedBannerAd(RuntimeEnvironment.getApplication()) }
        val controller = BannerAdControllerImpl("banner", config(preload = true), source, runtime, this)
        controller.load()
        advanceUntilIdle()
        assertEquals(1, loadCount)

        val ad = controller.lease() as FakeLoadedBannerAd
        controller.release(ad)
        advanceUntilIdle()

        assertTrue(ad.destroyed)
        assertEquals(2, loadCount) // preload tự tải ad tiếp theo

        // Sau khi release, lease() lại thành công vì placement không còn bị khóa.
        assertTrue(controller.lease() != null)
    }

    @Test
    fun `release does not re-preload when preload is disabled`() = runTest {
        val runtime = newTestRuntime()
        var loadCount = 0
        val source = FakeBannerAdSource { _, _ -> loadCount++; FakeLoadedBannerAd(RuntimeEnvironment.getApplication()) }
        val controller = BannerAdControllerImpl("banner", config(preload = false), source, runtime, this)
        controller.load()
        advanceUntilIdle()
        assertEquals(1, loadCount)

        val ad = controller.lease() as FakeLoadedBannerAd
        controller.release(ad)
        advanceUntilIdle()

        assertEquals(1, loadCount) // không tự load lại
    }

    @Test
    fun `a banner ad never expires regardless of how much time passes`() = runTest {
        val runtime = newTestRuntime()
        val source = FakeBannerAdSource { _, _ -> FakeLoadedBannerAd(RuntimeEnvironment.getApplication()) }
        val controller = BannerAdControllerImpl("banner", config(), source, runtime, this)
        controller.load()
        advanceUntilIdle()
        assertTrue(controller.state.value is AdState.Loaded)

        advanceUntilIdle() // không có expiryJob nào được lên lịch (expiryMs = null trong config())

        assertTrue(controller.state.value is AdState.Loaded)
    }
}
