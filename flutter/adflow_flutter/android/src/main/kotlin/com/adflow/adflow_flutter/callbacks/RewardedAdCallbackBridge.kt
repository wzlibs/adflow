package com.adflow.adflow_flutter.callbacks

import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.PShowEventKind
import com.adflow.adflow_flutter.toPigeon
import com.adflow.core.AdFlowError
import com.adflow.core.BlockReason
import com.adflow.core.rewarded.RewardItem
import com.adflow.core.rewarded.RewardedAdCallback

/**
 * Tương tự [ShowCallbackBridge] nhưng cho [RewardedAdCallback] - thêm nhánh
 * [PShowEventKind.USER_EARNED_REWARD] mang [RewardItem]. `onAdLoaded`/`onAdFailedToLoad` không
 * override vì `AdMobRewardedAdManager` không bao giờ gọi 2 callback đó (đã xác nhận qua khảo sát
 * code thật), nên không cần map sang Dart.
 */
class RewardedAdCallbackBridge(
    private val placementId: String,
    private val flutterApi: AdFlowFlutterApi,
) : RewardedAdCallback {

    override fun onAdShown() {
        flutterApi.onShowEvent(placementId, PShowEventKind.SHOWN, null, null, null) {}
    }

    override fun onAdFailedToShow(error: AdFlowError) {
        flutterApi.onShowEvent(placementId, PShowEventKind.FAILED_TO_SHOW, error.toPigeon(), null, null) {}
    }

    override fun onAdDismissed() {
        flutterApi.onShowEvent(placementId, PShowEventKind.DISMISSED, null, null, null) {}
    }

    override fun onShowBlocked(reason: BlockReason) {
        flutterApi.onShowEvent(placementId, PShowEventKind.SHOW_BLOCKED, null, reason.toPigeon(), null) {}
    }

    override fun onUserEarnedReward(reward: RewardItem) {
        flutterApi.onShowEvent(placementId, PShowEventKind.USER_EARNED_REWARD, null, null, reward.toPigeon()) {}
    }
}
