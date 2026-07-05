package com.adflow.core

import android.content.Context
import android.view.View

interface NativeAdRenderer {
    fun createView(context: Context): View
    fun bind(view: View, assets: NativeAdAssets)
}
