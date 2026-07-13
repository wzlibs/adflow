package com.dev.adflow

import android.app.Application
import com.adflow.admob.AdMobNetwork
import com.adflow.admob.nativead.DefaultMediumNativeAdRenderer
import com.adflow.core.AdFlow
import com.adflow.core.logging.LogcatAdFlowLogger

class AdFlowDemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdFlow.initialize(this) {
            network = AdMobNetwork()
            logger = LogcatAdFlowLogger(tag = "AdFlowDebug")

            interstitial("splash_interstitial") {
                adUnits("ca-app-pub-3940256099942544/1033173712")
                // Chỉ show đúng 1 lần lúc khởi động - preload = true sẽ tự load thêm 1 ad thừa
                // sau khi show xong (afterShowEnds()) mà không bao giờ dùng tới. awaitReady() tự
                // load() rồi mới đợi, nên không cần preload để có ad sẵn sàng lúc splash hiển thị.
                preload = false
                loadWhen { !PremiumState.isPremium }
                showWhen { !PremiumState.isPremium }
            }
            interstitial("global_interstitial") {
                adUnits("ca-app-pub-3940256099942544/1033173712")
                loadWhen { !PremiumState.isPremium }
                showWhen { !PremiumState.isPremium }
            }
            appOpen("app_open") {
                adUnits("ca-app-pub-3940256099942544/9257395921")
                loadWhen { !PremiumState.isPremium }
                showWhen { !PremiumState.isPremium }
                // Tự động show mỗi khi app quay lại foreground - không bao giờ đè lên full-screen
                // ad khác đang hiển thị.
                autoShowOnForeground = true
            }
            rewarded("rewarded") {
                adUnits("ca-app-pub-3940256099942544/5224354917")
            }
            banner("home_banner") {
                adUnits("ca-app-pub-3940256099942544/6300978111")
                loadWhen { !PremiumState.isPremium }
            }
            native("home_native") {
                adUnits("ca-app-pub-3940256099942544/2247696110")
                loadWhen { !PremiumState.isPremium }
                renderer = DefaultMediumNativeAdRenderer()
            }
        }
    }
}
