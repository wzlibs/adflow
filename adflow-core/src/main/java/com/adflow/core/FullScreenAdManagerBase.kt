package com.adflow.core

import android.app.Activity

abstract class FullScreenAdManagerBase<TAd : Any>(
    config: PlacementConfig,
    adType: AdType,
) : CachedAdLoaderBase<TAd>(config, adType), FullScreenAdManager {

    protected abstract fun performShow(ad: TAd, activity: Activity, callback: ShowCallback)

    override fun show(activity: Activity, callback: ShowCallback) {
        if (checkNotReadyOrShowRuleBlocked(callback)) return
        if (!AdShowIntervalPolicy.canShow(adType, nowProvider())) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, "interval not elapsed")
            callback.onShowBlocked(BlockReason.INTERVAL_NOT_ELAPSED)
            return
        }
        // Claim trước khi consume cached ad, để nếu claim thất bại thì cached ad không bị mất
        // oan: 2 full-screen ad (dù từ manager khác nhau) không bao giờ được cùng hiển thị.
        if (!AdFlowCore.tryClaimFullScreenSlot()) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, "another full-screen ad is showing")
            callback.onShowBlocked(BlockReason.ANOTHER_AD_SHOWING)
            return
        }
        val ad = consumeCachedAd()
        AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOWN)
        try {
            performShow(
                ad,
                activity,
                object : ShowCallback {
                    override fun onAdShown() = callback.onAdShown()

                    override fun onAdDismissed() {
                        // Đồng hồ cooldown chỉ bắt đầu tính khi user thực sự xem xong ad, không
                        // phải ngay lúc ta yêu cầu SDK hiển thị: thời gian hiển thị khác nhau tùy
                        // ad và không do ta kiểm soát, nên tính từ lúc "show" sẽ đếm thiếu khoảng
                        // cách thật mà user cảm nhận giữa 2 lần xem ad.
                        AdShowIntervalPolicy.recordShown(adType, nowProvider())
                        AdFlowCore.releaseFullScreenSlot()
                        callback.onAdDismissed()
                        preloadIfEnabled()
                    }

                    override fun onAdFailedToShow(error: AdFlowError) {
                        AdFlowCore.releaseFullScreenSlot()
                        callback.onAdFailedToShow(error)
                        preloadIfEnabled()
                    }
                },
            )
        } catch (e: Throwable) {
            // performShow() được kỳ vọng báo lỗi qua onAdFailedToShow, không throw - nhưng nếu SDK
            // vẫn throw đồng bộ, slot không được giữ mãi ở trạng thái đã claim (nếu không sẽ âm
            // thầm vô hiệu hóa AppOpenAdController và mọi full-screen show khác cho tới hết đời
            // process).
            AdFlowCore.releaseFullScreenSlot()
            // Ad đã bị consume và trạng thái của nó sau khi SDK throw đồng bộ là không xác định,
            // nên tự phục hồi (self-heal) bằng 1 lần load mới - giống hướng xử lý ad hết hạn/chưa
            // ready - thay vì để placement bị kẹt ở trạng thái not-ready cho đến khi có caller
            // khác vô tình gọi load(). Không điều kiện (không phụ thuộc preloadEnabled): đây là
            // phục hồi sau lỗi, không phải preload chủ động mà preloadIfEnabled() dùng cho.
            load {}
            throw e
        }
    }
}
