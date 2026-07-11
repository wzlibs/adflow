package com.adflow.adflow_flutter

import android.util.Log
import com.adflow.adflow_flutter.callbacks.RewardedAdCallbackBridge
import com.adflow.adflow_flutter.callbacks.ShowCallbackBridge
import com.adflow.adflow_flutter.generated.AdFlowFlutterApi
import com.adflow.adflow_flutter.generated.AdHostApi
import com.adflow.adflow_flutter.generated.PAdType
import com.adflow.adflow_flutter.generated.PShowEventKind
import com.adflow.core.AdFlow
import com.adflow.core.AdFlowError

private const val TAG = "AdFlowFlutter"

/**
 * Thao tác theo `placementId` cho mọi loại ad - [FlutterBridgeState.placementTypes] (build lúc
 * `AdFlowHostApi.initialize()`) cho biết id đó thuộc loại nào để gọi đúng
 * `AdFlow.interstitial/appOpen/rewarded/banner/native()`. Dart chặn trước bằng type system (mỗi
 * handle chỉ lộ method hợp lệ - vd handle Banner không có `show()`), nên các nhánh "sai loại" ở
 * đây chỉ là phòng thủ cho lỗi lập trình, không phải luồng chính.
 */
class AdHostApiImpl(
    private val state: FlutterBridgeState,
    private val flutterApi: AdFlowFlutterApi,
) : AdHostApi {

    override fun load(placementId: String) {
        when (state.placementTypes[placementId]) {
            PAdType.INTERSTITIAL -> AdFlow.interstitial(placementId).load()
            PAdType.APP_OPEN -> AdFlow.appOpen(placementId).load()
            PAdType.REWARDED -> AdFlow.rewarded(placementId).load()
            PAdType.BANNER -> AdFlow.banner(placementId).load()
            PAdType.NATIVE -> AdFlow.native(placementId).load()
            null -> Log.w(TAG, "load('$placementId') - placementId chưa được khai báo trong AdFlow.initialize()")
        }
    }

    override fun reload(placementId: String) {
        when (state.placementTypes[placementId]) {
            PAdType.NATIVE -> AdFlow.native(placementId).reload()
            null -> Log.w(TAG, "reload('$placementId') - placementId chưa được khai báo trong AdFlow.initialize()")
            else -> Log.w(TAG, "reload('$placementId') - chỉ native ad hỗ trợ reload(), bỏ qua")
        }
    }

    override fun show(placementId: String) {
        val activity = state.currentActivity
        if (activity == null) {
            Log.w(TAG, "show('$placementId') được gọi nhưng chưa có Activity nào attach - bỏ qua")
            flutterApi.onShowEvent(placementId, PShowEventKind.FAILED_TO_SHOW, AdFlowError(-4, "no activity attached").toPigeon(), null, null) {}
            return
        }
        when (state.placementTypes[placementId]) {
            PAdType.INTERSTITIAL -> AdFlow.interstitial(placementId).show(activity, ShowCallbackBridge(placementId, flutterApi))
            PAdType.APP_OPEN -> AdFlow.appOpen(placementId).show(activity, ShowCallbackBridge(placementId, flutterApi))
            PAdType.REWARDED -> AdFlow.rewarded(placementId).show(activity, RewardedAdCallbackBridge(placementId, flutterApi))
            null -> {
                Log.w(TAG, "show('$placementId') - placementId chưa được khai báo trong AdFlow.initialize()")
                flutterApi.onShowEvent(placementId, PShowEventKind.FAILED_TO_SHOW, AdFlowError(-1, "unknown placementId '$placementId'").toPigeon(), null, null) {}
            }
            else -> {
                Log.w(TAG, "show('$placementId') - chỉ interstitial/appOpen/rewarded hỗ trợ show(), bỏ qua")
                flutterApi.onShowEvent(placementId, PShowEventKind.FAILED_TO_SHOW, AdFlowError(-2, "'$placementId' is not a full-screen ad type").toPigeon(), null, null) {}
            }
        }
    }
}
