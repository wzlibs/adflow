package com.adflow.adflow_flutter.platformview

import com.adflow.adflow_flutter.PlacementRegistry
import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.PShowEventKind
import com.adflow.adflow_flutter.toPigeon
import com.adflow.core.BlockReason

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

/**
 * Forward lý do bị chặn của `createView()`/`getView()` (Native/Banner) sang Dart qua cùng kênh
 * [AdFlowFlutterApi.onShowEvent] mà Interstitial/AppOpen/Rewarded đã dùng cho `show()` (xem
 * `callbacks/ShowCallbackBridge.kt`) - tái dùng [PShowEventKind.SHOW_BLOCKED] và
 * [BlockReason.toPigeon] sẵn có, không cần thêm event kind mới.
 */
internal fun showBlockedReporter(placementId: String, flutterApi: AdFlowFlutterApi): (BlockReason) -> Unit =
    { reason -> flutterApi.onShowEvent(placementId, PShowEventKind.SHOW_BLOCKED, null, reason.toPigeon(), null) {} }
