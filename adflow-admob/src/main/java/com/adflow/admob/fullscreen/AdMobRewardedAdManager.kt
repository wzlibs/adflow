package com.adflow.admob.fullscreen

import android.app.Activity
import android.content.Context
import com.adflow.admob.precisionName
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.AdFlowEvent
import com.adflow.core.AdLoadResult
import com.adflow.core.AdRevenueEvent
import com.adflow.core.AdType
import com.adflow.core.BlockReason
import com.adflow.core.PlacementConfig
import com.adflow.core.RetryingAdLoader
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
 * Unlike [com.adflow.core.FullScreenAdManagerBase]-based managers, this class cannot extend that
 * base because [RewardedAdManager.show] takes a [RewardedAdCallback] (which additionally surfaces
 * [RewardedAdCallback.onUserEarnedReward] and [RewardedAdCallback.onAdExpired]) rather than the
 * plain `ShowCallback` the base class is built around. The load/retry plumbing, however, is
 * identical to the full-screen managers, so it delegates to the same shared
 * [com.adflow.core.RetryingAdLoader] rather than reimplementing the waterfall/backoff state
 * machine inline. This class keeps its own cached-ad/expiry bookkeeping and `show()` signature.
 *
 * Rewarded ads are intentionally NOT subject to [com.adflow.core.AdShowIntervalPolicy] frequency
 * capping - that policy only applies to interstitial/app open ads by design.
 */
open class AdMobRewardedAdManager(
    private val context: Context,
    private val config: PlacementConfig,
) : RewardedAdManager {

    private val placementId = config.placementId

    private val loader: RetryingAdLoader<RewardedAd> =
        RetryingAdLoader(config, AdType.REWARDED) { adUnitId, onResult -> requestAd(adUnitId, onResult) }

    internal var scheduleRetry: (delayMs: Long, action: () -> Unit) -> Unit
        get() = loader.scheduleRetry
        set(value) { loader.scheduleRetry = value }
    internal var nowProvider: () -> Long = { System.currentTimeMillis() }

    private var cachedAd: RewardedAd? = null
    private var loadedAtMs: Long = 0L
    private var isLoading: Boolean = false

    override fun isReady(): Boolean =
        cachedAd != null && nowProvider() - loadedAtMs < config.expiryMs

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
        if (isReady()) {
            onResult(AdLoadResult.Success)
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

    internal open fun requestAd(adUnitId: String, onResult: (Result<RewardedAd>) -> Unit) {
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
        if (!isReady()) {
            // isReady() is the single source of truth for whether the cached ad is usable, whether
            // it never loaded or went stale past expiryMs; wasCached only distinguishes which
            // callback signal fits. Drop any stale ad rather than holding onto it until the next
            // successful load, and always kick off a fresh load(): it's a no-op if one is already in
            // flight, and otherwise this placement would silently report NOT_READY/expired forever,
            // since nothing else ever re-triggers a load once the cached ad expires unshown.
            val wasCached = cachedAd != null
            cachedAd = null
            if (wasCached) {
                AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.EXPIRED)
                callback.onAdExpired()
            } else {
                AdFlowCore.logger.log(placementId, AdType.REWARDED, AdFlowEvent.SHOW_BLOCKED, "not ready")
                callback.onShowBlocked(BlockReason.NOT_READY)
            }
            load()
            return
        }
        val ad = requireNotNull(cachedAd)
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
}
