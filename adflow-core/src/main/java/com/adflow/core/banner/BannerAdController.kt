package com.adflow.core.banner

import com.adflow.core.AdListener
import com.adflow.core.AdState
import kotlinx.coroutines.flow.StateFlow

/**
 * Controller headless của 1 placement banner - giữ state/preload/retry. Hiển thị thì dùng
 * [AdFlowBannerView] (hoặc composable `AdFlowBanner` trong module adflow-compose): view tự quan
 * sát controller này và tự gắn ad khi sẵn sàng, client không phải poll.
 */
interface BannerAdController {
    val placementId: String
    val state: StateFlow<AdState>

    fun load()

    fun addListener(listener: AdListener)
    fun removeListener(listener: AdListener)
}
