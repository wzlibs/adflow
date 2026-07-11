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

private class FakeAppOpenAd(initialState: AdState) : AppOpenAd {
    override val placementId = "app_open"
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(initialState)
    override val state: kotlinx.coroutines.flow.StateFlow<AdState> get() = _state
    override val isReady: Boolean get() = _state.value is AdState.Loaded
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
        val appOpen = FakeAppOpenAd(AdState.Loaded(0))
        val observer = AppOpenForegroundObserver(application, appOpen, runtime)
        observer.start()

        observer.showIfPossible()

        assertNull(appOpen.shownWith)
    }

    @Test
    fun `shows the app open ad once an activity resumes, when ready and nothing else is showing`() {
        val appOpen = FakeAppOpenAd(AdState.Loaded(0))
        val observer = AppOpenForegroundObserver(application, appOpen, runtime)
        observer.start()

        val activityController = Robolectric.buildActivity(Activity::class.java).create().start().resume()
        observer.showIfPossible()

        assertEquals(activityController.get(), appOpen.shownWith)
    }

    @Test
    fun `defers a foreground signal that arrives before any activity has resumed`() {
        val appOpen = FakeAppOpenAd(AdState.Loaded(0))
        val observer = AppOpenForegroundObserver(application, appOpen, runtime)
        observer.start()

        observer.onForegroundStart() // process vừa start, chưa có Activity nào resume
        assertNull(appOpen.shownWith)

        val activityController = Robolectric.buildActivity(Activity::class.java).create().start().resume()

        assertEquals(activityController.get(), appOpen.shownWith) // tự show ngay khi Activity resume
    }

    @Test
    fun `never shows over another full-screen ad already showing`() {
        val appOpen = FakeAppOpenAd(AdState.Loaded(0))
        val observer = AppOpenForegroundObserver(application, appOpen, runtime)
        observer.start()
        Robolectric.buildActivity(Activity::class.java).create().start().resume()
        runtime.fullScreenSlot.tryClaim()

        observer.showIfPossible()

        assertNull(appOpen.shownWith)
    }

    @Test
    fun `does not show when the ad is not yet Loaded`() {
        val appOpen = FakeAppOpenAd(AdState.Loading)
        val observer = AppOpenForegroundObserver(application, appOpen, runtime)
        observer.start()
        Robolectric.buildActivity(Activity::class.java).create().start().resume()

        observer.showIfPossible()

        assertNull(appOpen.shownWith)
    }
}
