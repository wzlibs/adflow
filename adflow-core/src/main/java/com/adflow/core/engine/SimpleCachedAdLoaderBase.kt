package com.adflow.core.engine

import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.AdLoadResult
import com.adflow.core.AdType
import com.adflow.core.BlockReason
import com.adflow.core.config.PlacementConfig
import com.adflow.core.logging.AdFlowEvent
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

    /** Được gọi khi 1 ad bị thay thế/loại bỏ khỏi cache (qua [forceLoad] thành công, hoặc qua
     * [ExpiringCachedAdLoaderBase.dropIfExpired]) - chỗ để subclass giải phóng tài nguyên gắn với
     * ad đó (vd `NativeAd.destroy()`). No-op mặc định. */
    protected open fun onDrop(ad: TAd) {}

    private fun passesGates(onResult: (AdLoadResult) -> Unit): Boolean {
        if (!config.enabled) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.LOAD_FAILED, "disabled")
            onResult(AdLoadResult.Failure(AdFlowError(-1, "placement disabled")))
            return false
        }
        if (!AdFlowCore.consentAllowsAdRequests) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.LOAD_FAILED, "consent not obtained")
            onResult(AdLoadResult.Failure(AdFlowError(-3, "consent not obtained")))
            return false
        }
        if (config.loadRule?.isAllowed(config.placementId) == false) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.LOAD_FAILED, "loadRule rejected")
            onResult(AdLoadResult.Failure(AdFlowError(-2, "load rule rejected")))
            return false
        }
        return true
    }

    private fun startFetch(
        onResult: (AdLoadResult) -> Unit,
        onSwapped: (previous: TAd?, new: TAd) -> Unit,
    ) {
        loader.start { result, ad ->
            // Guard bằng identity, không chỉ success+non-null: RetryingAdLoader coalesce mọi lệnh
            // load()/forceLoad() gọi trong lúc 1 cycle đang chạy vào cùng cycle đó, nên callback
            // này có thể chạy nhiều hơn 1 lần cho 1 lần load thật, mỗi lần cho 1 caller được
            // coalesce, tất cả cùng nhận (result, ad) giống nhau. Chỉ lần đầu tiên mới thực sự
            // swap cache và gọi onSwapped(); các lần sau chỉ cần gọi onResult() cho đúng caller.
            if (result is AdLoadResult.Success && ad != null && cachedAd !== ad) {
                val previous = cachedAd
                cachedAd = ad
                onSwapped(previous, ad)
            }
            onResult(result)
        }
    }

    open fun load(onResult: (AdLoadResult) -> Unit) {
        if (!passesGates(onResult)) return
        if (isReady()) {
            onResult(AdLoadResult.Success)
            return
        }
        startFetch(onResult) { _, ad -> onLoaded(ad) }
    }

    /** Bỏ qua short-circuit [isReady] để ép fetch 1 ad mới thật sự dù ad đang cache vẫn còn hạn.
     * [cachedAd] chỉ bị swap - và [onDrop] chỉ được gọi trên ad *cũ* - khi ad mới load thành công;
     * nếu fetch thất bại, [cachedAd] giữ nguyên không đổi (ad cũ vẫn dùng được cho bất kỳ View nào
     * đang gắn vào nó). */
    protected fun forceLoad(onResult: (AdLoadResult) -> Unit) {
        if (!passesGates(onResult)) return
        startFetch(onResult) { previous, ad ->
            onLoaded(ad)
            if (previous != null) onDrop(previous)
        }
    }

    /**
     * Predicate không throw cho [PlacementConfig.showRule] - dùng nội bộ bởi `createView()`/
     * `getView()` để quyết định bind ad thật hay báo `onShowBlocked(BlockReason.RULE_REJECTED)` (xem
     * `AdMobNativeAdManager`/`AdMobBannerAdManager`).
     */
    protected fun isShowAllowed(): Boolean = config.showRule?.isAllowed(config.placementId) != false
}
