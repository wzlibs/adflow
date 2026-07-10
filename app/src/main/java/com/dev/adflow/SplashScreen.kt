package com.dev.adflow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.adflow.core.AdFlowError
import com.adflow.core.BlockReason
import com.adflow.core.fullscreen.ShowCallback
import kotlinx.coroutines.delay

private const val SPLASH_POLL_INTERVAL_MS = 500L
private const val SPLASH_READY_TIMEOUT_MS = 5000L

@Composable
fun SplashScreen(placements: DemoAdPlacements, onDone: () -> Unit) {
    val context = LocalContext.current

    // AdFlowDemoApp.onCreate() kích hoạt splashInterstitial.load() bất đồng bộ ở background.
    // Nếu show() được gọi ngay lúc composition đầu tiên, nó gần như luôn chạy trước lần load
    // đó và FullScreenAdManagerBase.show() sẽ chặn đồng bộ với NOT_READY (nó không đợi 1 lần
    // load đang chạy). Poll isReady() trước, theo đúng pattern dùng cho native/banner ad ở
    // HomeScreen, có timeout để user không bao giờ bị kẹt ở splash screen nếu ad không bao
    // giờ ready (no fill, không có network, v.v.).
    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect onDone()

        var waitedMs = 0L
        while (!placements.splashInterstitial.isReady() && waitedMs < SPLASH_READY_TIMEOUT_MS) {
            delay(SPLASH_POLL_INTERVAL_MS)
            waitedMs += SPLASH_POLL_INTERVAL_MS
        }

        if (!placements.splashInterstitial.isReady()) {
            onDone()
            return@LaunchedEffect
        }

        placements.splashInterstitial.show(activity, object : ShowCallback {
            override fun onAdDismissed() = onDone()
            override fun onAdFailedToShow(error: AdFlowError) = onDone()
            override fun onShowBlocked(reason: BlockReason) = onDone()
        })
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
        Text(text = "AdFlow demo", modifier = Modifier)
    }
}
