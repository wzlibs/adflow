package com.adflow.admob.nativead

import android.content.Context
import android.view.View
import com.adflow.admob.precisionName
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.AdLoadResult
import com.adflow.core.AdRevenueEvent
import com.adflow.core.AdType
import com.adflow.core.NativeAdAssets
import com.adflow.core.NativeAdManager
import com.adflow.core.NativeAdRenderer
import com.adflow.core.PlacementConfig
import com.adflow.core.RetryingAdLoader
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

open class AdMobNativeAdManager(
    private val context: Context,
    private val config: PlacementConfig,
) : NativeAdManager {

    private var cachedAd: NativeAd? = null
    private var isLoading: Boolean = false

    private val loader: RetryingAdLoader<NativeAd> =
        RetryingAdLoader(config, AdType.NATIVE) { adUnitId, onResult -> requestAd(adUnitId, onResult) }

    internal var scheduleRetry: (delayMs: Long, action: () -> Unit) -> Unit
        get() = loader.scheduleRetry
        set(value) { loader.scheduleRetry = value }

    // Native ads are never subject to expiry (unlike full-screen/rewarded ads): readiness is a
    // plain non-null check, per the AdFlow design constraint that only full-screen ad types stale.
    override fun isReady(): Boolean = cachedAd != null

    override fun load(onResult: (AdLoadResult) -> Unit) {
        if (!config.enabled) {
            onResult(AdLoadResult.Failure(AdFlowError(-1, "placement disabled")))
            return
        }
        if (config.loadRule?.isAllowed(config.placementId) == false) {
            onResult(AdLoadResult.Failure(AdFlowError(-2, "load rule rejected")))
            return
        }
        if (isLoading) return
        isLoading = true
        loader.start { result, ad ->
            if (result is AdLoadResult.Success && ad != null) {
                cachedAd = ad
            }
            isLoading = false
            onResult(result)
        }
    }

    internal open fun requestAd(adUnitId: String, onResult: (Result<NativeAd>) -> Unit) {
        val loader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                nativeAd.setOnPaidEventListener { adValue ->
                    AdFlowCore.dispatchRevenue(
                        AdRevenueEvent(
                            placementId = config.placementId,
                            adType = AdType.NATIVE,
                            adUnitId = adUnitId,
                            valueMicros = adValue.valueMicros,
                            currencyCode = adValue.currencyCode,
                            precision = precisionName(adValue.precisionType),
                            adNetwork = nativeAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName,
                        ),
                    )
                }
                onResult(Result.success(nativeAd))
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    onResult(Result.failure(RuntimeException(error.message)))
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
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
