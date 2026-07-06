package com.dev.adflow

import android.content.Context
import com.adflow.admob.AdMobProvider
import com.adflow.core.AdNetworkProvider
import com.adflow.core.AdRule
import com.adflow.core.AppOpenAdManager
import com.adflow.core.BannerAdManager
import com.adflow.core.InterstitialAdManager
import com.adflow.core.NativeAdManager
import com.adflow.core.PlacementConfig
import com.adflow.core.RewardedAdManager

class DemoAdPlacements(context: Context) {

    // The single line that ties the app to a network implementation; swap
    // AdMobProvider for another AdNetworkProvider implementation to switch networks.
    val provider: AdNetworkProvider = AdMobProvider(context)

    private val notPremium = AdRule { !PremiumState.isPremium }

    val splashInterstitial: InterstitialAdManager = provider.createInterstitial(
        PlacementConfig(
            placementId = "splash_interstitial",
            adUnitIds = listOf("ca-app-pub-3940256099942544/1033173712"),
            loadRule = notPremium,
            showRule = notPremium,
        ),
    )

    val globalInterstitial: InterstitialAdManager = provider.createInterstitial(
        PlacementConfig(
            placementId = "global_interstitial",
            adUnitIds = listOf("ca-app-pub-3940256099942544/1033173712"),
            loadRule = notPremium,
            showRule = notPremium,
        ),
    )

    val appOpen: AppOpenAdManager = provider.createAppOpen(
        PlacementConfig(
            placementId = "app_open",
            adUnitIds = listOf("ca-app-pub-3940256099942544/9257395921"),
            loadRule = notPremium,
            showRule = notPremium,
        ),
    )

    val rewarded: RewardedAdManager = provider.createRewarded(
        PlacementConfig(
            placementId = "rewarded",
            adUnitIds = listOf("ca-app-pub-3940256099942544/5224354917"),
        ),
    )

    val banner: BannerAdManager = provider.createBanner(
        PlacementConfig(
            placementId = "home_banner",
            adUnitIds = listOf("ca-app-pub-3940256099942544/9214589741"),
            loadRule = notPremium,
        ),
    )

    val native: NativeAdManager = provider.createNative(
        PlacementConfig(
            placementId = "home_native",
            adUnitIds = listOf("ca-app-pub-3940256099942544/2247696110"),
            loadRule = notPremium,
        ),
    )
}
