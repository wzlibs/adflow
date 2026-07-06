package com.adflow.admob.fullscreen

import android.app.Activity
import android.content.Context
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.AdRevenueEvent
import com.adflow.core.AdType
import com.adflow.core.FullScreenAdManagerBase
import com.adflow.core.InterstitialAdManager
import com.adflow.core.PlacementConfig
import com.adflow.core.ShowCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class AdMobInterstitialAdManager(
    private val context: Context,
    config: PlacementConfig,
) : FullScreenAdManagerBase<InterstitialAd>(config, AdType.INTERSTITIAL), InterstitialAdManager {

    private val placementId = config.placementId

    override fun requestAd(adUnitId: String, onResult: (Result<InterstitialAd>) -> Unit) {
        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    ad.onPaidEventListener = OnPaidEventListener { adValue ->
                        AdFlowCore.dispatchRevenue(
                            AdRevenueEvent(
                                placementId = placementId,
                                adType = AdType.INTERSTITIAL,
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

    override fun performShow(ad: InterstitialAd, activity: Activity, callback: ShowCallback) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = callback.onAdShown()
            override fun onAdDismissedFullScreenContent() = callback.onAdDismissed()
            override fun onAdFailedToShowFullScreenContent(error: AdError) =
                callback.onAdFailedToShow(AdFlowError(error.code, error.message))
        }
        ad.show(activity)
    }

    private fun precisionName(@AdValue.PrecisionType precisionType: Int): String = when (precisionType) {
        AdValue.PrecisionType.PRECISE -> "PRECISE"
        AdValue.PrecisionType.ESTIMATED -> "ESTIMATED"
        AdValue.PrecisionType.PUBLISHER_PROVIDED -> "PUBLISHER_PROVIDED"
        AdValue.PrecisionType.UNKNOWN -> "UNKNOWN"
        else -> "UNKNOWN"
    }
}
