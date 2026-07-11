package com.adflow.core.nativead

import com.adflow.core.AdListener
import com.adflow.core.AdState
import kotlinx.coroutines.flow.StateFlow

/**
 * Controller headless của 1 placement native. Hiển thị thì dùng [AdFlowNativeAdView] (hoặc
 * composable `AdFlowNative`): view tự quan sát và tự rebind khi có ad mới, kể cả sau [reload].
 */
interface NativeAdController {
    val placementId: String
    val state: StateFlow<AdState>

    fun load()

    /** Ép fetch ad MỚI dù ad đang cache còn hạn (vd user quay lại màn hình muốn thấy ad khác).
     * Ad cũ giữ nguyên cho tới khi ad mới load thành công; view đang hiển thị tự rebind khi state
     * phát [AdState.Loaded] mới - không cần ép tạo lại view. */
    fun reload()

    fun addListener(listener: AdListener)
    fun removeListener(listener: AdListener)
}
