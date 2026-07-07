package com.adflow.core

import android.content.Context

interface AdNetworkProvider {
    fun initialize(context: Context, onComplete: () -> Unit = {})
    fun createConsentManager(context: Context): ConsentManager
    fun createInterstitial(config: PlacementConfig): InterstitialAdManager
    fun createAppOpen(config: PlacementConfig): AppOpenAdManager
    fun createRewarded(config: PlacementConfig): RewardedAdManager
    fun createNative(config: PlacementConfig): NativeAdManager
    fun createBanner(config: PlacementConfig): BannerAdManager
}
