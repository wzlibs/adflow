package com.adflow.core.nativead

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.adflow.core.AdFlow
import com.adflow.core.AdFlowError
import com.adflow.core.AdListener
import com.adflow.core.BlockReason
import com.adflow.core.R

/**
 * View tự quản lý toàn bộ vòng đời hiển thị của 1 native placement - cùng triết lý với
 * [com.adflow.core.banner.AdFlowBannerView]: khai báo `placementId`, đặt vào cây UI, không cần tự
 * `load()`/poll. Khác Banner ở chỗ ad có thể được nhiều view cùng bind (không độc quyền), và
 * [reload] có thể đổi sang ad khác trong lúc view vẫn đang hiển thị - view tự rebind khi state
 * phát [com.adflow.core.AdState.Loaded] mới, không cần app ép tạo lại view.
 */
class AdFlowNativeAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var placementId: String? = null

    /** null (mặc định) = dùng renderer mặc định khai báo cho placement trong DSL
     * (`native(id) { renderer = ... }`). */
    var renderer: NativeAdRenderer? = null
    var adListener: AdListener? = null
    var autoCollapse: Boolean = true

    private var controller: NativeAdControllerImpl? = null
    private var boundAd: Boolean = false

    private val listener = object : AdListener {
        override fun onAdLoading() {
            adListener?.onAdLoading()
        }

        override fun onAdLoaded() {
            rebind()
            adListener?.onAdLoaded()
        }

        override fun onAdFailedToLoad(error: AdFlowError, willRetry: Boolean) {
            collapse()
            adListener?.onAdFailedToLoad(error, willRetry)
        }

        override fun onAdBlocked(reason: BlockReason) {
            collapse()
            adListener?.onAdBlocked(reason)
        }
    }

    init {
        if (autoCollapse) visibility = View.GONE
        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.AdFlowAdView)
            placementId = a.getString(R.styleable.AdFlowAdView_adflowPlacementId)
            a.recycle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    /** Ép fetch 1 ad mới dù ad đang cache vẫn còn hạn - xem [NativeAdController.reload]. View tự
     * rebind khi ad mới load xong, không cần gọi gì thêm sau lệnh này. */
    fun reload() {
        controller?.reload()
    }

    private fun start() {
        val id = placementId
        if (id == null) {
            Log.w(TAG, "AdFlowNativeAdView chưa set placementId - không có gì để hiển thị")
            return
        }
        val resolved = AdFlow.nativeControllerImpl(id)
        controller = resolved
        // load() TRƯỚC addListener() - cùng lý do với AdFlowBannerView.start(): tránh 1 vòng load
        // thừa chạy sau khi replay đã kích hoạt rebind(). bind() không tiêu thụ cache (peek()) như
        // lease() của Banner nên bug cụ thể đó không xảy ra ở Native, nhưng giữ thứ tự nhất quán.
        resolved.load()
        resolved.addListener(listener) // replay ngay onAdLoaded() nếu ad đã sẵn sàng từ trước
    }

    private fun stop() {
        controller?.removeListener(listener)
        if (boundAd) controller?.unbind()
        boundAd = false
        controller = null
        removeAllViews()
    }

    private fun rebind() {
        val c = controller ?: return
        if (!c.isShowAllowed()) {
            adListener?.onAdBlocked(BlockReason.RULE_REJECTED)
            collapse()
            return
        }
        if (boundAd) c.unbind() // nhả bind cũ trước khi bind ad mới - giữ đúng 1 lần bind/view
        val ad = c.bind()
        if (ad == null) {
            boundAd = false
            collapse()
            return
        }
        boundAd = true

        val effectiveRenderer = renderer ?: c.defaultRenderer
        if (effectiveRenderer == null) {
            Log.w(TAG, "Không có NativeAdRenderer nào cho placement '$placementId' - set renderer trên view hoặc trong DSL native(id) { renderer = ... }")
            collapse()
            return
        }

        removeAllViews()
        val rendered = effectiveRenderer.onCreateView(context, this)
        effectiveRenderer.onBind(rendered, ad.assets)
        addView(ad.wrapBoundView(context, rendered))
        if (autoCollapse) visibility = View.VISIBLE
    }

    private fun collapse() {
        if (boundAd) controller?.unbind()
        boundAd = false
        removeAllViews()
        if (autoCollapse) visibility = View.GONE
    }

    companion object {
        private const val TAG = "AdFlowNativeAdView"
    }
}
