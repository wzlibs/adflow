package com.adflow.core

import android.content.Context
import android.view.View

interface BannerAdManager {
    fun isReady(): Boolean
    fun load(onResult: (AdLoadResult) -> Unit = {})
    fun getView(context: Context): View
}
