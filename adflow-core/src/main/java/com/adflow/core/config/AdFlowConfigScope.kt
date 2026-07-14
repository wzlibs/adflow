package com.adflow.core.config

import com.adflow.core.banner.BannerSize
import com.adflow.core.consent.ConsentDebugGeography
import com.adflow.core.logging.AdFlowLogger
import com.adflow.core.nativead.NativeAdRenderer
import com.adflow.core.network.AdNetwork
import com.adflow.core.revenue.RevenueLogger
import kotlin.time.Duration

/**
 * Scope của `AdFlow.initialize(context) { ... }` - điểm đăng ký duy nhất cho mọi placement: khai
 * network + cấu hình chung + từng placement (Interstitial/App Open/Rewarded/Native/Banner) ngay
 * trong 1 khối DSL, không cần tự viết class quản lý placement hay tự wire network riêng.
 */
@AdFlowDsl
interface AdFlowConfigScope {
    /** Bắt buộc set - chọn implementation mạng quảng cáo (vd `AdMobNetwork()`). Đây là điểm swap
     * mạng duy nhất: đổi network sau này chỉ cần đổi dòng này. */
    var network: AdNetwork

    var logger: AdFlowLogger

    fun showIntervals(block: ShowIntervalScope.() -> Unit)

    fun consentDebug(block: ConsentDebugScope.() -> Unit)

    fun revenueLogger(logger: RevenueLogger)

    fun interstitial(placementId: String, block: PlacementScope.() -> Unit)
    fun appOpen(placementId: String, block: AppOpenPlacementScope.() -> Unit)
    fun rewarded(placementId: String, block: PlacementScope.() -> Unit)
    fun banner(placementId: String, block: BannerPlacementScope.() -> Unit)
    fun native(placementId: String, block: NativePlacementScope.() -> Unit)
}

/** Field chung cho mọi loại placement, trừ Banner (không có [expiry] - xem [BannerPlacementScope]). */
@AdFlowDsl
interface BasePlacementScope {
    /** Danh sách ad unit ID theo thứ tự waterfall - bắt buộc, không được rỗng. */
    fun adUnits(vararg ids: String)

    /** true (mặc định): sau khi ad hiện tại bị tiêu thụ (show xong với full-screen, `release()` với
     * banner) thì tự load ngay 1 ad kế tiếp. Không áp dụng cho lượt load đầu tiên - placement lúc
     * mới `initialize()` chưa có ad nào, app phải tự gọi `.load()` (banner/native tự load khi gắn
     * view, không cần app gọi). */
    var preload: Boolean
    var retryPolicy: RetryPolicy

    fun loadWhen(rule: AdRule)
    fun showWhen(rule: AdRule)
}

@AdFlowDsl
interface PlacementScope : BasePlacementScope {
    /** Ad được giữ trong cache tối đa bao lâu trước khi bị coi là cũ và bỏ đi. Mặc định 4 giờ
     * (khuyến nghị của AdMob cho full-screen/Native). */
    var expiry: Duration
}

@AdFlowDsl
interface AppOpenPlacementScope : PlacementScope {
    /** true: tự động show placement này mỗi khi app quay lại foreground (không bao giờ đè lên
     * full-screen ad khác đang hiển thị). Mặc định false - app tự gọi `show()` thủ công. */
    var autoShowOnForeground: Boolean
}

/** Banner KHÔNG có [PlacementScope.expiry] - 1 banner được hiển thị và tự làm mới bởi mạng quảng
 * cáo ngay khi load xong, không cache-rồi-show-sau như các loại khác nên không có khái niệm "cũ". */
@AdFlowDsl
interface BannerPlacementScope : BasePlacementScope {
    var size: BannerSize
}

@AdFlowDsl
interface NativePlacementScope : PlacementScope {
    /** Renderer mặc định cho placement này khi `AdFlowNativeAdView`/`AdFlowNative` không tự chỉ
     * định renderer riêng. null = dùng renderer mặc định của adapter mạng. */
    var renderer: NativeAdRenderer?
}

@AdFlowDsl
interface ShowIntervalScope {
    var interstitialAfterInterstitial: Duration
    var appOpenAfterAppOpen: Duration
    var interstitialAfterAppOpen: Duration
    var appOpenAfterInterstitial: Duration
}

@AdFlowDsl
interface ConsentDebugScope {
    var geography: ConsentDebugGeography
    var testDeviceHashedIds: List<String>
}
