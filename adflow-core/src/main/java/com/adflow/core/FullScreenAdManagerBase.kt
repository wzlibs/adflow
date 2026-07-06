package com.adflow.core

import android.app.Activity

abstract class FullScreenAdManagerBase<TAd : Any>(
    config: PlacementConfig,
    adType: AdType,
) : CachedAdLoaderBase<TAd>(config, adType), FullScreenAdManager {

    protected abstract fun performShow(ad: TAd, activity: Activity, callback: ShowCallback)

    override fun show(activity: Activity, callback: ShowCallback) {
        dropIfExpired()
        if (!isReady()) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, "not ready")
            callback.onShowBlocked(BlockReason.NOT_READY)
            // Self-heal: retrigger a load so this placement doesn't stay stuck reporting not-ready
            // forever - harmless no-op if one is already in flight (RetryingAdLoader ignores a
            // concurrent start() call rather than starting a second, independent one).
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
        val ad = consumeCachedAd()
        AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOWN)
        performShow(
            ad,
            activity,
            object : ShowCallback {
                override fun onAdShown() = callback.onAdShown()

                override fun onAdDismissed() {
                    // The interval cooldown starts once the user actually finishes viewing the ad,
                    // not the instant we asked the SDK to display it: display duration varies per
                    // ad and isn't something we control, so anchoring on "show" would under-count
                    // the real gap between ads the user experiences.
                    AdShowIntervalPolicy.recordShown(adType, nowProvider())
                    callback.onAdDismissed()
                    preloadIfEnabled()
                }

                override fun onAdFailedToShow(error: AdFlowError) {
                    callback.onAdFailedToShow(error)
                    preloadIfEnabled()
                }
            },
        )
    }
}
