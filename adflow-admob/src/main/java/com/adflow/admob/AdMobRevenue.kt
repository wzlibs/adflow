package com.adflow.admob

import com.adflow.core.AdFlowCore
import com.adflow.core.AdRevenueEvent
import com.adflow.core.AdType
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo

/**
 * Builds and dispatches the [AdRevenueEvent] for a paid-event callback. Every AdMob*AdManager
 * wires this to the SDK's `onPaidEventListener`/`setOnPaidEventListener` with identical logic
 * except for which placement/ad type/ad unit/response info it's reporting for - shared here so
 * that mapping isn't copy-pasted once per ad type.
 */
internal fun dispatchRevenue(
    placementId: String,
    adType: AdType,
    adUnitId: String,
    adValue: AdValue,
    responseInfo: ResponseInfo?,
) {
    AdFlowCore.dispatchRevenue(
        AdRevenueEvent(
            placementId = placementId,
            adType = adType,
            adUnitId = adUnitId,
            valueMicros = adValue.valueMicros,
            currencyCode = adValue.currencyCode,
            precision = precisionName(adValue.precisionType),
            adNetwork = responseInfo?.loadedAdapterResponseInfo?.adSourceName,
        ),
    )
}
