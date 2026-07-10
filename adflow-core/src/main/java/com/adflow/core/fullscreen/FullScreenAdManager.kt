package com.adflow.core.fullscreen

import android.app.Activity
import com.adflow.core.AdLoadResult

interface FullScreenAdManager {
    fun isReady(): Boolean
    fun load(onResult: (AdLoadResult) -> Unit = {})
    fun show(activity: Activity, callback: ShowCallback = ShowCallback.NONE)
}
