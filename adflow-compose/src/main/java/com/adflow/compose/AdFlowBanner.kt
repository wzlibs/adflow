package com.adflow.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adflow.core.AdFlow
import com.adflow.core.AdFlowError
import com.adflow.core.AdState
import com.adflow.core.banner.AdFlowBannerView

/**
 * Composable slot cho 1 banner placement - quan sát `AdFlow.banner(placementId).state` và tự
 * chuyển giữa [loading]/[failed]/ad thật: mỗi lần state đổi, Compose recompose slot tương ứng;
 * riêng nhánh ad thật dùng [AdFlowBannerView] (đã tự quản lý attach/detach/lease) nên
 * `AndroidView` chỉ tạo view 1 lần, không cần tái tạo thủ công.
 *
 * [AndroidView] (nơi duy nhất gọi `load()`, qua `AdFlowBannerView.start()`) chỉ được tạo khi state
 * đã là [AdState.Loaded] - nếu không có [LaunchedEffect] tự gọi `load()` ở đây, placement không
 * bao giờ rời khỏi [AdState.Idle] (không còn auto-load lúc `AdFlow.initialize()` nữa).
 */
@Composable
fun AdFlowBanner(
    placementId: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    loading: @Composable BoxScope.() -> Unit = {},
    failed: @Composable BoxScope.(error: AdFlowError) -> Unit = {},
) {
    val controller = AdFlow.banner(placementId)
    LaunchedEffect(placementId) { controller.load() }
    val state by controller.state.collectAsStateWithLifecycle()

    Box(modifier = modifier) {
        when (val current = state) {
            AdState.Idle, AdState.Loading -> loading()
            is AdState.Failed -> failed(current.error)
            is AdState.Loaded -> AndroidView(
                factory = { context ->
                    AdFlowBannerView(context).apply { this.placementId = placementId }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AdState.Showing -> Unit // Banner không dùng trạng thái Showing (chỉ full-screen).
        }
    }
}
