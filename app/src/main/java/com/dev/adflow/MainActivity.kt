package com.dev.adflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dev.adflow.ui.theme.AdFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val placements = (application as AdFlowDemoApp).placements

        // GDPR/UMP: xin consent trước khi load ads. requestConsentIfNeeded() không bắt buộc gọi ở
        // đây cụ thể - chỉ cần 1 Activity nào tiện là được, đặt ở Activity đầu tiên của app cho
        // đơn giản. load() tự động tôn trọng consent (xem SimpleCachedAdLoaderBase.load()), nên
        // các lệnh load() dưới đây vô hại nếu gọi trước khi consent resolve (chỉ fail an toàn).
        placements.provider.createConsentManager(this).requestConsentIfNeeded(this) { _ ->
            // initialize() an toàn để gọi nhiều lần (no-op sau lần đầu thật) - đảm bảo load() dưới
            // đây không chạy trước khi SDK init xong, kể cả khi consent resolve nhanh hơn nhánh
            // runOnFirstForeground trong AdFlowDemoApp.onCreate().
            placements.provider.initialize(this) {
                placements.splashInterstitial.load()
                placements.globalInterstitial.load()
                placements.appOpen.load()
                placements.rewarded.load()
                placements.banner.load()
                placements.native.load()
            }
        }

        setContent {
            AdFlowTheme {
                var showSplash by remember { mutableStateOf(true) }
                if (showSplash) {
                    SplashScreen(placements = placements, onDone = { showSplash = false })
                } else {
                    HomeScreen(placements = placements)
                }
            }
        }
    }
}
