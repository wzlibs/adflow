package com.adflow.core.nativead

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** App viết renderer riêng để có layout native ad tùy biến hoàn toàn - implement 2 hàm này rồi
 * gán vào DSL (`renderer = ...`) hoặc `AdFlowNativeAdView.renderer`. */
interface NativeAdRenderer {
    fun onCreateView(context: Context, parent: ViewGroup): View

    fun onBind(view: View, assets: NativeAdAssets)
}
