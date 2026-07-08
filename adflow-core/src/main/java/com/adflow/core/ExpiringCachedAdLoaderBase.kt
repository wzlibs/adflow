package com.adflow.core

/**
 * Bổ sung expiry vào vòng đời load/cache/retry của [SimpleCachedAdLoaderBase]: drop cached ad khi
 * đã quá [PlacementConfig.expiryMs] thay vì giữ một reference cũ (stale) cho đến khi lần load
 * thành công tiếp theo ghi đè lên nó.
 *
 * Dùng chung cho mọi loại ad bị "cũ" (stale) sau khi cache: full-screen ad và Rewarded (qua
 * [CachedAdLoaderBase], có thêm các helper "tiêu thụ" khi show() mà các loại ad "chỉ show 1 lần"
 * này cần) và Native (trực tiếp - một native ad có thể gắn vào nhiều view mà không bị "tiêu thụ"
 * như full-screen ad khi `show()`, nên không cần đến `consumeCachedAd()`/`preloadIfEnabled()` của
 * [CachedAdLoaderBase]). Banner là loại ad duy nhất không bao giờ hết hạn, theo Global Constraint
 * của thiết kế - banner được hiển thị và tự refresh bởi SDK ngay khi load xong, không cache trước
 * rồi show sau - nên nó dùng trực tiếp [SimpleCachedAdLoaderBase].
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

    /** Drop cached ad khi đã quá [PlacementConfig.expiryMs], thay vì giữ một reference cũ (stale)
     * cho đến khi lần load thành công tiếp theo ghi đè lên nó. */
    protected fun dropIfExpired() {
        val ad = cachedAd
        if (ad != null && nowProvider() - loadedAtMs >= config.expiryMs) {
            cachedAd = null
            onDrop(ad)
        }
    }
}
