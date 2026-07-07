package com.adflow.adflow_flutter

import android.app.Activity
import android.app.Application
import android.content.Context
import com.adflow.admob.AdMobProvider
import com.adflow.core.AdNetworkProvider
import com.adflow.core.AppOpenAdController
import com.adflow.core.AppOpenAdManager
import com.adflow.core.BannerAdManager
import com.adflow.core.InterstitialAdManager
import com.adflow.core.NativeAdManager
import com.adflow.core.RewardedAdManager

/**
 * Giữ instance thật của mọi placement do Flutter tạo, khoá theo placementId - phía Dart chỉ giữ 1
 * "handle" nhẹ (placementId + các lệnh gọi HostApi), toàn bộ state ad thật nằm ở đây, sống suốt
 * vòng đời process giống pattern DemoAdPlacements.kt bên native thuần.
 */
class PlacementRegistry(context: Context) {
    val application: Application = context.applicationContext as Application

    val provider: AdNetworkProvider by lazy { AdMobProvider(application) }

    /** Activity hiện đang gắn với Flutter engine - cập nhật bởi AdflowFlutterPlugin (ActivityAware).
     * show() cần Activity thật; null nghĩa là chưa có Activity nào attach (quá sớm, hoặc Flutter
     * engine chạy headless). */
    var currentActivity: Activity? = null

    val interstitials = mutableMapOf<String, InterstitialAdManager>()
    val appOpens = mutableMapOf<String, AppOpenAdManager>()
    val appOpenControllers = mutableMapOf<String, AppOpenAdController>()
    val rewardeds = mutableMapOf<String, RewardedAdManager>()
    val banners = mutableMapOf<String, BannerAdManager>()
    val natives = mutableMapOf<String, NativeAdManager>()

    /**
     * Override bật/tắt runtime độc lập với `PlacementConfig.enabled` bất biến lúc tạo - dùng thay
     * cho AdRule (loadRule/showRule không bridge qua channel được, xem pigeons/adflow_api.dart).
     * Mặc định allow (true) cho tới khi Dart gọi setEnabled(placementId, false).
     */
    private val enabledOverrides = mutableMapOf<String, Boolean>()

    fun isEnabled(placementId: String): Boolean = enabledOverrides[placementId] ?: true

    fun setEnabled(placementId: String, enabled: Boolean) {
        enabledOverrides[placementId] = enabled
    }
}
