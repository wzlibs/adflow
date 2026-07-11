package com.adflow.admob.fullscreen

import android.app.Activity
import android.content.Context
import com.adflow.admob.mapRevenue
import com.adflow.core.AdFlowError
import com.adflow.core.network.AdLoadException
import com.adflow.core.network.AdRequestInfo
import com.adflow.core.network.FullScreenAdSource
import com.adflow.core.network.LoadedFullScreenAd
import com.adflow.core.rewarded.RewardItem
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class AdMobRewardedSource(private val context: Context) : FullScreenAdSource {
    override suspend fun load(request: AdRequestInfo): LoadedFullScreenAd =
        suspendCancellableCoroutine { cont ->
            RewardedAd.load(
                context,
                request.adUnitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        ad.onPaidEventListener = OnPaidEventListener { value ->
                            request.onRevenue(mapRevenue(request, value, ad.responseInfo))
                        }
                        cont.resume(AdMobLoadedRewarded(ad))
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        cont.resumeWithException(AdLoadException(AdFlowError(error.code, error.message, error.code)))
                    }
                },
            )
        }
}

private class AdMobLoadedRewarded(private val ad: RewardedAd) : LoadedFullScreenAd {
    override fun show(activity: Activity, listener: LoadedFullScreenAd.ShowListener) {
        ad.fullScreenContentCallback = buildFullScreenContentCallback(listener)
        ad.show(activity) { rewardItem ->
            listener.onUserEarnedReward(RewardItem(rewardItem.amount, rewardItem.type))
        }
    }
}
