package com.adflow.core.rewarded

import com.adflow.core.fullscreen.FullScreenCallback

interface RewardedAdCallback : FullScreenCallback {
    fun onUserEarnedReward(reward: RewardItem)
}
