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
import com.adflow.core.PrivacyOptionsRequirement
import com.adflow.core.RewardItem
import com.adflow.core.RewardedAdCallback
import com.adflow.core.ShowCallback
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(placements: DemoAdPlacements) {
    val context = LocalContext.current
    var premium by remember { mutableStateOf(PremiumState.isPremium) }
    var lastReward by remember { mutableStateOf<RewardItem?>(null) }
    // Tạo mới ConsentManager ở đây là an toàn - nó chỉ bọc lại 1 ConsentInformation singleton dùng
    // chung trong toàn app (UserMessagingPlatform.getConsentInformation() trả về cùng 1 instance).
    val consentManager = remember { placements.provider.createConsentManager(context) }
    val privacyOptionsRequired = consentManager.getPrivacyOptionsRequirement() == PrivacyOptionsRequirement.REQUIRED

    // NativeAdView/BannerAdView đều tự check lại isReady() ở mỗi lần recomposition, nhưng
    // không có gì ở đây kích hoạt recomposition khi ad load xong ở background, nên 1 ad
    // chưa ready sẽ âm thầm không bao giờ xuất hiện. Poll isReady() theo 1 khoảng ngắn cho
    // đến khi mỗi ad ready thì dừng; việc đọc state kết quả ở dưới chính là thứ buộc
    // HomeScreen phải recompose vào lúc đó.
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

        // Chỉ hiện lối vào này khi UMP yêu cầu (thường do user đã từng đồng ý và cần cách đổi ý
        // sau) - đây là yêu cầu bắt buộc theo chính sách AdMob/Google Play, không phải tuỳ chọn.
        if (privacyOptionsRequired) {
            Button(onClick = {
                (context as? android.app.Activity)?.let { consentManager.showPrivacyOptionsForm(it) {} }
            }) { Text("Privacy options") }
        }

        // Đọc nativeReady/bannerReady ở đây (dù các composable đã tự check lại isReady())
        // chính là thứ gắn recomposition với các thay đổi trạng thái ad-ready.
        if (nativeReady) {
            NativeAdView(manager = placements.native)
        }
        if (bannerReady) {
            BannerAdView(manager = placements.banner)
        }
    }
}
