package com.adflow.admob.fullscreen

import android.app.Activity
import android.content.Context
import com.adflow.admob.dispatchRevenue
import com.adflow.core.AdType
import com.adflow.core.fullscreen.FullScreenAdManagerBase
import com.adflow.core.fullscreen.InterstitialAdManager
import com.adflow.core.config.PlacementConfig
import com.adflow.core.fullscreen.ShowCallback
import com.google.android.gms.ads.AdRequest
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
                        dispatchRevenue(placementId, AdType.INTERSTITIAL, adUnitId, adValue, ad.responseInfo)
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
        ad.fullScreenContentCallback = fullScreenContentCallback(callback)
        ad.show(activity)
    }
}
