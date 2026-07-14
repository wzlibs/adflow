package com.adflow.core.engine

import android.content.Context
import com.adflow.core.consent.ConsentDebugConfig
import com.adflow.core.consent.ConsentManager
import com.adflow.core.logging.AdFlowLogger
import com.adflow.core.network.AdNetwork
import com.adflow.core.network.BannerAdSource
import com.adflow.core.network.FullScreenAdSource
import com.adflow.core.network.NativeAdSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

private object UnusedAdNetworkFake : AdNetwork {
    override val name: String = "fake"
    override fun initialize(context: Context, onComplete: () -> Unit) = error("not used")
    override fun createConsentManager(
        context: Context,
        debug: ConsentDebugConfig?,
        onConsentChanged: (Boolean) -> Unit,
    ): ConsentManager = error("not used")
    override fun interstitialSource(context: Context): FullScreenAdSource = error("not used")
    override fun appOpenSource(context: Context): FullScreenAdSource = error("not used")
    override fun rewardedSource(context: Context): FullScreenAdSource = error("not used")
    override fun bannerSource(context: Context): BannerAdSource = error("not used")
    override fun nativeSource(context: Context): NativeAdSource = error("not used")
}

/** Test riêng cho phần init SDK chỉ theo consent - xem
 * docs/features/2026-07-14-ad-network-init-consent-gate.md. */
class AdFlowRuntimeTest {

    private fun newRuntime(): AdFlowRuntime =
        AdFlowRuntime(
            network = UnusedAdNetworkFake,
            logger = AdFlowLogger { _, _, _, _ -> },
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

    @Test
    fun `consentAllowsAdRequests defaults to false (fail-safe deny)`() {
        assertFalse(newRuntime().consentAllowsAdRequests)
    }

    @Test
    fun `updateConsent(true) invokes networkInitializer exactly once`() {
        val runtime = newRuntime()
        var initCount = 0
        runtime.networkInitializer = { initCount++ }

        runtime.updateConsent(true)

        assertEquals(1, initCount)
        assertEquals(true, runtime.consentAllowsAdRequests)
    }

    @Test
    fun `updateConsent(false) never invokes networkInitializer`() {
        val runtime = newRuntime()
        var initCount = 0
        runtime.networkInitializer = { initCount++ }

        runtime.updateConsent(false)

        assertEquals(0, initCount)
        assertFalse(runtime.consentAllowsAdRequests)
    }

    @Test
    fun `updateConsent(true) called repeatedly only invokes networkInitializer once`() {
        val runtime = newRuntime()
        var initCount = 0
        runtime.networkInitializer = { initCount++ }

        runtime.updateConsent(true)
        runtime.updateConsent(true)
        runtime.updateConsent(true)

        assertEquals(1, initCount)
    }

    @Test
    fun `revoke then re-grant after init does not re-invoke networkInitializer`() {
        val runtime = newRuntime()
        var initCount = 0
        runtime.networkInitializer = { initCount++ }

        runtime.updateConsent(true)
        runtime.updateConsent(false)
        runtime.updateConsent(true)

        assertEquals(1, initCount)
        assertEquals(true, runtime.consentAllowsAdRequests)
    }

    @Test
    fun `networkInitializer assigned after consent already granted is not retroactively invoked`() {
        val runtime = newRuntime()
        runtime.updateConsent(true)

        var initCount = 0
        runtime.networkInitializer = { initCount++ }

        assertEquals(0, initCount)
    }

    @Test
    fun `onConsentGranted listener fires on the false-to-true transition`() {
        val runtime = newRuntime()
        var grantedCount = 0
        runtime.onConsentGranted { grantedCount++ }

        runtime.updateConsent(true)

        assertEquals(1, grantedCount)
    }

    @Test
    fun `onConsentGranted listener does not fire on updateConsent(false)`() {
        val runtime = newRuntime()
        var grantedCount = 0
        runtime.onConsentGranted { grantedCount++ }

        runtime.updateConsent(false)

        assertEquals(0, grantedCount)
    }

    @Test
    fun `onConsentGranted listener does not fire again on repeated updateConsent(true)`() {
        val runtime = newRuntime()
        var grantedCount = 0
        runtime.onConsentGranted { grantedCount++ }

        runtime.updateConsent(true)
        runtime.updateConsent(true)

        assertEquals(1, grantedCount)
    }

    @Test
    fun `onConsentGranted listener fires again on revoke then re-grant`() {
        val runtime = newRuntime()
        var grantedCount = 0
        runtime.onConsentGranted { grantedCount++ }

        runtime.updateConsent(true)
        runtime.updateConsent(false)
        runtime.updateConsent(true)

        assertEquals(2, grantedCount)
    }
}
