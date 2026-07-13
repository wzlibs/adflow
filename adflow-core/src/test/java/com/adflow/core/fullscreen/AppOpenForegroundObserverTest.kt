package com.adflow.core.fullscreen

import android.app.Activity
import android.app.Application
import com.adflow.core.AdListener
import com.adflow.core.AdState
import com.adflow.core.engine.AdFlowRuntime
import com.adflow.core.logging.AdFlowLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

private class FakeAppOpenAd(initialState: AdState, private val runtime: AdFlowRuntime) : AppOpenAd {
    override val placementId = "app_open"
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(initialState)
    override val state: kotlinx.coroutines.flow.StateFlow<AdState> get() = _state
    override val isReady: Boolean get() = _state.value is AdState.Loaded

    /** null (mặc định) - suy ra như thật (isReady + slot rảnh). Set tay để giả lập canShow bị
     * chặn bởi lý do khác (showRule/interval) mà isReady/slot không phản ánh được, xác nhận
     * showIfPossible() thực sự dựa vào canShow chứ không tự suy luận lại. */
    var canShowOverride: Boolean? = null
    override val canShow: Boolean
        get() = canShowOverride ?: (isReady && !runtime.fullScreenSlot.isShowing)
    var shownWith: Activity? = null

    override fun load() {}
    override fun addListener(listener: AdListener) {}
    override fun removeListener(listener: AdListener) {}
    override fun show(activity: Activity, callback: FullScreenCallback) {
        shownWith = activity
    }
}

@RunWith(RobolectricTestRunner::class)
class AppOpenForegroundObserverTest {

    private val application: Application = RuntimeEnvironment.getApplication()
    private val runtime = AdFlowRuntime(
        network = NoOpAdNetwork,
        logger = AdFlowLogger { _, _, _, _ -> },
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    @Test
    fun `does nothing when there is no resumed activity`() {
        val appOpen = FakeAppOpenAd(AdState.Loaded(0), runtime)
        val observer = AppOpenForegroundObserver(application, appOpen)
        observer.start()

        observer.showIfPossible()

        assertNull(appOpen.shownWith)
    }

    @Test
    fun `shows the app open ad once an activity resumes, when ready and nothing else is showing`() {
        val appOpen = FakeAppOpenAd(AdState.Loaded(0), runtime)
        val observer = AppOpenForegroundObserver(application, appOpen)
        observer.start()

        val activityController = Robolectric.buildActivity(Activity::class.java).create().start().resume()
        observer.showIfPossible()

        assertEquals(activityController.get(), appOpen.shownWith)
    }

    @Test
    fun `defers a foreground signal that arrives before any activity has resumed`() {
        val appOpen = FakeAppOpenAd(AdState.Loaded(0), runtime)
        val observer = AppOpenForegroundObserver(application, appOpen)
        observer.start()

        observer.onForegroundStart() // process vừa start, chưa có Activity nào resume
        assertNull(appOpen.shownWith)

        val activityController = Robolectric.buildActivity(Activity::class.java).create().start().resume()

        assertEquals(activityController.get(), appOpen.shownWith) // tự show ngay khi Activity resume
    }

    @Test
    fun `never shows over another full-screen ad already showing`() {
        val appOpen = FakeAppOpenAd(AdState.Loaded(0), runtime)
        val observer = AppOpenForegroundObserver(application, appOpen)
        observer.start()
        Robolectric.buildActivity(Activity::class.java).create().start().resume()
        runtime.fullScreenSlot.tryClaim()

        observer.showIfPossible()

        assertNull(appOpen.shownWith)
    }

    @Test
    fun `does not show when the ad is not yet Loaded`() {
        val appOpen = FakeAppOpenAd(AdState.Loading, runtime)
        val observer = AppOpenForegroundObserver(application, appOpen)
        observer.start()
        Robolectric.buildActivity(Activity::class.java).create().start().resume()

        observer.showIfPossible()

        assertNull(appOpen.shownWith)
    }

    @Test
    fun `defers entirely to canShow - does not show when canShow is false even though ready and slot is free`() {
        val appOpen = FakeAppOpenAd(AdState.Loaded(0), runtime).apply { canShowOverride = false }
        val observer = AppOpenForegroundObserver(application, appOpen)
        observer.start()
        Robolectric.buildActivity(Activity::class.java).create().start().resume()

        observer.showIfPossible()

        assertNull(appOpen.shownWith) // isReady=true, slot rảnh - nhưng canShow=false (vd showRule/interval) vẫn phải chặn
    }
}
