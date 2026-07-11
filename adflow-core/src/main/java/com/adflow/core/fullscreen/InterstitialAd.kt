package com.adflow.core.fullscreen

import android.app.Activity

interface InterstitialAd : FullScreenAd {
    fun show(activity: Activity, callback: FullScreenCallback = FullScreenCallback.EMPTY)
}
