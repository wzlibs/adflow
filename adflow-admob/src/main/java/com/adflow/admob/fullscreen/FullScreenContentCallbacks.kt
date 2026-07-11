package com.adflow.admob.fullscreen

import com.adflow.core.AdFlowError
import com.adflow.core.network.LoadedFullScreenAd
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback

/**
 * Xây [FullScreenContentCallback] mà mọi loại full-screen ad của AdMob gắn vào ad object trước
 * khi `show()` - hình dạng callback của SDK giống nhau giữa Interstitial/App Open/Rewarded, chỉ
 * khác class ad cụ thể, không khác cách nối này.
 */
internal fun buildFullScreenContentCallback(listener: LoadedFullScreenAd.ShowListener): FullScreenContentCallback =
    object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() = listener.onShowed()
        override fun onAdDismissedFullScreenContent() = listener.onDismissed()
        override fun onAdFailedToShowFullScreenContent(error: AdError) =
            listener.onFailedToShow(AdFlowError(error.code, error.message, error.code))
        override fun onAdImpression() = listener.onImpression()
        override fun onAdClicked() = listener.onClicked()
    }
