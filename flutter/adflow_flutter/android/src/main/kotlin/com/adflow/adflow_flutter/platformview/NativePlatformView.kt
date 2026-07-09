package com.adflow.adflow_flutter.platformview

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.adflow.admob.nativead.DefaultMediumNativeAdRenderer
import com.adflow.adflow_flutter.PlacementRegistry
import com.adflow.core.NativeAdManager
import com.adflow.core.NativeAdRenderer
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

private const val TAG = "AdFlowFlutter"

/**
 * Giống [BannerPlatformViewFactory] nhưng cho Native ad. Chọn renderer qua `rendererId` trong
 * [args] - resolve bằng [resolveRenderer], khớp với renderer app đã đăng ký qua
 * `AdflowFlutterPlugin.registerNativeAdRenderer()`.
 */
class NativePlatformViewFactory(
    private val registry: PlacementRegistry,
    private val renderers: Map<String, NativeAdRenderer>,
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val map = args as? Map<*, *>
        val placementId = map?.get("placementId") as? String
        val rendererId = map?.get("rendererId") as? String
        val manager = enabledManager(registry, placementId, registry.natives)
        return NativePlatformView(context, manager, resolveRenderer(rendererId, renderers))
    }
}

/**
 * Tách riêng khỏi [NativePlatformViewFactory.create] để test được bằng mock, không cần Robolectric.
 * `rendererId` null hoặc không khớp renderer nào đã đăng ký → fallback [DefaultMediumNativeAdRenderer]
 * (không bao giờ crash app vì thiếu đăng ký, khớp triết lý an toàn/fallback đã dùng cho `reload()`).
 */
internal fun resolveRenderer(rendererId: String?, renderers: Map<String, NativeAdRenderer>): NativeAdRenderer {
    if (rendererId == null) return DefaultMediumNativeAdRenderer()
    return renderers[rendererId] ?: run {
        Log.w(
            TAG,
            "Không tìm thấy NativeAdRenderer đã đăng ký cho rendererId='$rendererId' - dùng " +
                "renderer mặc định. Kiểm tra đã gọi AdflowFlutterPlugin.registerNativeAdRenderer() " +
                "trong MainActivity.configureFlutterEngine() trước khi tạo AdFlowNativeAdView với " +
                "rendererId này chưa.",
        )
        DefaultMediumNativeAdRenderer()
    }
}

private class NativePlatformView(
    context: Context,
    manager: NativeAdManager?,
    renderer: NativeAdRenderer,
) : PlatformView {
    private val view: View = manager?.createView(context, renderer) ?: FrameLayout(context)

    override fun getView(): View = view

    override fun dispose() {}
}
