package com.adflow.core

object AdFlowCore {
    var logger: AdFlowLogger = LogcatAdFlowLogger()
        private set

    private val revenueLoggers = mutableListOf<RevenueLogger>()

    /**
     * True while any full-screen ad (Interstitial/AppOpen/Rewarded) is currently on screen -
     * set by [CachedAdLoaderBase]'s show()-consuming subclasses. Consulted by
     * [AppOpenAdController] so it never shows an App Open ad on top of an already-visible
     * full-screen ad.
     */
    var isShowingFullScreenAd: Boolean = false
        private set

    fun setShowingFullScreenAd(showing: Boolean) {
        isShowingFullScreenAd = showing
    }

    fun configure(
        showIntervalConfig: ShowIntervalConfig = ShowIntervalConfig(),
        logger: AdFlowLogger = LogcatAdFlowLogger(),
    ) {
        this.logger = logger
        AdShowIntervalPolicy.configure(showIntervalConfig)
    }

    fun addRevenueLogger(logger: RevenueLogger) {
        revenueLoggers += logger
    }

    fun removeRevenueLogger(logger: RevenueLogger) {
        revenueLoggers -= logger
    }

    fun dispatchRevenue(event: AdRevenueEvent) {
        revenueLoggers.forEach { it.onRevenuePaid(event) }
    }

    internal fun reset() {
        logger = LogcatAdFlowLogger()
        revenueLoggers.clear()
        isShowingFullScreenAd = false
        AdShowIntervalPolicy.reset()
    }
}
