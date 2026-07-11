package com.adflow.core.config

import com.adflow.core.AdType
import com.adflow.core.banner.BannerSize
import com.adflow.core.nativead.NativeAdRenderer

/**
 * Cấu hình đã resolve của 1 placement - sản phẩm của DSL `AdFlow.initialize {}`, không phải thứ
 * app tạo trực tiếp. Internal: client chỉ nhìn thấy DSL scope + controller, không thấy class này.
 */
internal class PlacementConfig(
    val placementId: String,
    val adType: AdType,
    val adUnitIds: List<String>,
    val preload: Boolean,
    /** null = không bao giờ hết hạn (Banner). */
    val expiryMs: Long?,
    val retryPolicy: RetryPolicy,
    val loadRule: AdRule?,
    val showRule: AdRule?,
    // Chỉ có nghĩa với từng loại tương ứng - null cho các loại khác.
    val autoShowOnForeground: Boolean = false,      // App Open
    val bannerSize: BannerSize? = null,             // Banner
    val defaultRenderer: NativeAdRenderer? = null,  // Native
) {
    init {
        require(adUnitIds.isNotEmpty()) { "adUnits must not be empty for placement '$placementId'" }
    }
}
