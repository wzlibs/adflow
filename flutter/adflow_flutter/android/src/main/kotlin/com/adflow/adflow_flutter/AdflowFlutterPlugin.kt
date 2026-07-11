package com.adflow.adflow_flutter

import com.adflow.admob.nativead.DefaultMediumNativeAdRenderer
import com.adflow.admob.nativead.DefaultSmallNativeAdRenderer
import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.AdFlowHostApi
import com.adflow.adflow_flutter.generated.AdHostApi
import com.adflow.adflow_flutter.platformview.BannerPlatformViewFactory
import com.adflow.adflow_flutter.platformview.NativePlatformViewFactory
import com.adflow.core.nativead.NativeAdRenderer
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding

private const val BANNER_VIEW_TYPE = "adflow/banner_ad_view"
private const val NATIVE_VIEW_TYPE = "adflow/native_ad_view"

/**
 * Entry point Android của AdFlow Flutter plugin. `show()`/consent cần 1 [android.app.Activity]
 * hiện tại - [ActivityAware] theo dõi Activity nào đang gắn với Flutter engine để cung cấp nó qua
 * [FlutterBridgeState.currentActivity], độc lập với việc Flutter engine có bị detach/reattach (xoay
 * màn hình, multi-window...) hay không.
 */
class AdflowFlutterPlugin : FlutterPlugin, ActivityAware {

    private var state: FlutterBridgeState? = null

    /** Renderer Kotlin do app đăng ký qua [registerNativeAdRenderer], khoá theo `rendererId` -
     * độc lập với `placementId`, cho phép nhiều native ad placement dùng nhiều UI khác nhau trong
     * cùng 1 app. Instance property (không phải trong companion) vì mỗi [FlutterEngine] có 1
     * [AdflowFlutterPlugin] riêng - an toàn khi app chạy nhiều engine cùng lúc (add-to-app). Đã
     * sẵn "medium"/"small" trỏ tới 2 renderer dựng sẵn của adflow-admob - app có thể ghi đè nếu
     * muốn 1 giao diện khác cho id này. */
    private val nativeAdRenderers = mutableMapOf<String, NativeAdRenderer>(
        "medium" to DefaultMediumNativeAdRenderer(),
        "small" to DefaultSmallNativeAdRenderer(),
    )

    companion object {
        /** Đăng ký 1 [NativeAdRenderer] Kotlin app tự viết, chọn được từ Dart qua tham số
         * `rendererId` của `NativePlacement`/`AdFlowNative`. Gọi trong
         * `MainActivity.configureFlutterEngine()`, sau `super.configureFlutterEngine(flutterEngine)`
         * (để plugin đã kịp [onAttachedToEngine]). */
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
        val newState = FlutterBridgeState(binding.applicationContext)
        this.state = newState

        val flutterApi = AdFlowFlutterApi(binding.binaryMessenger)

        AdFlowHostApi.setUp(binding.binaryMessenger, AdFlowHostApiImpl(newState, flutterApi, nativeAdRenderers))
        AdHostApi.setUp(binding.binaryMessenger, AdHostApiImpl(newState, flutterApi))

        binding.platformViewRegistry.registerViewFactory(BANNER_VIEW_TYPE, BannerPlatformViewFactory())
        binding.platformViewRegistry.registerViewFactory(NATIVE_VIEW_TYPE, NativePlatformViewFactory(nativeAdRenderers))
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        // Pigeon setUp(binaryMessenger, null) để gỡ đăng ký sạch sẽ khi engine detach.
        AdFlowHostApi.setUp(binding.binaryMessenger, null)
        AdHostApi.setUp(binding.binaryMessenger, null)
        state = null
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        state?.currentActivity = binding.activity
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        state?.currentActivity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        state?.currentActivity = null
    }

    override fun onDetachedFromActivity() {
        state?.currentActivity = null
    }
}
