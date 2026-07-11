package com.adflow.admob.banner

import com.adflow.core.banner.BannerSize
import com.google.android.gms.ads.AdSize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AdMobBannerSourceTest {

    @Test
    fun `fixed sizes map to their matching AdMob constant`() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals(AdSize.BANNER, mapBannerSize(context, BannerSize.BANNER))
        assertEquals(AdSize.LARGE_BANNER, mapBannerSize(context, BannerSize.LARGE_BANNER))
        assertEquals(AdSize.MEDIUM_RECTANGLE, mapBannerSize(context, BannerSize.MEDIUM_RECTANGLE))
    }

    @Test
    fun `adaptive size is derived from the screen width, not a fixed constant`() {
        val context = RuntimeEnvironment.getApplication()
        val screenWidthDp = (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()

        val adaptive = mapBannerSize(context, BannerSize.ADAPTIVE)

        assertEquals(screenWidthDp, adaptive.width)
    }
}
