package com.adflow.core

/**
 * Listener phía load, kiểu AdMob `AdListener` - kênh thứ 2 song song với `StateFlow<AdState>`
 * cho client không dùng coroutines (XML/View truyền thống). Đăng ký qua `addListener()` trên
 * controller hoặc gán vào `adListener` của `AdFlowBannerView`/`AdFlowNativeAdView`.
 *
 * Listener mới đăng ký được replay ngay trạng thái hiện tại (ví dụ ad đã Loaded từ trước thì
 * [onAdLoaded] gọi liền) - client không bao giờ lỡ mất trạng thái vì đăng ký muộn.
 */
interface AdListener {
    fun onAdLoading() {}
    fun onAdLoaded() {}
    fun onAdFailedToLoad(error: AdFlowError, willRetry: Boolean) {}

    /** Placement bị chặn không load/hiển thị được vì lý do ngoài chuyện no-fill (tắt qua config,
     * consent chưa có, loadRule/showRule từ chối...). */
    fun onAdBlocked(reason: BlockReason) {}
}
