package com.adflow.adflow_flutter

import android.app.Activity
import android.util.Log
import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.PShowEventKind
import com.adflow.core.BlockReason

private const val TAG = "AdFlowFlutter"

/**
 * Gate dùng chung cho show() của Interstitial/AppOpen/Rewarded: chặn ngay nếu placement bị tắt
 * qua setEnabled(false) (thay AdRule - xem PlacementRegistry), hoặc nếu chưa có Activity nào attach
 * (quá sớm, hoặc Flutter engine chạy headless) - log cảnh báo, không crash. [show] chỉ chạy khi cả
 * 2 điều kiện trên đều qua.
 */
internal inline fun showGated(
    registry: PlacementRegistry,
    flutterApi: AdFlowFlutterApi,
    placementId: String,
    hasManager: Boolean,
    show: (Activity) -> Unit,
) {
    if (!hasManager) return
    if (!registry.isEnabled(placementId)) {
        flutterApi.onShowEvent(placementId, PShowEventKind.SHOW_BLOCKED, null, BlockReason.DISABLED.toPigeon(), null) {}
        return
    }
    val activity = registry.currentActivity
    if (activity == null) {
        Log.w(TAG, "show($placementId) được gọi nhưng chưa có Activity nào attach - bỏ qua")
        return
    }
    show(activity)
}
