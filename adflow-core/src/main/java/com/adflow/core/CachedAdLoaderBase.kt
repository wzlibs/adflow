package com.adflow.core

/**
 * Owns the "load, cache, expire, retry, preload" lifecycle shared by every ad type that caches a
 * single ad instance ahead of display: full-screen ads (Interstitial/AppOpen, via
 * [FullScreenAdManagerBase]) and Rewarded (via `AdMobRewardedAdManager` in the admob module).
 *
 * `show()` itself is deliberately NOT here: [com.adflow.core] declares two different show contracts
 * (`FullScreenAdManager.show` takes a [ShowCallback], `RewardedAdManager.show` takes a
 * [RewardedAdCallback] which additionally surfaces [RewardedAdCallback.onUserEarnedReward]), so each
 * concrete manager implements its own `show()` using the protected helpers here ([dropIfExpired],
 * [consumeCachedAd], [preloadIfEnabled]) rather than duplicating the caching/expiry/retry bookkeeping
 * itself.
 *
 * Banner/Native managers don't extend this: per the design's Global Constraint, they're never
 * subject to expiry, so their (simpler, expiry-free) cache bookkeeping stays separate.
 */
abstract class CachedAdLoaderBase<TAd : Any>(
    protected val config: PlacementConfig,
    protected val adType: AdType,
) {
    protected abstract fun requestAd(adUnitId: String, onResult: (Result<TAd>) -> Unit)

    private val loader: RetryingAdLoader<TAd> =
        RetryingAdLoader(config, adType) { adUnitId, onResult -> requestAd(adUnitId, onResult) }

    var scheduleRetry: (delayMs: Long, action: () -> Unit) -> Unit
        get() = loader.scheduleRetry
        set(value) { loader.scheduleRetry = value }
    var nowProvider: () -> Long = { System.currentTimeMillis() }

    protected var cachedAd: TAd? = null
        private set
    private var loadedAtMs: Long = 0L

    open fun isReady(): Boolean {
        val ageMs = nowProvider() - loadedAtMs
        return cachedAd != null && ageMs < config.expiryMs
    }

    /** Drops the cached ad once it's past [PlacementConfig.expiryMs], rather than holding onto a
     * stale reference until the next successful load overwrites it. */
    protected fun dropIfExpired() {
        if (cachedAd != null && nowProvider() - loadedAtMs >= config.expiryMs) {
            cachedAd = null
        }
    }

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
                loadedAtMs = nowProvider()
            }
            onResult(result)
        }
    }

    /**
     * Takes and clears the cached ad - subclasses call this right before actually displaying it,
     * once every show-blocking check (ready/showRule/interval) has already passed. Requires
     * [isReady] to have just been checked true; fails loudly instead of silently swallowing the
     * show() call if that invariant is ever violated.
     */
    protected fun consumeCachedAd(): TAd {
        val ad = requireNotNull(cachedAd)
        cachedAd = null
        return ad
    }

    /**
     * Preloads the next ad if [PlacementConfig.preloadEnabled] is set. Call this once the current
     * ad's display lifecycle has truly ended (dismissed or failed to show) - not immediately when
     * show() is called - since display duration varies per ad and isn't something callers control.
     */
    protected fun preloadIfEnabled() {
        if (config.preloadEnabled) load {}
    }

    /**
     * Checks and reports the two not-ready/showRule blocking conditions every `show()`
     * implementation built on this base shares (interval-capping, where it applies, is each
     * subclass's own concern - see [AdShowIntervalPolicy]). Drops a stale ad first, logs and
     * notifies [callback] via [AdShowBlockedCallback.onShowBlocked] if blocked, and self-heals with
     * a fresh [load] when the block is due to not being ready (a no-op if one is already in flight).
     *
     * @return true if `show()` should return immediately; false if the caller may proceed to
     * [consumeCachedAd] and actually display the ad.
     */
    protected fun checkNotReadyOrShowRuleBlocked(callback: AdShowBlockedCallback): Boolean {
        dropIfExpired()
        if (!isReady()) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, "not ready")
            callback.onShowBlocked(BlockReason.NOT_READY)
            // Self-heal: retrigger a load so this placement doesn't stay stuck reporting not-ready
            // forever - harmless no-op if one is already in flight (RetryingAdLoader ignores a
            // concurrent start() call rather than starting a second, independent one).
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
