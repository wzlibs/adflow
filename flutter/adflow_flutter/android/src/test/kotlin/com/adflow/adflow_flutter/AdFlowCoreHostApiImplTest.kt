package com.adflow.adflow_flutter

import android.app.Application
import android.content.Context
import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Chỉ test nhánh "chưa có Activity attach" của requestConsentIfNeeded()/showPrivacyOptionsForm() -
 * nhánh còn lại (và getConsentStatus()/getPrivacyOptionsRequirement()/canRequestAds()) đều dựng
 * AdMobConsentManager thật, gọi thẳng UserMessagingPlatform.getConsentInformation() (Google UMP SDK
 * thật) - không unit-test được trong JVM thuần, cùng lý do AdMobProvider không unit-test được (xem
 * InterstitialAdHostApiImplTest.kt) - đã verify thủ công qua adb logcat trên device thật.
 */
class AdFlowCoreHostApiImplTest {

    private fun newRegistry(): PlacementRegistry {
        val application = mock<Application>()
        val context = mock<Context> { whenever(it.applicationContext).thenReturn(application) }
        return PlacementRegistry(context)
    }

    @Test
    fun `requestConsentIfNeeded reports an error and never touches UMP when no activity is attached`() {
        val registry = newRegistry()
        val flutterApi = mock<AdFlowFlutterApi>()
        val impl = AdFlowCoreHostApiImpl(registry, flutterApi)

        var result: Result<com.adflow.adflow_flutter.generated.PAdFlowError?>? = null
        impl.requestConsentIfNeeded(null, emptyList()) { result = it }

        assertEquals(true, result?.isSuccess)
        assertEquals(-4L, result?.getOrNull()?.code)
    }

    @Test
    fun `showPrivacyOptionsForm reports an error and never touches UMP when no activity is attached`() {
        val registry = newRegistry()
        val flutterApi = mock<AdFlowFlutterApi>()
        val impl = AdFlowCoreHostApiImpl(registry, flutterApi)

        var result: Result<com.adflow.adflow_flutter.generated.PAdFlowError?>? = null
        impl.showPrivacyOptionsForm { result = it }

        assertEquals(true, result?.isSuccess)
        assertEquals(-4L, result?.getOrNull()?.code)
    }
}
