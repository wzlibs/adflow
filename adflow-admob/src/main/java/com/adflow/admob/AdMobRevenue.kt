package com.adflow.admob

import com.adflow.core.AdFlowCore
import com.adflow.core.AdRevenueEvent
import com.adflow.core.AdType
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo

/**
 * Xây dựng và dispatch [AdRevenueEvent] cho 1 callback paid-event. Mọi AdMob*AdManager đều nối
 * hàm này vào `onPaidEventListener`/`setOnPaidEventListener` của SDK với logic giống nhau, chỉ
 * khác placement/ad type/ad unit/response info đang report - đặt chung ở đây để không phải
 * copy-paste việc mapping này cho mỗi loại ad.
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
