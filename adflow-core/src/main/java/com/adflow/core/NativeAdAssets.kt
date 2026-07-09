package com.adflow.core

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View

data class NativeAdAssets(
    val headline: String,
    val body: String?,
    /** Ảnh icon đã decode sẵn (vd `NativeAd.icon?.drawable` phía AdMob) - dùng trực tiếp qua
     * `ImageView.setImageDrawable()`, không cần tự tải ảnh qua URI/network. */
    val icon: Drawable?,
    val callToAction: String?,
    val starRating: Double?,
    val advertiser: String?,
    val mediaViewSlot: (Context) -> View,
)
