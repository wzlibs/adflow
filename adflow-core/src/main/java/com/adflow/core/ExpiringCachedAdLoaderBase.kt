package com.adflow.core

/**
 * Adds expiry to [SimpleCachedAdLoaderBase]'s load/cache/retry lifecycle: drops the cached ad once
 * it's past [PlacementConfig.expiryMs] instead of holding onto a stale reference until the next
 * successful load overwrites it.
 *
 * Shared by every ad type that goes stale once cached: full-screen ads and Rewarded (via
 * [CachedAdLoaderBase], which additionally adds the show()-consumption helpers those "shown once"
 * ad types need) and Native (directly - a native ad can be bound to multiple views without being
 * "consumed" the way a full-screen ad is on `show()`, so it has no use for
 * [CachedAdLoaderBase]'s `consumeCachedAd()`/`preloadIfEnabled()`). Banner is the only ad type that
 * never expires, per the design's Global Constraint - a banner is meant to be displayed and
 * self-refreshed by the SDK immediately upon load, not cached ahead of time and shown later - so it
 * stays on [SimpleCachedAdLoaderBase] directly.
 */
abstract class ExpiringCachedAdLoaderBase<TAd : Any>(
    config: PlacementConfig,
    adType: AdType,
) : SimpleCachedAdLoaderBase<TAd>(config, adType) {

    var nowProvider: () -> Long = { System.currentTimeMillis() }

    private var loadedAtMs: Long = 0L

    override fun onLoaded(ad: TAd) {
        loadedAtMs = nowProvider()
    }

    override fun isReady(): Boolean {
        val ageMs = nowProvider() - loadedAtMs
        return cachedAd != null && ageMs < config.expiryMs
    }

    /** Drops the cached ad once it's past [PlacementConfig.expiryMs], rather than holding onto a
     * stale reference until the next successful load overwrites it. */
    protected fun dropIfExpired() {
        if (cachedAd != null && nowProvider() - loadedAtMs >= config.expiryMs) {
            cachedAd = null
        }
    }
}
