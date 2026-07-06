package com.adflow.core

import android.app.Activity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RewardedAdManagerContractTest {

    private class FakeRewarded : RewardedAdManager {
        override fun isReady(): Boolean = true
        override fun load(onResult: (AdLoadResult) -> Unit) = onResult(AdLoadResult.Success)
        override fun show(activity: Activity, callback: RewardedAdCallback) {
            callback.onUserEarnedReward(RewardItem("coins", 10))
        }
    }

    @Test
    fun `rewarded manager show delivers a reward through the rewarded callback`() {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        var earned: RewardItem? = null
        FakeRewarded().show(activity, object : RewardedAdCallback {
            override fun onUserEarnedReward(reward: RewardItem) { earned = reward }
        })
        assertTrue(earned == RewardItem("coins", 10))
    }
}
