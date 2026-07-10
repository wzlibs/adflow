package com.adflow.core.engine

import com.adflow.core.AdFlowCore
import com.adflow.core.AdShowBlockedCallback
import com.adflow.core.AdType
import com.adflow.core.BlockReason
import com.adflow.core.config.PlacementConfig
import com.adflow.core.fullscreen.FullScreenAdManager
import com.adflow.core.fullscreen.ShowCallback
import com.adflow.core.logging.AdFlowEvent
import com.adflow.core.rewarded.RewardedAdCallback
import com.adflow.core.rewarded.RewardedAdManager
/**
 * Bổ sung các helper cho việc "tiêu thụ" (consumption) khi show() mà full-screen ad và Rewarded
 * cần, dựa trên nền tracking expiry của [ExpiringCachedAdLoaderBase].
 *
 * `show()` bản thân nó CHỦ Ý không nằm ở đây: [com.adflow.core] khai báo 2 contract show() khác
 * nhau (`FullScreenAdManager.show` nhận [ShowCallback], `RewardedAdManager.show` nhận
 * [RewardedAdCallback] - có thêm [RewardedAdCallback.onUserEarnedReward]), nên mỗi manager cụ thể
 * tự triển khai `show()` riêng, dùng các helper protected ở đây ([dropIfExpired],
 * [consumeCachedAd], [preloadIfEnabled]) thay vì phải lặp lại logic cache/expiry/retry.
 */
abstract class CachedAdLoaderBase<TAd : Any>(
    config: PlacementConfig,
    adType: AdType,
) : ExpiringCachedAdLoaderBase<TAd>(config, adType) {

    /**
     * Lấy ra và xóa cached ad - subclass gọi hàm này ngay trước khi thực sự hiển thị ad, sau khi
     * mọi check chặn-show (ready/showRule/interval) đã pass. Yêu cầu [isReady] vừa được check là
     * true; fail rõ ràng (loudly) thay vì âm thầm nuốt (swallow) lệnh show() nếu invariant này bị
     * vi phạm.
     */
    protected fun consumeCachedAd(): TAd {
        val ad = requireNotNull(cachedAd)
        cachedAd = null
        return ad
    }

    /**
     * Preload ad tiếp theo nếu [PlacementConfig.preloadEnabled] được bật. Gọi hàm này khi vòng đời
     * hiển thị của ad hiện tại đã thực sự kết thúc (dismissed hoặc show lỗi) - không gọi ngay lúc
     * show() được gọi - vì thời gian hiển thị khác nhau tùy từng ad và không do caller kiểm soát.
     */
    protected fun preloadIfEnabled() {
        if (config.preloadEnabled) load {}
    }

    /**
     * Check và báo cáo 2 điều kiện chặn not-ready/showRule mà mọi triển khai `show()` dựa trên
     * base này đều dùng chung (interval-capping, nơi nó áp dụng, là việc riêng của từng subclass -
     * xem [AdShowIntervalPolicy]). Drop ad cũ (stale) trước, log và báo [callback] qua
     * [AdShowBlockedCallback.onShowBlocked] nếu bị chặn, và tự phục hồi (self-heal) bằng một lần
     * [load] mới khi bị chặn do not-ready (no-op nếu đã có 1 lần load đang chạy).
     *
     * @return true nếu `show()` nên return ngay; false nếu caller có thể tiếp tục gọi
     * [consumeCachedAd] và thực sự hiển thị ad.
     */
    protected fun checkNotReadyOrShowRuleBlocked(callback: AdShowBlockedCallback): Boolean {
        dropIfExpired()
        if (!isReady()) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, "not ready")
            callback.onShowBlocked(BlockReason.NOT_READY)
            // Self-heal: kích hoạt lại 1 lần load để placement này không bị kẹt mãi ở trạng thái
            // not-ready - vô hại (no-op) nếu đã có 1 lần load đang chạy (RetryingAdLoader sẽ
            // coalesce lệnh start() đồng thời này vào cycle đang chạy, không tạo cycle độc lập thứ 2).
            load {}
            return true
        }
        if (config.showRule?.isAllowed(config.placementId) == false) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, "showRule rejected")
            callback.onShowBlocked(BlockReason.RULE_REJECTED)
            return true
        }
        return false
    }
}
