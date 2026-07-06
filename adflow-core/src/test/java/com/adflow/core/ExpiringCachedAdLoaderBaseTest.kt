package com.adflow.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExpiringCachedAdLoaderBaseTest {

    @After
    fun tearDown() {
        AdFlowCore.reset()
    }

    @Test
    fun `loads successfully and is ready while fresh`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"), expiryMs = 1_000)
        val manager = object : ExpiringCachedAdLoaderBase<String>(config, AdType.NATIVE) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                onResult(Result.success("ad-A"))
            }
        }
        var result: AdLoadResult? = null
        manager.load { result = it }
        assertEquals(AdLoadResult.Success, result)
        assertTrue(manager.isReady())
    }

    @Test
    fun `drops the cached ad and reloads once it goes past expiryMs`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"), expiryMs = 1_000)
        var loadCount = 0
        val manager = object : ExpiringCachedAdLoaderBase<String>(config, AdType.NATIVE) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                loadCount += 1
                onResult(Result.success("ad-$loadCount"))
            }
        }
        var now = 0L
        manager.nowProvider = { now }
        manager.load {}
        assertEquals(1, loadCount)
        assertTrue(manager.isReady())

        now = 2_000 // past expiryMs
        assertFalse(manager.isReady()) // stale, even though dropIfExpired() hasn't run yet

        var result: AdLoadResult? = null
        manager.load { result = it }
        assertEquals(AdLoadResult.Success, result)
        assertEquals(2, loadCount) // isReady() being false let load() proceed with a fresh waterfall
    }

    @Test
    fun `onLoaded is not fired again by the isReady() short-circuit on a fresh ad`() {
        val config = PlacementConfig(placementId = "p1", adUnitIds = listOf("A"), expiryMs = 1_000)
        var onLoadedCallCount = 0
        val manager = object : ExpiringCachedAdLoaderBase<String>(config, AdType.NATIVE) {
            override fun requestAd(adUnitId: String, onResult: (Result<String>) -> Unit) {
                onResult(Result.success("ad-A"))
            }
            override fun onLoaded(ad: String) {
                super.onLoaded(ad)
                onLoadedCallCount += 1
            }
        }
        manager.load {}
        assertEquals(1, onLoadedCallCount)

        manager.load {} // still fresh - isReady() shortcut, must not touch the load timestamp again
        assertEquals(1, onLoadedCallCount)
    }
}
