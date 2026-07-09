package com.adflow.adflow_flutter.platformview

import com.adflow.admob.nativead.DefaultMediumNativeAdRenderer
import com.adflow.core.NativeAdRenderer
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class NativePlatformViewFactoryTest {

    @Test
    fun `rendererId null falls back to the default renderer`() {
        val renderer = resolveRenderer(null, emptyMap())

        assertTrue(renderer is DefaultMediumNativeAdRenderer)
    }

    @Test
    fun `rendererId matching a registered renderer returns that exact instance`() {
        val custom = mock<NativeAdRenderer>()

        val renderer = resolveRenderer("custom", mapOf("custom" to custom))

        assertSame(custom, renderer)
    }

    @Test
    fun `rendererId with no matching registered renderer falls back to the default renderer`() {
        val custom = mock<NativeAdRenderer>()

        // resolveRenderer() gọi android.util.Log.w() khi không tìm thấy renderer - stub tĩnh vì
        // Log thật không hoạt động trong JVM unit test thuần (không có Robolectric), giống pattern
        // đã dùng ở InterstitialAdHostApiImplTest.
        val renderer = org.mockito.Mockito.mockStatic(android.util.Log::class.java).use {
            resolveRenderer("unknown", mapOf("custom" to custom))
        }

        assertTrue(renderer is DefaultMediumNativeAdRenderer)
    }
}
