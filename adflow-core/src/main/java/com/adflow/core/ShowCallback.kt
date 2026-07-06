package com.adflow.core

interface ShowCallback : AdShowBlockedCallback {
    fun onAdShown() {}
    fun onAdFailedToShow(error: AdFlowError) {}
    fun onAdDismissed() {}

    companion object {
        val NONE: ShowCallback = object : ShowCallback {}
    }
}
