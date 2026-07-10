package com.adflow.admob.fullscreen

import com.adflow.core.AdFlowError
import com.adflow.core.fullscreen.ShowCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback

/**
 * Xây dựng [FullScreenContentCallback] mà mọi loại full-screen ad của AdMob gắn vào ad object của
 * nó trước khi gọi `show()` - hình dạng callback của SDK giống nhau giữa các loại ad (Interstitial,
 * App Open), chỉ khác class ad cụ thể, không khác cách nối này. [AdMobRewardedAdManager] không
 * dùng hàm này: `FullScreenContentCallback` của nó làm thêm việc khi dismiss/fail (kích hoạt
 * preload, và log [com.adflow.core.AdFlowEvent.SHOW_FAILED]) mà bộ forwarder đơn giản này không cần.
 */
internal fun fullScreenContentCallback(callback: ShowCallback): FullScreenContentCallback =
    object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() = callback.onAdShown()
        override fun onAdDismissedFullScreenContent() = callback.onAdDismissed()
        override fun onAdFailedToShowFullScreenContent(error: AdError) =
            callback.onAdFailedToShow(AdFlowError(error.code, error.message))
    }
