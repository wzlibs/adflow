package com.adflow.adflow_flutter

import android.app.Activity
import android.app.Application
import android.content.Context
import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.PBlockReason
import com.adflow.adflow_flutter.generated.PShowEventKind
import com.adflow.core.AdLoadResult
import com.adflow.core.InterstitialAdManager
import com.adflow.core.ShowCallback
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private class FakeInterstitialAdManager : InterstitialAdManager {
    var shownWith: Activity? = null
    var shownCallback: ShowCallback? = null

    override fun isReady(): Boolean = true

    override fun load(onResult: (AdLoadResult) -> Unit) {}

    override fun show(activity: Activity, callback: ShowCallback) {
        shownWith = activity
        shownCallback = callback
    }
}

/**
 * Không gọi create() trong các test này - đi thẳng registry.interstitials["p1"] = fake để tránh
 * đụng tới AdMobProvider thật (không unit-test được, cần thiết bị/emulator - đã verify thủ công
 * qua adb logcat trên device thật, xem README phần Testing).
 */
class InterstitialAdHostApiImplTest {

    private fun newRegistry(): PlacementRegistry {
        val application = mock<Application>()
        val context = mock<Context> { whenever(it.applicationContext).thenReturn(application) }
        return PlacementRegistry(context)
    }

    @Test
    fun `show() calls manager show() when enabled and an activity is attached`() {
        val registry = newRegistry()
        val manager = FakeInterstitialAdManager()
        registry.interstitials["p1"] = manager
        val activity = mock<Activity>()
        registry.currentActivity = activity
        val flutterApi = mock<AdFlowFlutterApi>()
        val impl = InterstitialAdHostApiImpl(registry, flutterApi)

        impl.show("p1")

        assertSame(activity, manager.shownWith)
        verify(flutterApi, never()).onShowEvent(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `show() is blocked and never touches the manager when the placement is disabled`() {
        val registry = newRegistry()
        val manager = FakeInterstitialAdManager()
        registry.interstitials["p1"] = manager
        registry.currentActivity = mock<Activity>()
        registry.setEnabled("p1", false)
        val flutterApi = mock<AdFlowFlutterApi>()
        val impl = InterstitialAdHostApiImpl(registry, flutterApi)

        impl.show("p1")

        assertNull(manager.shownWith)
        verify(flutterApi).onShowEvent(
            eq("p1"),
            eq(PShowEventKind.SHOW_BLOCKED),
            eq(null),
            eq(PBlockReason.DISABLED),
            eq(null),
            any(),
        )
    }

    @Test
    fun `show() reports SHOW_BLOCKED (not silence) when no activity is attached`() {
        val registry = newRegistry()
        val manager = FakeInterstitialAdManager()
        registry.interstitials["p1"] = manager
        val flutterApi = mock<AdFlowFlutterApi>()
        val impl = InterstitialAdHostApiImpl(registry, flutterApi)

        // showGated() gọi android.util.Log.w() khi không có Activity - stub tĩnh vì Log thật
        // không hoạt động trong JVM unit test thuần (không có Robolectric).
        org.mockito.Mockito.mockStatic(android.util.Log::class.java).use {
            impl.show("p1")
        }

        assertNull(manager.shownWith)
        // Dart show() chỉ hoàn tất Future qua onShowEvent (xem show_event_support.dart) - im lặng
        // hoàn toàn ở đây sẽ khiến await ad.show() treo vĩnh viễn nếu Activity detach đúng lúc
        // (xoay màn hình, app bị background giữa lúc load() xong và show() được gọi).
        verify(flutterApi).onShowEvent(
            eq("p1"),
            eq(PShowEventKind.SHOW_BLOCKED),
            eq(null),
            eq(PBlockReason.NOT_READY),
            eq(null),
            any(),
        )
    }

    @Test
    fun `show() reports SHOW_BLOCKED (not silence) when no manager exists for the placement`() {
        val registry = newRegistry()
        val flutterApi = mock<AdFlowFlutterApi>()
        val impl = InterstitialAdHostApiImpl(registry, flutterApi)

        impl.show("unregistered")

        verify(flutterApi).onShowEvent(
            eq("unregistered"),
            eq(PShowEventKind.SHOW_BLOCKED),
            eq(null),
            eq(PBlockReason.NOT_READY),
            eq(null),
            any(),
        )
    }

    @Test
    fun `create() is idempotent - a placementId that already exists is never recreated`() {
        val registry = newRegistry()
        val manager = FakeInterstitialAdManager()
        registry.interstitials["p1"] = manager
        val flutterApi = mock<AdFlowFlutterApi>()
        val impl = InterstitialAdHostApiImpl(registry, flutterApi)

        // "p1" đã tồn tại trong registry nên create() phải return sớm, không bao giờ chạm tới
        // registry.provider (AdMobProvider thật) - nếu nó chạm tới, test này sẽ throw vì
        // AdMobProvider gọi vào Android/GMS SDK thật, không chạy được trong JVM unit test.
        impl.create(
            com.adflow.adflow_flutter.generated.PPlacementConfig(
                placementId = "p1",
                enabled = true,
                preloadEnabled = true,
                adUnitIds = listOf("unit-1"),
                retryPolicy = com.adflow.adflow_flutter.generated.PRetryPolicy(
                    initialDelayMs = 1000,
                    multiplier = 2.0,
                    maxDelayMs = 8000,
                    maxRetries = 3,
                ),
                expiryMs = 1000,
            ),
        )

        assertSame(manager, registry.interstitials["p1"])
    }
}
