package com.adflow.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adflow.core.AdFlow
import com.adflow.core.AdFlowError
import com.adflow.core.AdState
import com.adflow.core.nativead.AdFlowNativeAdView
import com.adflow.core.nativead.NativeAdRenderer

/** Composable slot cho 1 native placement - cùng triết lý với [AdFlowBanner]. [renderer] = null
 * dùng renderer mặc định khai báo cho placement trong DSL (`native(id) { renderer = ... }`). */
@Composable
fun AdFlowNative(
    placementId: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    renderer: NativeAdRenderer? = null,
    loading: @Composable BoxScope.() -> Unit = {},
    failed: @Composable BoxScope.(error: AdFlowError) -> Unit = {},
) {
    val state by AdFlow.native(placementId).state.collectAsStateWithLifecycle()

    Box(modifier = modifier) {
        when (val current = state) {
            AdState.Idle, AdState.Loading -> loading()
            is AdState.Failed -> failed(current.error)
            is AdState.Loaded -> AndroidView(
                factory = { context ->
                    AdFlowNativeAdView(context).apply {
                        this.placementId = placementId
                        this.renderer = renderer
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            AdState.Showing -> Unit
        }
    }
}
