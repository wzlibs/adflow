package com.adflow.admob.consent

import android.app.Activity
import android.content.Context
import com.adflow.core.AdFlowError
import com.adflow.core.consent.ConsentDebugConfig
import com.adflow.core.consent.ConsentDebugGeography
import com.adflow.core.consent.ConsentManager
import com.adflow.core.consent.ConsentStatus
import com.adflow.core.consent.PrivacyOptionsRequirement
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * [ConsentManager] dùng Google User Messaging Platform (UMP) - CMP tích hợp sẵn với AdMob, tự
 * phát hiện khu vực (EEA/UK) cần consent. [onConsentChanged] được gọi mỗi khi consent resolve/đổi
 * - `AdFlow.initialize()` nối nó vào `AdFlowRuntime.consentAllowsAdRequests` để mọi lượt load tự
 * động tôn trọng consent, app không cần tự viết điều kiện check riêng.
 */
internal class AdMobConsentManager(
    context: Context,
    debug: ConsentDebugConfig?,
    private val onConsentChanged: (allowsAdRequests: Boolean) -> Unit,
) : ConsentManager {

    private val appContext = context.applicationContext
    private val consentInformation: ConsentInformation = UserMessagingPlatform.getConsentInformation(appContext)

    private val debugGeography: Int? = when (debug?.geography) {
        ConsentDebugGeography.EEA -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
        ConsentDebugGeography.NOT_EEA -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_NOT_EEA
        ConsentDebugGeography.DISABLED, null -> null
    }
    private val testDeviceHashedIds: List<String> = debug?.testDeviceHashedIds ?: emptyList()

    private val _status = MutableStateFlow(mapStatus(consentInformation.consentStatus))
    override val status: StateFlow<ConsentStatus> get() = _status

    override val privacyOptionsRequirement: PrivacyOptionsRequirement
        get() = when (consentInformation.privacyOptionsRequirementStatus) {
            ConsentInformation.PrivacyOptionsRequirementStatus.NOT_REQUIRED -> PrivacyOptionsRequirement.NOT_REQUIRED
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED -> PrivacyOptionsRequirement.REQUIRED
            else -> PrivacyOptionsRequirement.UNKNOWN
        }

    override fun canRequestAds(): Boolean = consentInformation.canRequestAds()

    override fun requestIfNeeded(activity: Activity, onComplete: (AdFlowError?) -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .apply { debugSettings()?.let { setConsentDebugSettings(it) } }
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    refreshStatus()
                    onComplete(formError?.toAdFlowError())
                }
            },
            { requestError ->
                refreshStatus()
                onComplete(requestError.toAdFlowError())
            },
        )
    }

    override fun showPrivacyOptionsForm(activity: Activity, onComplete: (AdFlowError?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            refreshStatus()
            onComplete(formError?.toAdFlowError())
        }
    }

    private fun refreshStatus() {
        _status.value = mapStatus(consentInformation.consentStatus)
        onConsentChanged(canRequestAds())
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

internal fun mapStatus(raw: Int): ConsentStatus = when (raw) {
    ConsentInformation.ConsentStatus.NOT_REQUIRED -> ConsentStatus.NOT_REQUIRED
    ConsentInformation.ConsentStatus.REQUIRED -> ConsentStatus.REQUIRED
    ConsentInformation.ConsentStatus.OBTAINED -> ConsentStatus.OBTAINED
    else -> ConsentStatus.UNKNOWN
}
