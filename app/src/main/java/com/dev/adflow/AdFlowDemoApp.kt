package com.dev.adflow

import android.app.Application
import com.adflow.core.AdFlowCore
import com.adflow.core.AppOpenAdController
import com.adflow.core.LogcatAdFlowLogger

class AdFlowDemoApp : Application() {

    lateinit var placements: DemoAdPlacements
        private set

    override fun onCreate() {
        super.onCreate()
        AdFlowCore.configure(logger = LogcatAdFlowLogger(tag = "AdFlowDebug"))
        placements = DemoAdPlacements(this)
        placements.provider.initialize(this) {
            placements.splashInterstitial.load()
            placements.globalInterstitial.load()
            placements.appOpen.load()
            placements.rewarded.load()
            placements.banner.load()
            placements.native.load()
        }
        // Tự động show App Open ad mỗi khi app quay lại foreground - không bao giờ đè lên full-screen
        // ad khác, và vẫn bị chặn bởi các check show-interval/showRule thông thường trong
        // AppOpenAdManager.show() giống mọi lần hiển thị App Open khác.
        AppOpenAdController(this, placements.appOpen).start()
    }
}
