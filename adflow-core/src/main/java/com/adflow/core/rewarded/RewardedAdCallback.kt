package com.adflow.core.rewarded

import com.adflow.core.AdFlowError
import com.adflow.core.AdShowBlockedCallback
interface RewardedAdCallback : AdShowBlockedCallback {
    fun onAdLoaded() {}
    fun onAdFailedToLoad(error: AdFlowError) {}
    fun onAdShown() {}
    fun onAdFailedToShow(error: AdFlowError) {}
    fun onUserEarnedReward(reward: RewardItem) {}
    fun onAdDismissed() {}

    companion object {
        val NONE: RewardedAdCallback = object : RewardedAdCallback {}
    }
}
