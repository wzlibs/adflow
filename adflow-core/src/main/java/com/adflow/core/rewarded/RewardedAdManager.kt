package com.adflow.core.rewarded

import android.app.Activity
import com.adflow.core.AdLoadResult

interface RewardedAdManager {
    fun isReady(): Boolean
    fun load(onResult: (AdLoadResult) -> Unit = {})
    fun show(activity: Activity, callback: RewardedAdCallback = RewardedAdCallback.NONE)
}
