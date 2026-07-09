package com.adflow.core

import android.content.Context
import android.view.View
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NativeAdRendererContractTest {

    private class SimpleTextRenderer : NativeAdRenderer {
        override fun createView(context: Context): View = TextView(context)
        override fun bind(view: View, assets: NativeAdAssets) {
            (view as TextView).text = assets.headline
        }
    }

    @Test
    fun `renderer binds headline text onto the created view`() {
        val context = RuntimeEnvironment.getApplication()
        val renderer = SimpleTextRenderer()
        val view = renderer.createView(context)
        val assets = NativeAdAssets(
            headline = "Great app",
            body = null,
            icon = null,
            callToAction = null,
            starRating = null,
            advertiser = null,
            mediaViewSlot = { ctx -> View(ctx) },
        )
        renderer.bind(view, assets)
        assertEquals("Great app", (view as TextView).text)
    }
}
