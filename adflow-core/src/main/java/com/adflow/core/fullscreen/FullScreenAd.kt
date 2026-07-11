package com.adflow.core.fullscreen

import com.adflow.core.AdListener
import com.adflow.core.AdState
import kotlinx.coroutines.flow.StateFlow

/** Contract chung của mọi placement full-screen (Interstitial/App Open/Rewarded). */
interface FullScreenAd {
    val placementId: String

    val state: StateFlow<AdState>

    /** Tiện lợi: `state.value is Loaded` (đã tính cả expiry). */
    val isReady: Boolean

    /** Mở 1 lượt load nếu chưa có ad và không có lượt nào đang chạy - gọi lúc nào cũng an toàn,
     * lệnh gọi trùng được gộp vào lượt đang chạy. */
    fun load()

    fun addListener(listener: AdListener)
    fun removeListener(listener: AdListener)
}
