package com.adflow.admob.nativead

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.adflow.core.nativead.NativeAdAssets
import com.adflow.core.nativead.NativeAdRenderer
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Renderer mặc định "đầy đủ" - headline + media + body + CTA, bố cục dọc. Dùng khi có đủ chỗ
 * hiển thị (vd 1 card riêng trên màn hình), không phù hợp cho item danh sách chật hẹp (xem
 * [DefaultSmallNativeAdRenderer]).
 */
class DefaultMediumNativeAdRenderer : NativeAdRenderer {
    private fun Int.dp(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

    override fun onCreateView(context: Context, parent: ViewGroup): View {
        val nativeAdView = NativeAdView(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(context), 12.dp(context), 12.dp(context), 12.dp(context))
        }

        val headline = TextView(context).apply {
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val mediaContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 160.dp(context)).apply {
                topMargin = 8.dp(context)
                bottomMargin = 8.dp(context)
            }
        }
        val body = TextView(context).apply { textSize = 14f }
        val cta = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8.dp(context)
            }
        }

        root.addView(headline)
        root.addView(mediaContainer)
        root.addView(body)
        root.addView(cta)
        nativeAdView.addView(root)

        nativeAdView.headlineView = headline
        nativeAdView.bodyView = body
        nativeAdView.callToActionView = cta
        nativeAdView.tag = TagRefs(mediaContainer)
        return nativeAdView
    }

    override fun onBind(view: View, assets: NativeAdAssets) {
        val nativeAdView = view as NativeAdView
        val refs = nativeAdView.tag as TagRefs

        (nativeAdView.headlineView as TextView).text = assets.headline
        (nativeAdView.bodyView as TextView).apply {
            text = assets.body
            visibility = if (assets.body != null) View.VISIBLE else View.GONE
        }
        (nativeAdView.callToActionView as Button).apply {
            text = assets.callToAction
            visibility = if (assets.callToAction != null) View.VISIBLE else View.GONE
        }

        refs.mediaContainer.removeAllViews()
        val mediaView = assets.mediaViewSlot(view.context)
        refs.mediaContainer.addView(mediaView)
        if (mediaView is com.google.android.gms.ads.nativead.MediaView) nativeAdView.mediaView = mediaView
    }

    private class TagRefs(val mediaContainer: FrameLayout)
}
