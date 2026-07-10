package com.adflow.adflow_flutter.callbacks

import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.PShowEventKind
import com.adflow.adflow_flutter.toPigeon
import com.adflow.core.AdFlowError
import com.adflow.core.BlockReason
import com.adflow.core.fullscreen.ShowCallback

/**
 * Chuyển tiếp sự kiện show() của Interstitial/AppOpen ([ShowCallback]) sang Dart qua
 * [AdFlowFlutterApi.onShowEvent] - dùng chung [PShowEventKind] với [RewardedAdCallbackBridge] vì
 * tập sự kiện thực tế trùng nhau 4/5 (xem pigeons/adflow_api.dart).
 */
class ShowCallbackBridge(
    private val placementId: String,
    private val flutterApi: AdFlowFlutterApi,
) : ShowCallback {

    override fun onAdShown() {
        flutterApi.onShowEvent(placementId, PShowEventKind.SHOWN, null, null, null) {}
    }

    override fun onAdFailedToShow(error: AdFlowError) {
        flutterApi.onShowEvent(placementId, PShowEventKind.FAILED_TO_SHOW, error.toPigeon(), null, null) {}
    }

    override fun onAdDismissed() {
        flutterApi.onShowEvent(placementId, PShowEventKind.DISMISSED, null, null, null) {}
    }

    override fun onShowBlocked(reason: BlockReason) {
        flutterApi.onShowEvent(placementId, PShowEventKind.SHOW_BLOCKED, null, reason.toPigeon(), null) {}
    }
}
