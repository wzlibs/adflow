package com.adflow.admob

import android.content.Context
import com.adflow.admob.banner.AdMobBannerAdManager
import com.adflow.admob.consent.AdMobConsentManager
import com.adflow.admob.fullscreen.AdMobAppOpenAdManager
import com.adflow.admob.fullscreen.AdMobInterstitialAdManager
import com.adflow.admob.fullscreen.AdMobRewardedAdManager
import com.adflow.admob.nativead.AdMobNativeAdManager
import com.adflow.core.AdNetworkProvider
import com.adflow.core.AppOpenAdManager
import com.adflow.core.BannerAdManager
import com.adflow.core.ConsentManager
import com.adflow.core.InterstitialAdManager
import com.adflow.core.NativeAdManager
import com.adflow.core.PlacementConfig
import com.adflow.core.RewardedAdManager
import com.google.android.gms.ads.MobileAds

class AdMobProvider(context: Context) : AdNetworkProvider {

    // Resolve ngay từ đầu: mọi manager dưới đây đều tồn tại lâu dài (thường là suốt cả process),
    // nên nếu lưu nguyên Context mà caller truyền vào - ví dụ 1 Activity - sẽ leak nó suốt vòng
    // đời đó và tiếp tục load ad bằng context cũ sau khi nó đã destroy. Application context luôn
    // an toàn cho các lệnh load()/AdLoader của SDK; show() và việc gắn view đã tự nhận
    // Activity/Context mới cho từng lần gọi riêng.
    internal val context: Context = context.applicationContext

    override fun initialize(context: Context, onComplete: () -> Unit) {
        MobileAds.initialize(context) { onComplete() }
    }

    override fun createConsentManager(context: Context): ConsentManager = AdMobConsentManager(context)

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
