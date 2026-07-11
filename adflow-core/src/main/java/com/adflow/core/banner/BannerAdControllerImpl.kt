package com.adflow.core.banner

import com.adflow.core.AdListener
import com.adflow.core.AdState
import com.adflow.core.config.PlacementConfig
import com.adflow.core.engine.AdFlowRuntime
import com.adflow.core.engine.AdLoadEngine
import com.adflow.core.logging.AdFlowEvent
import com.adflow.core.network.AdRequestInfo
import com.adflow.core.network.BannerAdSource
import com.adflow.core.network.LoadedBannerAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * `lease()`/`release()` (internal, KHÔNG thuộc [BannerAdController] public) là cơ chế
 * [com.adflow.core.banner.AdFlowBannerView] dùng để chiếm độc quyền ad đang cache khi attach - 1
 * banner ad là 1 `android.view.View` thật của mạng quảng cáo, không thể gắn cùng lúc vào 2 view
 * cha khác nhau (re-parent), nên `lease()` "tiêu thụ" cache như full-screen `take()` thay vì đọc
 * không tiêu thụ như Native. Nếu 1 view thứ 2 cố `lease()` trong khi placement đã bị 1 view khác
 * lease, nó nhận `null` và bị log cảnh báo - đây là lỗi dùng sai của app (nên tách 2 placement
 * riêng cho 2 vị trí hiển thị), không phải lỗi thư viện.
 */
internal class BannerAdControllerImpl(
    override val placementId: String,
    private val config: PlacementConfig,
    source: BannerAdSource,
    private val runtime: AdFlowRuntime,
    scope: CoroutineScope,
) : BannerAdController {

    private val engine = AdLoadEngine<LoadedBannerAd>(
        config = config,
        loadOne = { adUnitId ->
            source.load(
                AdRequestInfo(placementId, config.adType, adUnitId, onRevenue = runtime::dispatchRevenue),
                config.bannerSize ?: BannerSize.ADAPTIVE,
            )
        },
        onDrop = { it.destroy() },
        runtime = runtime,
        scope = scope,
    )

    override val state: StateFlow<AdState> get() = engine.state

    override fun load() = engine.ensureLoaded()
    override fun addListener(listener: AdListener) = engine.addListener(listener)
    override fun removeListener(listener: AdListener) = engine.removeListener(listener)

    private var leased = false

    internal fun lease(): LoadedBannerAd? {
        if (leased) {
            runtime.logger.log(placementId, config.adType, AdFlowEvent.SHOW_BLOCKED, "banner already leased to another view")
            return null
        }
        val ad = engine.take() ?: return null
        leased = true
        return ad
    }

    internal fun release(ad: LoadedBannerAd) {
        leased = false
        ad.destroy()
        if (config.preload) engine.ensureLoaded()
    }

    internal fun isShowAllowed(): Boolean = config.showRule?.isAllowed(placementId) != false
}
