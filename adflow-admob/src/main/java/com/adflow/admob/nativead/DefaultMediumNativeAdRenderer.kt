package com.adflow.admob.nativead

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.adflow.core.nativead.NativeAdAssets
import com.adflow.core.nativead.NativeAdRenderer
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAdView

class DefaultMediumNativeAdRenderer : NativeAdRenderer {

    override fun createView(context: Context): View {
        val headline = TextView(context).apply { id = View.generateViewId() }
        val body = TextView(context).apply { id = View.generateViewId() }
        val media = MediaView(context).apply { id = View.generateViewId() }
        val cta = Button(context).apply { id = View.generateViewId() }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            addView(headline)
            addView(media)
            addView(body)
            addView(cta)
        }
        return NativeAdView(context).apply {
            addView(container)
            headlineView = headline
            bodyView = body
            mediaView = media
            callToActionView = cta
        }
    }

    override fun bind(view: View, assets: NativeAdAssets) {
        val adView = view as NativeAdView
        (adView.headlineView as TextView).text = assets.headline
        (adView.bodyView as TextView).text = assets.body.orEmpty()
        (adView.callToActionView as Button).text = assets.callToAction.orEmpty()
    }
}
