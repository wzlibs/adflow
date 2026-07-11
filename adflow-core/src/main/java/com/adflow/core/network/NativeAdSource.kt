package com.adflow.core.network

import android.content.Context
import android.view.View
import com.adflow.core.nativead.NativeAdAssets

interface NativeAdSource {
    suspend fun load(request: AdRequestInfo): LoadedNativeAd
}

interface LoadedNativeAd {
    val assets: NativeAdAssets

    /**
     * Bọc view mà renderer của app dựng + bind vào container đăng ký click/impression của mạng
     * (AdMob: `NativeAdView` + `setNativeAd`) - trả về view cuối cùng để gắn lên cây UI.
     */
    fun wrapBoundView(context: Context, renderedView: View): View

    fun destroy()
}
