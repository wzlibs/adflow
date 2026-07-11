package com.adflow.core.engine

import com.adflow.core.config.ShowIntervalConfig
import com.adflow.core.logging.AdFlowLogger
import com.adflow.core.network.AdNetwork
import com.adflow.core.revenue.AdRevenueEvent
import com.adflow.core.revenue.RevenueLogger
import kotlinx.coroutines.CoroutineScope

/**
 * Toàn bộ state runtime của 1 lần `AdFlow.initialize {}` - thay cho `AdFlowCore` global object
 * mutable của v1 bằng 1 instance sở hữu bởi facade `AdFlow`. Test tạo instance riêng thay vì phải
 * reset 1 singleton toàn cục.
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
