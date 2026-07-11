@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.adflow.core.fullscreen

import android.app.Activity
import android.content.Context
import com.adflow.core.consent.ConsentDebugConfig
import com.adflow.core.consent.ConsentManager
import com.adflow.core.engine.AdFlowRuntime
import com.adflow.core.logging.AdFlowLogger
import com.adflow.core.network.AdNetwork
import com.adflow.core.network.AdRequestInfo
import com.adflow.core.network.BannerAdSource
import com.adflow.core.network.FullScreenAdSource
import com.adflow.core.network.LoadedFullScreenAd
import com.adflow.core.network.NativeAdSource
import kotlinx.coroutines.test.TestScope

/** Fake dùng chung cho test của InterstitialAdImpl/AppOpenAdImpl/RewardedAdImpl/
 * AppOpenForegroundObserver - internal nên chỉ dùng được trong module này. */
internal class FakeLoadedFullScreenAd(
    val onShow: (Activity, LoadedFullScreenAd.ShowListener) -> Unit,
) : LoadedFullScreenAd {
    override fun show(activity: Activity, listener: LoadedFullScreenAd.ShowListener) = onShow(activity, listener)
}

internal class FakeFullScreenAdSource(
    private val provide: suspend (adUnitId: String) -> LoadedFullScreenAd,
) : FullScreenAdSource {
    val requestedAdUnitIds = mutableListOf<String>()

    override suspend fun load(request: AdRequestInfo): LoadedFullScreenAd {
        requestedAdUnitIds += request.adUnitId
        return provide(request.adUnitId)
    }
}

internal object NoOpAdNetwork : AdNetwork {
    override val name: String = "fake"
    override fun initialize(context: Context, onComplete: () -> Unit) = error("not used")
    override fun createConsentManager(
        context: Context,
        debug: ConsentDebugConfig?,
        onConsentChanged: (Boolean) -> Unit,
    ): ConsentManager = error("not used")
    override fun interstitialSource(context: Context): FullScreenAdSource = error("not used")
    override fun appOpenSource(context: Context): FullScreenAdSource = error("not used")
    override fun rewardedSource(context: Context): FullScreenAdSource = error("not used")
    override fun bannerSource(context: Context): BannerAdSource = error("not used")
    override fun nativeSource(context: Context): NativeAdSource = error("not used")
}

internal fun TestScope.newTestRuntime(): AdFlowRuntime =
    AdFlowRuntime(
        network = NoOpAdNetwork,
        logger = AdFlowLogger { _, _, _, _ -> },
        scope = this,
        clock = { testScheduler.currentTime },
    )
