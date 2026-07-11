package com.adflow.core.nativead

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View

data class NativeAdAssets(
    val headline: String,
    val body: String?,
    /** Icon đã decode sẵn (AdMob: `NativeAd.icon?.drawable`) - gán thẳng qua
     * `ImageView.setImageDrawable()`, không cần tự tải qua URI. */
    val icon: Drawable?,
    val callToAction: String?,
    val starRating: Double?,
    val advertiser: String?,
    /** Nhà máy tạo media view thật của mạng (AdMob: `MediaView`) - renderer đặt vào slot ảnh lớn. */
    val mediaViewSlot: (Context) -> View,
)
