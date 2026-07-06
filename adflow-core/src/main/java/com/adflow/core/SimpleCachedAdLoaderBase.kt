package com.adflow.core

/**
 * Owns the "load, cache, retry" lifecycle shared by ad types that never go stale once cached:
 * Banner and Native (via their respective admob-module managers). Per the design's Global
 * Constraint, expiry only applies to full-screen/rewarded ad types - see [CachedAdLoaderBase] for
 * that variant, which additionally tracks a load timestamp and drops the ad past
 * [PlacementConfig.expiryMs]. This class intentionally doesn't share a hierarchy with
 * [CachedAdLoaderBase]: the two differ in exactly the expiry bookkeeping, and threading that
 * through a common base would need more generic hooks than the modest duplication it would save.
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
        private set

    open fun isReady(): Boolean = cachedAd != null

    open fun load(onResult: (AdLoadResult) -> Unit) {
        if (!config.enabled) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.LOAD_FAILED, "disabled")
            onResult(AdLoadResult.Failure(AdFlowError(-1, "placement disabled")))
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
            if (result is AdLoadResult.Success && ad != null) {
                cachedAd = ad
            }
            onResult(result)
        }
    }
}
