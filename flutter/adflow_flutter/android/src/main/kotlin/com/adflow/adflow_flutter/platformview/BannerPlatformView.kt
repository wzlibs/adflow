package com.adflow.adflow_flutter.platformview

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.adflow.adflow_flutter.PlacementRegistry
import com.adflow.core.BannerAdManager
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

/**
 * `creationParams` (StandardMessageCodec) chỉ mang `placementId: String` - primitive duy nhất
 * truyền được qua PlatformView creation params. Factory tra [PlacementRegistry.banners] (đã được
 * `BannerAdHostApi.create()` đăng ký từ trước khi Dart dựng widget) để lấy [BannerAdManager] thật
 * rồi gọi `getView()`.
 */
class BannerPlatformViewFactory(private val registry: PlacementRegistry) :
    PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val placementId = (args as? Map<*, *>)?.get("placementId") as? String
        val manager = placementId?.let { registry.banners[it] }
        return BannerPlatformView(context, manager)
    }
}

private class BannerPlatformView(
    context: Context,
    manager: BannerAdManager?,
) : PlatformView {
    // Fallback rỗng nếu chưa tìm thấy manager (vd load() chưa được gọi/chưa xong) - không crash,
    // Dart facade chỉ nên dựng widget này sau khi await load() thành công (xem AdFlowBannerAdView).
    private val view: View = manager?.getView(context) ?: FrameLayout(context)

    override fun getView(): View = view

    override fun dispose() {}
}
