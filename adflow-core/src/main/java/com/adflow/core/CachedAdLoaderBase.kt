package com.adflow.core

/**
 * Adds the show()-consumption helpers full-screen ads and Rewarded need on top of
 * [ExpiringCachedAdLoaderBase]'s expiry tracking.
 *
 * `show()` itself is deliberately NOT here: [com.adflow.core] declares two different show contracts
 * (`FullScreenAdManager.show` takes a [ShowCallback], `RewardedAdManager.show` takes a
 * [RewardedAdCallback] which additionally surfaces [RewardedAdCallback.onUserEarnedReward]), so each
 * concrete manager implements its own `show()` using the protected helpers here ([dropIfExpired],
 * [consumeCachedAd], [preloadIfEnabled]) rather than duplicating the caching/expiry/retry bookkeeping
 * itself.
 */
abstract class CachedAdLoaderBase<TAd : Any>(
    config: PlacementConfig,
    adType: AdType,
) : ExpiringCachedAdLoaderBase<TAd>(config, adType) {

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
