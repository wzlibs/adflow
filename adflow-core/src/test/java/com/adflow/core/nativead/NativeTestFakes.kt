package com.adflow.core.nativead

import android.content.Context
import android.view.View
import com.adflow.core.network.AdRequestInfo
import com.adflow.core.network.LoadedNativeAd
import com.adflow.core.network.NativeAdSource

internal class FakeLoadedNativeAd(context: Context) : LoadedNativeAd {
    override val assets = NativeAdAssets(
        headline = "headline",
        body = null,
        icon = null,
        callToAction = null,
        starRating = null,
        advertiser = null,
        mediaViewSlot = { ctx -> View(ctx) },
    )
    var destroyed = false
    override fun wrapBoundView(context: Context, renderedView: View): View = renderedView
    override fun destroy() { destroyed = true }
}

internal class FakeNativeAdSource(
    private val provide: suspend (adUnitId: String) -> LoadedNativeAd,
) : NativeAdSource {
    val requestedAdUnitIds = mutableListOf<String>()

    override suspend fun load(request: AdRequestInfo): LoadedNativeAd {
        requestedAdUnitIds += request.adUnitId
        return provide(request.adUnitId)
    }
}
