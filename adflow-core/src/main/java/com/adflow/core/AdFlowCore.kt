package com.adflow.core

object AdFlowCore {
    var logger: AdFlowLogger = LogcatAdFlowLogger()
        private set

    private val revenueLoggers = mutableListOf<RevenueLogger>()

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
        AdShowIntervalPolicy.reset()
    }
}
