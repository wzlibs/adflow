package com.adflow.admob.consent

import android.app.Activity
import android.content.Context
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.ConsentManager
import com.adflow.core.ConsentStatus
import com.adflow.core.PrivacyOptionsRequirement
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

/**
 * [ConsentManager] dùng Google User Messaging Platform (UMP) - CMP chính thức tích hợp sẵn với
 * AdMob, tự phát hiện khu vực (EEA/UK) cần consent, không cần app tự viết logic phát hiện vùng.
 *
 * [debugGeography]/[testDeviceHashedIds] chỉ dùng để test flow EEA khi không ở EEA thật (xem
 * README) - chi tiết riêng của UMP, không đưa lên interface [ConsentManager] chung.
 */
class AdMobConsentManager(
    context: Context,
    // Int constant từ ConsentDebugSettings.DebugGeography (vd DEBUG_GEOGRAPHY_EEA) - đây là
    // @IntDef, không phải enum class thật.
    private val debugGeography: Int? = null,
    private val testDeviceHashedIds: List<String> = emptyList(),
) : ConsentManager {

    private val appContext: Context = context.applicationContext
    private val consentInformation: ConsentInformation = UserMessagingPlatform.getConsentInformation(appContext)

    override fun getConsentStatus(): ConsentStatus =
        when (consentInformation.consentStatus) {
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> ConsentStatus.NOT_REQUIRED
            ConsentInformation.ConsentStatus.REQUIRED -> ConsentStatus.REQUIRED
            ConsentInformation.ConsentStatus.OBTAINED -> ConsentStatus.OBTAINED
            else -> ConsentStatus.UNKNOWN
        }

    override fun getPrivacyOptionsRequirement(): PrivacyOptionsRequirement =
        when (consentInformation.privacyOptionsRequirementStatus) {
            ConsentInformation.PrivacyOptionsRequirementStatus.NOT_REQUIRED -> PrivacyOptionsRequirement.NOT_REQUIRED
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED -> PrivacyOptionsRequirement.REQUIRED
            else -> PrivacyOptionsRequirement.UNKNOWN
        }

    override fun canRequestAds(): Boolean = consentInformation.canRequestAds()

    override fun requestConsentIfNeeded(activity: Activity, onComplete: (AdFlowError?) -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .apply { debugSettings()?.let { setConsentDebugSettings(it) } }
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    AdFlowCore.updateConsent(canRequestAds())
                    onComplete(formError?.toAdFlowError())
                }
            },
            { requestError ->
                AdFlowCore.updateConsent(canRequestAds())
                onComplete(requestError.toAdFlowError())
            },
        )
    }

    override fun showPrivacyOptionsForm(activity: Activity, onComplete: (AdFlowError?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            AdFlowCore.updateConsent(canRequestAds())
            onComplete(formError?.toAdFlowError())
        }
    }

    private fun debugSettings(): ConsentDebugSettings? {
        if (debugGeography == null && testDeviceHashedIds.isEmpty()) return null
        return ConsentDebugSettings.Builder(appContext)
            .apply {
                debugGeography?.let { setDebugGeography(it) }
                testDeviceHashedIds.forEach { addTestDeviceHashedId(it) }
            }
            .build()
    }

    private fun FormError.toAdFlowError(): AdFlowError = AdFlowError(errorCode, message)
}
