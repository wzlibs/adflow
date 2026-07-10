package com.dev.adflow

import android.content.Context
import com.adflow.admob.AdMobProvider
import com.adflow.core.AdNetworkProvider
import com.adflow.core.config.AdRule
import com.adflow.core.fullscreen.AppOpenAdManager
import com.adflow.core.banner.BannerAdManager
import com.adflow.core.fullscreen.InterstitialAdManager
import com.adflow.core.nativead.NativeAdManager
import com.adflow.core.config.PlacementConfig
import com.adflow.core.rewarded.RewardedAdManager

class DemoAdPlacements(context: Context) {

    // Dòng duy nhất gắn app với 1 network implementation cụ thể; đổi
    // AdMobProvider sang implementation AdNetworkProvider khác để chuyển network.
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
