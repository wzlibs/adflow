package com.adflow.admob.banner.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.adflow.core.banner.BannerAdManager
import com.adflow.core.BlockReason

@Composable
fun BannerAdView(
    manager: BannerAdManager,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onShowBlocked: (BlockReason) -> Unit = {},
) {
    AndroidView(factory = { context -> manager.getView(context, onShowBlocked) }, modifier = modifier)
}
