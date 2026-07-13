package com.adflow.core.fullscreen

import com.adflow.core.AdState
import kotlin.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tự [load] rồi đợi placement sẵn sàng trong tối đa [timeout] - pattern splash: đợi rồi show, hết
 * giờ thì đi tiếp không show. Trả về true nếu ad ready trong hạn. Không cần `preload = true` cho
 * placement kiểu này (chỉ show đúng 1 lần) - `load()` gọi ở đây đã đủ, gọi lại không sao (coalesce
 * nếu đang có lượt load khác chạy, no-op nếu đã ready).
 *
 * ```kotlin
 * if (AdFlow.interstitial("splash").awaitReady(8.seconds)) {
 *     AdFlow.interstitial("splash").show(activity, callback)
 * } else navigateHome()
 * ```
 */
suspend fun FullScreenAd.awaitReady(timeout: Duration): Boolean {
    load()
    return withTimeoutOrNull(timeout) { state.first { it is AdState.Loaded } } != null
}
