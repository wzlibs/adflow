package com.adflow.adflow_flutter.platformview

import com.adflow.adflow_flutter.PlacementRegistry

/**
 * Trả về manager thật cho [placementId] nếu placement đó đang enabled, ngược lại `null` - factory
 * gọi hàm này thay vì tra thẳng [managers] để `setEnabled(false)` chặn được cả việc render Banner/
 * Native (trước đây 2 loại này không có gate nào cả, vì không có show() riêng để chặn như
 * Interstitial/AppOpen/Rewarded). Kết quả `null` tái dùng đúng nhánh "chưa có manager" đã có sẵn -
 * fallback về `View` rỗng, không crash. Tách khỏi `create()` để test được bằng [PlacementRegistry]
 * thật (không cần Context/View thật, không cần Robolectric).
 */
internal fun <T> enabledManager(registry: PlacementRegistry, placementId: String?, managers: Map<String, T>): T? =
    placementId?.takeIf { registry.isEnabled(it) }?.let { managers[it] }
