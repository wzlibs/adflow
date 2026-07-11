package com.adflow.core.banner

import android.content.Context
import android.view.View
import com.adflow.core.network.AdRequestInfo
import com.adflow.core.network.BannerAdSource
import com.adflow.core.network.LoadedBannerAd

internal class FakeLoadedBannerAd(context: Context) : LoadedBannerAd {
    override val view: View = View(context)
    var destroyed = false
    override fun destroy() { destroyed = true }
}

internal class FakeBannerAdSource(
    private val provide: suspend (adUnitId: String, size: BannerSize) -> LoadedBannerAd,
) : BannerAdSource {
    val requestedAdUnitIds = mutableListOf<String>()

    override suspend fun load(request: AdRequestInfo, size: BannerSize): LoadedBannerAd {
        requestedAdUnitIds += request.adUnitId
        return provide(request.adUnitId, size)
    }
}
