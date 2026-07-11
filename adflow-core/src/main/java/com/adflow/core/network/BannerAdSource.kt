package com.adflow.core.network

import android.view.View
import com.adflow.core.banner.BannerSize

interface BannerAdSource {
    suspend fun load(request: AdRequestInfo, size: BannerSize): LoadedBannerAd
}

interface LoadedBannerAd {
    val view: View
    fun destroy()
}
