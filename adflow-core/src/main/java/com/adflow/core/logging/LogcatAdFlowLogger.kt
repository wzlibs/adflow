package com.adflow.core.logging

import android.util.Log
import com.adflow.core.AdType

class LogcatAdFlowLogger(private val tag: String = "AdFlowSDK") : AdFlowLogger {
    override fun log(placementId: String, adType: AdType, event: AdFlowEvent, detail: String?) {
        Log.d(tag, "[$adType/$placementId] $event${detail?.let { " ($it)" } ?: ""}")
    }
}
