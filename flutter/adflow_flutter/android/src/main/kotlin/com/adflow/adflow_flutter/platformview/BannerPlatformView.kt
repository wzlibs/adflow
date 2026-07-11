package com.adflow.adflow_flutter.platformview

import android.content.Context
import com.adflow.core.banner.AdFlowBannerView
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

/**
 * `creationParams` (StandardMessageCodec) chỉ mang `placementId: String` - primitive duy nhất
 * truyền được qua PlatformView creation params. Host thẳng [AdFlowBannerView] (adflow-core) - view
 * này tự resolve controller qua `AdFlow.bannerControllerImpl()`, tự load()/lease()/collapse/rebind
 * khi attach vào window, không cần factory tra cứu manager hay forward showBlocked như v1.
 */
class BannerPlatformViewFactory : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val placementId = (args as? Map<*, *>)?.get("placementId") as? String
        return BannerPlatformView(context, placementId)
    }
}

private class BannerPlatformView(context: Context, placementId: String?) : PlatformView {
    private val view = AdFlowBannerView(context).apply { this.placementId = placementId }

    override fun getView() = view

    override fun dispose() {}
}
