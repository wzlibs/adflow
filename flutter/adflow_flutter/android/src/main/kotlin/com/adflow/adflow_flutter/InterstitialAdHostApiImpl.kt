package com.adflow.adflow_flutter

import com.adflow.adflow_flutter.callbacks.ShowCallbackBridge
import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.InterstitialAdHostApi
import com.adflow.adflow_flutter.generated.PLoadResult
import com.adflow.adflow_flutter.generated.PPlacementConfig

class InterstitialAdHostApiImpl(
    private val registry: PlacementRegistry,
    private val flutterApi: AdFlowFlutterApi,
) : InterstitialAdHostApi {

    override fun create(config: PPlacementConfig) {
        // Idempotent - Dart facade constructor có thể gọi create() nhiều lần (hot reload, tạo lại
        // instance Dart cho cùng placementId); không recreate manager thật.
        if (registry.interstitials.containsKey(config.placementId)) return
        registry.interstitials[config.placementId] = registry.provider.createInterstitial(config.toCore())
    }

    override fun isReady(placementId: String): Boolean =
        registry.interstitials[placementId]?.isReady() ?: false

    override fun load(placementId: String, callback: (Result<PLoadResult>) -> Unit) {
        val manager = registry.interstitials[placementId]
        if (manager == null) {
            callback(Result.success(PLoadResult(success = false, error = null)))
            return
        }
        manager.load { result -> callback(Result.success(result.toPigeon())) }
    }

    override fun show(placementId: String) {
        val manager = registry.interstitials[placementId]
        showGated(registry, flutterApi, placementId, manager != null) { activity ->
            manager!!.show(activity, ShowCallbackBridge(placementId, flutterApi))
        }
    }

    override fun setEnabled(placementId: String, enabled: Boolean) {
        registry.setEnabled(placementId, enabled)
    }
}
