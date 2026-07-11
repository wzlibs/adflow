@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.adflow.core.nativead

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.adflow.core.AdFlow
import com.adflow.core.AdFlowError
import com.adflow.core.AdListener
import com.adflow.core.BlockReason
import com.adflow.core.config.RetryPolicy
import com.adflow.core.consent.ConsentDebugConfig
import com.adflow.core.consent.ConsentManager
import com.adflow.core.consent.ConsentStatus
import com.adflow.core.consent.PrivacyOptionsRequirement
import com.adflow.core.network.AdNetwork
import com.adflow.core.network.AdRequestInfo
import com.adflow.core.network.BannerAdSource
import com.adflow.core.network.FullScreenAdSource
import com.adflow.core.network.LoadedNativeAd
import com.adflow.core.network.NativeAdSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

private class TestAdNetwork(private val native: NativeAdSource) : AdNetwork {
    override val name = "fake"
    override fun initialize(context: Context, onComplete: () -> Unit) = onComplete()
    override fun createConsentManager(
        context: Context,
        debug: ConsentDebugConfig?,
        onConsentChanged: (Boolean) -> Unit,
    ): ConsentManager = object : ConsentManager {
        override val status = MutableStateFlow(ConsentStatus.NOT_REQUIRED)
        override val privacyOptionsRequirement = PrivacyOptionsRequirement.NOT_REQUIRED
        override fun canRequestAds() = true
        override fun requestIfNeeded(activity: Activity, onComplete: (AdFlowError?) -> Unit) = onComplete(null)
        override fun showPrivacyOptionsForm(activity: Activity, onComplete: (AdFlowError?) -> Unit) = onComplete(null)
    }
    override fun interstitialSource(context: Context): FullScreenAdSource = error("not used")
    override fun appOpenSource(context: Context): FullScreenAdSource = error("not used")
    override fun rewardedSource(context: Context): FullScreenAdSource = error("not used")
    override fun bannerSource(context: Context): BannerAdSource = error("not used")
    override fun nativeSource(context: Context): NativeAdSource = native
}

private class RecordingRenderer : NativeAdRenderer {
    var boundAssets: NativeAdAssets? = null
    override fun onCreateView(context: Context, parent: android.view.ViewGroup): View = View(context)
    override fun onBind(view: View, assets: NativeAdAssets) { boundAssets = assets }
}

@RunWith(RobolectricTestRunner::class)
class AdFlowNativeAdViewTest {

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        AdFlow.resetForTesting()
        Dispatchers.resetMain()
    }

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
    fun `attaching with an already-loaded ad renders it via the view's own renderer`() {
        val fakeAd = FakeLoadedNativeAd(RuntimeEnvironment.getApplication())
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = TestAdNetwork(FakeNativeAdSource { fakeAd })
            native("native") { adUnits("u1") }
        }

        val renderer = RecordingRenderer()
        val view = AdFlowNativeAdView(RuntimeEnvironment.getApplication())
        view.placementId = "native"
        view.renderer = renderer
        attachToWindow(view)

        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(1, view.childCount)
        assertNotNull(renderer.boundAssets)
    }

    @Test
    fun `falls back to the placement's default renderer when the view has none`() {
        val fakeAd = FakeLoadedNativeAd(RuntimeEnvironment.getApplication())
        val defaultRenderer = RecordingRenderer()
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = TestAdNetwork(FakeNativeAdSource { fakeAd })
            native("native") { adUnits("u1"); renderer = defaultRenderer }
        }

        val view = AdFlowNativeAdView(RuntimeEnvironment.getApplication())
        view.placementId = "native" // view.renderer để null - phải rơi xuống default của placement
        attachToWindow(view)

        assertEquals(View.VISIBLE, view.visibility)
        assertNotNull(defaultRenderer.boundAssets)
    }

    @Test
    fun `a load failure collapses the view and reports the app listener`() {
        val source = object : NativeAdSource {
            override suspend fun load(request: AdRequestInfo): LoadedNativeAd {
                throw com.adflow.core.network.AdLoadException(AdFlowError(1, "no fill"))
            }
        }
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = TestAdNetwork(source)
            native("native") { adUnits("u1"); retryPolicy = RetryPolicy(maxRetries = 1) }
        }

        var failedError: AdFlowError? = null
        val view = AdFlowNativeAdView(RuntimeEnvironment.getApplication())
        view.placementId = "native"
        view.renderer = RecordingRenderer()
        view.adListener = object : AdListener {
            override fun onAdFailedToLoad(error: AdFlowError, willRetry: Boolean) { failedError = error }
        }
        attachToWindow(view)

        assertEquals(View.GONE, view.visibility)
        assertEquals(0, view.childCount)
        assertEquals(1, failedError?.code)
    }

    @Test
    fun `reload rebinds the view to the new ad without recreating it externally`() {
        var currentAd = FakeLoadedNativeAd(RuntimeEnvironment.getApplication())
        val source = object : NativeAdSource {
            override suspend fun load(request: AdRequestInfo): LoadedNativeAd = currentAd
        }
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = TestAdNetwork(source)
            native("native") { adUnits("u1") }
        }

        val renderer = RecordingRenderer()
        val view = AdFlowNativeAdView(RuntimeEnvironment.getApplication())
        view.placementId = "native"
        view.renderer = renderer
        attachToWindow(view)
        val firstAssets = renderer.boundAssets

        currentAd = FakeLoadedNativeAd(RuntimeEnvironment.getApplication())
        view.reload()
        idleMainLooper()

        assertNotNull(renderer.boundAssets)
        // rebind() gọi renderer.onBind() lại với assets mới - đối tượng assets khác lần đầu.
        assertEquals(true, firstAssets !== renderer.boundAssets)
    }

    @Test
    fun `showRule rejection reports RULE_REJECTED and keeps the view collapsed`() {
        val fakeAd = FakeLoadedNativeAd(RuntimeEnvironment.getApplication())
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = TestAdNetwork(FakeNativeAdSource { fakeAd })
            native("native") { adUnits("u1"); showWhen { false } }
        }

        var blockedReason: BlockReason? = null
        val view = AdFlowNativeAdView(RuntimeEnvironment.getApplication())
        view.placementId = "native"
        view.renderer = RecordingRenderer()
        view.adListener = object : AdListener {
            override fun onAdBlocked(reason: BlockReason) { blockedReason = reason }
        }
        attachToWindow(view)

        assertEquals(BlockReason.RULE_REJECTED, blockedReason)
        assertEquals(View.GONE, view.visibility)
        assertEquals(0, view.childCount)
    }
}
