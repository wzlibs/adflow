package com.adflow.core.engine

import com.adflow.core.AdType
import com.adflow.core.config.ShowIntervalConfig

/**
 * Giới hạn tần suất hiển thị giữa Interstitial và App Open (cả cùng loại lẫn chéo loại) - no-op
 * cho Rewarded/Native/Banner. Instance (không phải object toàn cục) để test không cần reset state
 * chung; [AdFlowRuntime] sở hữu đúng 1 instance cho cả process.
 */
internal class ShowIntervalPolicy(
    private val config: ShowIntervalConfig,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var lastInterstitialDismissedAt: Long? = null
    private var lastAppOpenDismissedAt: Long? = null

    fun canShow(type: AdType): Boolean {
        val now = clock()
        return when (type) {
            AdType.INTERSTITIAL -> {
                val sameOk = lastInterstitialDismissedAt?.let { now - it >= config.interstitialAfterInterstitialMs } ?: true
                val crossOk = lastAppOpenDismissedAt?.let { now - it >= config.interstitialAfterAppOpenMs } ?: true
                sameOk && crossOk
            }
            AdType.APP_OPEN -> {
                val sameOk = lastAppOpenDismissedAt?.let { now - it >= config.appOpenAfterAppOpenMs } ?: true
                val crossOk = lastInterstitialDismissedAt?.let { now - it >= config.appOpenAfterInterstitialMs } ?: true
                sameOk && crossOk
            }
            else -> true
        }
    }

    /** Gọi khi ad được ĐÓNG (dismiss) - đồng hồ cooldown tính từ lúc user xem xong, không phải
     * lúc gọi show(), vì thời lượng hiển thị không do ta kiểm soát. */
    fun recordDismissed(type: AdType) {
        val now = clock()
        when (type) {
            AdType.INTERSTITIAL -> lastInterstitialDismissedAt = now
            AdType.APP_OPEN -> lastAppOpenDismissedAt = now
            else -> Unit
        }
    }
}
