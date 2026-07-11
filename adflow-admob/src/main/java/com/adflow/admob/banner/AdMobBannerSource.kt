package com.adflow.admob.banner

import android.content.Context
import android.view.View
import com.adflow.admob.mapRevenue
import com.adflow.core.AdFlowError
import com.adflow.core.banner.BannerSize
import com.adflow.core.network.AdLoadException
import com.adflow.core.network.AdRequestInfo
import com.adflow.core.network.BannerAdSource
import com.adflow.core.network.LoadedBannerAd
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class AdMobBannerSource(private val context: Context) : BannerAdSource {
    override suspend fun load(request: AdRequestInfo, size: BannerSize): LoadedBannerAd =
        suspendCancellableCoroutine { cont ->
            val view = AdView(context)
            view.setAdSize(mapBannerSize(context, size))
            view.adUnitId = request.adUnitId
            view.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    cont.resume(AdMobLoadedBanner(view))
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    cont.resumeWithException(AdLoadException(AdFlowError(error.code, error.message, error.code)))
                }
            }
            view.onPaidEventListener = OnPaidEventListener { value ->
                request.onRevenue(mapRevenue(request, value, view.responseInfo))
            }
            view.loadAd(AdRequest.Builder().build())
        }
}

internal fun mapBannerSize(context: Context, size: BannerSize): AdSize = when (size) {
    BannerSize.BANNER -> AdSize.BANNER
    BannerSize.LARGE_BANNER -> AdSize.LARGE_BANNER
    BannerSize.MEDIUM_RECTANGLE -> AdSize.MEDIUM_RECTANGLE
    BannerSize.ADAPTIVE -> {
        val metrics = context.resources.displayMetrics
        val adWidthPixels = metrics.widthPixels.toFloat()
        val adWidth = (adWidthPixels / metrics.density).toInt()
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
    }
}

private class AdMobLoadedBanner(private val adView: AdView) : LoadedBannerAd {
    override val view: View = adView
    override fun destroy() = adView.destroy()
}
