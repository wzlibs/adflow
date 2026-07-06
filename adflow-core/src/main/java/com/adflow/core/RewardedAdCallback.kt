package com.adflow.core

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
