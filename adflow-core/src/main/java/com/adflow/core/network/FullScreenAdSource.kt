package com.adflow.core.network

import android.app.Activity
import com.adflow.core.AdFlowError
import com.adflow.core.rewarded.RewardItem

/** Nguồn load 1 ad full-screen (Interstitial/App Open/Rewarded) từ SDK mạng.
 * Throw [AdLoadException] khi no-fill/lỗi. */
interface FullScreenAdSource {
    suspend fun load(request: AdRequestInfo): LoadedFullScreenAd
}

/** 1 ad full-screen đã load xong, sẵn sàng hiển thị đúng 1 lần. */
interface LoadedFullScreenAd {
    fun show(activity: Activity, listener: ShowListener)

    interface ShowListener {
        fun onShowed()
        fun onDismissed()
        fun onFailedToShow(error: AdFlowError)
        fun onImpression()
        fun onClicked()

        /** Chỉ Rewarded gọi; các loại khác không bao giờ gọi. */
        fun onUserEarnedReward(reward: RewardItem)
    }
}
