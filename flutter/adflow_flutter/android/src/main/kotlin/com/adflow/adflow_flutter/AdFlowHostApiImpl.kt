package com.adflow.adflow_flutter

import com.adflow.admob.AdMobNetwork
import com.adflow.adflow_flutter.callbacks.RevenueLoggerBridge
import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.AdFlowHostApi
import com.adflow.adflow_flutter.generated.PAdFlowError
import com.adflow.adflow_flutter.generated.PAdType
import com.adflow.adflow_flutter.generated.PConsentStatus
import com.adflow.adflow_flutter.generated.PDebugGeography
import com.adflow.adflow_flutter.generated.PPlacementConfig
import com.adflow.adflow_flutter.generated.PPrivacyOptionsRequirement
import com.adflow.adflow_flutter.generated.PShowIntervalConfig
import com.adflow.core.AdFlow
import com.adflow.core.AdFlowError
import com.adflow.core.AdState
import com.adflow.core.AdType
import com.adflow.core.banner.BannerSize
import com.adflow.core.config.BasePlacementScope
import com.adflow.core.logging.AdFlowEvent
import com.adflow.core.logging.AdFlowLogger
import com.adflow.core.logging.LogcatAdFlowLogger
import com.adflow.core.nativead.NativeAdRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** Không log gì cả - dùng khi Dart gọi initialize(useLogcatLogger: false). Không có cơ chế bridge 1
 * AdFlowLogger tùy biến từ Dart sang Kotlin (mỗi lệnh log sẽ cần round-trip channel, không đáng
 * cho use-case log thuần tuý) - app Flutter muốn custom logging nên tự đọc Logcat hoặc theo dõi
 * qua onRevenuePaid/onShowEvent/onAdState thay vì qua AdFlowLogger. */
private object NoOpAdFlowLogger : AdFlowLogger {
    override fun log(placementId: String, adType: AdType, event: AdFlowEvent, detail: String?) {}
}

/**
 * Khai báo toàn bộ placement 1 lần qua [initialize] (mirror `AdFlow.initialize {}` native v2),
 * cộng các thao tác toàn cục (setAdsEnabled/consent/revenue logger). Thao tác theo từng placement
 * (load/reload/show) nằm ở [AdHostApiImpl].
 */
class AdFlowHostApiImpl(
    private val state: FlutterBridgeState,
    private val flutterApi: AdFlowFlutterApi,
    private val nativeAdRenderers: Map<String, NativeAdRenderer>,
) : AdFlowHostApi {

    private var revenueLoggerRegistered = false

    // Scope riêng để collect StateFlow<AdState> của mọi placement và đẩy sang Dart qua onAdState -
    // sống theo vòng đời tiến trình (giống AdFlow.initialize() chỉ gọi 1 lần/process), không cần
    // huỷ khi engine detach vì app Flutter Android không tách rời process app.
    private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun initialize(
        placements: List<PPlacementConfig>,
        showIntervalConfig: PShowIntervalConfig,
        useLogcatLogger: Boolean,
        consentDebugGeography: PDebugGeography?,
        consentDebugTestDeviceHashedIds: List<String>,
    ) {
        AdFlow.initialize(state.application) {
            network = AdMobNetwork()
            logger = if (useLogcatLogger) LogcatAdFlowLogger() else NoOpAdFlowLogger

            showIntervals {
                interstitialAfterInterstitial = showIntervalConfig.interstitialAfterInterstitialMs.milliseconds
                appOpenAfterAppOpen = showIntervalConfig.appOpenAfterAppOpenMs.milliseconds
                interstitialAfterAppOpen = showIntervalConfig.interstitialAfterAppOpenMs.milliseconds
                appOpenAfterInterstitial = showIntervalConfig.appOpenAfterInterstitialMs.milliseconds
            }

            // consentDebug{} của native v2 chỉ cấu hình được ở đây (lúc initialize()), không nhận
            // theo từng lệnh requestConsentIfNeeded() như AdMobConsentManager v1 - Dart phải truyền
            // debug config ngay lúc AdFlow.initialize() nếu cần test flow EEA.
            if (consentDebugGeography != null) {
                consentDebug {
                    geography = consentDebugGeography.toCore()
                    testDeviceHashedIds = consentDebugTestDeviceHashedIds
                }
            }

            placements.forEach { p ->
                state.placementTypes[p.placementId] = p.adType
                when (p.adType) {
                    PAdType.INTERSTITIAL -> interstitial(p.placementId) {
                        applyBase(this, p)
                        p.expiryMs?.let { expiry = it.milliseconds }
                    }
                    PAdType.REWARDED -> rewarded(p.placementId) {
                        applyBase(this, p)
                        p.expiryMs?.let { expiry = it.milliseconds }
                    }
                    PAdType.APP_OPEN -> appOpen(p.placementId) {
                        applyBase(this, p)
                        p.expiryMs?.let { expiry = it.milliseconds }
                        autoShowOnForeground = p.autoShowOnForeground
                    }
                    PAdType.NATIVE -> native(p.placementId) {
                        applyBase(this, p)
                        p.expiryMs?.let { expiry = it.milliseconds }
                        renderer = resolveRenderer(p.rendererId, nativeAdRenderers)
                    }
                    PAdType.BANNER -> banner(p.placementId) {
                        adUnits(*p.adUnitIds.toTypedArray())
                        preload = p.preload
                        retryPolicy = p.retryPolicy.toCore()
                        size = p.bannerSize?.toCore() ?: BannerSize.ADAPTIVE
                        loadWhen { state.adsEnabled }
                        showWhen { state.adsEnabled }
                    }
                }
            }
        }

        subscribeStates(placements)
    }

    private fun applyBase(scope: BasePlacementScope, config: PPlacementConfig) {
        scope.adUnits(*config.adUnitIds.toTypedArray())
        scope.preload = config.preload
        scope.retryPolicy = config.retryPolicy.toCore()
        // Thay cho `enabled` đã bỏ ở native v2 - AdFlowHostApi.setAdsEnabled() toàn cục đọc field
        // này thay vì per-placement setEnabled() như v1.
        scope.loadWhen { state.adsEnabled }
        scope.showWhen { state.adsEnabled }
    }

    // AdFlow.initialize() đăng ký controller cho mọi placement ĐỒNG BỘ trước khi trả về (xem
    // AdFlow.kt) - gọi AdFlow.<type>(id) ngay sau đó luôn an toàn, không có race.
    private fun subscribeStates(placements: List<PPlacementConfig>) {
        placements.forEach { p ->
            stateScope.launch {
                stateFlowFor(p.placementId, p.adType).collect { s ->
                    flutterApi.onAdState(p.placementId, s.toPigeon()) {}
                }
            }
        }
    }

    private fun stateFlowFor(placementId: String, adType: PAdType): StateFlow<AdState> =
        when (adType) {
            PAdType.INTERSTITIAL -> AdFlow.interstitial(placementId).state
            PAdType.APP_OPEN -> AdFlow.appOpen(placementId).state
            PAdType.REWARDED -> AdFlow.rewarded(placementId).state
            PAdType.BANNER -> AdFlow.banner(placementId).state
            PAdType.NATIVE -> AdFlow.native(placementId).state
        }

    override fun setAdsEnabled(enabled: Boolean) {
        val reopening = enabled && !state.adsEnabled
        state.adsEnabled = enabled
        // Demand-driven: bật lại sau khi tắt tự kích load() cho mọi placement đã biết - coi việc
        // bật ads lại là 1 tín hiệu nhu cầu mới, không chờ view attach/show() tự self-heal.
        if (reopening) {
            state.placementTypes.forEach { (placementId, adType) ->
                when (adType) {
                    PAdType.INTERSTITIAL -> AdFlow.interstitial(placementId).load()
                    PAdType.APP_OPEN -> AdFlow.appOpen(placementId).load()
                    PAdType.REWARDED -> AdFlow.rewarded(placementId).load()
                    PAdType.BANNER -> AdFlow.banner(placementId).load()
                    PAdType.NATIVE -> AdFlow.native(placementId).load()
                }
            }
        }
    }

    override fun addRevenueLogger() {
        if (revenueLoggerRegistered) return
        revenueLoggerRegistered = true
        AdFlow.addRevenueLogger(RevenueLoggerBridge(flutterApi))
    }

    override fun getConsentStatus(): PConsentStatus = AdFlow.consent.status.value.toPigeon()

    override fun getPrivacyOptionsRequirement(): PPrivacyOptionsRequirement =
        AdFlow.consent.privacyOptionsRequirement.toPigeon()

    override fun canRequestAds(): Boolean = AdFlow.consent.canRequestAds()

    override fun requestConsentIfNeeded(callback: (Result<PAdFlowError?>) -> Unit) {
        val activity = state.currentActivity
        if (activity == null) {
            callback(Result.success(AdFlowError(-4, "no activity attached").toPigeon()))
            return
        }
        AdFlow.consent.requestIfNeeded(activity) { error -> callback(Result.success(error?.toPigeon())) }
    }

    override fun showPrivacyOptionsForm(callback: (Result<PAdFlowError?>) -> Unit) {
        val activity = state.currentActivity
        if (activity == null) {
            callback(Result.success(AdFlowError(-4, "no activity attached").toPigeon()))
            return
        }
        AdFlow.consent.showPrivacyOptionsForm(activity) { error -> callback(Result.success(error?.toPigeon())) }
    }
}
