package com.adflow.core

interface AdFlowLogger {
    fun log(placementId: String, adType: AdType, event: AdFlowEvent, detail: String? = null)
}
