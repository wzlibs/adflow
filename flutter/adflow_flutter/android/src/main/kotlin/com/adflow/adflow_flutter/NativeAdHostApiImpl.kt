package com.adflow.adflow_flutter

import com.adflow.adflow_flutter.generated.NativeAdHostApi
import com.adflow.adflow_flutter.generated.PLoadResult
import com.adflow.adflow_flutter.generated.PPlacementConfig

/** Không có show()/createView() - [com.adflow.adflow_flutter.platformview.NativePlatformViewFactory]
 * tự tra [PlacementRegistry.natives] theo placementId để lấy View thật khi Flutter dựng AndroidView. */
class NativeAdHostApiImpl(private val registry: PlacementRegistry) : NativeAdHostApi {

    override fun create(config: PPlacementConfig) {
        if (registry.natives.containsKey(config.placementId)) return
        registry.natives[config.placementId] = registry.provider.createNative(config.toCore())
    }

    override fun isReady(placementId: String): Boolean =
        registry.natives[placementId]?.isReady() ?: false

    override fun load(placementId: String, callback: (Result<PLoadResult>) -> Unit) {
        val manager = registry.natives[placementId]
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
