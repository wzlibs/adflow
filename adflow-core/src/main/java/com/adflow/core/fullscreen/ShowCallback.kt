package com.adflow.core.fullscreen

import com.adflow.core.AdFlowError
import com.adflow.core.AdShowBlockedCallback
interface ShowCallback : AdShowBlockedCallback {
    fun onAdShown() {}
    fun onAdFailedToShow(error: AdFlowError) {}
    fun onAdDismissed() {}

    companion object {
        val NONE: ShowCallback = object : ShowCallback {}
    }
}
