package com.adflow.admob.fullscreen

import android.app.Activity
import android.content.Context
import com.adflow.admob.dispatchRevenue
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.AdFlowEvent
import com.adflow.core.AdType
import com.adflow.core.BlockReason
import com.adflow.core.CachedAdLoaderBase
import com.adflow.core.PlacementConfig
import com.adflow.core.RewardItem
import com.adflow.core.RewardedAdCallback
import com.adflow.core.RewardedAdManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * [RewardedAdManager] implementation backed by AdMob's [RewardedAd].
 *
 * Shares the load/cache/expiry/retry/preload lifecycle with full-screen managers via
 * [CachedAdLoaderBase] - only `show()` differs, since [RewardedAdManager.show] takes a
 * [RewardedAdCallback] (which additionally surfaces [RewardedAdCallback.onUserEarnedReward])
 * rather than the plain `ShowCallback` [com.adflow.core.FullScreenAdManagerBase] is built around.
 *
 * Rewarded ads are intentionally NOT subject to [com.adflow.core.AdShowIntervalPolicy] frequency
 * capping - that policy only applies to interstitial/app open ads by design.
 */
open class AdMobRewardedAdManager(
    private val context: Context,
    config: PlacementConfig,
) : CachedAdLoaderBase<RewardedAd>(config, AdType.REWARDED), RewardedAdManager {

    private val placementId = config.placementId

    override fun requestAd(adUnitId: String, onResult: (Result<RewardedAd>) -> Unit) {
        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    ad.onPaidEventListener = OnPaidEventListener { adValue ->
                        dispatchRevenue(placementId, AdType.REWARDED, adUnitId, adValue, ad.responseInfo)
                    }
                    onResult(Result.success(ad))
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    onResult(Result.failure(RuntimeException(error.message)))
                }
            },
        )
    }

    override fun show(activity: Activity, callback: RewardedAdCallback) {
        if (checkNotReadyOrShowRuleBlocked(callback)) return
        // Claimed before consuming the cached ad so a losing claim never sacrifices it: two
        // full-screen ads (even from different managers) must never be on screen at once.
        if (!AdFlowCore.tryClaimFullScreenSlot()) {
            AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.SHOW_BLOCKED, "another full-screen ad is showing")
            callback.onShowBlocked(BlockReason.ANOTHER_AD_SHOWING)
            return
        }
        val ad = consumeCachedAd()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = callback.onAdShown()

            override fun onAdDismissedFullScreenContent() {
                AdFlowCore.releaseFullScreenSlot()
                callback.onAdDismissed()
                // Preload the next ad once this one is actually done, not the instant show() was
                // called - the display duration is out of our control.
                preloadIfEnabled()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AdFlowCore.releaseFullScreenSlot()
                AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.SHOW_FAILED, error.message)
                callback.onAdFailedToShow(AdFlowError(error.code, error.message))
                preloadIfEnabled()
            }
        }
        AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.SHOWN)
        try {
            ad.show(activity) { rewardItem ->
                callback.onUserEarnedReward(RewardItem(rewardItem.type, rewardItem.amount))
            }
        } catch (e: Throwable) {
            // ad.show() is expected to report failure via onAdFailedToShowFullScreenContent, not
            // throw - but if the SDK ever does throw synchronously, the slot must not stay claimed
            // forever (which would silently disable AppOpenAdController and every other
            // full-screen show for the rest of the process).
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
