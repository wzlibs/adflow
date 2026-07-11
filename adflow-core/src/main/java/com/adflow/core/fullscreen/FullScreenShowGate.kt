package com.adflow.core.fullscreen

import android.app.Activity
import com.adflow.core.AdFlowError
import com.adflow.core.AdState
import com.adflow.core.BlockReason
import com.adflow.core.config.PlacementConfig
import com.adflow.core.engine.AdFlowRuntime
import com.adflow.core.engine.AdLoadEngine
import com.adflow.core.logging.AdFlowEvent
import com.adflow.core.network.LoadedFullScreenAd
import com.adflow.core.rewarded.RewardItem

/**
 * Logic `show()` dùng chung cho Interstitial/App Open/Rewarded - 3 loại này chỉ khác nhau ở hình
 * dạng callback app truyền vào ([FullScreenCallback] hay `RewardedAdCallback` có thêm
 * `onUserEarnedReward`), không khác gì ở thứ tự gate hay cách nối vào [LoadedFullScreenAd]. Mỗi
 * `*Impl` gọi hàm này, truyền các lambda tương ứng của callback nó nhận (Interstitial/App Open
 * truyền [onReward] rỗng vì không bao giờ có phần thưởng).
 *
 * Thứ tự chặn: showRule -> interval -> slot -> lấy ad ([AdLoadEngine.take]). Claim slot TRƯỚC khi
 * lấy ad, để nếu claim thất bại thì ad cache không bị mất oan.
 */
internal fun showFullScreenAd(
    placementId: String,
    config: PlacementConfig,
    engine: AdLoadEngine<LoadedFullScreenAd>,
    runtime: AdFlowRuntime,
    activity: Activity,
    onShowed: () -> Unit,
    onDismissed: () -> Unit,
    onFailedToShow: (AdFlowError) -> Unit,
    onBlocked: (BlockReason) -> Unit,
    onImpression: () -> Unit,
    onClicked: () -> Unit,
    onReward: (RewardItem) -> Unit,
) {
    val adType = config.adType

    fun blocked(reason: BlockReason, detail: String) {
        runtime.logger.log(placementId, adType, AdFlowEvent.SHOW_BLOCKED, detail)
        onBlocked(reason)
    }

    if (config.showRule?.isAllowed(placementId) == false) {
        blocked(BlockReason.RULE_REJECTED, "showRule rejected")
        return
    }
    if (!runtime.showIntervalPolicy.canShow(adType)) {
        blocked(BlockReason.INTERVAL_NOT_ELAPSED, "interval not elapsed")
        return
    }
    if (!runtime.fullScreenSlot.tryClaim()) {
        blocked(BlockReason.ANOTHER_AD_SHOWING, "another full-screen ad is showing")
        return
    }

    val ad = engine.take()
    if (ad == null) {
        runtime.fullScreenSlot.release()
        val reason = if (engine.state.value is AdState.Loading) BlockReason.STILL_LOADING else BlockReason.NO_AD_AVAILABLE
        blocked(reason, "not ready")
        // Self-heal: không để placement kẹt mãi ở not-ready - vô hại nếu đã có 1 lần load đang chạy.
        engine.ensureLoaded()
        return
    }

    engine.markShowing()
    runtime.logger.log(placementId, adType, AdFlowEvent.SHOWN, null)

    fun afterShowEnds() {
        if (config.preload) engine.ensureLoaded() else engine.markIdle()
    }

    try {
        ad.show(
            activity,
            object : LoadedFullScreenAd.ShowListener {
                override fun onShowed() = onShowed()

                override fun onDismissed() {
                    // Đồng hồ cooldown chỉ bắt đầu tính khi user thực sự xem xong ad, không phải
                    // lúc yêu cầu SDK hiển thị - thời lượng hiển thị không do ta kiểm soát.
                    runtime.showIntervalPolicy.recordDismissed(adType)
                    runtime.fullScreenSlot.release()
                    onDismissed()
                    afterShowEnds()
                }

                override fun onFailedToShow(error: AdFlowError) {
                    runtime.fullScreenSlot.release()
                    runtime.logger.log(placementId, adType, AdFlowEvent.SHOW_FAILED, error.message)
                    onFailedToShow(error)
                    afterShowEnds()
                }

                override fun onImpression() = onImpression()
                override fun onClicked() = onClicked()
                override fun onUserEarnedReward(reward: RewardItem) = onReward(reward)
            },
        )
    } catch (e: Throwable) {
        // ad.show() được kỳ vọng báo lỗi qua onFailedToShow, không throw - nhưng nếu SDK vẫn throw
        // đồng bộ, slot không được giữ mãi ở trạng thái đã claim (nếu không sẽ âm thầm vô hiệu hóa
        // AppOpenForegroundObserver và mọi full-screen show khác cho tới hết đời process). Ad đã bị
        // take() và trạng thái của nó sau khi SDK throw đồng bộ là không xác định, nên tự phục hồi
        // bằng 1 lần load mới thay vì kẹt ở not-ready.
        runtime.fullScreenSlot.release()
        engine.ensureLoaded()
        throw e
    }
}
