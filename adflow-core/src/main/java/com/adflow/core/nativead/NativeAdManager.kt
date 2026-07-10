package com.adflow.core.nativead

import android.content.Context
import android.view.View
import com.adflow.core.AdLoadResult
import com.adflow.core.BlockReason
import com.adflow.core.config.PlacementConfig
import com.adflow.core.fullscreen.ShowCallback

interface NativeAdManager {
    fun isReady(): Boolean
    fun load(onResult: (AdLoadResult) -> Unit = {})

    /** Ép fetch 1 ad mới thật sự dù ad đang cache vẫn còn hạn - dùng khi app muốn đổi sang ad mới
     * (vd user quay lại 1 màn hình đang hiển thị native ad). Ad cũ vẫn dùng được cho đến khi ad
     * mới load thành công; app tự chịu trách nhiệm ép tạo lại [createView] (vd đổi key) sau khi
     * [onResult] báo thành công - hàm này không tự rebind View đang hiển thị. */
    fun reload(onResult: (AdLoadResult) -> Unit = {})

    /**
     * Luôn an toàn để gọi, không cần check [isReady] trước - nếu ad chưa ready hoặc
     * [PlacementConfig.showRule] đang từ chối, trả về 1 `View` rỗng (không hiển thị gì, không
     * chiếm layout) và báo lý do qua [onShowBlocked], giống hệt cách `show()` của full-screen ad
     * báo qua [ShowCallback.onShowBlocked] thay vì throw.
     */
    fun createView(context: Context, renderer: NativeAdRenderer, onShowBlocked: (BlockReason) -> Unit = {}): View
}
