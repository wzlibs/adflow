package com.adflow.core.fullscreen

import android.app.Activity

/** App Open ad. Ngoài show() thủ công, bật `autoShowOnForeground = true` trong DSL để lib tự
 * show mỗi khi app quay lại foreground (không bao giờ đè lên full-screen ad khác). */
interface AppOpenAd : FullScreenAd {
    fun show(activity: Activity, callback: FullScreenCallback = FullScreenCallback.EMPTY)
}
