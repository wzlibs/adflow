package com.adflow.core

data class ShowIntervalConfig(
    val interstitialAfterInterstitialMs: Long = 30_000,
    val appOpenAfterAppOpenMs: Long = 30_000,
    val interstitialAfterAppOpenMs: Long = 6_000,
    val appOpenAfterInterstitialMs: Long = 6_000,
)
