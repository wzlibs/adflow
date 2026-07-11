package com.adflow.admob

import android.content.Context
import com.adflow.admob.banner.AdMobBannerSource
import com.adflow.admob.consent.AdMobConsentManager
import com.adflow.admob.fullscreen.AdMobAppOpenSource
import com.adflow.admob.fullscreen.AdMobInterstitialSource
import com.adflow.admob.fullscreen.AdMobRewardedSource
import com.adflow.admob.nativead.AdMobNativeSource
import com.adflow.core.consent.ConsentDebugConfig
import com.adflow.core.consent.ConsentManager
import com.adflow.core.network.AdNetwork
import com.adflow.core.network.BannerAdSource
import com.adflow.core.network.FullScreenAdSource
import com.adflow.core.network.NativeAdSource
import com.google.android.gms.ads.MobileAds

/**
 * Implementation [AdNetwork] dùng Google AdMob - điểm swap mạng quảng cáo duy nhất mà app chọn
 * qua `AdFlow.initialize { network = AdMobNetwork() }`. `:adflow-core` không phụ thuộc module
 * này; 1 adapter khác (vd MAX) chỉ cần implement lại đúng interface [AdNetwork].
 */
class AdMobNetwork : AdNetwork {
    override val name: String = "AdMob"

    override fun initialize(context: Context, onComplete: () -> Unit) {
        MobileAds.initialize(context.applicationContext) { onComplete() }
    }

    override fun createConsentManager(
        context: Context,
        debug: ConsentDebugConfig?,
        onConsentChanged: (Boolean) -> Unit,
    ): ConsentManager = AdMobConsentManager(context.applicationContext, debug, onConsentChanged)

    override fun interstitialSource(context: Context): FullScreenAdSource =
        AdMobInterstitialSource(context.applicationContext)

    override fun appOpenSource(context: Context): FullScreenAdSource =
        AdMobAppOpenSource(context.applicationContext)

    override fun rewardedSource(context: Context): FullScreenAdSource =
        AdMobRewardedSource(context.applicationContext)

    override fun bannerSource(context: Context): BannerAdSource =
        AdMobBannerSource(context.applicationContext)

    override fun nativeSource(context: Context): NativeAdSource =
        AdMobNativeSource(context.applicationContext)
}
