package com.adflow.core

data class ShowIntervalConfig(
    val interstitialAfterInterstitialMs: Long = 5_000,
    val appOpenAfterAppOpenMs: Long = 5_000,
    val interstitialAfterAppOpenMs: Long = 3_000,
    val appOpenAfterInterstitialMs: Long = 2_000,
)
