package com.adflow.core

data class AdRevenueEvent(
    val placementId: String,
    val adType: AdType,
    val adUnitId: String,
    val valueMicros: Long,
    val currencyCode: String,
    val precision: String,
    val adNetwork: String?,
)
