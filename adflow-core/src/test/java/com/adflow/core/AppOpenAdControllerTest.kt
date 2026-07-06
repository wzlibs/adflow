package com.adflow.core

import android.app.Activity
import android.app.Application
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppOpenAdControllerTest {

    private val application: Application = RuntimeEnvironment.getApplication()

    private class FakeAppOpenAdManager(var ready: Boolean = false) : AppOpenAdManager {
        var shownWith: Activity? = null

        override fun isReady(): Boolean = ready
        override fun load(onResult: (AdLoadResult) -> Unit) {}
        override fun show(activity: Activity, callback: ShowCallback) {
            shownWith = activity
        }
    }

    @After
    fun tearDown() {
        AdFlowCore.reset()
    }

    @Test
    fun `does nothing when there is no resumed activity`() {
        val appOpen = FakeAppOpenAdManager(ready = true)
        val controller = AppOpenAdController(application, appOpen)
        controller.start()

        controller.showIfPossible()

        assertNull(appOpen.shownWith)
    }

    @Test
    fun `shows the app open ad once an activity resumes, when ready and nothing else is showing`() {
        val appOpen = FakeAppOpenAdManager(ready = true)
        val controller = AppOpenAdController(application, appOpen)
        controller.start()

        val activityController = Robolectric.buildActivity(Activity::class.java).create().start().resume()

        controller.showIfPossible()

        assertEquals(activityController.get(), appOpen.shownWith)
    }

    @Test
    fun `does not show when another full-screen ad is currently on screen`() {
        val appOpen = FakeAppOpenAdManager(ready = true)
        val controller = AppOpenAdController(application, appOpen)
        controller.start()
        Robolectric.buildActivity(Activity::class.java).create().start().resume()
        AdFlowCore.setShowingFullScreenAd(true)

        controller.showIfPossible()

        assertNull(appOpen.shownWith)
    }

    @Test
    fun `does not show when the app open ad isn't ready`() {
        val appOpen = FakeAppOpenAdManager(ready = false)
        val controller = AppOpenAdController(application, appOpen)
        controller.start()
        Robolectric.buildActivity(Activity::class.java).create().start().resume()

        controller.showIfPossible()

        assertNull(appOpen.shownWith)
    }

    @Test
    fun `forgets the activity once it pauses`() {
        val appOpen = FakeAppOpenAdManager(ready = true)
        val controller = AppOpenAdController(application, appOpen)
        controller.start()
        val activityController = Robolectric.buildActivity(Activity::class.java).create().start().resume()
        activityController.pause()

        controller.showIfPossible()

        assertNull(appOpen.shownWith)
    }
}
