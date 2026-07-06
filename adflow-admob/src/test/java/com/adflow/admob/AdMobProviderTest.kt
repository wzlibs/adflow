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
        // Regression test: trước đây AdMobProvider lưu nguyên Context được truyền vào và đưa cho
        // mọi manager tồn tại lâu dài mà nó tạo ra. Nếu caller truyền Activity context thay vì
        // Application context, mọi manager sẽ leak Activity đó suốt cả process và tiếp tục load
        // ad với context cũ sau khi nó đã destroy.
        val activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val provider = AdMobProvider(activity)

        assertSame(RuntimeEnvironment.getApplication(), provider.context)
    }
}
