package com.adflow.admob.nativead.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.adflow.admob.nativead.DefaultMediumNativeAdRenderer
import com.adflow.core.BlockReason
import com.adflow.core.nativead.NativeAdManager
import com.adflow.core.nativead.NativeAdRenderer

@Composable
fun NativeAdView(
    manager: NativeAdManager,
    renderer: NativeAdRenderer = DefaultMediumNativeAdRenderer(),
    modifier: Modifier = Modifier.fillMaxWidth(),
    onShowBlocked: (BlockReason) -> Unit = {},
) {
    AndroidView(factory = { context -> manager.createView(context, renderer, onShowBlocked) }, modifier = modifier)
}
