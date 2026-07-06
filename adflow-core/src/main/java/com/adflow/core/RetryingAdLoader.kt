package com.adflow.core

/**
 * Drives a single [WaterfallLoader] pass for a placement and, on failure, retries the whole
 * waterfall again after [RetryPolicy.delayForAttempt] via [scheduleRetry], up to
 * [RetryPolicy.maxRetries].
 *
 * This is the shared "load + retry-with-backoff-until-exhausted" state machine used by every ad
 * type that retries (full-screen managers via [FullScreenAdManagerBase], plus banner/native/
 * rewarded managers in adapter modules). It intentionally knows nothing about caching the loaded
 * ad, timestamping it, or expiry - callers decide what to do with a successfully loaded ad.
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
     * Starts a load: if one is already in flight (mid-retry-backoff), [onResult] is coalesced onto
     * that in-flight attempt instead of starting a second, independent waterfall pass - it does not
     * join by re-running requestAd itself, it just waits for the same cycle's outcome. Every
     * [onResult] registered this way is invoked exactly once, with that cycle's eventual result,
     * once it finishes.
     */
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

    private fun finish(result: AdLoadResult, ad: TAd?) {
        isRunning = false
        val callbacks = pendingCallbacks.toList()
        pendingCallbacks.clear()
        callbacks.forEach { it.invoke(result, ad) }
    }
}
