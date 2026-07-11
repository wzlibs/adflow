package com.adflow.core.network

import android.content.Context
import com.adflow.core.consent.ConsentDebugConfig
import com.adflow.core.consent.ConsentManager

/**
 * Điểm swap mạng quảng cáo duy nhất - `AdMobNetwork` là implementation hiện có; thêm adapter mới
 * (MAX...) là viết 1 implementation khác của interface này, không đổi code app. Core sở hữu toàn
 * bộ orchestration (waterfall/retry/cache/expiry/interval/exclusivity); adapter chỉ dịch đúng 1
 * lần request sang SDK thật.
 */
interface AdNetwork {
    val name: String

    fun initialize(context: Context, onComplete: () -> Unit)

    /** [onConsentChanged] - adapter gọi lại mỗi khi consent resolve/đổi, với giá trị
     * `canRequestAds()` mới; core dùng nó làm gate load, mặc định cho phép khi chưa resolve lần
     * nào (không phá app chưa tích hợp consent). */
    fun createConsentManager(
        context: Context,
        debug: ConsentDebugConfig?,
        onConsentChanged: (allowsAdRequests: Boolean) -> Unit,
    ): ConsentManager

    fun interstitialSource(context: Context): FullScreenAdSource
    fun appOpenSource(context: Context): FullScreenAdSource
    fun rewardedSource(context: Context): FullScreenAdSource
    fun bannerSource(context: Context): BannerAdSource
    fun nativeSource(context: Context): NativeAdSource
}
