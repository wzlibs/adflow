package com.adflow.core

import android.content.Context
import com.adflow.core.banner.BannerAdManager
import com.adflow.core.config.PlacementConfig
import com.adflow.core.consent.ConsentManager
import com.adflow.core.fullscreen.AppOpenAdManager
import com.adflow.core.fullscreen.InterstitialAdManager
import com.adflow.core.nativead.NativeAdManager
import com.adflow.core.rewarded.RewardedAdManager

interface AdNetworkProvider {
    fun initialize(context: Context, onComplete: () -> Unit = {})
    fun createConsentManager(context: Context): ConsentManager
    fun createInterstitial(config: PlacementConfig): InterstitialAdManager
    fun createAppOpen(config: PlacementConfig): AppOpenAdManager
    fun createRewarded(config: PlacementConfig): RewardedAdManager
    fun createNative(config: PlacementConfig): NativeAdManager
    fun createBanner(config: PlacementConfig): BannerAdManager
}
