package com.adflow.adflow_flutter

import com.adflow.adflow_flutter.callbacks.RewardedAdCallbackBridge
import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.PLoadResult
import com.adflow.adflow_flutter.generated.PPlacementConfig
import com.adflow.adflow_flutter.generated.RewardedAdHostApi

class RewardedAdHostApiImpl(
    private val registry: PlacementRegistry,
    private val flutterApi: AdFlowFlutterApi,
) : RewardedAdHostApi {

    override fun create(config: PPlacementConfig) {
        if (registry.rewardeds.containsKey(config.placementId)) return
        registry.rewardeds[config.placementId] = registry.provider.createRewarded(config.toCore())
    }

    override fun isReady(placementId: String): Boolean =
        registry.rewardeds[placementId]?.isReady() ?: false

    override fun load(placementId: String, callback: (Result<PLoadResult>) -> Unit) {
        val manager = registry.rewardeds[placementId]
        if (manager == null) {
            callback(Result.success(PLoadResult(success = false, error = null)))
            return
        }
        manager.load { result -> callback(Result.success(result.toPigeon())) }
    }

    override fun show(placementId: String) {
        val manager = registry.rewardeds[placementId]
        showGated(registry, flutterApi, placementId, manager != null) { activity ->
            manager!!.show(activity, RewardedAdCallbackBridge(placementId, flutterApi))
        }
    }

    override fun setEnabled(placementId: String, enabled: Boolean) {
        registry.setEnabled(placementId, enabled)
    }
}
