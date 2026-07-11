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
import com.adflow.core.AdFlow
import com.adflow.core.fullscreen.FullScreenCallback
import com.adflow.core.fullscreen.awaitReady
import kotlin.time.Duration.Companion.seconds

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val context = LocalContext.current

    // awaitReady() thay hẳn pattern poll isReady()+delay(500) của v1 - state-first, chỉ đơn giản
    // đợi StateFlow phát Loaded trong tối đa 8s rồi tiếp tục dù ad có ready hay không.
    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect onDone()
        val splash = AdFlow.interstitial("splash_interstitial")

        val ready = splash.awaitReady(8.seconds)
        if (!ready) {
            onDone()
            return@LaunchedEffect
        }

        splash.show(
            activity,
            object : FullScreenCallback {
                override fun onAdDismissed() = onDone()
                override fun onAdFailedToShow(error: com.adflow.core.AdFlowError) = onDone()
                override fun onAdBlocked(reason: com.adflow.core.BlockReason) = onDone()
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
        Text(text = "AdFlow demo")
    }
}
