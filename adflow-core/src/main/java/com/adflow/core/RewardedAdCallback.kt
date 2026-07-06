package com.adflow.core

interface RewardedAdCallback {
    fun onAdLoaded() {}
    fun onAdFailedToLoad(error: AdFlowError) {}
    fun onAdShown() {}
    fun onAdFailedToShow(error: AdFlowError) {}
    fun onUserEarnedReward(reward: RewardItem) {}
    fun onAdDismissed() {}
    fun onAdExpired() {}
    fun onShowBlocked(reason: BlockReason) {}

    companion object {
        val NONE: RewardedAdCallback = object : RewardedAdCallback {}
    }
}
