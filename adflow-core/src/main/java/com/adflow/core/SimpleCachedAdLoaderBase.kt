package com.adflow.core

/**
 * Owns the "load, cache, retry" lifecycle shared by every ad type that caches a single ad instance
 * ahead of use: the enabled/loadRule checks, the isReady() short-circuit that skips a redundant
 * waterfall pass, and caching the ad on a successful load. [ExpiringCachedAdLoaderBase] extends
 * this to add expiry (dropping the ad once stale, recording a load timestamp via [onLoaded]) for
 * the ad types that go stale (full-screen, Rewarded, Native); Banner uses this class directly,
 * since per the design's Global Constraint it never goes stale once cached and doesn't need that
 * bookkeeping.
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

    /** Called once a load succeeds, after [cachedAd] is already set to the new ad. No-op here;
     * [CachedAdLoaderBase] overrides it to record the load timestamp for expiry tracking. */
    protected open fun onLoaded(ad: TAd) {}

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
                onLoaded(ad)
            }
            onResult(result)
        }
    }
}
