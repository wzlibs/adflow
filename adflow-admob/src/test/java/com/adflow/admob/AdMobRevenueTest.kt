package com.adflow.admob

import com.adflow.core.AdType
import com.adflow.core.network.AdRequestInfo
import com.google.android.gms.ads.AdValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AdMobRevenueTest {

    // AdValue không có constructor public - mock lại 3 getter nó có, đủ cho mapRevenue().
    private fun fakeAdValue(precisionType: Int, valueMicros: Long, currencyCode: String): AdValue =
        mock<AdValue>().apply {
            whenever(this.precisionType).thenReturn(precisionType)
            whenever(this.valueMicros).thenReturn(valueMicros)
            whenever(this.currencyCode).thenReturn(currencyCode)
        }

    @Test
    fun `mapRevenue carries every field from the request and AdValue`() {
        val request = AdRequestInfo(
            placementId = "home_banner",
            adType = AdType.BANNER,
            adUnitId = "unit-1",
            onRevenue = {},
        )
        val adValue = fakeAdValue(AdValue.PrecisionType.ESTIMATED, 1_500_000L, "USD")

        val event = mapRevenue(request, adValue, responseInfo = null)

        assertEquals("home_banner", event.placementId)
        assertEquals(AdType.BANNER, event.adType)
        assertEquals("unit-1", event.adUnitId)
        assertEquals(1_500_000L, event.valueMicros)
        assertEquals("USD", event.currencyCode)
        assertEquals("ESTIMATED", event.precision)
        assertNull(event.adNetwork) // responseInfo null -> không có tên mạng
    }

    @Test
    fun `precisionName maps every AdMob precision constant`() {
        assertEquals("ESTIMATED", precisionName(AdValue.PrecisionType.ESTIMATED))
        assertEquals("PUBLISHER_PROVIDED", precisionName(AdValue.PrecisionType.PUBLISHER_PROVIDED))
        assertEquals("PRECISE", precisionName(AdValue.PrecisionType.PRECISE))
        assertEquals("UNKNOWN", precisionName(-1))
    }
}
