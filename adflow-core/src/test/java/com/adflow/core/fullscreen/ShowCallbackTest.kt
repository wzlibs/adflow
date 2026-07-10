package com.adflow.core.fullscreen

import com.adflow.core.AdFlowError
import com.adflow.core.BlockReason
import org.junit.Assert.assertEquals
import org.junit.Test

class ShowCallbackTest {
    @Test
    fun `NONE default methods do not throw`() {
        var blockedReason: BlockReason? = null
        val callback = object : ShowCallback {
            override fun onShowBlocked(reason: BlockReason) {
                blockedReason = reason
            }
        }
        ShowCallback.NONE.onAdShown()
        ShowCallback.NONE.onAdDismissed()
        ShowCallback.NONE.onAdFailedToShow(AdFlowError(1, "x"))
        callback.onShowBlocked(BlockReason.NOT_READY)
        assertEquals(BlockReason.NOT_READY, blockedReason)
    }
}
