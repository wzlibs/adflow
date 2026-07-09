package com.adflow.admob.nativead

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.adflow.core.NativeAdAssets
import com.adflow.core.NativeAdRenderer
import com.google.android.gms.ads.nativead.NativeAdView

private const val ICON_SIZE_DP = 40

class DefaultSmallNativeAdRenderer : NativeAdRenderer {

    override fun createView(context: Context): View {
        val iconSizePx = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
        val icon = ImageView(context).apply { id = View.generateViewId() }
        val headline = TextView(context).apply { id = View.generateViewId() }
        val body = TextView(context).apply { id = View.generateViewId() }
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            addView(headline)
            addView(body)
        }
        // textColumn cần layout_weight=1 (width 0dp) - nếu không, icon có kích thước cố định vẫn
        // hiện được, nhưng textColumn sẽ tràn hết chiều rộng còn lại một cách không kiểm soát thay
        // vì được co giãn đúng theo phần còn lại sau icon.
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(icon, LinearLayout.LayoutParams(iconSizePx, iconSizePx))
            addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        return NativeAdView(context).apply {
            addView(container, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            headlineView = headline
            bodyView = body
            iconView = icon
        }
    }

    override fun bind(view: View, assets: NativeAdAssets) {
        val adView = view as NativeAdView
        (adView.headlineView as TextView).text = assets.headline
        (adView.bodyView as TextView).text = assets.body.orEmpty()
        val iconView = adView.iconView as ImageView
        // Không phải ad nào cũng có icon - ẩn hẳn ImageView thay vì để trống/vỡ layout khi null.
        if (assets.icon != null) {
            iconView.setImageDrawable(assets.icon)
            iconView.visibility = View.VISIBLE
        } else {
            iconView.visibility = View.GONE
        }
    }
}
