package com.adflow.adflow_flutter.platformview

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.adflow.admob.nativead.DefaultMediumNativeAdRenderer
import com.adflow.adflow_flutter.PlacementRegistry
import com.adflow.core.NativeAdManager
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

/**
 * Giống [BannerPlatformViewFactory] nhưng cho Native ad. Dùng cứng [DefaultMediumNativeAdRenderer]
 * cho v1 - custom renderer không expose qua Dart (giới hạn đã biết, xem README phần "Rủi ro/giới
 * hạn": NativeAdRenderer nhận View Kotlin thật, không mô tả được bằng Dart widget tree).
 */
class NativePlatformViewFactory(private val registry: PlacementRegistry) :
    PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val placementId = (args as? Map<*, *>)?.get("placementId") as? String
        val manager = placementId?.let { registry.natives[it] }
        return NativePlatformView(context, manager)
    }
}

private class NativePlatformView(
    context: Context,
    manager: NativeAdManager?,
) : PlatformView {
    private val view: View = manager?.createView(context, DefaultMediumNativeAdRenderer()) ?: FrameLayout(context)

    override fun getView(): View = view

    override fun dispose() {}
}
