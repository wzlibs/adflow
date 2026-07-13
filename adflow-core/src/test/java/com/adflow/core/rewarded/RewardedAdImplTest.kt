@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.adflow.core.rewarded

import android.app.Activity
import com.adflow.core.AdFlowError
import com.adflow.core.AdType
import com.adflow.core.BlockReason
import com.adflow.core.config.PlacementConfig
import com.adflow.core.config.RetryPolicy
import com.adflow.core.fullscreen.FakeFullScreenAdSource
import com.adflow.core.fullscreen.FakeLoadedFullScreenAd
import com.adflow.core.fullscreen.newTestRuntime
import com.adflow.core.network.LoadedFullScreenAd
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RewardedAdImplTest {

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).create().get()

    private fun config() = PlacementConfig(
        placementId = "rewarded",
        adType = AdType.REWARDED,
        adUnitIds = listOf("unit-a"),
        preload = false,
        expiryMs = null,
        retryPolicy = RetryPolicy.DEFAULT,
        loadRule = null,
        showRule = null,
    )

    private class RecordingRewardedCallback : RewardedAdCallback {
        var reward: RewardItem? = null
        var blockedReason: BlockReason? = null
        var dismissed = false
        override fun onUserEarnedReward(reward: RewardItem) { this.reward = reward }
        override fun onAdDismissed() { dismissed = true }
        override fun onAdBlocked(reason: BlockReason) { blockedReason = reason }
    }

    @Test
    fun `onUserEarnedReward from the network forwards the reward to the app callback`() = runTest {
        val runtime = newTestRuntime()
        var listenerRef: LoadedFullScreenAd.ShowListener? = null
        val source = FakeFullScreenAdSource { FakeLoadedFullScreenAd { _, listener -> listenerRef = listener } }
        val rewarded = RewardedAdImpl("rewarded", config(), source, runtime, this)
        rewarded.load()
        advanceUntilIdle()

        val callback = RecordingRewardedCallback()
        rewarded.show(activity, callback)
        listenerRef!!.onUserEarnedReward(RewardItem(amount = 10, type = "coins"))

        assertEquals(RewardItem(10, "coins"), callback.reward)

        listenerRef!!.onDismissed()
        assertEquals(RewardItem(10, "coins"), callback.reward) // dismiss không xóa reward đã báo
        assert(callback.dismissed)
    }

    @Test
    fun `the onReward convenience overload receives the reward without a full callback`() = runTest {
        val runtime = newTestRuntime()
        var listenerRef: LoadedFullScreenAd.ShowListener? = null
        val source = FakeFullScreenAdSource { FakeLoadedFullScreenAd { _, listener -> listenerRef = listener } }
        val rewarded = RewardedAdImpl("rewarded", config(), source, runtime, this)
        rewarded.load()
        advanceUntilIdle()

        var received: RewardItem? = null
        rewarded.show(activity) { received = it }
        listenerRef!!.onUserEarnedReward(RewardItem(amount = 5, type = "gems"))

        assertEquals(RewardItem(5, "gems"), received)
    }

    @Test
    fun `show still goes through the shared gate - not ready reports NO_AD_AVAILABLE`() = runTest {
        val runtime = newTestRuntime()
        val failingSource = FakeFullScreenAdSource {
            throw com.adflow.core.network.AdLoadException(AdFlowError(1, "no fill"))
        }
        val rewarded = RewardedAdImpl("rewarded", config(), failingSource, runtime, this)

        val callback = RecordingRewardedCallback()
        rewarded.show(activity, callback)
        advanceUntilIdle() // để lượt load self-heal (ensureLoaded()) chạy hết, không văng exception chưa xử lý

        assertEquals(BlockReason.NO_AD_AVAILABLE, callback.blockedReason)
        assertNull(callback.reward)
    }

    @Test
    fun `canShow reflects the shared gate - false once the load has fully failed`() = runTest {
        val runtime = newTestRuntime()
        val source = FakeFullScreenAdSource {
            throw com.adflow.core.network.AdLoadException(AdFlowError(1, "no fill"))
        }
        val rewarded = RewardedAdImpl("rewarded", config(), source, runtime, this)
        rewarded.load()
        advanceUntilIdle()

        assertFalse(rewarded.canShow)
    }
}
