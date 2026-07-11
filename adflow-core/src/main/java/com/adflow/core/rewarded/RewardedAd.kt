package com.adflow.core.rewarded

import android.app.Activity
import com.adflow.core.fullscreen.FullScreenAd

interface RewardedAd : FullScreenAd {
    fun show(activity: Activity, callback: RewardedAdCallback)

    /** Overload tiện khi chỉ quan tâm phần thưởng. */
    fun show(activity: Activity, onReward: (RewardItem) -> Unit) =
        show(
            activity,
            object : RewardedAdCallback {
                override fun onUserEarnedReward(reward: RewardItem) = onReward(reward)
            },
        )
}
