package com.adflow.core

object AdFlowCore {
    var logger: AdFlowLogger = LogcatAdFlowLogger()
        private set

    private val revenueLoggers = mutableListOf<RevenueLogger>()

    /**
     * True while any full-screen ad (Interstitial/AppOpen/Rewarded) is currently on screen.
     * Claimed/released atomically via [tryClaimFullScreenSlot]/[releaseFullScreenSlot] by every
     * full-screen show() path, so two full-screen ads - even from different managers, or
     * concurrent show() calls - can never both be on screen at once. Consulted by
     * [AppOpenAdController] as a best-effort pre-check so it never even attempts to show an App
     * Open ad on top of an already-visible full-screen ad.
     */
    var isShowingFullScreenAd: Boolean = false
        private set

    /**
     * Atomically claims the single full-screen slot: returns true and marks it taken if it was
     * free, or returns false with no side effect if another full-screen ad is already showing.
     * Every successful claim must be paired with exactly one [releaseFullScreenSlot] call once
     * that ad's show lifecycle actually ends (dismissed, failed to show, or the SDK threw
     * synchronously) - callers must not proceed to display an ad after a false result.
     */
    @Synchronized
    fun tryClaimFullScreenSlot(): Boolean {
        if (isShowingFullScreenAd) return false
        isShowingFullScreenAd = true
        return true
    }

    /** Releases a slot previously won via [tryClaimFullScreenSlot]. */
    @Synchronized
    fun releaseFullScreenSlot() {
        isShowingFullScreenAd = false
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
