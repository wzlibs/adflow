package com.adflow.adflow_flutter

import android.app.Activity
import com.adflow.adflow_flutter.generated.AdFlowCoreHostApi
import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.AppOpenAdHostApi
import com.adflow.adflow_flutter.generated.BannerAdHostApi
import com.adflow.adflow_flutter.generated.InterstitialAdHostApi
import com.adflow.adflow_flutter.generated.NativeAdHostApi
import com.adflow.adflow_flutter.generated.RewardedAdHostApi
import com.adflow.adflow_flutter.platformview.BannerPlatformViewFactory
import com.adflow.adflow_flutter.platformview.NativePlatformViewFactory
import com.adflow.core.NativeAdRenderer
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding

private const val BANNER_VIEW_TYPE = "adflow/banner_ad_view"
private const val NATIVE_VIEW_TYPE = "adflow/native_ad_view"

/**
 * Entry point Android của AdFlow Flutter plugin. `show()` của mọi ad type cần 1 [Activity] hiện
 * tại - [ActivityAware] theo dõi Activity nào đang gắn với Flutter engine để cung cấp nó qua
 * [PlacementRegistry.currentActivity], độc lập với việc Flutter engine có bị detach/reattach (xoay
 * màn hình, multi-window...) hay không.
 */
class AdflowFlutterPlugin : FlutterPlugin, ActivityAware {

    private var registry: PlacementRegistry? = null

    /** Renderer Kotlin do app đăng ký qua [registerNativeAdRenderer], khoá theo `rendererId` -
     * độc lập với `placementId`, cho phép nhiều native ad placement dùng nhiều UI khác nhau trong
     * cùng 1 app. Instance property (không phải trong companion) vì mỗi [FlutterEngine] có 1
     * [AdflowFlutterPlugin] riêng - an toàn khi app chạy nhiều engine cùng lúc (add-to-app). */
    private val nativeAdRenderers = mutableMapOf<String, NativeAdRenderer>()

    companion object {
        /** Đăng ký 1 [NativeAdRenderer] Kotlin app tự viết, chọn được từ Dart qua tham số
         * `rendererId` của `AdFlowNativeAdView`. Gọi trong `MainActivity.configureFlutterEngine()`,
         * sau `super.configureFlutterEngine(flutterEngine)` (để plugin đã kịp [onAttachedToEngine]). */
        fun registerNativeAdRenderer(flutterEngine: FlutterEngine, rendererId: String, renderer: NativeAdRenderer) {
            val plugin = flutterEngine.plugins.get(AdflowFlutterPlugin::class.java) as? AdflowFlutterPlugin
                ?: throw IllegalStateException(
                    "AdflowFlutterPlugin chưa được đăng ký với FlutterEngine này - gọi " +
                        "registerNativeAdRenderer() sau super.configureFlutterEngine(flutterEngine)",
                )
            plugin.nativeAdRenderers[rendererId] = renderer
        }

        /** Gỡ đăng ký - tùy chọn, dọn dẹp khi 1 renderer không còn cần dùng nữa. */
        fun unregisterNativeAdRenderer(flutterEngine: FlutterEngine, rendererId: String) {
            val plugin = flutterEngine.plugins.get(AdflowFlutterPlugin::class.java) as? AdflowFlutterPlugin
            plugin?.nativeAdRenderers?.remove(rendererId)
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        val registry = PlacementRegistry(binding.applicationContext)
        this.registry = registry

        val flutterApi = AdFlowFlutterApi(binding.binaryMessenger)

        AdFlowCoreHostApi.setUp(binding.binaryMessenger, AdFlowCoreHostApiImpl(registry, flutterApi))
        InterstitialAdHostApi.setUp(binding.binaryMessenger, InterstitialAdHostApiImpl(registry, flutterApi))
        AppOpenAdHostApi.setUp(binding.binaryMessenger, AppOpenAdHostApiImpl(registry, flutterApi))
        RewardedAdHostApi.setUp(binding.binaryMessenger, RewardedAdHostApiImpl(registry, flutterApi))
        BannerAdHostApi.setUp(binding.binaryMessenger, BannerAdHostApiImpl(registry))
        NativeAdHostApi.setUp(binding.binaryMessenger, NativeAdHostApiImpl(registry))

        binding.platformViewRegistry.registerViewFactory(
            BANNER_VIEW_TYPE,
            BannerPlatformViewFactory(registry, flutterApi),
        )
        binding.platformViewRegistry.registerViewFactory(
            NATIVE_VIEW_TYPE,
            NativePlatformViewFactory(registry, nativeAdRenderers, flutterApi),
        )
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        // Pigeon setUp(binaryMessenger, null) để gỡ đăng ký sạch sẽ khi engine detach.
        AdFlowCoreHostApi.setUp(binding.binaryMessenger, null)
        InterstitialAdHostApi.setUp(binding.binaryMessenger, null)
        AppOpenAdHostApi.setUp(binding.binaryMessenger, null)
        RewardedAdHostApi.setUp(binding.binaryMessenger, null)
        BannerAdHostApi.setUp(binding.binaryMessenger, null)
        NativeAdHostApi.setUp(binding.binaryMessenger, null)
        registry = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        registry?.currentActivity = binding.activity
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        registry?.currentActivity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        registry?.currentActivity = null
    }

    override fun onDetachedFromActivity() {
        registry?.currentActivity = null
    }
}
