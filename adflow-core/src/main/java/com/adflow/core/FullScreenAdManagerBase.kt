package com.adflow.core

import android.app.Activity

abstract class FullScreenAdManagerBase<TAd : Any>(
    private val config: PlacementConfig,
    private val adType: AdType,
) : FullScreenAdManager {

    protected abstract fun requestAd(adUnitId: String, onResult: (Result<TAd>) -> Unit)
    protected abstract fun performShow(ad: TAd, activity: Activity, callback: ShowCallback)

    internal var scheduleRetry: (delayMs: Long, action: () -> Unit) -> Unit =
        { delayMs, action -> android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(action, delayMs) }
    internal var nowProvider: () -> Long = { System.currentTimeMillis() }

    private var cachedAd: TAd? = null
    private var loadedAtMs: Long = 0L
    private var retryAttempt: Int = 0
    private var isLoading: Boolean = false

    override fun isReady(): Boolean {
        val ageMs = nowProvider() - loadedAtMs
        return cachedAd != null && ageMs < config.expiryMs
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
        if (isLoading) return
        isLoading = true
        retryAttempt = 0
        startWaterfall(onResult)
    }

    private fun startWaterfall(onResult: (AdLoadResult) -> Unit) {
        AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.LOADING)
        val loader = WaterfallLoader(config.adUnitIds) { adUnitId, cb ->
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.WATERFALL_NEXT, adUnitId)
            requestAd(adUnitId, cb)
        }
        loader.start { result ->
            result.fold(
                onSuccess = { ad ->
                    cachedAd = ad
                    loadedAtMs = nowProvider()
                    isLoading = false
                    retryAttempt = 0
                    AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.LOADED)
                    onResult(AdLoadResult.Success)
                },
                onFailure = { error ->
                    AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.NO_FILL)
                    retryAttempt += 1
                    if (retryAttempt > config.retryPolicy.maxRetries) {
                        isLoading = false
                        onResult(AdLoadResult.Failure(AdFlowError(-3, error.message ?: "waterfall exhausted")))
                        return@fold
                    }
                    val delayMs = config.retryPolicy.delayForAttempt(retryAttempt)
                    AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.RETRYING, "attempt=$retryAttempt delay=$delayMs")
                    scheduleRetry(delayMs) { startWaterfall(onResult) }
                },
            )
        }
    }

    override fun show(activity: Activity, callback: ShowCallback) {
        if (!isReady()) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, "not ready")
            callback.onShowBlocked(BlockReason.NOT_READY)
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
