package com.adflow.core

import android.app.Activity

abstract class FullScreenAdManagerBase<TAd : Any>(
    config: PlacementConfig,
    adType: AdType,
) : CachedAdLoaderBase<TAd>(config, adType), FullScreenAdManager {

    protected abstract fun performShow(ad: TAd, activity: Activity, callback: ShowCallback)

    override fun show(activity: Activity, callback: ShowCallback) {
        if (checkNotReadyOrShowRuleBlocked(callback)) return
        if (!AdShowIntervalPolicy.canShow(adType, nowProvider())) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, "interval not elapsed")
            callback.onShowBlocked(BlockReason.INTERVAL_NOT_ELAPSED)
            return
        }
        val ad = consumeCachedAd()
        AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOWN)
        // Tracked so AppOpenAdController never shows an App Open ad on top of this one.
        AdFlowCore.setShowingFullScreenAd(true)
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
                    AdFlowCore.setShowingFullScreenAd(false)
                    callback.onAdDismissed()
                    preloadIfEnabled()
                }

                override fun onAdFailedToShow(error: AdFlowError) {
                    AdFlowCore.setShowingFullScreenAd(false)
                    callback.onAdFailedToShow(error)
                    preloadIfEnabled()
                }
            },
        )
    }
}
