package com.adflow.core

import android.content.Context
import android.view.View

data class NativeAdAssets(
    val headline: String,
    val body: String?,
    val iconUri: String?,
    val callToAction: String?,
    val starRating: Double?,
    val advertiser: String?,
    val mediaViewSlot: (Context) -> View,
)
