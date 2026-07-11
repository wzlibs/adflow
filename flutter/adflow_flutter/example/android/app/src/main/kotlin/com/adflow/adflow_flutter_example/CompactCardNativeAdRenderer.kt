package com.adflow.adflow_flutter_example

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.adflow.core.nativead.NativeAdAssets
import com.adflow.core.nativead.NativeAdRenderer
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Ví dụ 1 [NativeAdRenderer] app tự viết - layout **ngang** (headline+body xếp dọc bên trái, CTA
 * bên phải), cố tình khác hẳn cấu trúc `DefaultMediumNativeAdRenderer` (dọc:
 * headline→media→body→cta) để chứng minh đây là renderer hoàn toàn khác, không phải style lại
 * renderer mặc định. Đăng ký qua `AdflowFlutterPlugin.registerNativeAdRenderer()` trong
 * [MainActivity], chọn từ Dart qua `AdFlowNative(rendererId: 'compactCard')`.
 */
class CompactCardNativeAdRenderer : NativeAdRenderer {

    override fun onCreateView(context: Context, parent: ViewGroup): View {
        val headline = TextView(context).apply { id = View.generateViewId() }
        val body = TextView(context).apply { id = View.generateViewId() }
        val cta = Button(context).apply { id = View.generateViewId() }
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(headline)
            addView(body)
        }
        // textColumn cần layout_weight=1 (width 0dp) - nếu không, 1 TextView WRAP_CONTENT sẽ
        // chiếm hết chiều rộng còn lại của row trước khi đến lượt đo cta, đẩy cta co về 0 width
        // (nút biến mất dù vẫn nằm đúng trong cây view).
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(cta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return NativeAdView(context).apply {
            addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            headlineView = headline
            bodyView = body
            callToActionView = cta
        }
    }

    override fun onBind(view: View, assets: NativeAdAssets) {
        val adView = view as NativeAdView
        (adView.headlineView as TextView).text = assets.headline
        (adView.bodyView as TextView).text = assets.body.orEmpty()
        (adView.callToActionView as Button).text = assets.callToAction.orEmpty()
    }
}
