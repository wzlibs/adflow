package com.adflow.core.banner

import android.content.Context
import android.view.View
import com.adflow.core.AdLoadResult
import com.adflow.core.BlockReason
import com.adflow.core.config.PlacementConfig
import com.adflow.core.fullscreen.ShowCallback

interface BannerAdManager {
    fun isReady(): Boolean

    fun load(onResult: (AdLoadResult) -> Unit = {})

    /**
     * Luôn an toàn để gọi, không cần check [isReady] trước - nếu ad chưa ready hoặc
     * [PlacementConfig.showRule] đang từ chối, trả về 1 `View` rỗng (không hiển thị gì, không
     * chiếm layout) và báo lý do qua [onShowBlocked], giống hệt cách `show()` của full-screen ad
     * báo qua [ShowCallback.onShowBlocked] thay vì throw.
     */
    fun getView(context: Context, onShowBlocked: (BlockReason) -> Unit = {}): View
}
