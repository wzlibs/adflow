package com.adflow.admob.consent

import com.adflow.core.consent.ConsentStatus
import com.google.android.ump.ConsentInformation
import org.junit.Assert.assertEquals
import org.junit.Test

class AdMobConsentManagerTest {

    @Test
    fun `mapStatus maps every UMP consent status constant`() {
        assertEquals(ConsentStatus.NOT_REQUIRED, mapStatus(ConsentInformation.ConsentStatus.NOT_REQUIRED))
        assertEquals(ConsentStatus.REQUIRED, mapStatus(ConsentInformation.ConsentStatus.REQUIRED))
        assertEquals(ConsentStatus.OBTAINED, mapStatus(ConsentInformation.ConsentStatus.OBTAINED))
        assertEquals(ConsentStatus.UNKNOWN, mapStatus(-1))
    }
}
