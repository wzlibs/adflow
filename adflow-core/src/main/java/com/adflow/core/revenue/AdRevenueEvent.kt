package com.adflow.core.revenue

import com.adflow.core.AdType
data class AdRevenueEvent(
    val placementId: String,
    val adType: AdType,
    val adUnitId: String,
    val valueMicros: Long,
    val currencyCode: String,
    val precision: String,
    val adNetwork: String?,
)
