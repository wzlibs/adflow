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
        AdFlowCore.configure(logger = LogcatAdFlowLogger(tag = "AdFlow"))
        placements = DemoAdPlacements(this)
        placements.provider.initialize(this) {
            placements.splashInterstitial.load()
            placements.globalInterstitial.load()
            placements.appOpen.load()
            placements.rewarded.load()
            placements.banner.load()
            placements.native.load()
        }
        // Shows the App Open ad whenever the app returns to the foreground - never on top of
        // another full-screen ad, and gated by the usual show-interval/showRule checks inside
        // AppOpenAdManager.show() like any other App Open display.
        AppOpenAdController(this, placements.appOpen).start()
    }
}
