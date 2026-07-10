package com.adflow.admob.fullscreen

import android.app.Activity
import android.content.Context
import com.adflow.admob.dispatchRevenue
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.logging.AdFlowEvent
import com.adflow.core.AdType
import com.adflow.core.BlockReason
import com.adflow.core.engine.CachedAdLoaderBase
import com.adflow.core.config.PlacementConfig
import com.adflow.core.rewarded.RewardItem
import com.adflow.core.rewarded.RewardedAdCallback
import com.adflow.core.rewarded.RewardedAdManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Triển khai [RewardedAdManager] dựa trên [RewardedAd] của AdMob.
 *
 * Dùng chung vòng đời load/cache/expiry/retry/preload với các full-screen manager qua
 * [CachedAdLoaderBase] - chỉ `show()` khác, vì [RewardedAdManager.show] nhận
 * [RewardedAdCallback] (có thêm [RewardedAdCallback.onUserEarnedReward]) thay vì `ShowCallback`
 * thông thường mà [com.adflow.core.FullScreenAdManagerBase] dùng.
 *
 * Rewarded ad CHỦ Ý không bị giới hạn tần suất bởi [com.adflow.core.AdShowIntervalPolicy] -
 * policy đó theo thiết kế chỉ áp dụng cho interstitial/app open.
 */
open class AdMobRewardedAdManager(
    private val context: Context,
    config: PlacementConfig,
) : CachedAdLoaderBase<RewardedAd>(config, AdType.REWARDED), RewardedAdManager {

    private val placementId = config.placementId

    override fun requestAd(adUnitId: String, onResult: (Result<RewardedAd>) -> Unit) {
        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    ad.onPaidEventListener = OnPaidEventListener { adValue ->
                        dispatchRevenue(placementId, AdType.REWARDED, adUnitId, adValue, ad.responseInfo)
                    }
                    onResult(Result.success(ad))
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    onResult(Result.failure(RuntimeException(error.message)))
                }
            },
        )
    }

    override fun show(activity: Activity, callback: RewardedAdCallback) {
        if (checkNotReadyOrShowRuleBlocked(callback)) return
        // Claim trước khi consume cached ad, để nếu claim thất bại thì cached ad không bị mất
        // oan: 2 full-screen ad (dù từ manager khác nhau) không bao giờ được cùng hiển thị.
        if (!AdFlowCore.tryClaimFullScreenSlot()) {
            AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.SHOW_BLOCKED, "another full-screen ad is showing")
            callback.onShowBlocked(BlockReason.ANOTHER_AD_SHOWING)
            return
        }
        val ad = consumeCachedAd()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = callback.onAdShown()

            override fun onAdDismissedFullScreenContent() {
                AdFlowCore.releaseFullScreenSlot()
                callback.onAdDismissed()
                // Preload ad tiếp theo khi ad này thực sự đã xong, không phải ngay lúc show() được
                // gọi - thời gian hiển thị không do ta kiểm soát.
                preloadIfEnabled()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AdFlowCore.releaseFullScreenSlot()
                AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.SHOW_FAILED, error.message)
                callback.onAdFailedToShow(AdFlowError(error.code, error.message))
                preloadIfEnabled()
            }
        }
        AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.SHOWN)
        try {
            ad.show(activity) { rewardItem ->
                callback.onUserEarnedReward(RewardItem(rewardItem.type, rewardItem.amount))
            }
        } catch (e: Throwable) {
            // ad.show() được kỳ vọng báo lỗi qua onAdFailedToShowFullScreenContent, không throw -
            // nhưng nếu SDK vẫn throw đồng bộ, slot không được giữ mãi ở trạng thái đã claim (nếu
            // không sẽ âm thầm vô hiệu hóa AppOpenAdController và mọi full-screen show khác cho
            // tới hết đời process).
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
