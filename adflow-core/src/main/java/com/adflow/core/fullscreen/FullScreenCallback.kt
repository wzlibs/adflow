package com.adflow.core.fullscreen

import com.adflow.core.AdFlowError
import com.adflow.core.BlockReason

/** Callback cho 1 lần show() cụ thể - kiểu AdMob `FullScreenContentCallback`. `show()` không bao
 * giờ throw: bị chặn thì báo qua [onAdBlocked] với lý do cụ thể. */
interface FullScreenCallback {
    fun onAdShowed() {}
    fun onAdDismissed() {}
    fun onAdFailedToShow(error: AdFlowError) {}
    fun onAdBlocked(reason: BlockReason) {}
    fun onAdImpression() {}
    fun onAdClicked() {}

    companion object {
        val EMPTY: FullScreenCallback = object : FullScreenCallback {}
    }
}
