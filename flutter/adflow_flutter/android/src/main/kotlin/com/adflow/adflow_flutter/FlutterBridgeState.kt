package com.adflow.adflow_flutter

import android.app.Activity
import android.app.Application
import android.content.Context
import com.adflow.adflow_flutter.generated.PAdType

/**
 * State nhẹ dùng chung giữa các HostApiImpl - không giữ manager thật nào: `AdFlow` (adflow-core)
 * đã là registry duy nhất cho mọi placement (xem `AdFlow.interstitial/appOpen/rewarded/banner/
 * native()`).
 */
class FlutterBridgeState(context: Context) {
    val application: Application = context.applicationContext as Application

    /** Activity hiện đang gắn với Flutter engine - cập nhật bởi [AdflowFlutterPlugin] (ActivityAware).
     * show()/consent cần Activity thật; null nghĩa là chưa có Activity nào attach (quá sớm, hoặc
     * Flutter engine chạy headless). */
    var currentActivity: Activity? = null

    /** true (mặc định) - mỗi placement đăng ký lúc `AdFlowHostApi.initialize()` gắn `loadWhen`/
     * `showWhen` đọc field này, mô phỏng `AdFlow.setAdsEnabled()` toàn cục thay cho `enabled` đã bỏ
     * ở native v2 (AdRule không bridge qua channel được cho từng placement). */
    @Volatile var adsEnabled: Boolean = true

    /** placementId -> loại ad - build đúng 1 lần lúc `AdFlowHostApi.initialize()`, dùng để
     * [AdHostApiImpl] biết gọi `AdFlow.interstitial/appOpen/rewarded/banner/native()` nào cho
     * load()/reload()/show() theo id (Pigeon chỉ có 1 `AdHostApi` chung cho mọi loại). */
    val placementTypes = mutableMapOf<String, PAdType>()
}
