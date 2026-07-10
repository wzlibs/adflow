package com.adflow.core.logging

import com.adflow.core.AdType
interface AdFlowLogger {
    fun log(placementId: String, adType: AdType, event: AdFlowEvent, detail: String? = null)
}
