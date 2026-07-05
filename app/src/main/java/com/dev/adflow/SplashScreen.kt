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

@Composable
fun SplashScreen(placements: DemoAdPlacements, onDone: () -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect onDone()
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
