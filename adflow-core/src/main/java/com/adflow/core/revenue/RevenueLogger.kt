package com.adflow.core.revenue

fun interface RevenueLogger {
    fun onRevenuePaid(event: AdRevenueEvent)
}
