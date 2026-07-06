package com.adflow.admob.fullscreen

import android.app.Activity
import android.content.Context
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.AdFlowEvent
import com.adflow.core.AdLoadResult
import com.adflow.core.AdRevenueEvent
import com.adflow.core.AdType
import com.adflow.core.BlockReason
import com.adflow.core.PlacementConfig
import com.adflow.core.RewardItem
import com.adflow.core.RewardedAdCallback
import com.adflow.core.RewardedAdManager
import com.adflow.core.WaterfallLoader
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * [RewardedAdManager] implementation backed by AdMob's [RewardedAd].
 *
 * Unlike [com.adflow.core.FullScreenAdManagerBase]-based managers, this class cannot extend that
 * base because [RewardedAdManager.show] takes a [RewardedAdCallback] (which additionally surfaces
 * [RewardedAdCallback.onUserEarnedReward] and [RewardedAdCallback.onAdExpired]) rather than the
 * plain `ShowCallback` the base class is built around. The load/retry/expiry state machine is
 * therefore reimplemented inline, mirroring `FullScreenAdManagerBase`'s behavior.
 *
 * Rewarded ads are intentionally NOT subject to [com.adflow.core.AdShowIntervalPolicy] frequency
 * capping - that policy only applies to interstitial/app open ads by design.
 */
class AdMobRewardedAdManager(
    private val context: Context,
    private val config: PlacementConfig,
) : RewardedAdManager {

    private val placementId = config.placementId

    private var cachedAd: RewardedAd? = null
    private var loadedAtMs: Long = 0L
    private var retryAttempt: Int = 0
    private var isLoading: Boolean = false

    override fun isReady(): Boolean =
        cachedAd != null && System.currentTimeMillis() - loadedAtMs < config.expiryMs

    override fun load(onResult: (AdLoadResult) -> Unit) {
        if (!config.enabled) {
            AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.LOAD_FAILED, "disabled")
            onResult(AdLoadResult.Failure(AdFlowError(-1, "placement disabled")))
            return
        }
        if (config.loadRule?.isAllowed(placementId) == false) {
            AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.LOAD_FAILED, "loadRule rejected")
            onResult(AdLoadResult.Failure(AdFlowError(-2, "load rule rejected")))
            return
        }
        if (isLoading) return
        isLoading = true
        retryAttempt = 0
        startWaterfall(onResult)
    }

    private fun startWaterfall(onResult: (AdLoadResult) -> Unit) {
        AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.LOADING)
        val loader = WaterfallLoader(config.adUnitIds) { adUnitId, cb ->
            AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.WATERFALL_NEXT, adUnitId)
            requestAd(adUnitId, cb)
        }
        loader.start { result ->
            result.fold(
                onSuccess = { ad ->
                    cachedAd = ad
                    loadedAtMs = System.currentTimeMillis()
                    isLoading = false
                    retryAttempt = 0
                    AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.LOADED)
                    onResult(AdLoadResult.Success)
                },
                onFailure = { error ->
                    AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.NO_FILL)
                    retryAttempt += 1
                    if (retryAttempt > config.retryPolicy.maxRetries) {
                        isLoading = false
                        onResult(AdLoadResult.Failure(AdFlowError(-3, error.message ?: "waterfall exhausted")))
                        return@fold
                    }
                    val delayMs = config.retryPolicy.delayForAttempt(retryAttempt)
                    AdFlowCore.logger.log(
                        placementId,
                        AdType.REWARDED,
                        AdFlowEvent.RETRYING,
                        "attempt=$retryAttempt delay=$delayMs",
                    )
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ startWaterfall(onResult) }, delayMs)
                },
            )
        }
    }

    private fun requestAd(adUnitId: String, onResult: (Result<RewardedAd>) -> Unit) {
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
        val ad = cachedAd
        if (ad == null) {
            AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.SHOW_BLOCKED, "not ready")
            callback.onShowBlocked(BlockReason.NOT_READY)
            return
        }
        if (System.currentTimeMillis() - loadedAtMs >= config.expiryMs) {
            cachedAd = null
            AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.EXPIRED)
            callback.onAdExpired()
            return
        }
        if (config.showRule?.isAllowed(placementId) == false) {
            AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.SHOW_BLOCKED, "showRule rejected")
            callback.onShowBlocked(BlockReason.RULE_REJECTED)
            return
        }
        cachedAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = callback.onAdShown()
            override fun onAdDismissedFullScreenContent() = callback.onAdDismissed()
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.SHOW_FAILED, error.message)
                callback.onAdFailedToShow(AdFlowError(error.code, error.message))
            }
        }
        AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.SHOWN)
        ad.show(activity) { rewardItem ->
            callback.onUserEarnedReward(RewardItem(rewardItem.type, rewardItem.amount))
        }
        if (config.preloadEnabled) load()
    }

    private fun precisionName(@AdValue.PrecisionType precisionType: Int): String = when (precisionType) {
        AdValue.PrecisionType.PRECISE -> "PRECISE"
        AdValue.PrecisionType.ESTIMATED -> "ESTIMATED"
        AdValue.PrecisionType.PUBLISHER_PROVIDED -> "PUBLISHER_PROVIDED"
        AdValue.PrecisionType.UNKNOWN -> "UNKNOWN"
        else -> "UNKNOWN"
    }
}
