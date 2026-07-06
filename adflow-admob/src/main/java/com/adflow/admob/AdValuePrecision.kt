package com.adflow.admob

import com.google.android.gms.ads.AdValue

/**
 * Maps AdMob's [AdValue.PrecisionType] constant to the human-readable name used in
 * [com.adflow.core.AdRevenueEvent.precision]. Shared by every AdMob*AdManager so the mapping
 * isn't copy-pasted per ad type.
 */
internal fun precisionName(@AdValue.PrecisionType precisionType: Int): String = when (precisionType) {
    AdValue.PrecisionType.PRECISE -> "PRECISE"
    AdValue.PrecisionType.ESTIMATED -> "ESTIMATED"
    AdValue.PrecisionType.PUBLISHER_PROVIDED -> "PUBLISHER_PROVIDED"
    AdValue.PrecisionType.UNKNOWN -> "UNKNOWN"
    else -> "UNKNOWN"
}
