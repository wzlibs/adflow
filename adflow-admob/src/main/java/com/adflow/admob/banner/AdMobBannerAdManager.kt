package com.adflow.admob.banner

import android.content.Context
import android.view.View
import com.adflow.admob.dispatchRevenue
import com.adflow.core.AdType
import com.adflow.core.BannerAdManager
import com.adflow.core.PlacementConfig
import com.adflow.core.SimpleCachedAdLoaderBase
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener

open class AdMobBannerAdManager(
    private val context: Context,
    config: PlacementConfig,
) : SimpleCachedAdLoaderBase<AdView>(config, AdType.BANNER), BannerAdManager {

    private val placementId = config.placementId

    override fun requestAd(adUnitId: String, onResult: (Result<AdView>) -> Unit) {
        val view = AdView(context)
        view.setAdSize(AdSize.BANNER)
        view.adUnitId = adUnitId
        view.adListener = object : AdListener() {
            override fun onAdLoaded() {
                onResult(Result.success(view))
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                onResult(Result.failure(RuntimeException(error.message)))
            }
        }
        view.onPaidEventListener = OnPaidEventListener { adValue ->
            dispatchRevenue(placementId, AdType.BANNER, adUnitId, adValue, view.responseInfo)
        }
        view.loadAd(AdRequest.Builder().build())
    }

    override fun getView(context: Context): View {
        val view = cachedAd ?: throw IllegalStateException("Banner for '${config.placementId}' has not loaded yet")
        requireShowAllowed()
        return view
    }
}
