package com.adflow.core.network

import com.adflow.core.AdType
import com.adflow.core.revenue.AdRevenueEvent

/** Ngữ cảnh 1 lần request ad tới adapter mạng - adapter gắn [onRevenue] vào paid-event listener
 * của SDK để doanh thu tự chảy về các RevenueLogger đã đăng ký. */
class AdRequestInfo(
    val placementId: String,
    val adType: AdType,
    val adUnitId: String,
    val onRevenue: (AdRevenueEvent) -> Unit,
)
