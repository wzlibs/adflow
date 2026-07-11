package com.dev.adflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adflow.compose.AdFlowBanner
import com.adflow.compose.AdFlowNative
import com.adflow.core.AdFlow
import com.adflow.core.consent.PrivacyOptionsRequirement
import com.adflow.core.fullscreen.FullScreenCallback
import com.adflow.core.rewarded.RewardItem
import com.adflow.core.rewarded.RewardedAdCallback
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var premium by remember { mutableStateOf(PremiumState.isPremium) }
    var lastReward by remember { mutableStateOf<RewardItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val privacyOptionsRequired = AdFlow.consent.privacyOptionsRequirement == PrivacyOptionsRequirement.REQUIRED

    fun showBlocked(reason: String) {
        scope.launch { snackbarHostState.showSnackbar("Blocked: $reason") }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row {
                Text("Premium (disable ads)")
                Switch(
                    checked = premium,
                    onCheckedChange = {
                        premium = it
                        PremiumState.isPremium = it
                    },
                )
            }

            Button(onClick = {
                (context as? android.app.Activity)?.let { activity ->
                    AdFlow.interstitial("global_interstitial").show(
                        activity,
                        object : FullScreenCallback {
                            override fun onAdBlocked(reason: com.adflow.core.BlockReason) = showBlocked(reason.name)
                        },
                    )
                }
            }) { Text("Show Global Interstitial") }

            Button(onClick = {
                (context as? android.app.Activity)?.let { activity ->
                    AdFlow.rewarded("rewarded").show(
                        activity,
                        object : RewardedAdCallback {
                            override fun onUserEarnedReward(reward: RewardItem) { lastReward = reward }
                            override fun onAdBlocked(reason: com.adflow.core.BlockReason) = showBlocked(reason.name)
                        },
                    )
                }
            }) { Text("Show Rewarded Ad") }
            Text("Last reward: ${lastReward?.let { "${it.amount} ${it.type}" } ?: "none yet"}")

            Button(onClick = {
                (context as? android.app.Activity)?.let { activity ->
                    AdFlow.appOpen("app_open").show(activity, FullScreenCallback.EMPTY)
                }
            }) { Text("Show App Open Ad") }

            if (privacyOptionsRequired) {
                Button(onClick = {
                    (context as? android.app.Activity)?.let { AdFlow.consent.showPrivacyOptionsForm(it) {} }
                }) { Text("Privacy options") }
            }

            Button(onClick = { AdFlow.native("home_native").reload() }) {
                Text("Reload Native Ad")
            }

            // AdFlowBanner/AdFlowNative tự quan sát state và tự thay nội dung, kể cả sau reload().
            AdFlowNative(
                placementId = "home_native",
                loading = { ShimmerBox(height = 120.dp) },
                failed = { Text("Native ad unavailable") },
            )
            AdFlowBanner(
                placementId = "home_banner",
                loading = { ShimmerBox(height = 60.dp) },
            )

            val bannerState by AdFlow.banner("home_banner").state.collectAsStateWithLifecycle()
            Text("Banner state: $bannerState")
        }
    }
}
