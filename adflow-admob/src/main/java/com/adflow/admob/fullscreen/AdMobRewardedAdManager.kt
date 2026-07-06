package com.adflow.admob.fullscreen

import android.app.Activity
import android.content.Context
import com.adflow.admob.precisionName
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.AdFlowEvent
import com.adflow.core.AdRevenueEvent
import com.adflow.core.AdType
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
                        AdFlowCore.dispatchRevenue(
                            AdRevenueEvent(
                                placementId = placementId,
                                adType = AdType.REWARDED,
                                adUnitId = adUnitId,
                                valueMicros = adValue.valueMicros,
                                currencyCode = adValue.currencyCode,
                                precision = precisionName(adValue.precisionType),
                                adNetwork = ad.responseInfo?.loadedAdapterResponseInfo?.adSourceName,
                            ),
                        )
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
        val ad = consumeCachedAd()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = callback.onAdShown()

            override fun onAdDismissedFullScreenContent() {
                callback.onAdDismissed()
                // Preload the next ad once this one is actually done, not the instant show() was
                // called - the display duration is out of our control.
                preloadIfEnabled()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.SHOW_FAILED, error.message)
                callback.onAdFailedToShow(AdFlowError(error.code, error.message))
                preloadIfEnabled()
            }
        }
        AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.SHOWN)
        ad.show(activity) { rewardItem ->
            callback.onUserEarnedReward(RewardItem(rewardItem.type, rewardItem.amount))
        }
    }
}
