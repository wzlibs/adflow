package com.adflow.adflow_flutter

import com.adflow.adflow_flutter.generated.BannerAdHostApi
import com.adflow.adflow_flutter.generated.PLoadResult
import com.adflow.adflow_flutter.generated.PPlacementConfig

/** Không có show()/getView() - [com.adflow.adflow_flutter.platformview.BannerPlatformViewFactory]
 * tự tra [PlacementRegistry.banners] theo placementId để lấy View thật khi Flutter dựng AndroidView. */
class BannerAdHostApiImpl(private val registry: PlacementRegistry) : BannerAdHostApi {

    override fun create(config: PPlacementConfig) {
        if (registry.banners.containsKey(config.placementId)) return
        registry.banners[config.placementId] = registry.provider.createBanner(config.toCore())
    }

    override fun isReady(placementId: String): Boolean =
        registry.banners[placementId]?.isReady() ?: false

    override fun load(placementId: String, callback: (Result<PLoadResult>) -> Unit) {
        val manager = registry.banners[placementId]
        if (manager == null) {
            callback(Result.success(PLoadResult(success = false, error = null)))
            return
        }
        manager.load { result -> callback(Result.success(result.toPigeon())) }
    }

    override fun setEnabled(placementId: String, enabled: Boolean) {
        registry.setEnabled(placementId, enabled)
    }
}
