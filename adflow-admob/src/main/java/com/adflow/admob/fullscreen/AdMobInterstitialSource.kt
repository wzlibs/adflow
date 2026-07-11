package com.adflow.admob.fullscreen

import android.app.Activity
import android.content.Context
import com.adflow.admob.mapRevenue
import com.adflow.core.AdFlowError
import com.adflow.core.network.AdLoadException
import com.adflow.core.network.AdRequestInfo
import com.adflow.core.network.FullScreenAdSource
import com.adflow.core.network.LoadedFullScreenAd
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class AdMobInterstitialSource(private val context: Context) : FullScreenAdSource {
    override suspend fun load(request: AdRequestInfo): LoadedFullScreenAd =
        suspendCancellableCoroutine { cont ->
            InterstitialAd.load(
                context,
                request.adUnitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        ad.onPaidEventListener = OnPaidEventListener { value ->
                            request.onRevenue(mapRevenue(request, value, ad.responseInfo))
                        }
                        cont.resume(AdMobLoadedInterstitial(ad))
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        cont.resumeWithException(AdLoadException(AdFlowError(error.code, error.message, error.code)))
                    }
                },
            )
        }
}

private class AdMobLoadedInterstitial(private val ad: InterstitialAd) : LoadedFullScreenAd {
    override fun show(activity: Activity, listener: LoadedFullScreenAd.ShowListener) {
        ad.fullScreenContentCallback = buildFullScreenContentCallback(listener)
        ad.show(activity)
    }
}
