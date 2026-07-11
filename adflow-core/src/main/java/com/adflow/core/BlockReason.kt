package com.adflow.core

/**
 * Lý do 1 lệnh show()/render bị chặn - báo qua [AdListener.onAdBlocked] hoặc
 * `FullScreenCallback.onAdBlocked`, không bao giờ throw.
 *
 * V1 chỉ có NOT_READY chung chung; v2 tách thành [STILL_LOADING] (đang load, chờ được) và
 * [NO_AD_AVAILABLE] (lượt load đã kết thúc thất bại, không có gì để chờ) để client quyết định
 * đúng: hiện tiếp shimmer hay dismiss slot.
 */
enum class BlockReason {
    /** Chưa có ad nhưng một lượt load đang chạy - sẽ sớm có [AdState.Loaded] hoặc [AdState.Failed]. */
    STILL_LOADING,

    /** Chưa có ad và cũng không có lượt load nào đang chạy (lượt trước thất bại hết retry). */
    NO_AD_AVAILABLE,

    /** Consent GDPR chưa cho phép request ads. */
    CONSENT_REQUIRED,

    /** `loadWhen`/`showWhen` rule của app từ chối. */
    RULE_REJECTED,

    /** Chưa đủ khoảng nghỉ tối thiểu giữa 2 lần hiển thị full-screen (Interstitial/App Open). */
    INTERVAL_NOT_ELAPSED,

    /** Đang có 1 full-screen ad khác hiển thị - không bao giờ hiển thị chồng. */
    ANOTHER_AD_SHOWING,
}
