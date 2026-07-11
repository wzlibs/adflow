package com.adflow.core.engine

import com.adflow.core.config.ShowIntervalConfig
import com.adflow.core.logging.AdFlowLogger
import com.adflow.core.network.AdNetwork
import com.adflow.core.revenue.AdRevenueEvent
import com.adflow.core.revenue.RevenueLogger
import kotlinx.coroutines.CoroutineScope

/**
 * Toàn bộ state runtime của 1 lần `AdFlow.initialize {}` - 1 instance sở hữu bởi facade `AdFlow`,
 * gom network, logger, slot chống chồng full-screen, chính sách giới hạn tần suất, cổng chờ
 * foreground, và trạng thái consent. Test tạo được instance riêng, độc lập với nhau.
 */
internal class AdFlowRuntime(
    val network: AdNetwork,
    var logger: AdFlowLogger,
    val scope: CoroutineScope,
    val clock: () -> Long = System::currentTimeMillis,
    showIntervalConfig: ShowIntervalConfig = ShowIntervalConfig(),
) {
    val fullScreenSlot = FullScreenSlot()
    val showIntervalPolicy = ShowIntervalPolicy(showIntervalConfig, clock)
    val foregroundGate = ForegroundGate()

    /** GDPR/consent - mặc định true (không chặn gì) để không phá app chưa tích hợp
     * [com.adflow.core.consent.ConsentManager]. Cập nhật qua callback `onConsentChanged` truyền
     * vào `AdNetwork.createConsentManager()` mỗi khi consent resolve/đổi. */
    var consentAllowsAdRequests: Boolean = true

    private val revenueLoggers = mutableListOf<RevenueLogger>()

    fun addRevenueLogger(logger: RevenueLogger) {
        revenueLoggers += logger
    }

    fun removeRevenueLogger(logger: RevenueLogger) {
        revenueLoggers -= logger
    }

    fun dispatchRevenue(event: AdRevenueEvent) {
        revenueLoggers.forEach { it.onRevenuePaid(event) }
    }
}
