package com.adflow.core

interface ShowCallback {
    fun onAdShown() {}
    fun onAdFailedToShow(error: AdFlowError) {}
    fun onAdDismissed() {}
    fun onShowBlocked(reason: BlockReason) {}

    companion object {
        val NONE: ShowCallback = object : ShowCallback {}
    }
}
