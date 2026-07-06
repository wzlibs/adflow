package com.adflow.admob.fullscreen

import com.adflow.core.AdFlowError
import com.adflow.core.ShowCallback
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback

/**
 * Builds the [FullScreenContentCallback] every AdMob full-screen ad type wires onto its ad object
 * before calling `show()` - the SDK's callback shape is identical across ad types (Interstitial,
 * App Open), so only the concrete ad class differs, not this wiring. [AdMobRewardedAdManager]
 * doesn't use this: its `FullScreenContentCallback` does extra work on dismiss/fail (triggering a
 * preload, and logging [com.adflow.core.AdFlowEvent.SHOW_FAILED]) that this simple forwarder
 * doesn't need.
 */
internal fun fullScreenContentCallback(callback: ShowCallback): FullScreenContentCallback =
    object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() = callback.onAdShown()
        override fun onAdDismissedFullScreenContent() = callback.onAdDismissed()
        override fun onAdFailedToShowFullScreenContent(error: AdError) =
            callback.onAdFailedToShow(AdFlowError(error.code, error.message))
    }
