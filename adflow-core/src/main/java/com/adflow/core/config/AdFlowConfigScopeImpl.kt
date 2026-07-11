package com.adflow.core.config

import com.adflow.core.AdType
import com.adflow.core.banner.BannerSize
import com.adflow.core.consent.ConsentDebugConfig
import com.adflow.core.consent.ConsentDebugGeography
import com.adflow.core.logging.AdFlowLogger
import com.adflow.core.logging.LogcatAdFlowLogger
import com.adflow.core.nativead.NativeAdRenderer
import com.adflow.core.network.AdNetwork
import com.adflow.core.revenue.RevenueLogger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/** Gom toàn bộ cấu hình khai báo trong `AdFlow.initialize { ... }` - `AdFlow.initialize()` đọc
 * lại các list `*Configs` này để tạo controller thật cho từng placement. */
internal class AdFlowConfigScopeImpl : AdFlowConfigScope {
    private var networkOrNull: AdNetwork? = null
    override var network: AdNetwork
        get() = networkOrNull ?: error("AdFlow.initialize { } requires `network = ...` (vd AdMobNetwork())")
        set(value) { networkOrNull = value }

    override var logger: AdFlowLogger = LogcatAdFlowLogger()
    override var preloadOnFirstForeground: Boolean = true

    var showIntervalConfig: ShowIntervalConfig = ShowIntervalConfig()
        private set
    var consentDebugConfig: ConsentDebugConfig? = null
        private set

    val revenueLoggers = mutableListOf<RevenueLogger>()

    val interstitialConfigs = mutableListOf<PlacementConfig>()
    val appOpenConfigs = mutableListOf<PlacementConfig>()
    val rewardedConfigs = mutableListOf<PlacementConfig>()
    val bannerConfigs = mutableListOf<PlacementConfig>()
    val nativeConfigs = mutableListOf<PlacementConfig>()

    override fun showIntervals(block: ShowIntervalScope.() -> Unit) {
        val scope = ShowIntervalScopeImpl()
        scope.block()
        showIntervalConfig = scope.build()
    }

    override fun consentDebug(block: ConsentDebugScope.() -> Unit) {
        val scope = ConsentDebugScopeImpl()
        scope.block()
        consentDebugConfig = scope.build()
    }

    override fun revenueLogger(logger: RevenueLogger) {
        revenueLoggers += logger
    }

    override fun interstitial(placementId: String, block: PlacementScope.() -> Unit) {
        val scope = PlacementScopeImpl()
        scope.block()
        interstitialConfigs += scope.build(placementId, AdType.INTERSTITIAL)
    }

    override fun appOpen(placementId: String, block: AppOpenPlacementScope.() -> Unit) {
        val scope = AppOpenPlacementScopeImpl()
        scope.block()
        appOpenConfigs += scope.build(placementId)
    }

    override fun rewarded(placementId: String, block: PlacementScope.() -> Unit) {
        val scope = PlacementScopeImpl()
        scope.block()
        rewardedConfigs += scope.build(placementId, AdType.REWARDED)
    }

    override fun banner(placementId: String, block: BannerPlacementScope.() -> Unit) {
        val scope = BannerPlacementScopeImpl()
        scope.block()
        bannerConfigs += scope.build(placementId)
    }

    override fun native(placementId: String, block: NativePlacementScope.() -> Unit) {
        val scope = NativePlacementScopeImpl()
        scope.block()
        nativeConfigs += scope.build(placementId)
    }
}

private class PlacementScopeImpl : PlacementScope {
    private var adUnitIds: List<String> = emptyList()
    override var preload: Boolean = true
    override var expiry: Duration = 4.hours
    override var retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
    private var loadRule: AdRule? = null
    private var showRule: AdRule? = null

    override fun adUnits(vararg ids: String) { adUnitIds = ids.toList() }
    override fun loadWhen(rule: AdRule) { loadRule = rule }
    override fun showWhen(rule: AdRule) { showRule = rule }

    fun build(placementId: String, adType: AdType) = PlacementConfig(
        placementId = placementId,
        adType = adType,
        adUnitIds = adUnitIds,
        preload = preload,
        expiryMs = expiry.inWholeMilliseconds,
        retryPolicy = retryPolicy,
        loadRule = loadRule,
        showRule = showRule,
    )
}

private class AppOpenPlacementScopeImpl : AppOpenPlacementScope {
    private var adUnitIds: List<String> = emptyList()
    override var preload: Boolean = true
    override var expiry: Duration = 4.hours
    override var retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
    override var autoShowOnForeground: Boolean = false
    private var loadRule: AdRule? = null
    private var showRule: AdRule? = null

    override fun adUnits(vararg ids: String) { adUnitIds = ids.toList() }
    override fun loadWhen(rule: AdRule) { loadRule = rule }
    override fun showWhen(rule: AdRule) { showRule = rule }

    fun build(placementId: String) = PlacementConfig(
        placementId = placementId,
        adType = AdType.APP_OPEN,
        adUnitIds = adUnitIds,
        preload = preload,
        expiryMs = expiry.inWholeMilliseconds,
        retryPolicy = retryPolicy,
        loadRule = loadRule,
        showRule = showRule,
        autoShowOnForeground = autoShowOnForeground,
    )
}

private class BannerPlacementScopeImpl : BannerPlacementScope {
    private var adUnitIds: List<String> = emptyList()
    override var preload: Boolean = true
    override var retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
    override var size: BannerSize = BannerSize.ADAPTIVE
    private var loadRule: AdRule? = null
    private var showRule: AdRule? = null

    override fun adUnits(vararg ids: String) { adUnitIds = ids.toList() }
    override fun loadWhen(rule: AdRule) { loadRule = rule }
    override fun showWhen(rule: AdRule) { showRule = rule }

    fun build(placementId: String) = PlacementConfig(
        placementId = placementId,
        adType = AdType.BANNER,
        adUnitIds = adUnitIds,
        preload = preload,
        expiryMs = null,
        retryPolicy = retryPolicy,
        loadRule = loadRule,
        showRule = showRule,
        bannerSize = size,
    )
}

private class NativePlacementScopeImpl : NativePlacementScope {
    private var adUnitIds: List<String> = emptyList()
    override var preload: Boolean = true
    override var expiry: Duration = 4.hours
    override var retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
    override var renderer: NativeAdRenderer? = null
    private var loadRule: AdRule? = null
    private var showRule: AdRule? = null

    override fun adUnits(vararg ids: String) { adUnitIds = ids.toList() }
    override fun loadWhen(rule: AdRule) { loadRule = rule }
    override fun showWhen(rule: AdRule) { showRule = rule }

    fun build(placementId: String) = PlacementConfig(
        placementId = placementId,
        adType = AdType.NATIVE,
        adUnitIds = adUnitIds,
        preload = preload,
        expiryMs = expiry.inWholeMilliseconds,
        retryPolicy = retryPolicy,
        loadRule = loadRule,
        showRule = showRule,
        defaultRenderer = renderer,
    )
}

private class ShowIntervalScopeImpl : ShowIntervalScope {
    private val defaults = ShowIntervalConfig()
    override var interstitialAfterInterstitial: Duration = defaults.interstitialAfterInterstitialMs.milliseconds
    override var appOpenAfterAppOpen: Duration = defaults.appOpenAfterAppOpenMs.milliseconds
    override var interstitialAfterAppOpen: Duration = defaults.interstitialAfterAppOpenMs.milliseconds
    override var appOpenAfterInterstitial: Duration = defaults.appOpenAfterInterstitialMs.milliseconds

    fun build() = ShowIntervalConfig(
        interstitialAfterInterstitialMs = interstitialAfterInterstitial.inWholeMilliseconds,
        appOpenAfterAppOpenMs = appOpenAfterAppOpen.inWholeMilliseconds,
        interstitialAfterAppOpenMs = interstitialAfterAppOpen.inWholeMilliseconds,
        appOpenAfterInterstitialMs = appOpenAfterInterstitial.inWholeMilliseconds,
    )
}

private class ConsentDebugScopeImpl : ConsentDebugScope {
    override var geography: ConsentDebugGeography = ConsentDebugGeography.DISABLED
    override var testDeviceHashedIds: List<String> = emptyList()

    fun build() = ConsentDebugConfig(geography = geography, testDeviceHashedIds = testDeviceHashedIds)
}
