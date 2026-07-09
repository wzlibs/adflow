package com.adflow.adflow_flutter

import android.app.Activity
import android.util.Log
import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.PLoadResult
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
    // Dart show() chỉ hoàn tất Future qua onShowEvent (xem show_event_support.dart) - mọi nhánh
    // chặn ở đây đều phải gửi 1 event, nếu không await ad.show() sẽ treo vĩnh viễn. hasManager=false
    // thường là lỗi gọi sai placementId; activity=null thì hoàn toàn có thể xảy ra bình thường
    // (xoay màn hình, hoặc app bị background giữa lúc load() xong và show() được gọi).
    if (!hasManager) {
        flutterApi.onShowEvent(placementId, PShowEventKind.SHOW_BLOCKED, null, BlockReason.NOT_READY.toPigeon(), null) {}
        return
    }
    if (!registry.isEnabled(placementId)) {
        flutterApi.onShowEvent(placementId, PShowEventKind.SHOW_BLOCKED, null, BlockReason.DISABLED.toPigeon(), null) {}
        return
    }
    val activity = registry.currentActivity
    if (activity == null) {
        Log.w(TAG, "show($placementId) được gọi nhưng chưa có Activity nào attach - bỏ qua")
        flutterApi.onShowEvent(placementId, PShowEventKind.SHOW_BLOCKED, null, BlockReason.NOT_READY.toPigeon(), null) {}
        return
    }
    show(activity)
}

/**
 * Gate dùng chung cho load()/reload() của mọi loại ad: chặn ngay, không chạm tới manager, nếu
 * không có manager (thường do sai placementId) hoặc placement đang bị tắt qua setEnabled(false).
 * Trước đây setEnabled(false) chỉ chặn show() - load() vẫn âm thầm chạy nền, tốn ad request vô ích
 * khi app đã tắt hẳn 1 placement (vd user lên VIP). Cùng 1 gate với [showGated] để setEnabled() giữ
 * đúng lời hứa "tắt hẳn" cho mọi loại ad, kể cả Banner/Native (vốn không có show() riêng).
 */
internal inline fun loadGated(
    registry: PlacementRegistry,
    placementId: String,
    hasManager: Boolean,
    callback: (Result<PLoadResult>) -> Unit,
    load: () -> Unit,
) {
    if (!hasManager || !registry.isEnabled(placementId)) {
        callback(Result.success(PLoadResult(success = false, error = null)))
        return
    }
    load()
}
