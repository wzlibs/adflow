package com.adflow.admob

import android.content.Context
import com.adflow.admob.banner.AdMobBannerAdManager
import com.adflow.admob.fullscreen.AdMobAppOpenAdManager
import com.adflow.admob.fullscreen.AdMobInterstitialAdManager
import com.adflow.admob.fullscreen.AdMobRewardedAdManager
import com.adflow.admob.nativead.AdMobNativeAdManager
import com.adflow.core.AdNetworkProvider
import com.adflow.core.AppOpenAdManager
import com.adflow.core.BannerAdManager
import com.adflow.core.InterstitialAdManager
import com.adflow.core.NativeAdManager
import com.adflow.core.PlacementConfig
import com.adflow.core.RewardedAdManager
import com.google.android.gms.ads.MobileAds

class AdMobProvider(context: Context) : AdNetworkProvider {

    // Resolved once, up front: every manager below is long-lived (typically for the whole
    // process), so storing whatever Context the caller happened to pass - e.g. an Activity -
    // would leak it for that lifetime and keep loading ads with a stale context after it's
    // destroyed. The Application context is always safe for the SDK's load()/AdLoader calls;
    // show() and view-attachment already take their own fresh Activity/Context per call.
    internal val context: Context = context.applicationContext

    override fun initialize(context: Context, onComplete: () -> Unit) {
        MobileAds.initialize(context) { onComplete() }
    }

    override fun createInterstitial(config: PlacementConfig): InterstitialAdManager =
        AdMobInterstitialAdManager(context, config)

    override fun createAppOpen(config: PlacementConfig): AppOpenAdManager =
        AdMobAppOpenAdManager(context, config)

    override fun createRewarded(config: PlacementConfig): RewardedAdManager =
        AdMobRewardedAdManager(context, config)

    override fun createNative(config: PlacementConfig): NativeAdManager =
        AdMobNativeAdManager(context, config)

    override fun createBanner(config: PlacementConfig): BannerAdManager =
        AdMobBannerAdManager(context, config)
}
