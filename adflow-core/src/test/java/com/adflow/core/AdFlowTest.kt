package com.adflow.core

import android.content.Context
import com.adflow.core.banner.BannerAdController
import com.adflow.core.consent.ConsentDebugConfig
import com.adflow.core.consent.ConsentManager
import com.adflow.core.consent.ConsentStatus
import com.adflow.core.consent.PrivacyOptionsRequirement
import com.adflow.core.fullscreen.AppOpenAd
import com.adflow.core.fullscreen.InterstitialAd
import com.adflow.core.nativead.NativeAdController
import com.adflow.core.network.AdNetwork
import com.adflow.core.network.AdRequestInfo
import com.adflow.core.network.BannerAdSource
import com.adflow.core.network.FullScreenAdSource
import com.adflow.core.network.LoadedFullScreenAd
import com.adflow.core.network.NativeAdSource
import com.adflow.core.rewarded.RewardedAd
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Fake [AdNetwork] không bao giờ thực sự load gì - chỉ đủ để [AdFlow.initialize] chạy hết mà
 * không đụng SDK Android thật, phục vụ test cấu trúc DSL/registry. */
private object FakeAdNetwork : AdNetwork {
    override val name: String = "fake"

    override fun initialize(context: Context, onComplete: () -> Unit) = onComplete()

    override fun createConsentManager(
        context: Context,
        debug: ConsentDebugConfig?,
        onConsentChanged: (Boolean) -> Unit,
    ): ConsentManager {
        // Mô phỏng đúng hành vi seed đồng bộ của AdMobConsentManager thật (canRequestAds() = true
        // ngay từ đầu ở đây) - nếu không, consentAllowsAdRequests mặc định false sẽ chặn mọi test load.
        onConsentChanged(true)
        return object : ConsentManager {
            override val status = MutableStateFlow(ConsentStatus.NOT_REQUIRED)
            override val privacyOptionsRequirement = PrivacyOptionsRequirement.NOT_REQUIRED
            override fun canRequestAds() = true
            override fun requestIfNeeded(activity: android.app.Activity, onComplete: (AdFlowError?) -> Unit) = onComplete(null)
            override fun showPrivacyOptionsForm(activity: android.app.Activity, onComplete: (AdFlowError?) -> Unit) = onComplete(null)
        }
    }

    private val neverLoadingFullScreen = object : FullScreenAdSource {
        override suspend fun load(request: AdRequestInfo): LoadedFullScreenAd = suspendForever()
    }

    override fun interstitialSource(context: Context): FullScreenAdSource = neverLoadingFullScreen
    override fun appOpenSource(context: Context): FullScreenAdSource = neverLoadingFullScreen
    override fun rewardedSource(context: Context): FullScreenAdSource = neverLoadingFullScreen

    override fun bannerSource(context: Context): BannerAdSource = object : BannerAdSource {
        override suspend fun load(request: AdRequestInfo, size: com.adflow.core.banner.BannerSize) =
            suspendForever<com.adflow.core.network.LoadedBannerAd>()
    }

    override fun nativeSource(context: Context): NativeAdSource = object : NativeAdSource {
        override suspend fun load(request: AdRequestInfo) = suspendForever<com.adflow.core.network.LoadedNativeAd>()
    }

    private suspend fun <T> suspendForever(): T = kotlinx.coroutines.suspendCancellableCoroutine { }
}

/** Fake [AdNetwork] đếm số lần `initialize()`/`load()` được gọi và cho phép test tự tay bắn lại
 * callback consent - dùng để test trọng tâm của việc init SDK chỉ theo consent, tách khỏi
 * foreground (xem docs/features/2026-07-14-ad-network-init-consent-gate.md). */
private class ConfigurableAdNetwork(private val seedConsent: Boolean) : AdNetwork {
    override val name: String = "configurable-fake"

    var initializeCallCount = 0
        private set
    var loadCallCount = 0
        private set
    var capturedOnConsentChanged: ((Boolean) -> Unit)? = null
        private set

    override fun initialize(context: Context, onComplete: () -> Unit) {
        initializeCallCount++
        onComplete()
    }

    override fun createConsentManager(
        context: Context,
        debug: ConsentDebugConfig?,
        onConsentChanged: (Boolean) -> Unit,
    ): ConsentManager {
        capturedOnConsentChanged = onConsentChanged
        onConsentChanged(seedConsent)
        return object : ConsentManager {
            override val status = MutableStateFlow(ConsentStatus.NOT_REQUIRED)
            override val privacyOptionsRequirement = PrivacyOptionsRequirement.NOT_REQUIRED
            override fun canRequestAds() = seedConsent
            override fun requestIfNeeded(activity: android.app.Activity, onComplete: (AdFlowError?) -> Unit) = onComplete(null)
            override fun showPrivacyOptionsForm(activity: android.app.Activity, onComplete: (AdFlowError?) -> Unit) = onComplete(null)
        }
    }

    private val countingFullScreen = object : FullScreenAdSource {
        override suspend fun load(request: AdRequestInfo): LoadedFullScreenAd {
            loadCallCount++
            return suspendForever()
        }
    }

    override fun interstitialSource(context: Context): FullScreenAdSource = countingFullScreen
    override fun appOpenSource(context: Context): FullScreenAdSource = countingFullScreen
    override fun rewardedSource(context: Context): FullScreenAdSource = countingFullScreen

    override fun bannerSource(context: Context): BannerAdSource = object : BannerAdSource {
        override suspend fun load(request: AdRequestInfo, size: com.adflow.core.banner.BannerSize): com.adflow.core.network.LoadedBannerAd {
            loadCallCount++
            return suspendForever()
        }
    }

    override fun nativeSource(context: Context): NativeAdSource = object : NativeAdSource {
        override suspend fun load(request: AdRequestInfo): com.adflow.core.network.LoadedNativeAd {
            loadCallCount++
            return suspendForever()
        }
    }

    private suspend fun <T> suspendForever(): T = kotlinx.coroutines.suspendCancellableCoroutine { }
}

@RunWith(RobolectricTestRunner::class)
class AdFlowTest {

    @After
    fun tearDown() {
        AdFlow.resetForTesting()
    }

    @Test
    fun `initialize registers every declared placement with the correct type`() {
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = FakeAdNetwork
            interstitial("inter") { adUnits("u1") }
            appOpen("open") { adUnits("u1") }
            rewarded("reward") { adUnits("u1") }
            banner("banner") { adUnits("u1") }
            native("native") { adUnits("u1") }
        }

        assertTrue(AdFlow.interstitial("inter") is InterstitialAd)
        assertTrue(AdFlow.appOpen("open") is AppOpenAd)
        assertTrue(AdFlow.rewarded("reward") is RewardedAd)
        assertTrue(AdFlow.banner("banner") is BannerAdController)
        assertTrue(AdFlow.native("native") is NativeAdController)
        assertEquals("inter", AdFlow.interstitial("inter").placementId)
    }

    @Test(expected = IllegalStateException::class)
    fun `duplicate placementId across different ad types throws during initialize`() {
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = FakeAdNetwork
            interstitial("shared") { adUnits("u1") }
            banner("shared") { adUnits("u1") } // trùng ID với Interstitial ở trên - phải throw
        }
    }

    @Test(expected = IllegalStateException::class)
    fun `looking up an unknown placementId throws`() {
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = FakeAdNetwork
            interstitial("inter") { adUnits("u1") }
        }

        AdFlow.interstitial("does-not-exist")
    }

    @Test(expected = IllegalStateException::class)
    fun `looking up a placementId with the wrong type throws`() {
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = FakeAdNetwork
            banner("banner") { adUnits("u1") }
        }

        AdFlow.interstitial("banner") // "banner" được đăng ký là Banner, không phải Interstitial
    }

    @Test
    fun `a second initialize call is a no-op and keeps the first registration`() {
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = FakeAdNetwork
            interstitial("inter") { adUnits("u1") }
        }
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = FakeAdNetwork
            interstitial("other") { adUnits("u1") } // không được áp dụng
        }

        assertTrue(AdFlow.isInitialized)
        assertEquals("inter", AdFlow.interstitial("inter").placementId)
    }

    @Test(expected = IllegalStateException::class)
    fun `initialize without setting network throws a clear error`() {
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            interstitial("inter") { adUnits("u1") }
        }
    }

    @Test
    fun `network initialize runs immediately when consent already allows ads at seed`() {
        val fakeNetwork = ConfigurableAdNetwork(seedConsent = true)
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = fakeNetwork
            interstitial("inter") { adUnits("u1") }
        }

        assertEquals(1, fakeNetwork.initializeCallCount)
    }

    @Test
    fun `network initialize is deferred until consent callback reports true`() {
        val fakeNetwork = ConfigurableAdNetwork(seedConsent = false)
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = fakeNetwork
            interstitial("inter") { adUnits("u1") }
        }
        assertEquals(0, fakeNetwork.initializeCallCount)

        fakeNetwork.capturedOnConsentChanged?.invoke(true)

        assertEquals(1, fakeNetwork.initializeCallCount)
    }

    @Test
    fun `initialize never triggers a load for any placement even though preload defaults to true`() {
        val fakeNetwork = ConfigurableAdNetwork(seedConsent = true)
        AdFlow.initialize(RuntimeEnvironment.getApplication()) {
            network = fakeNetwork
            interstitial("inter") { adUnits("u1") }
            appOpen("open") { adUnits("u1") }
            rewarded("reward") { adUnits("u1") }
            banner("banner") { adUnits("u1") }
            native("native") { adUnits("u1") }
        }

        assertEquals(0, fakeNetwork.loadCallCount)
    }
}
