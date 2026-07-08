package com.adflow.core

import android.util.Log

class LogcatAdFlowLogger(private val tag: String = "AdFlowSDK") : AdFlowLogger {
    override fun log(placementId: String, adType: AdType, event: AdFlowEvent, detail: String?) {
        Log.d(tag, "[$adType/$placementId] $event${detail?.let { " ($it)" } ?: ""}")
    }
}
