package com.adflow.core.banner

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
import com.adflow.core.network.LoadedBannerAd

/**
 * View tự quản lý toàn bộ vòng đời hiển thị của 1 banner placement - khai báo `placementId` (qua
 * XML attr `app:adflowPlacementId` hoặc gán thuộc tính [placementId] bằng code) rồi đặt vào cây
 * UI như 1 `View` bình thường, KHÔNG cần tự gọi `load()`, không cần tự poll `isReady()`/state:
 * view tự resolve controller lúc attach, tự lease ad khi có, tự collapse ([autoCollapse]) khi
 * chưa có/bị chặn, và tự thay ad mới vào khi state phát [com.adflow.core.AdState.Loaded] - kể cả
 * ad đến muộn sau khi view đã attach.
 */
class AdFlowBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var placementId: String? = null
    var adListener: AdListener? = null

    /** true (mặc định): tự set `visibility = GONE` (không chiếm layout) khi chưa có ad để hiển
     * thị, tự set lại `VISIBLE` khi có. false: app tự quản lý visibility, view chỉ thay nội dung. */
    var autoCollapse: Boolean = true

    private var controller: BannerAdControllerImpl? = null
    private var leasedAd: LoadedBannerAd? = null

    private val listener = object : AdListener {
        override fun onAdLoading() {
            adListener?.onAdLoading()
        }

        override fun onAdLoaded() {
            attachLeasedAd()
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

    /** Ép tải lại từ đầu (detach khỏi controller hiện tại, gọi lại attach) - hiếm khi cần, chủ
     * yếu để app tự chủ động thử lại sau khi đổi [placementId] bằng code. */
    fun reload() {
        stop()
        start()
    }

    private fun start() {
        val id = placementId
        if (id == null) {
            Log.w(TAG, "AdFlowBannerView chưa set placementId - không có gì để hiển thị")
            return
        }
        val resolved = AdFlow.bannerControllerImpl(id)
        controller = resolved
        // load() TRƯỚC addListener(): lease() (gọi trong attachLeasedAd() khi addListener() replay
        // Loaded) tiêu thụ cachedAd, nên nếu addListener() chạy trước, load() gọi sau đó sẽ thấy
        // isReady() = false (cache vừa bị lease rỗng) và tự mở thêm 1 vòng load thừa - vòng thừa
        // đó phát Loaded lần 2, kích lease() lần 2 trong lúc ad đầu vẫn đang leased, trả về null
        // và làm sập (collapse) view vừa hiển thị thành công. Gọi load() trước loại bỏ hẳn cạnh
        // tranh này: nếu đã Loaded từ trước, load() no-op; addListener() replay đúng 1 lần.
        resolved.load()
        resolved.addListener(listener)
    }

    private fun stop() {
        controller?.removeListener(listener)
        leasedAd?.let { controller?.release(it) }
        leasedAd = null
        controller = null
        removeAllViews()
    }

    private fun attachLeasedAd() {
        val c = controller ?: return
        if (!c.isShowAllowed()) {
            adListener?.onAdBlocked(BlockReason.RULE_REJECTED)
            collapse()
            return
        }
        val ad = c.lease()
        if (ad == null) {
            collapse()
            return
        }
        leasedAd = ad
        removeAllViews()
        addView(ad.view)
        if (autoCollapse) visibility = View.VISIBLE
    }

    private fun collapse() {
        val c = controller
        leasedAd?.let { c?.release(it) }
        leasedAd = null
        removeAllViews()
        if (autoCollapse) visibility = View.GONE
    }

    companion object {
        private const val TAG = "AdFlowBannerView"
    }
}
