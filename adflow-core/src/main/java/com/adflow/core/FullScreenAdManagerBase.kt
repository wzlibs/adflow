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
        // Claimed before consuming the cached ad so a losing claim never sacrifices it: two
        // full-screen ads (even from different managers) must never be on screen at once.
        if (!AdFlowCore.tryClaimFullScreenSlot()) {
            AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOW_BLOCKED, "another full-screen ad is showing")
            callback.onShowBlocked(BlockReason.ANOTHER_AD_SHOWING)
            return
        }
        val ad = consumeCachedAd()
        AdFlowCore.logger.log(config.placementId, adType, AdFlowEvent.SHOWN)
        try {
            performShow(
                ad,
                activity,
                object : ShowCallback {
                    override fun onAdShown() = callback.onAdShown()

                    override fun onAdDismissed() {
                        // The interval cooldown starts once the user actually finishes viewing the
                        // ad, not the instant we asked the SDK to display it: display duration
                        // varies per ad and isn't something we control, so anchoring on "show"
                        // would under-count the real gap between ads the user experiences.
                        AdShowIntervalPolicy.recordShown(adType, nowProvider())
                        AdFlowCore.releaseFullScreenSlot()
                        callback.onAdDismissed()
                        preloadIfEnabled()
                    }

                    override fun onAdFailedToShow(error: AdFlowError) {
                        AdFlowCore.releaseFullScreenSlot()
                        callback.onAdFailedToShow(error)
                        preloadIfEnabled()
                    }
                },
            )
        } catch (e: Throwable) {
            // performShow() is expected to report failure via onAdFailedToShow, not throw - but if
            // the SDK ever does throw synchronously, the slot must not stay claimed forever (which
            // would silently disable AppOpenAdController and every other full-screen show for the
            // rest of the process).
            AdFlowCore.releaseFullScreenSlot()
            // The consumed ad is gone and its state after a synchronous SDK throw is unknown, so
            // self-heal with a fresh load - same as the expired/not-ready path - instead of leaving
            // the placement stuck reporting not-ready until an unrelated caller happens to load()
            // again. Unconditional (not gated on preloadEnabled): this is recovery from a failure,
            // not the ahead-of-time preload preloadIfEnabled() is for.
            load {}
            throw e
        }
    }
}
