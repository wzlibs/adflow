package com.adflow.admob.nativead

import android.content.Context
import android.view.View
import com.adflow.admob.dispatchRevenue
import com.adflow.core.AdType
import com.adflow.core.NativeAdAssets
import com.adflow.core.NativeAdManager
import com.adflow.core.NativeAdRenderer
import com.adflow.core.PlacementConfig
import com.adflow.core.SimpleCachedAdLoaderBase
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

open class AdMobNativeAdManager(
    private val context: Context,
    config: PlacementConfig,
) : SimpleCachedAdLoaderBase<NativeAd>(config, AdType.NATIVE), NativeAdManager {

    private val placementId = config.placementId

    override fun requestAd(adUnitId: String, onResult: (Result<NativeAd>) -> Unit) {
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                nativeAd.setOnPaidEventListener { adValue ->
                    dispatchRevenue(placementId, AdType.NATIVE, adUnitId, adValue, nativeAd.responseInfo)
                }
                onResult(Result.success(nativeAd))
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    onResult(Result.failure(RuntimeException(error.message)))
                }
            })
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    override fun createView(context: Context, renderer: NativeAdRenderer): View {
        val ad = cachedAd ?: throw IllegalStateException("Native ad for '${config.placementId}' has not loaded yet")
        val view = renderer.createView(context)
        val assets = NativeAdAssets(
            headline = ad.headline.orEmpty(),
            body = ad.body,
            iconUri = ad.icon?.uri?.toString(),
            callToAction = ad.callToAction,
            starRating = ad.starRating,
            advertiser = ad.advertiser,
            mediaViewSlot = { ctx -> MediaView(ctx) },
        )
        renderer.bind(view, assets)
        if (view is NativeAdView) {
            view.setNativeAd(ad)
        }
        return view
    }
}
