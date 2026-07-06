package com.adflow.core

import android.app.Activity

interface FullScreenAdManager {
    fun isReady(): Boolean
    fun load(onResult: (AdLoadResult) -> Unit = {})
    fun show(activity: Activity, callback: ShowCallback = ShowCallback.NONE)
}
