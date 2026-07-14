package com.adflow.core.banner

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.adflow.core.AdFlow
import com.adflow.core.AdFlowError
import com.adflow.core.AdListener
import com.adflow.core.BlockReason
import com.adflow.core.R
import com.adflow.core.consent.ConsentDebugConfig
import com.adflow.core.consent.ConsentManager
import com.adflow.core.consent.ConsentStatus
import com.adflow.core.consent.PrivacyOptionsRequirement
import com.adflow.core.network.AdNetwork
import com.adflow.core.network.AdRequestInfo
import com.adflow.core.network.BannerAdSource
import com.adflow.core.network.FullScreenAdSource
import com.adflow.core.network.LoadedFullScreenAd
import com.adflow.core.network.NativeAdSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

private class TestAdNetwork(private val banner: BannerAdSource) : AdNetwork {
    override val name = "fake"
    override fun initialize(context: Context, onComplete: () -> Unit) = onComplete()
    override fun createConsentManager(
        context: Context,
        debug: ConsentDebugConfig?,
        onConsentChanged: (Boolean) -> Unit,
    ): ConsentManager {
        onConsentChanged(true) // mô phỏng seed đồng bộ của AdMobConsentManager thật
        return object : ConsentManager {
            override val status = MutableStateFlow(ConsentStatus.NOT_REQUIRED)
            override val privacyOptionsRequirement = PrivacyOptionsRequirement.NOT_REQUIRED
            override fun canRequestAds() = true
            override fun requestIfNeeded(activity: Activity, onComplete: (AdFlowError?) -> Unit) = onComplete(null)
            override fun showPrivacyOptionsForm(activity: Activity, onComplete: (AdFlowError?) -> Unit) = onComplete(null)
        }
    }
    override fun interstitialSource(context: Context): FullScreenAdSource = error("not used")
    override fun appOpenSource(context: Context): FullScreenAdSource = error("not used")
    override fun rewardedSource(context: Context): FullScreenAdSource = error("not used")
    override fun bannerSource(context: Context): BannerAdSource = banner
    override fun nativeSource(context: Context): NativeAdSource = error("not used")
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AdFlowBannerViewTest {

    // AdFlow.initialize() dùng cứng Dispatchers.Main.immediate cho CoroutineScope của engine -
    // dưới JVM unit test (kể cả có Robolectric), Dispatchers.Main không tự có backing thật trừ
    // khi được set tường minh; UnconfinedTestDispatcher chạy đồng bộ ngay tại chỗ, khớp với hành
    // vi "immediate" thật mà production code mong đợi.
    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        AdFlow.resetForTesting()
        Dispatchers.resetMain()
    }

    /** [AdFlow.initialize] chạy engine trên `Dispatchers.Main` thật (không phải `TestScope` ảo
     * như các test controller khác) - dưới Robolectric (`LooperMode.PAUSED` mặc định), việc post
     * lên main looper chỉ thực sự chạy khi được "idle" thủ công. */
    private fun idleMainLooper() {
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun attachToWindow(view: View) {
        val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume().visible().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        root.addView(view)
        idleMainLooper()
    }

    @Test
    fun `xml attribute sets placementId`() {
        val attrs = Robolectric.buildAttributeSet()
            .addAttribute(R.attr.adflowPlacementId, "from_xml")
            .build()
        val view = AdFlowBannerView(RuntimeEnvironment.getApplication(), attrs)

        assertEquals("from_xml", view.placementId)
    }

    @Test
    fun `attaching to a window with an already-loaded ad shows it immediately, no polling needed`() {
        val fakeAd = FakeLoadedBannerAd(RuntimeEnvironment.getApplication())
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = TestAdNetwork(FakeBannerAdSource { _, _ -> fakeAd })
            banner("banner") { adUnits("u1") }
        }
        // Preload mặc định true nhưng chỉ chạy khi foreground gate kích hoạt - load thủ công ở
        // đây để mô phỏng "ad đã sẵn sàng từ trước khi view attach".
        AdFlow.banner("banner").load()
        idleMainLooper()

        val view = AdFlowBannerView(RuntimeEnvironment.getApplication())
        view.placementId = "banner"
        attachToWindow(view)

        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(1, view.childCount)
        assertEquals(fakeAd.view, view.getChildAt(0))
    }

    @Test
    fun `a load failure collapses the view and reports the app listener`() {
        val source = object : BannerAdSource {
            override suspend fun load(request: AdRequestInfo, size: BannerSize): com.adflow.core.network.LoadedBannerAd {
                throw com.adflow.core.network.AdLoadException(AdFlowError(1, "no fill"))
            }
        }
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = TestAdNetwork(source)
            banner("banner") { adUnits("u1"); retryPolicy = com.adflow.core.config.RetryPolicy(maxRetries = 1) }
        }

        var failedError: AdFlowError? = null
        val view = AdFlowBannerView(RuntimeEnvironment.getApplication())
        view.placementId = "banner"
        view.adListener = object : AdListener {
            override fun onAdFailedToLoad(error: AdFlowError, willRetry: Boolean) { failedError = error }
        }
        attachToWindow(view)

        assertEquals(View.GONE, view.visibility)
        assertEquals(0, view.childCount)
        assertEquals(1, failedError?.code)
    }

    @Test
    fun `a late-arriving ad after attach expands the view without any external polling`() {
        val pending = CompletableDeferred<com.adflow.core.network.LoadedBannerAd>()
        val source = object : BannerAdSource {
            override suspend fun load(request: AdRequestInfo, size: BannerSize) = pending.await()
        }
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = TestAdNetwork(source)
            banner("banner") { adUnits("u1") }
        }

        val view = AdFlowBannerView(RuntimeEnvironment.getApplication())
        view.placementId = "banner"
        attachToWindow(view)

        assertEquals(View.GONE, view.visibility) // chưa có ad - collapsed

        val fakeAd = FakeLoadedBannerAd(RuntimeEnvironment.getApplication())
        pending.complete(fakeAd)
        idleMainLooper()

        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(fakeAd.view, view.getChildAt(0))
    }

    @Test
    fun `detaching releases the leased ad`() {
        val fakeAd = FakeLoadedBannerAd(RuntimeEnvironment.getApplication())
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = TestAdNetwork(FakeBannerAdSource { _, _ -> fakeAd })
            banner("banner") { adUnits("u1"); preload = false }
        }
        AdFlow.banner("banner").load()
        idleMainLooper()

        val activity = Robolectric.buildActivity(Activity::class.java).create().start().resume().visible().get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        val view = AdFlowBannerView(RuntimeEnvironment.getApplication())
        view.placementId = "banner"
        root.addView(view)
        idleMainLooper()
        assertTrue(view.childCount == 1)

        root.removeView(view)

        assertTrue(fakeAd.destroyed)
    }

    @Test
    fun `showRule rejection reports RULE_REJECTED and keeps the view collapsed`() {
        val fakeAd = FakeLoadedBannerAd(RuntimeEnvironment.getApplication())
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = TestAdNetwork(FakeBannerAdSource { _, _ -> fakeAd })
            banner("banner") { adUnits("u1"); showWhen { false } }
        }
        AdFlow.banner("banner").load()
        idleMainLooper()

        var blockedReason: BlockReason? = null
        val view = AdFlowBannerView(RuntimeEnvironment.getApplication())
        view.placementId = "banner"
        view.adListener = object : AdListener {
            override fun onAdBlocked(reason: BlockReason) { blockedReason = reason }
        }
        attachToWindow(view)

        assertEquals(BlockReason.RULE_REJECTED, blockedReason)
        assertEquals(View.GONE, view.visibility)
        assertEquals(0, view.childCount)
    }
}
