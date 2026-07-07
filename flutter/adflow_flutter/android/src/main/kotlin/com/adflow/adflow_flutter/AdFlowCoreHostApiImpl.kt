package com.adflow.adflow_flutter

import com.adflow.adflow_flutter.callbacks.RevenueLoggerBridge
import com.adflow.adflow_flutter.generated.AdFlowCoreHostApi
import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.PShowIntervalConfig
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowEvent
import com.adflow.core.AdFlowLogger
import com.adflow.core.AdType
import com.adflow.core.LogcatAdFlowLogger

/** Không log gì cả - dùng khi Dart gọi configure(useLogcatLogger: false). Không có cơ chế bridge
 * 1 AdFlowLogger tùy biến từ Dart sang Kotlin (mỗi lệnh log sẽ cần round-trip channel, không đáng
 * cho use-case log thuần tuý) - app Flutter muốn custom logging nên tự đọc Logcat hoặc theo dõi
 * qua onRevenuePaid/onShowEvent thay vì qua AdFlowLogger. */
private object NoOpAdFlowLogger : AdFlowLogger {
    override fun log(placementId: String, adType: AdType, event: AdFlowEvent, detail: String?) {}
}

class AdFlowCoreHostApiImpl(
    private val registry: PlacementRegistry,
    private val flutterApi: AdFlowFlutterApi,
) : AdFlowCoreHostApi {

    private var revenueLoggerRegistered = false

    override fun configure(showIntervalConfig: PShowIntervalConfig, useLogcatLogger: Boolean) {
        AdFlowCore.configure(
            showIntervalConfig = showIntervalConfig.toCore(),
            logger = if (useLogcatLogger) LogcatAdFlowLogger() else NoOpAdFlowLogger,
        )
    }

    override fun initializeProvider(callback: (Result<Unit>) -> Unit) {
        registry.provider.initialize(registry.application) {
            callback(Result.success(Unit))
        }
    }

    override fun isShowingFullScreenAd(): Boolean = AdFlowCore.isShowingFullScreenAd

    override fun addRevenueLogger() {
        if (revenueLoggerRegistered) return
        revenueLoggerRegistered = true
        AdFlowCore.addRevenueLogger(RevenueLoggerBridge(flutterApi))
    }
}
