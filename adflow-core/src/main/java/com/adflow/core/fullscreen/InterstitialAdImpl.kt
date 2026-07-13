package com.adflow.core.fullscreen

import android.app.Activity
import com.adflow.core.AdListener
import com.adflow.core.AdState
import com.adflow.core.config.PlacementConfig
import com.adflow.core.engine.AdFlowRuntime
import com.adflow.core.engine.AdLoadEngine
import com.adflow.core.network.AdRequestInfo
import com.adflow.core.network.FullScreenAdSource
import com.adflow.core.network.LoadedFullScreenAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

internal class InterstitialAdImpl(
    override val placementId: String,
    private val config: PlacementConfig,
    source: FullScreenAdSource,
    private val runtime: AdFlowRuntime,
    scope: CoroutineScope,
) : InterstitialAd {

    private val engine = AdLoadEngine<LoadedFullScreenAd>(
        config = config,
        loadOne = { adUnitId ->
            source.load(AdRequestInfo(placementId, config.adType, adUnitId, onRevenue = runtime::dispatchRevenue))
        },
        onDrop = {},
        runtime = runtime,
        scope = scope,
    )

    override val state: StateFlow<AdState> get() = engine.state
    override val isReady: Boolean get() = engine.isReady
    override val canShow: Boolean get() = evaluateShowGate(placementId, config, engine, runtime) == null

    override fun load() = engine.ensureLoaded()
    override fun addListener(listener: AdListener) = engine.addListener(listener)
    override fun removeListener(listener: AdListener) = engine.removeListener(listener)

    override fun show(activity: Activity, callback: FullScreenCallback) {
        showFullScreenAd(
            placementId = placementId,
            config = config,
            engine = engine,
            runtime = runtime,
            activity = activity,
            onShowed = callback::onAdShowed,
            onDismissed = callback::onAdDismissed,
            onFailedToShow = callback::onAdFailedToShow,
            onBlocked = callback::onAdBlocked,
            onImpression = callback::onAdImpression,
            onClicked = callback::onAdClicked,
            onReward = {}, // Interstitial không bao giờ có phần thưởng
        )
    }
}
