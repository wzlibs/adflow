package com.adflow.core.logging

import com.adflow.core.AdType

fun interface AdFlowLogger {
    fun log(placementId: String, adType: AdType, event: AdFlowEvent, detail: String?)
}
