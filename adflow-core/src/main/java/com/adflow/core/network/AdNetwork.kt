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

    /** [onConsentChanged] - implementation PHẢI gọi đồng bộ, đúng 1 lần ngay khi tạo (seed từ
     * `canRequestAds()` hiện có - fast path cho consent phiên trước/geography NOT_REQUIRED), và
     * mỗi lần trạng thái đổi sau đó. Core mặc định deny (fail-safe) tới khi seed này chạy - không
     * seed đồng bộ nghĩa là app không bao giờ init được SDK/load được ads. */
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
