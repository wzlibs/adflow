package com.adflow.core.config

data class PlacementConfig(
    val placementId: String,
    val enabled: Boolean = true,
    val preloadEnabled: Boolean = true,
    val adUnitIds: List<String>,
    val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
    val loadRule: AdRule? = null,
    val showRule: AdRule? = null,
    val expiryMs: Long = 4 * 60 * 60 * 1000L,
) {
    init {
        require(adUnitIds.isNotEmpty()) { "adUnitIds must not be empty for placement '$placementId'" }
    }
}
