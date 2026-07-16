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

    /** GDPR/consent - mặc định `false` (fail-safe deny): không load/init gì cho tới khi biết chắc
     * `canRequestAds()` cho phép. Cập nhật qua [updateConsent], được gọi từ callback
     * `onConsentChanged` truyền vào `AdNetwork.createConsentManager()` mỗi khi consent resolve/đổi. */
    var consentAllowsAdRequests: Boolean = false
        private set

    /** Chạy đúng 1 lần khi consent cho phép lần đầu tiên - gán bởi `AdFlow.initialize()`. */
    var networkInitializer: (() -> Unit)? = null
    private var networkInitStarted = false

    /** [AdLoadEngine] tự đăng ký ở đây để biết lúc nào nên flush 1 lượt `.load()` đã bị chặn tạm
     * thời bởi `CONSENT_REQUIRED` - xem [updateConsent]. Sống cùng vòng đời runtime, không cần
     * gỡ đăng ký (engine cũng sống cùng vòng đời đó). */
    private val consentGrantedListeners = mutableListOf<() -> Unit>()

    fun onConsentGranted(listener: () -> Unit) {
        consentGrantedListeners += listener
    }

    /** Cập nhật trạng thái consent; nếu vừa được phép và chưa init lần nào, kích [networkInitializer]
     * đúng 1 lần (không phụ thuộc foreground - xem docs/features/2026-07-14-ad-network-init-consent-gate.md).
     * Mỗi lần chuyển từ không cho phép sang cho phép (kể cả sau khi đã init, ví dụ revoke rồi
     * re-grant qua privacy options form) đều báo [consentGrantedListeners] để các lượt `.load()` bị
     * chặn tạm thời tự chạy lại - xem `AdLoadEngine.ensureLoaded()`. */
    fun updateConsent(allows: Boolean) {
        val wasAllowed = consentAllowsAdRequests
        consentAllowsAdRequests = allows
        if (allows && !networkInitStarted) {
            networkInitStarted = true
            networkInitializer?.invoke()
        }
        if (allows && !wasAllowed) {
            consentGrantedListeners.toList().forEach { it() }
        }
    }

    /** Đổi gap tối thiểu giữa Interstitial/App Open sau khi runtime đã chạy - xem [ShowIntervalPolicy.updateConfig]. */
    fun updateShowIntervalConfig(config: ShowIntervalConfig) = showIntervalPolicy.updateConfig(config)

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
