package com.adflow.admob.banner

import android.content.Context
import android.view.View
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.AdLoadResult
import com.adflow.core.AdRevenueEvent
import com.adflow.core.AdType
import com.adflow.core.BannerAdManager
import com.adflow.core.PlacementConfig
import com.adflow.core.WaterfallLoader
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener

class AdMobBannerAdManager(
    private val context: Context,
    private val config: PlacementConfig,
) : BannerAdManager {

    private var adView: AdView? = null

    override fun isReady(): Boolean = adView != null

    override fun load(onResult: (AdLoadResult) -> Unit) {
        if (!config.enabled) {
            onResult(AdLoadResult.Failure(AdFlowError(-1, "placement disabled")))
            return
        }
        if (config.loadRule?.isAllowed(config.placementId) == false) {
            onResult(AdLoadResult.Failure(AdFlowError(-2, "load rule rejected")))
            return
        }
        WaterfallLoader<AdView>(config.adUnitIds) { adUnitId, cb -> requestAd(adUnitId, cb) }.start { result ->
            result.fold(
                onSuccess = {
                    adView = it
                    onResult(AdLoadResult.Success)
                },
                onFailure = { error ->
                    onResult(AdLoadResult.Failure(AdFlowError(-3, error.message ?: "no fill")))
                },
            )
        }
    }

    private fun requestAd(adUnitId: String, onResult: (Result<AdView>) -> Unit) {
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
            AdFlowCore.dispatchRevenue(
                AdRevenueEvent(
                    placementId = config.placementId,
                    adType = AdType.BANNER,
                    adUnitId = adUnitId,
                    valueMicros = adValue.valueMicros,
                    currencyCode = adValue.currencyCode,
                    precision = precisionName(adValue.precisionType),
                    adNetwork = view.responseInfo?.loadedAdapterResponseInfo?.adSourceName,
                ),
            )
        }
        view.loadAd(AdRequest.Builder().build())
    }

    override fun getView(context: Context): View =
        adView ?: throw IllegalStateException("Banner for '${config.placementId}' has not loaded yet")

    private fun precisionName(@AdValue.PrecisionType precisionType: Int): String = when (precisionType) {
        AdValue.PrecisionType.PRECISE -> "PRECISE"
        AdValue.PrecisionType.ESTIMATED -> "ESTIMATED"
        AdValue.PrecisionType.PUBLISHER_PROVIDED -> "PUBLISHER_PROVIDED"
        AdValue.PrecisionType.UNKNOWN -> "UNKNOWN"
        else -> "UNKNOWN"
    }
}
