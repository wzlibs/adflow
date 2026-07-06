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

    override fun load(onResult: (AdLoadResult) -> Unit) {
        if (isReady()) {
            onResult(AdLoadResult.Success)
            return
        }
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
        if (!isReady()) {
            // Not ready whether the ad never loaded or went stale past expiryMs - isReady() is the
            // single source of truth for that distinction, so show() doesn't need to re-derive it.
            // Always kick off a fresh load(): it's a no-op if one is already in flight (isLoading
            // guard) and otherwise this placement would silently report NOT_READY forever, since
            // nothing else ever re-triggers a load once the cached ad expires unshown.
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, "not ready")
            callback.onShowBlocked(BlockReason.NOT_READY)
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
