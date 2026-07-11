package com.adflow.core.revenue

/** App đăng ký qua `AdFlow.addRevenueLogger()` để forward sự kiện doanh thu sang Adjust/AppsFlyer/
 * Firebase... - AdFlow không phụ thuộc SDK đo lường nào. */
fun interface RevenueLogger {
    fun onRevenuePaid(event: AdRevenueEvent)
}
