package com.adflow.admob

import android.app.Activity
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AdMobProviderTest {

    @Test
    fun `resolves to the application context even when constructed with an Activity context`() {
        // Regression test: AdMobProvider used to store whatever Context it was given as-is and
        // hand it to every long-lived manager it creates. If a caller passed an Activity context
        // instead of the Application context, every manager would leak that Activity for the
        // rest of the process and keep loading ads with a stale context after it's destroyed.
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val provider = AdMobProvider(activity)

        assertSame(RuntimeEnvironment.getApplication(), provider.context)
    }
}
