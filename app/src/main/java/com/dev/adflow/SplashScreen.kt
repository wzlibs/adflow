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
import com.adflow.core.ShowCallback
import kotlinx.coroutines.delay

private const val SPLASH_POLL_INTERVAL_MS = 500L
private const val SPLASH_READY_TIMEOUT_MS = 5000L

@Composable
fun SplashScreen(placements: DemoAdPlacements, onDone: () -> Unit) {
    val context = LocalContext.current

    // AdFlowDemoApp.onCreate() kicks off splashInterstitial.load() asynchronously in the
    // background. If show() is called immediately on first composition, it almost always
    // races ahead of that load and FullScreenAdManagerBase.show() synchronously blocks with
    // NOT_READY (it does not wait for a load in flight). Poll isReady() first, matching the
    // pattern used for the native/banner ads in HomeScreen, with a timeout so a user is never
    // stuck on the splash screen if the ad never becomes ready (no fill, no network, etc.).
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
