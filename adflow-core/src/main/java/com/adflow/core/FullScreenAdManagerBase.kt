package com.adflow.core

import android.app.Activity

abstract class FullScreenAdManagerBase<TAd : Any>(
    private val config: PlacementConfig,
    private val adType: AdType,
) : FullScreenAdManager {

    protected abstract fun requestAd(adUnitId: String, onResult: (Result<TAd>) -> Unit)
    protected abstract fun performShow(ad: TAd, activity: Activity, callback: ShowCallback)

    private val loader: RetryingAdLoader<TAd> =
        RetryingAdLoader(config, adType) { adUnitId, onResult -> requestAd(adUnitId, onResult) }

    internal var scheduleRetry: (delayMs: Long, action: () -> Unit) -> Unit
        get() = loader.scheduleRetry
        set(value) { loader.scheduleRetry = value }
    internal var nowProvider: () -> Long = { System.currentTimeMillis() }

    private var cachedAd: TAd? = null
    private var loadedAtMs: Long = 0L
    private var isLoading: Boolean = false

    override fun isReady(): Boolean {
        val ageMs = nowProvider() - loadedAtMs
        return cachedAd != null && ageMs < config.expiryMs
    }

    /** Drops the cached ad once it's past [PlacementConfig.expiryMs], rather than holding onto a
     * stale reference until the next successful load overwrites it. */
    private fun dropIfExpired() {
        if (cachedAd != null && nowProvider() - loadedAtMs >= config.expiryMs) {
            cachedAd = null
        }
    }

    override fun load(onResult: (AdLoadResult) -> Unit) {
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
        if (isLoading) return
        isLoading = true
        loader.start { result, ad ->
            if (result is AdLoadResult.Success && ad != null) {
                cachedAd = ad
                loadedAtMs = nowProvider()
            }
            isLoading = false
            onResult(result)
        }
    }

    override fun show(activity: Activity, callback: ShowCallback) {
        dropIfExpired()
        if (!isReady()) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, "not ready")
            callback.onShowBlocked(BlockReason.NOT_READY)
            // Self-heal: retrigger a load so this placement doesn't stay stuck reporting not-ready
            // forever - it's a no-op if one is already in flight, via load()'s isLoading guard.
            load()
            return
        }
        if (config.showRule?.isAllowed(config.placementId) == false) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, "showRule rejected")
            callback.onShowBlocked(BlockReason.RULE_REJECTED)
            return
        }
        if (!AdShowIntervalPolicy.canShow(adType, nowProvider())) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, "interval not elapsed")
            callback.onShowBlocked(BlockReason.INTERVAL_NOT_ELAPSED)
            return
        }
        val ad = cachedAd ?: return
        cachedAd = null
        AdShowIntervalPolicy.recordShown(adType, nowProvider())
        AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOWN)
        performShow(ad, activity, callback)
        if (config.preloadEnabled) load()
    }
}
