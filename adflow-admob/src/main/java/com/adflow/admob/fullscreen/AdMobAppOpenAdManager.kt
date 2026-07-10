package com.adflow.admob.fullscreen

import android.app.Activity
import android.content.Context
import com.adflow.admob.dispatchRevenue
import com.adflow.core.AdType
import com.adflow.core.fullscreen.AppOpenAdManager
import com.adflow.core.fullscreen.FullScreenAdManagerBase
import com.adflow.core.config.PlacementConfig
import com.adflow.core.fullscreen.ShowCallback
import com.google.android.gms.ads.AdRequest
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
                        dispatchRevenue(placementId, AdType.APP_OPEN, adUnitId, adValue, ad.responseInfo)
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
        ad.fullScreenContentCallback = fullScreenContentCallback(callback)
        ad.show(activity)
    }
}
