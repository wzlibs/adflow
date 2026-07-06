package com.adflow.core

import android.app.Activity

interface RewardedAdManager {
    fun isReady(): Boolean
    fun load(onResult: (AdLoadResult) -> Unit = {})
    fun show(activity: Activity, callback: RewardedAdCallback = RewardedAdCallback.NONE)
}
