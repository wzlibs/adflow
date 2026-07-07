package com.adflow.core

/**
 * Nắm giữ vòng đời "load, cache, retry" dùng chung cho mọi loại ad cache trước 1 instance ad để
 * dùng sau: check enabled/loadRule, short-circuit isReady() để bỏ qua 1 lượt waterfall dư thừa, và
 * cache ad khi load thành công. [ExpiringCachedAdLoaderBase] extend class này để thêm expiry (drop
 * ad khi đã cũ, ghi lại timestamp load qua [onLoaded]) cho các loại ad bị cũ (full-screen,
 * Rewarded, Native); Banner dùng trực tiếp class này, vì theo Global Constraint của thiết kế, nó
 * không bao giờ bị cũ sau khi cache nên không cần phần bookkeeping đó.
 */
abstract class SimpleCachedAdLoaderBase<TAd : Any>(
    protected val config: PlacementConfig,
    protected val adType: AdType,
) {
    protected abstract fun requestAd(adUnitId: String, onResult: (Result<TAd>) -> Unit)

    private val loader: RetryingAdLoader<TAd> =
        RetryingAdLoader(config, adType) { adUnitId, onResult -> requestAd(adUnitId, onResult) }

    var scheduleRetry: (delayMs: Long, action: () -> Unit) -> Unit
        get() = loader.scheduleRetry
        set(value) { loader.scheduleRetry = value }

    protected var cachedAd: TAd? = null
        protected set

    open fun isReady(): Boolean = cachedAd != null

    /** Được gọi khi 1 lần load thành công, sau khi [cachedAd] đã được set thành ad mới. Ở đây là
     * no-op; [CachedAdLoaderBase] override để ghi lại timestamp load phục vụ expiry tracking. */
    protected open fun onLoaded(ad: TAd) {}

    open fun load(onResult: (AdLoadResult) -> Unit) {
        if (!config.enabled) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.LOAD_FAILED, "disabled")
            onResult(AdLoadResult.Failure(AdFlowError(-1, "placement disabled")))
            return
        }
        if (!AdFlowCore.consentAllowsAdRequests) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.LOAD_FAILED, "consent not obtained")
            onResult(AdLoadResult.Failure(AdFlowError(-3, "consent not obtained")))
            return
        }
        if (config.loadRule?.isAllowed(config.placementId) == false) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.LOAD_FAILED, "loadRule rejected")
            onResult(AdLoadResult.Failure(AdFlowError(-2, "load rule rejected")))
            return
        }
        if (isReady()) {
            onResult(AdLoadResult.Success)
            return
        }
        loader.start { result, ad ->
            // Guard bằng identity, không chỉ success+non-null: RetryingAdLoader coalesce mọi lệnh
            // load() gọi trong lúc 1 cycle đang chạy vào cùng cycle đó, nên callback này có thể
            // chạy nhiều hơn 1 lần cho 1 lần load thật, mỗi lần cho 1 caller được coalesce, tất cả
            // cùng nhận (result, ad) giống nhau. Chỉ lần đầu tiên mới thực sự cache và gọi
            // onLoaded(); các lần sau chỉ cần gọi onResult() cho đúng caller của nó.
            if (result is AdLoadResult.Success && ad != null && cachedAd !== ad) {
                cachedAd = ad
                onLoaded(ad)
            }
            onResult(result)
        }
    }
}
