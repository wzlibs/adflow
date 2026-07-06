package com.adflow.admob.banner.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.adflow.core.BannerAdManager

@Composable
fun BannerAdView(manager: BannerAdManager, modifier: Modifier = Modifier.fillMaxWidth()) {
    if (!manager.isReady()) return
    AndroidView(factory = { context -> manager.getView(context) }, modifier = modifier)
}
