package com.adflow.core.engine

import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.AdLoadResult
import com.adflow.core.AdType
import com.adflow.core.config.PlacementConfig
import com.adflow.core.config.RetryPolicy
import com.adflow.core.fullscreen.FullScreenAdManagerBase
import com.adflow.core.logging.AdFlowEvent
/**
 * Chạy một lượt [WaterfallLoader] cho 1 placement, và khi thất bại, retry lại toàn bộ waterfall
 * sau [RetryPolicy.delayForAttempt] qua [scheduleRetry], tối đa [RetryPolicy.maxRetries] lần.
 *
 * Đây là state machine "load + retry-with-backoff-until-exhausted" dùng chung cho mọi loại ad có
 * retry (full-screen manager qua [FullScreenAdManagerBase], cùng banner/native/rewarded manager ở
 * các module adapter). Nó chủ ý không biết gì về việc cache ad đã load, đánh timestamp, hay
 * expiry - caller tự quyết định làm gì với ad load thành công.
 */
class RetryingAdLoader<TAd>(
    private val config: PlacementConfig,
    private val adType: AdType,
    private val requestAd: (adUnitId: String, onResult: (Result<TAd>) -> Unit) -> Unit,
) {
    var scheduleRetry: (delayMs: Long, action: () -> Unit) -> Unit =
        { delayMs, action -> android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(action, delayMs) }

    private var retryAttempt: Int = 0
    private var isRunning: Boolean = false
    private val pendingCallbacks = mutableListOf<(AdLoadResult, TAd?) -> Unit>()

    /**
     * Bắt đầu một lần load: nếu đã có 1 lần đang chạy (mid-retry-backoff), [onResult] được
     * coalesce vào lần đang chạy đó thay vì khởi động một lượt waterfall độc lập thứ 2 - nó không
     * "join" bằng cách tự gọi lại requestAd, mà chỉ đợi kết quả của cùng 1 cycle đó. Mỗi [onResult]
     * đăng ký theo cách này được gọi đúng 1 lần, với kết quả cuối cùng của cycle đó, khi nó xong.
     */
    // start()/finish() được synchronized để việc check-rồi-set trên isRunning (và state chung
    // pendingCallbacks/retryAttempt) là atomic giữa các thread, không chỉ đúng trong trường hợp
    // gọi lại tuần tự trên cùng 1 thread - callback của requestAd() không được đảm bảo chạy trên
    // đúng thread gốc của caller.
    @Synchronized
    fun start(onResult: (AdLoadResult, TAd?) -> Unit) {
        pendingCallbacks += onResult
        if (isRunning) return
        isRunning = true
        retryAttempt = 0
        attempt()
    }

    private fun attempt() {
        AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.LOADING)
        val loader = WaterfallLoader(config.adUnitIds) { adUnitId, cb ->
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.WATERFALL_NEXT, adUnitId)
            requestAd(adUnitId, cb)
        }
        loader.start { result ->
            result.fold(
                onSuccess = { ad ->
                    retryAttempt = 0
                    AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.LOADED)
                    finish(AdLoadResult.Success, ad)
                },
                onFailure = { error ->
                    AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.NO_FILL)
                    retryAttempt += 1
                    if (retryAttempt > config.retryPolicy.maxRetries) {
                        finish(AdLoadResult.Failure(AdFlowError(-3, error.message ?: "waterfall exhausted")), null)
                        return@fold
                    }
                    val delayMs = config.retryPolicy.delayForAttempt(retryAttempt)
                    AdFlowCore.logger.log(
                        config.placementId,
                        adType,
                        AdFlowEvent.RETRYING,
                        "attempt=$retryAttempt delay=$delayMs",
                    )
                    scheduleRetry(delayMs) { attempt() }
                },
            )
        }
    }

    @Synchronized
    private fun finish(result: AdLoadResult, ad: TAd?) {
        isRunning = false
        val callbacks = pendingCallbacks.toList()
        pendingCallbacks.clear()
        callbacks.forEach { it.invoke(result, ad) }
    }
}
