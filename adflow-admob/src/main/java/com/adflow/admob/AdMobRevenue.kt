package com.adflow.admob

import com.adflow.core.network.AdRequestInfo
import com.adflow.core.revenue.AdRevenueEvent
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo

/** Dựng [AdRevenueEvent] cho 1 paid event - dùng chung bởi mọi source AdMob (nối vào
 * `onPaidEventListener`/`setOnPaidEventListener` của SDK), tránh copy-paste mapping này 5 lần. */
internal fun mapRevenue(request: AdRequestInfo, adValue: AdValue, responseInfo: ResponseInfo?): AdRevenueEvent =
    AdRevenueEvent(
        placementId = request.placementId,
        adType = request.adType,
        adUnitId = request.adUnitId,
        valueMicros = adValue.valueMicros,
        currencyCode = adValue.currencyCode,
        precision = precisionName(adValue.precisionType),
        adNetwork = responseInfo?.loadedAdapterResponseInfo?.adSourceName,
    )

internal fun precisionName(precisionType: Int): String = when (precisionType) {
    AdValue.PrecisionType.ESTIMATED -> "ESTIMATED"
    AdValue.PrecisionType.PUBLISHER_PROVIDED -> "PUBLISHER_PROVIDED"
    AdValue.PrecisionType.PRECISE -> "PRECISE"
    else -> "UNKNOWN"
}
