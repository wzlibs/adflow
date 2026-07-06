package com.adflow.admob.nativead

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.adflow.core.NativeAdAssets
import com.adflow.core.NativeAdRenderer
import com.google.android.gms.ads.nativead.NativeAdView

class DefaultSmallNativeAdRenderer : NativeAdRenderer {

    override fun createView(context: Context): View {
        val headline = TextView(context).apply { id = View.generateViewId() }
        val body = TextView(context).apply { id = View.generateViewId() }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            addView(headline)
            addView(body)
        }
        return NativeAdView(context).apply {
            addView(container)
            headlineView = headline
            bodyView = body
        }
    }

    override fun bind(view: View, assets: NativeAdAssets) {
        val adView = view as NativeAdView
        (adView.headlineView as TextView).text = assets.headline
        (adView.bodyView as TextView).text = assets.body.orEmpty()
    }
}
