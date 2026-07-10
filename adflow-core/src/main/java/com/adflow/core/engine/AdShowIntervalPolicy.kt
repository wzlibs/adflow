package com.adflow.core.engine

import com.adflow.core.AdType
import com.adflow.core.config.ShowIntervalConfig
internal object AdShowIntervalPolicy {
    private var config = ShowIntervalConfig()
    private var lastInterstitialShownAt: Long? = null
    private var lastAppOpenShownAt: Long? = null

    fun configure(config: ShowIntervalConfig) {
        this.config = config
    }

    fun canShow(type: AdType, now: Long = System.currentTimeMillis()): Boolean = when (type) {
        AdType.INTERSTITIAL -> {
            val sameTypeOk = lastInterstitialShownAt?.let { now - it >= config.interstitialAfterInterstitialMs } ?: true
            val crossTypeOk = lastAppOpenShownAt?.let { now - it >= config.interstitialAfterAppOpenMs } ?: true
            sameTypeOk && crossTypeOk
        }
        AdType.APP_OPEN -> {
            val sameTypeOk = lastAppOpenShownAt?.let { now - it >= config.appOpenAfterAppOpenMs } ?: true
            val crossTypeOk = lastInterstitialShownAt?.let { now - it >= config.appOpenAfterInterstitialMs } ?: true
            sameTypeOk && crossTypeOk
        }
        else -> true
    }

    fun recordShown(type: AdType, now: Long = System.currentTimeMillis()) {
        when (type) {
            AdType.INTERSTITIAL -> lastInterstitialShownAt = now
            AdType.APP_OPEN -> lastAppOpenShownAt = now
            else -> Unit
        }
    }

    internal fun reset() {
        config = ShowIntervalConfig()
        lastInterstitialShownAt = null
        lastAppOpenShownAt = null
    }
}
