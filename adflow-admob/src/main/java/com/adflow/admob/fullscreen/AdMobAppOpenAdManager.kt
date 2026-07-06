package com.adflow.admob.fullscreen

import android.app.Activity
import android.content.Context
import com.adflow.admob.precisionName
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.AdRevenueEvent
import com.adflow.core.AdType
import com.adflow.core.AppOpenAdManager
import com.adflow.core.FullScreenAdManagerBase
import com.adflow.core.PlacementConfig
import com.adflow.core.ShowCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.appopen.AppOpenAd

class AdMobAppOpenAdManager(
    private val context: Context,
    config: PlacementConfig,
) : FullScreenAdManagerBase<AppOpenAd>(config, AdType.APP_OPEN), AppOpenAdManager {

    private val placementId = config.placementId

    override fun requestAd(adUnitId: String, onResult: (Result<AppOpenAd>) -> Unit) {
        AppOpenAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    ad.onPaidEventListener = OnPaidEventListener { adValue ->
                        AdFlowCore.dispatchRevenue(
                            AdRevenueEvent(
                                placementId = placementId,
                                adType = AdType.APP_OPEN,
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

    override fun performShow(ad: AppOpenAd, activity: Activity, callback: ShowCallback) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = callback.onAdShown()
            override fun onAdDismissedFullScreenContent() = callback.onAdDismissed()
            override fun onAdFailedToShowFullScreenContent(error: AdError) =
                callback.onAdFailedToShow(AdFlowError(error.code, error.message))
        }
        ad.show(activity)
    }
}
