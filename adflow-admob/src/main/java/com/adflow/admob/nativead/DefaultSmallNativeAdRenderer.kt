package com.adflow.admob.nativead

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.adflow.core.nativead.NativeAdAssets
import com.adflow.core.nativead.NativeAdRenderer
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Renderer mặc định gọn - headline + icon + body, bố cục ngang, không có [MediaView] - dùng khi
 * không đủ chỗ cho ảnh media lớn (vd item trong danh sách).
 */
class DefaultSmallNativeAdRenderer : NativeAdRenderer {
    private fun Int.dp(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

    override fun onCreateView(context: Context, parent: ViewGroup): View {
        val nativeAdView = NativeAdView(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8.dp(context), 8.dp(context), 8.dp(context), 8.dp(context))
        }

        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(40.dp(context), 40.dp(context)).apply {
                marginEnd = 8.dp(context)
            }
        }
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val headline = TextView(context).apply {
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val body = TextView(context).apply { textSize = 12f }

        textColumn.addView(headline)
        textColumn.addView(body)
        root.addView(icon)
        root.addView(textColumn)
        nativeAdView.addView(root)

        nativeAdView.headlineView = headline
        nativeAdView.bodyView = body
        nativeAdView.iconView = icon
        return nativeAdView
    }

    override fun onBind(view: View, assets: NativeAdAssets) {
        val nativeAdView = view as NativeAdView

        (nativeAdView.headlineView as TextView).text = assets.headline
        (nativeAdView.bodyView as TextView).apply {
            text = assets.body
            visibility = if (assets.body != null) View.VISIBLE else View.GONE
        }
        (nativeAdView.iconView as ImageView).apply {
            setImageDrawable(assets.icon)
            visibility = if (assets.icon != null) View.VISIBLE else View.GONE
        }
    }
}
