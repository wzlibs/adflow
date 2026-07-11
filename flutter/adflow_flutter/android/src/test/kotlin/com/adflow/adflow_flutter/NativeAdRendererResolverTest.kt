package com.adflow.adflow_flutter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.adflow.admob.nativead.DefaultMediumNativeAdRenderer
import com.adflow.core.nativead.NativeAdAssets
import com.adflow.core.nativead.NativeAdRenderer
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NativeAdRendererResolverTest {
    private val custom = object : NativeAdRenderer {
        override fun onCreateView(context: Context, parent: ViewGroup): View = View(context)
        override fun onBind(view: View, assets: NativeAdAssets) = Unit
    }

    @Test
    fun `registered renderer is returned`() {
        assertSame(custom, resolveRenderer("compact", mapOf("compact" to custom)))
    }

    @Test
    fun `null renderer id uses medium default`() {
        assertTrue(resolveRenderer(null, emptyMap()) is DefaultMediumNativeAdRenderer)
    }
}
