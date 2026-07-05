package com.adflow.core

fun interface RevenueLogger {
    fun onRevenuePaid(event: AdRevenueEvent)
}
