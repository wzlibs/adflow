package com.adflow.adflow_flutter

import com.adflow.admob.consent.AdMobConsentManager
import com.adflow.adflow_flutter.callbacks.RevenueLoggerBridge
import com.adflow.adflow_flutter.generated.AdFlowCoreHostApi
import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.PAdFlowError
import com.adflow.adflow_flutter.generated.PConsentStatus
import com.adflow.adflow_flutter.generated.PDebugGeography
import com.adflow.adflow_flutter.generated.PPrivacyOptionsRequirement
import com.adflow.adflow_flutter.generated.PShowIntervalConfig
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
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

    // AdMobConsentManager chỉ bọc lại ConsentInformation (1 singleton thật của UMP) - tạo mới 1
    // instance cho mỗi lệnh gọi là an toàn, luôn phản ánh đúng state chung, không cần cache trong
    // PlacementRegistry.
    override fun getConsentStatus(): PConsentStatus =
        AdMobConsentManager(registry.application).getConsentStatus().toPigeon()

    override fun getPrivacyOptionsRequirement(): PPrivacyOptionsRequirement =
        AdMobConsentManager(registry.application).getPrivacyOptionsRequirement().toPigeon()

    override fun canRequestAds(): Boolean = AdMobConsentManager(registry.application).canRequestAds()

    override fun requestConsentIfNeeded(
        debugGeography: PDebugGeography?,
        testDeviceHashedIds: List<String>,
        callback: (Result<PAdFlowError?>) -> Unit,
    ) {
        val activity = registry.currentActivity
        if (activity == null) {
            callback(Result.success(AdFlowError(-4, "no activity attached").toPigeon()))
            return
        }
        AdMobConsentManager(
            registry.application,
            debugGeography = debugGeography?.toDebugGeographyInt(),
            testDeviceHashedIds = testDeviceHashedIds,
        ).requestConsentIfNeeded(activity) { error -> callback(Result.success(error?.toPigeon())) }
    }

    override fun showPrivacyOptionsForm(callback: (Result<PAdFlowError?>) -> Unit) {
        val activity = registry.currentActivity
        if (activity == null) {
            callback(Result.success(AdFlowError(-4, "no activity attached").toPigeon()))
            return
        }
        AdMobConsentManager(registry.application)
            .showPrivacyOptionsForm(activity) { error -> callback(Result.success(error?.toPigeon())) }
    }

    private fun PDebugGeography.toDebugGeographyInt(): Int =
        when (this) {
            PDebugGeography.DISABLED -> 0
            PDebugGeography.EEA -> 1
            PDebugGeography.NOT_EEA -> 2
        }
}
