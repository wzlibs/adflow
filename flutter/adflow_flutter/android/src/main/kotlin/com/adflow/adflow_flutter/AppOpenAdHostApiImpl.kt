package com.adflow.adflow_flutter

import com.adflow.adflow_flutter.callbacks.ShowCallbackBridge
import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.AppOpenAdHostApi
import com.adflow.adflow_flutter.generated.PLoadResult
import com.adflow.adflow_flutter.generated.PPlacementConfig
import com.adflow.core.AppOpenAdController

class AppOpenAdHostApiImpl(
    private val registry: PlacementRegistry,
    private val flutterApi: AdFlowFlutterApi,
) : AppOpenAdHostApi {

    override fun create(config: PPlacementConfig) {
        if (registry.appOpens.containsKey(config.placementId)) return
        registry.appOpens[config.placementId] = registry.provider.createAppOpen(config.toCore())
    }

    override fun isReady(placementId: String): Boolean =
        registry.appOpens[placementId]?.isReady() ?: false

    override fun load(placementId: String, callback: (Result<PLoadResult>) -> Unit) {
        val manager = registry.appOpens[placementId]
        if (manager == null) {
            callback(Result.success(PLoadResult(success = false, error = null)))
            return
        }
        manager.load { result -> callback(Result.success(result.toPigeon())) }
    }

    override fun show(placementId: String) {
        val manager = registry.appOpens[placementId]
        showGated(registry, flutterApi, placementId, manager != null) { activity ->
            manager!!.show(activity, ShowCallbackBridge(placementId, flutterApi))
        }
    }

    override fun setEnabled(placementId: String, enabled: Boolean) {
        registry.setEnabled(placementId, enabled)
    }

    override fun startAutoShowOnForeground(placementId: String) {
        val manager = registry.appOpens[placementId] ?: return
        registry.appOpenControllers.getOrPut(placementId) {
            AppOpenAdController(registry.application, manager)
        }.start()
    }

    override fun stopAutoShowOnForeground(placementId: String) {
        registry.appOpenControllers[placementId]?.stop()
    }
}
