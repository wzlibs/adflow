package com.adflow.admob

import com.google.android.gms.ads.AdValue

/**
 * Map hằng số [AdValue.PrecisionType] của AdMob sang tên dễ đọc dùng trong
 * [com.adflow.core.AdRevenueEvent.precision]. Dùng chung cho mọi AdMob*AdManager để không phải
 * copy-paste việc mapping này cho mỗi loại ad.
 */
internal fun precisionName(@AdValue.PrecisionType precisionType: Int): String = when (precisionType) {
    AdValue.PrecisionType.PRECISE -> "PRECISE"
    AdValue.PrecisionType.ESTIMATED -> "ESTIMATED"
    AdValue.PrecisionType.PUBLISHER_PROVIDED -> "PUBLISHER_PROVIDED"
    AdValue.PrecisionType.UNKNOWN -> "UNKNOWN"
    else -> "UNKNOWN"
}
