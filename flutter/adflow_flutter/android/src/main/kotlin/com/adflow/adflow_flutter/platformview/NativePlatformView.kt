package com.adflow.adflow_flutter.platformview

import android.content.Context
import com.adflow.adflow_flutter.resolveRenderer
import com.adflow.core.nativead.AdFlowNativeAdView
import com.adflow.core.nativead.NativeAdRenderer
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

/**
 * Giống [BannerPlatformViewFactory] nhưng host [AdFlowNativeAdView] - tự resolve controller qua
 * `AdFlow.nativeControllerImpl()`, tự load()/bind()/collapse/rebind (kể cả sau `reload()` từ
 * Dart). `rendererId` trong [args] chỉ override renderer CHO RIÊNG WIDGET NÀY qua
 * [resolveRenderer] - để null thì view dùng renderer mặc định của placement (đã khai trong DSL lúc
 * `AdFlowHostApi.initialize()`, xem `AdFlowHostApiImpl`), không phải luôn ép về Medium.
 */
class NativePlatformViewFactory(
    private val renderers: Map<String, NativeAdRenderer>,
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val map = args as? Map<*, *>
        val placementId = map?.get("placementId") as? String
        val rendererId = map?.get("rendererId") as? String
        return NativePlatformView(context, placementId, rendererId, renderers)
    }
}

private class NativePlatformView(
    context: Context,
    placementId: String?,
    rendererId: String?,
    renderers: Map<String, NativeAdRenderer>,
) : PlatformView {
    private val view = AdFlowNativeAdView(context).apply {
        this.placementId = placementId
        if (rendererId != null) renderer = resolveRenderer(rendererId, renderers)
    }

    override fun getView() = view

    override fun dispose() {}
}
