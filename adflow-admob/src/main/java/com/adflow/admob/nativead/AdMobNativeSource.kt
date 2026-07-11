package com.adflow.admob.nativead

import android.content.Context
import android.view.View
import com.adflow.admob.mapRevenue
import com.adflow.core.AdFlowError
import com.adflow.core.nativead.NativeAdAssets
import com.adflow.core.network.AdLoadException
import com.adflow.core.network.AdRequestInfo
import com.adflow.core.network.LoadedNativeAd
import com.adflow.core.network.NativeAdSource
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class AdMobNativeSource(private val context: Context) : NativeAdSource {
    override suspend fun load(request: AdRequestInfo): LoadedNativeAd =
        suspendCancellableCoroutine { cont ->
            val adLoader = AdLoader.Builder(context, request.adUnitId)
                .forNativeAd { nativeAd ->
                    nativeAd.setOnPaidEventListener { value ->
                        request.onRevenue(mapRevenue(request, value, nativeAd.responseInfo))
                    }
                    cont.resume(AdMobLoadedNativeAd(nativeAd))
                }
                .withAdListener(
                    object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            cont.resumeWithException(AdLoadException(AdFlowError(error.code, error.message, error.code)))
                        }
                    },
                )
                .build()
            adLoader.loadAd(AdRequest.Builder().build())
        }
}

private class AdMobLoadedNativeAd(private val nativeAd: NativeAd) : LoadedNativeAd {
    override val assets = NativeAdAssets(
        headline = nativeAd.headline.orEmpty(),
        body = nativeAd.body,
        icon = nativeAd.icon?.drawable,
        callToAction = nativeAd.callToAction,
        starRating = nativeAd.starRating,
        advertiser = nativeAd.advertiser,
        mediaViewSlot = { ctx -> MediaView(ctx) },
    )

    override fun wrapBoundView(context: Context, renderedView: View): View {
        if (renderedView is NativeAdView) renderedView.setNativeAd(nativeAd)
        return renderedView
    }

    override fun destroy() = nativeAd.destroy()
}
