package com.dev.adflow

import android.util.Log
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adflow.admob.banner.compose.BannerAdView
import com.adflow.admob.nativead.compose.NativeAdView
import com.adflow.core.AdLoadResult
import com.adflow.core.PrivacyOptionsRequirement
import com.adflow.core.RewardItem
import com.adflow.core.RewardedAdCallback
import com.adflow.core.ShowCallback
import kotlinx.coroutines.delay

private const val TAG = "AdFlowDemo"

@Composable
fun HomeScreen(placements: DemoAdPlacements) {
    val context = LocalContext.current
    var premium by remember { mutableStateOf(PremiumState.isPremium) }
    var lastReward by remember { mutableStateOf<RewardItem?>(null) }
    // Tạo mới ConsentManager ở đây là an toàn - nó chỉ bọc lại 1 ConsentInformation singleton dùng
    // chung trong toàn app (UserMessagingPlatform.getConsentInformation() trả về cùng 1 instance).
    val consentManager = remember { placements.provider.createConsentManager(context) }
    val privacyOptionsRequired = consentManager.getPrivacyOptionsRequirement() == PrivacyOptionsRequirement.REQUIRED

    // NativeAdView/BannerAdView được gọi thẳng ngay, không check isReady() trước - nếu ad chưa
    // sẵn sàng, onShowBlocked báo về và nativeBlocked/bannerBlocked bật lên để ẩn UI phụ thuộc
    // (nút Reload). View thật bên trong tự render rỗng (GONE) khi bị chặn, không cần ẩn thủ công.
    // AndroidView() chỉ chạy factory tạo View 1 lần duy nhất lúc emit, không tự thử lại khi ad
    // load xong ở background - nên khi đang bị chặn, poll định kỳ rồi bump *Generation để ép
    // key() tạo lại NativeAdView/BannerAdView, tự động thử createView()/getView() lại lần nữa.
    var nativeGeneration by remember { mutableStateOf(0) }
    var nativeBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(placements.native) {
        while (true) {
            delay(500)
            if (nativeBlocked) {
                nativeBlocked = false
                nativeGeneration++
            }
        }
    }

    var bannerGeneration by remember { mutableStateOf(0) }
    var bannerBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(placements.banner) {
        while (true) {
            delay(500)
            if (bannerBlocked) {
                bannerBlocked = false
                bannerGeneration++
            }
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

        if (!nativeBlocked) {
            Button(onClick = {
                placements.native.reload { result ->
                    if (result is AdLoadResult.Success) nativeGeneration++
                }
            }) {
                Text("Reload Native Ad")
            }
        }
        key(nativeGeneration) {
            NativeAdView(
                manager = placements.native,
                onShowBlocked = { reason ->
                    nativeBlocked = true
                    Log.d(TAG, "Native ad blocked: $reason")
                },
            )
        }
        key(bannerGeneration) {
            BannerAdView(
                manager = placements.banner,
                onShowBlocked = { reason ->
                    bannerBlocked = true
                    Log.d(TAG, "Banner ad blocked: $reason")
                },
            )
        }
    }
}
