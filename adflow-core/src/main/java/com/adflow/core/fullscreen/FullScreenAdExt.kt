package com.adflow.core.fullscreen

import com.adflow.core.AdState
import kotlin.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Đợi placement sẵn sàng trong tối đa [timeout] - pattern splash: đợi rồi show, hết giờ thì đi
 * tiếp không show. Trả về true nếu ad ready trong hạn.
 *
 * ```kotlin
 * if (AdFlow.interstitial("splash").awaitReady(8.seconds)) {
 *     AdFlow.interstitial("splash").show(activity, callback)
 * } else navigateHome()
 * ```
 */
suspend fun FullScreenAd.awaitReady(timeout: Duration): Boolean =
    withTimeoutOrNull(timeout) { state.first { it is AdState.Loaded } } != null
