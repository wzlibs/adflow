package com.adflow.admob.consent

import android.app.Activity
import com.adflow.core.consent.ConsentStatus
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private class FakeConsentInformation(private val allows: Boolean) : ConsentInformation {
    override fun canRequestAds(): Boolean = allows
    override fun getConsentStatus(): Int = ConsentInformation.ConsentStatus.OBTAINED
    override fun getPrivacyOptionsRequirementStatus(): ConsentInformation.PrivacyOptionsRequirementStatus =
        ConsentInformation.PrivacyOptionsRequirementStatus.NOT_REQUIRED
    override fun isConsentFormAvailable(): Boolean = false
    override fun requestConsentInfoUpdate(
        activity: Activity,
        params: ConsentRequestParameters,
        onSuccess: ConsentInformation.OnConsentInfoUpdateSuccessListener,
        onFailure: ConsentInformation.OnConsentInfoUpdateFailureListener,
    ) = error("not used in seed tests")
    override fun reset() = error("not used in seed tests")
}

@RunWith(RobolectricTestRunner::class)
class AdMobConsentManagerTest {

    @Test
    fun `mapStatus maps every UMP consent status constant`() {
        assertEquals(ConsentStatus.NOT_REQUIRED, mapStatus(ConsentInformation.ConsentStatus.NOT_REQUIRED))
        assertEquals(ConsentStatus.REQUIRED, mapStatus(ConsentInformation.ConsentStatus.REQUIRED))
        assertEquals(ConsentStatus.OBTAINED, mapStatus(ConsentInformation.ConsentStatus.OBTAINED))
        assertEquals(ConsentStatus.UNKNOWN, mapStatus(-1))
    }

    @Test
    fun `seeds onConsentChanged(true) synchronously when canRequestAds is already true at creation`() {
        var seeded: Boolean? = null
        AdMobConsentManager(
            context = RuntimeEnvironment.getApplication(),
            debug = null,
            onConsentChanged = { seeded = it },
            consentInformation = FakeConsentInformation(allows = true),
        )
        assertEquals(true, seeded)
    }

    @Test
    fun `seeds onConsentChanged(false) synchronously when canRequestAds is not yet true at creation`() {
        var seeded: Boolean? = null
        AdMobConsentManager(
            context = RuntimeEnvironment.getApplication(),
            debug = null,
            onConsentChanged = { seeded = it },
            consentInformation = FakeConsentInformation(allows = false),
        )
        assertEquals(false, seeded)
    }
}
