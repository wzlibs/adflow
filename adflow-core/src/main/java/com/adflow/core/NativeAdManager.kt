package com.adflow.core

import android.content.Context
import android.view.View

interface NativeAdManager {
    fun isReady(): Boolean
    fun load(onResult: (AdLoadResult) -> Unit = {})
    fun createView(context: Context, renderer: NativeAdRenderer): View
}
