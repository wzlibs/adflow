package com.adflow.core

import android.app.Application
import android.content.Context
import android.util.Log
import com.adflow.core.banner.BannerAdController
import com.adflow.core.banner.BannerAdControllerImpl
import com.adflow.core.config.AdFlowConfigScope
import com.adflow.core.config.AdFlowConfigScopeImpl
import com.adflow.core.consent.ConsentManager
import com.adflow.core.engine.AdFlowRuntime
import com.adflow.core.engine.PlacementRegistry
import com.adflow.core.fullscreen.AppOpenAd
import com.adflow.core.fullscreen.AppOpenAdImpl
import com.adflow.core.fullscreen.AppOpenForegroundObserver
import com.adflow.core.fullscreen.InterstitialAd
import com.adflow.core.fullscreen.InterstitialAdImpl
import com.adflow.core.nativead.NativeAdController
import com.adflow.core.nativead.NativeAdControllerImpl
import com.adflow.core.revenue.RevenueLogger
import com.adflow.core.rewarded.RewardedAd
import com.adflow.core.rewarded.RewardedAdImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private const val TAG = "AdFlow"

/**
 * Điểm vào duy nhất của thư viện. Gọi [initialize] đúng 1 lần (thường ở `Application.onCreate()`)
 * để đăng ký toàn bộ placement qua DSL, sau đó lấy controller ở bất kỳ đâu bằng [interstitial]/
 * [appOpen]/[rewarded]/[banner]/[native] theo `placementId` đã khai báo.
 *
 * ```kotlin
 * AdFlow.initialize(this) {
 *     network = AdMobNetwork()
 *     interstitial("global_interstitial") { adUnits("ca-app-pub-...") }
 * }
 * // Ở bất kỳ đâu khác:
 * AdFlow.interstitial("global_interstitial").show(activity, callback)
 * ```
 */
object AdFlow {
    private var runtime: AdFlowRuntime? = null
    private var registry: PlacementRegistry? = null
    private var consentManager: ConsentManager? = null
    private var appOpenObservers: List<AppOpenForegroundObserver> = emptyList()

    val isInitialized: Boolean get() = runtime != null

    /** Gọi lần 2 trở đi là no-op (chỉ log cảnh báo) - an toàn nếu `Application.onCreate()` vô
     * tình chạy lại. */
    fun initialize(context: Context, configure: AdFlowConfigScope.() -> Unit) {
        if (isInitialized) {
            Log.w(TAG, "AdFlow.initialize() called more than once - ignoring the second call.")
            return
        }

        val appContext = context.applicationContext
        val scope = AdFlowConfigScopeImpl()
        scope.configure()
        val network = scope.network // throw rõ ràng ngay tại đây nếu app quên set `network = ...`

        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val newRuntime = AdFlowRuntime(
            network = network,
            logger = scope.logger,
            scope = coroutineScope,
            showIntervalConfig = scope.showIntervalConfig,
        )
        scope.revenueLoggers.forEach(newRuntime::addRevenueLogger)
        runtime = newRuntime

        val newRegistry = PlacementRegistry()
        registry = newRegistry

        val preloadActions = mutableListOf<() -> Unit>()
        val observers = mutableListOf<AppOpenForegroundObserver>()

        scope.interstitialConfigs.forEach { config ->
            val ad = InterstitialAdImpl(config.placementId, config, network.interstitialSource(appContext), newRuntime, coroutineScope)
            newRegistry.register(config.placementId, ad)
            if (config.preload) preloadActions += ad::load
        }
        scope.appOpenConfigs.forEach { config ->
            val ad = AppOpenAdImpl(config.placementId, config, network.appOpenSource(appContext), newRuntime, coroutineScope)
            newRegistry.register(config.placementId, ad)
            if (config.preload) preloadActions += ad::load
            if (config.autoShowOnForeground) {
                val application = appContext as? Application
                    ?: error("Placement '${config.placementId}' bật autoShowOnForeground nhưng context truyền vào AdFlow.initialize() không phải Application")
                observers += AppOpenForegroundObserver(application, ad, newRuntime).also { it.start() }
            }
        }
        scope.rewardedConfigs.forEach { config ->
            val ad = RewardedAdImpl(config.placementId, config, network.rewardedSource(appContext), newRuntime, coroutineScope)
            newRegistry.register(config.placementId, ad)
            if (config.preload) preloadActions += ad::load
        }
        scope.bannerConfigs.forEach { config ->
            val controller = BannerAdControllerImpl(config.placementId, config, network.bannerSource(appContext), newRuntime, coroutineScope)
            newRegistry.register(config.placementId, controller)
            if (config.preload) preloadActions += controller::load
        }
        scope.nativeConfigs.forEach { config ->
            val controller = NativeAdControllerImpl(config.placementId, config, network.nativeSource(appContext), newRuntime, coroutineScope)
            newRegistry.register(config.placementId, controller)
            if (config.preload) preloadActions += controller::load
        }
        appOpenObservers = observers

        consentManager = network.createConsentManager(appContext, scope.consentDebugConfig) { allows ->
            newRuntime.consentAllowsAdRequests = allows
        }

        newRuntime.foregroundGate.runOnFirstForeground {
            network.initialize(appContext) {
                if (scope.preloadOnFirstForeground) preloadActions.forEach { it() }
            }
        }
    }

    /** Chạy [action] đúng 1 lần vào lần app thực sự vào foreground đầu tiên - dùng cho việc khởi
     * tạo khác ngoài ads (ví dụ init 1 SDK đo lường khác) mà app muốn gate theo cùng quy tắc. */
    fun onFirstForeground(action: () -> Unit) {
        runtimeOrThrow().foregroundGate.runOnFirstForeground(action)
    }

    fun interstitial(placementId: String): InterstitialAd = registryOrThrow().get(placementId)
    fun appOpen(placementId: String): AppOpenAd = registryOrThrow().get(placementId)
    fun rewarded(placementId: String): RewardedAd = registryOrThrow().get(placementId)
    fun banner(placementId: String): BannerAdController = registryOrThrow().get(placementId)
    fun native(placementId: String): NativeAdController = registryOrThrow().get(placementId)

    /** Cho `AdFlowBannerView` (module này) đọc controller cụ thể để lease()/release() - không
     * thuộc API public, [banner] ở trên mới là API app dùng. */
    internal fun bannerControllerImpl(placementId: String): BannerAdControllerImpl = registryOrThrow().get(placementId)

    /** Cho `AdFlowNativeAdView` (module này) đọc controller cụ thể để bind()/unbind() - không
     * thuộc API public, [native] ở trên mới là API app dùng. */
    internal fun nativeControllerImpl(placementId: String): NativeAdControllerImpl = registryOrThrow().get(placementId)

    val consent: ConsentManager
        get() = consentManager ?: error("AdFlow.consent chỉ dùng được sau khi AdFlow.initialize() đã chạy")

    fun addRevenueLogger(logger: RevenueLogger) = runtimeOrThrow().addRevenueLogger(logger)
    fun removeRevenueLogger(logger: RevenueLogger) = runtimeOrThrow().removeRevenueLogger(logger)

    private fun runtimeOrThrow(): AdFlowRuntime =
        runtime ?: error("AdFlow.initialize() chưa được gọi")

    private fun registryOrThrow(): PlacementRegistry =
        registry ?: error("AdFlow.initialize() chưa được gọi")

    /** CHỈ dùng cho test - xóa toàn bộ state để mỗi test class có 1 [AdFlowRuntime] sạch, không
     * dùng trong code app thật. */
    fun resetForTesting() {
        appOpenObservers.forEach { it.stop() }
        appOpenObservers = emptyList()
        runtime = null
        registry = null
        consentManager = null
    }
}
