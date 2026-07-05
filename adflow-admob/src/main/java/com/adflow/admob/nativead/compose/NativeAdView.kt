package com.adflow.admob.nativead.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.adflow.admob.nativead.DefaultMediumNativeAdRenderer
import com.adflow.core.NativeAdManager
import com.adflow.core.NativeAdRenderer

@Composable
fun NativeAdView(
    manager: NativeAdManager,
    renderer: NativeAdRenderer = DefaultMediumNativeAdRenderer(),
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    if (!manager.isReady()) return
    AndroidView(factory = { context -> manager.createView(context, renderer) }, modifier = modifier)
}
