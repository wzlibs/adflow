package com.dev.adflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adflow.admob.banner.compose.BannerAdView
import com.adflow.admob.nativead.compose.NativeAdView
import com.adflow.core.RewardItem
import com.adflow.core.RewardedAdCallback
import com.adflow.core.ShowCallback
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(placements: DemoAdPlacements) {
    val context = LocalContext.current
    var premium by remember { mutableStateOf(PremiumState.isPremium) }
    var lastReward by remember { mutableStateOf<RewardItem?>(null) }

    // NativeAdView/BannerAdView each re-check isReady() on every recomposition, but
    // nothing here triggers a recomposition once the ad finishes loading in the
    // background, so a not-yet-ready ad would silently never appear. Poll isReady()
    // on a short interval until each ad becomes ready, then stop; reading the
    // resulting state below is what forces HomeScreen to recompose at that point.
    var nativeReady by remember { mutableStateOf(placements.native.isReady()) }
    LaunchedEffect(placements.native) {
        while (!nativeReady) {
            delay(500)
            nativeReady = placements.native.isReady()
        }
    }

    var bannerReady by remember { mutableStateOf(placements.banner.isReady()) }
    LaunchedEffect(placements.banner) {
        while (!bannerReady) {
            delay(500)
            bannerReady = placements.banner.isReady()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row {
            Text("Premium (disable ads)")
            Switch(checked = premium, onCheckedChange = {
                premium = it
                PremiumState.isPremium = it
            })
        }

        Button(onClick = {
            (context as? android.app.Activity)?.let { placements.globalInterstitial.show(it, ShowCallback.NONE) }
        }) { Text("Show Global Interstitial") }

        Button(onClick = {
            (context as? android.app.Activity)?.let {
                placements.rewarded.show(it, object : RewardedAdCallback {
                    override fun onUserEarnedReward(reward: RewardItem) { lastReward = reward }
                })
            }
        }) { Text("Show Rewarded Ad") }

        Text("Last reward: ${lastReward?.let { "${it.amount} ${it.type}" } ?: "none yet"}")

        Button(onClick = {
            (context as? android.app.Activity)?.let { placements.appOpen.show(it, ShowCallback.NONE) }
        }) { Text("Show App Open Ad") }

        // Reading nativeReady/bannerReady here (even though the composables re-check
        // isReady() themselves) is what ties recomposition to ad-ready state changes.
        if (nativeReady) {
            NativeAdView(manager = placements.native)
        }
        if (bannerReady) {
            BannerAdView(manager = placements.banner)
        }
    }
}
