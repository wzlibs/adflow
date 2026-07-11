package com.adflow.core.engine

/**
 * Ánh xạ `placementId -> controller thật` cho mọi loại ad, dùng chung 1 namespace ID (không phân
 * biệt theo loại) - đăng ký trùng `placementId`, dù khác loại ad, đều bị từ chối ngay lúc
 * `AdFlow.initialize {}` chạy, vì string ID này còn được `AdFlowBannerView`/`AdFlowNativeAdView`
 * tham chiếu qua XML mà không có context kiểu tĩnh nào khác để phân biệt.
 */
internal class PlacementRegistry {
    private val all = mutableMapOf<String, Any>()

    fun <T : Any> register(placementId: String, controller: T): T {
        check(placementId !in all) {
            "Duplicate placementId '$placementId' - placement IDs must be unique across every ad type"
        }
        all[placementId] = controller
        return controller
    }

    inline fun <reified T : Any> get(placementId: String): T {
        val value = all[placementId]
            ?: error("No placement registered for id '$placementId' - declare it in AdFlow.initialize { }")
        return value as? T
            ?: error("Placement '$placementId' is a ${value::class.simpleName}, not a ${T::class.simpleName}")
    }

    fun all(): List<Any> = all.values.toList()
}
