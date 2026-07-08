package com.adflow.core

import android.content.Context
import android.view.View

interface NativeAdManager {
    fun isReady(): Boolean
    fun load(onResult: (AdLoadResult) -> Unit = {})

    /** Ép fetch 1 ad mới thật sự dù ad đang cache vẫn còn hạn - dùng khi app muốn đổi sang ad mới
     * (vd user quay lại 1 màn hình đang hiển thị native ad). Ad cũ vẫn dùng được cho đến khi ad
     * mới load thành công; app tự chịu trách nhiệm ép tạo lại [createView] (vd đổi key) sau khi
     * [onResult] báo thành công - hàm này không tự rebind View đang hiển thị. */
    fun reload(onResult: (AdLoadResult) -> Unit = {})

    fun createView(context: Context, renderer: NativeAdRenderer): View
}
