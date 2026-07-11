package com.adflow.core.nativead

import com.adflow.core.AdListener
import com.adflow.core.AdState
import com.adflow.core.config.PlacementConfig
import com.adflow.core.engine.AdFlowRuntime
import com.adflow.core.engine.AdLoadEngine
import com.adflow.core.network.AdRequestInfo
import com.adflow.core.network.LoadedNativeAd
import com.adflow.core.network.NativeAdSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * `bind()`/`unbind()` (internal, KHÔNG thuộc [NativeAdController] public) là cơ chế
 * [com.adflow.core.nativead.AdFlowNativeAdView] dùng để đọc ad hiện tại - khác Banner, 1 native ad
 * có thể bind vào NHIỀU view cùng lúc (không phải view thật của mạng, chỉ là dữ liệu +
 * click/impression tracking), nên [bind] đọc không tiêu thụ ([AdLoadEngine.peek]) và đếm số view
 * đang bind ([bindCount]) thay vì lease độc quyền như Banner.
 *
 * Hợp đồng cho view gọi: mỗi lần ad hiển thị đổi (kể cả do [reload] thành công), gọi [unbind] cho
 * ad cũ RỒI [bind] lại để lấy ad mới - giữ đúng 1 lần bind cho mỗi view đang sống tại mọi thời
 * điểm; chỉ gọi [unbind] không kèm [bind] khi view thực sự bị hủy (detach).
 */
internal class NativeAdControllerImpl(
    override val placementId: String,
    private val config: PlacementConfig,
    source: NativeAdSource,
    runtime: AdFlowRuntime,
    scope: CoroutineScope,
) : NativeAdController {

    private var bindCount = 0
    private var pendingDestroy: LoadedNativeAd? = null

    private val engine = AdLoadEngine<LoadedNativeAd>(
        config = config,
        loadOne = { adUnitId ->
            source.load(AdRequestInfo(placementId, config.adType, adUnitId, onRevenue = runtime::dispatchRevenue))
        },
        onDrop = { old ->
            // Nếu đang có view bind vào ad này, hoãn destroy tới khi unbind hết - tránh hủy 1 ad
            // đang thực sự hiển thị/đang xử lý click dở trên màn hình.
            if (bindCount > 0) pendingDestroy = old else old.destroy()
        },
        runtime = runtime,
        scope = scope,
    )

    override val state: StateFlow<AdState> get() = engine.state

    /** Renderer mặc định khai báo cho placement này trong DSL (`native(id) { renderer = ... }`) -
     * dùng khi `AdFlowNativeAdView`/`AdFlowNative` không tự chỉ định renderer riêng. */
    internal val defaultRenderer: NativeAdRenderer? = config.defaultRenderer

    override fun load() = engine.ensureLoaded()
    override fun reload() = engine.forceReload()
    override fun addListener(listener: AdListener) = engine.addListener(listener)
    override fun removeListener(listener: AdListener) = engine.removeListener(listener)

    internal fun bind(): LoadedNativeAd? {
        val ad = engine.peek() ?: return null
        bindCount++
        return ad
    }

    internal fun unbind() {
        if (bindCount > 0) bindCount--
        if (bindCount == 0) {
            pendingDestroy?.destroy()
            pendingDestroy = null
        }
    }

    internal fun isShowAllowed(): Boolean = config.showRule?.isAllowed(placementId) != false
}
