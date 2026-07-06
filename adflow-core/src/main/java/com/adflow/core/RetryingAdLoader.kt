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

    fun start(onResult: (AdLoadResult, TAd?) -> Unit) {
        retryAttempt = 0
        attempt(onResult)
    }

    private fun attempt(onResult: (AdLoadResult, TAd?) -> Unit) {
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
                    onResult(AdLoadResult.Success, ad)
                },
                onFailure = { error ->
                    AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.NO_FILL)
                    retryAttempt += 1
                    if (retryAttempt > config.retryPolicy.maxRetries) {
                        onResult(AdLoadResult.Failure(AdFlowError(-3, error.message ?: "waterfall exhausted")), null)
                        return@fold
                    }
                    val delayMs = config.retryPolicy.delayForAttempt(retryAttempt)
                    AdFlowCore.logger.log(
                        config.placementId,
                        adType,
                        AdFlowEvent.RETRYING,
                        "attempt=$retryAttempt delay=$delayMs",
                    )
                    scheduleRetry(delayMs) { attempt(onResult) }
                },
            )
        }
    }
}
