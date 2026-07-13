@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.adflow.core.fullscreen

import com.adflow.core.AdType
import com.adflow.core.config.PlacementConfig
import com.adflow.core.config.RetryPolicy
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenAdExtTest {

    private fun config(preload: Boolean = false) = PlacementConfig(
        placementId = "p",
        adType = AdType.INTERSTITIAL,
        adUnitIds = listOf("unit-a"),
        preload = preload,
        expiryMs = null,
        retryPolicy = RetryPolicy.DEFAULT,
        loadRule = null,
        showRule = null,
    )

    @Test
    fun `awaitReady triggers its own load - caller does not need to call load() first`() = runTest {
        val runtime = newTestRuntime()
        val source = FakeFullScreenAdSource { FakeLoadedFullScreenAd { _, _ -> } }
        val interstitial = InterstitialAdImpl("p", config(preload = false), source, runtime, this)

        // Không gọi interstitial.load() trước - awaitReady() phải tự lo việc đó, giống hệt Dart.
        val ready = interstitial.awaitReady(8.seconds)

        assertTrue(ready)
        assertTrue(interstitial.isReady)
    }

    @Test
    fun `awaitReady does not trigger a duplicate load when one is already in flight`() = runTest {
        val runtime = newTestRuntime()
        var loadCount = 0
        val source = FakeFullScreenAdSource {
            loadCount++
            FakeLoadedFullScreenAd { _, _ -> }
        }
        val interstitial = InterstitialAdImpl("p", config(preload = false), source, runtime, this)
        interstitial.load()

        val ready = interstitial.awaitReady(8.seconds)

        assertTrue(ready)
        assertEquals(1, loadCount)
    }

    @Test
    fun `awaitReady returns false and gives up once the timeout passes without a ready ad`() = runTest {
        val runtime = newTestRuntime()
        val pending = kotlinx.coroutines.CompletableDeferred<com.adflow.core.network.LoadedFullScreenAd>()
        val source = FakeFullScreenAdSource { pending.await() }
        val interstitial = InterstitialAdImpl("p", config(preload = false), source, runtime, this)

        val ready = interstitial.awaitReady(8.seconds)

        assertFalse(ready)
        pending.complete(FakeLoadedFullScreenAd { _, _ -> })
    }
}
