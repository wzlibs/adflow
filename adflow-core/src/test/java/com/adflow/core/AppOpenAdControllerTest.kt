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
        AdFlowCore.tryClaimFullScreenSlot()

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

    @Test
    fun `defers the foreground show until an activity resumes if the process starts first`() {
        val appOpen = FakeAppOpenAdManager(ready = true)
        val controller = AppOpenAdController(application, appOpen)
        controller.start()

        controller.onForegroundStart()
        assertNull(appOpen.shownWith)

        val activityController = Robolectric.buildActivity(Activity::class.java).create().start().resume()

        assertEquals(activityController.get(), appOpen.shownWith)
    }

    @Test
    fun `forgets a pending foreground show once the process goes back to the background`() {
        val appOpen = FakeAppOpenAdManager(ready = true)
        val controller = AppOpenAdController(application, appOpen)
        controller.start()

        controller.onForegroundStart()
        controller.onForegroundStop()
        Robolectric.buildActivity(Activity::class.java).create().start().resume()

        assertNull(appOpen.shownWith)
    }

    @Test
    fun `start() is idempotent - a single stop() fully unregisters even after multiple start() calls`() {
        val appOpen = FakeAppOpenAdManager(ready = true)
        val controller = AppOpenAdController(application, appOpen)
        controller.start()
        controller.start() // must not create a second, independent registration
        controller.stop() // a single stop() must be enough to fully undo both start() calls

        // If start() had registered activityCallbacks twice, one stop() would leave one
        // registration dangling, and this resume would still capture currentActivity.
        Robolectric.buildActivity(Activity::class.java).create().start().resume()
        controller.showIfPossible()

        assertNull(appOpen.shownWith)
    }

    @Test
    fun `stop() unregisters so a later resume no longer captures the activity or auto-shows`() {
        val appOpen = FakeAppOpenAdManager(ready = true)
        val controller = AppOpenAdController(application, appOpen)
        controller.start()
        controller.stop()

        Robolectric.buildActivity(Activity::class.java).create().start().resume()
        controller.showIfPossible()

        assertNull(appOpen.shownWith)
    }
}
