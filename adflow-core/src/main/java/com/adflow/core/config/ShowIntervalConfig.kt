package com.adflow.core.config

/** Khoảng nghỉ tối thiểu giữa các lần hiển thị Interstitial/App Open - tính từ lúc ad TRƯỚC được
 * đóng (dismiss), không phải lúc gọi show(). */
data class ShowIntervalConfig(
    val interstitialAfterInterstitialMs: Long = 30_000,
    val appOpenAfterAppOpenMs: Long = 30_000,
    val interstitialAfterAppOpenMs: Long = 6_000,
    val appOpenAfterInterstitialMs: Long = 6_000,
)
