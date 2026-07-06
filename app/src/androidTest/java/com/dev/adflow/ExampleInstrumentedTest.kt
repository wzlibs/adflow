package com.dev.adflow

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, sẽ chạy trên thiết bị Android thật.
 *
 * Xem [tài liệu testing](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context của app đang được test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.dev.adflow", appContext.packageName)
    }
}