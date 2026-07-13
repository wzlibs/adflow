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

    /** placementId -> enabled override, mặc định true (chưa có entry = enabled). Mỗi placement
     * đăng ký lúc `AdFlowHostApi.initialize()` gắn `loadWhen`/`showWhen` đọc qua [isEnabled] - khôi
     * phục lại `setEnabled()` theo từng placement của v1 (native v2 không tự có field `enabled`,
     * vẫn phải mô phỏng qua AdRule như bản global cũ, chỉ đổi 1 cờ chung thành map theo id). */
    private val enabledOverrides = mutableMapOf<String, Boolean>()

    fun isEnabled(placementId: String): Boolean = enabledOverrides[placementId] ?: true

    fun setEnabled(placementId: String, enabled: Boolean) {
        enabledOverrides[placementId] = enabled
    }

    /** placementId -> loại ad - build đúng 1 lần lúc `AdFlowHostApi.initialize()`, dùng để
     * [AdHostApiImpl] biết gọi `AdFlow.interstitial/appOpen/rewarded/banner/native()` nào cho
     * load()/reload()/show() theo id (Pigeon chỉ có 1 `AdHostApi` chung cho mọi loại). */
    val placementTypes = mutableMapOf<String, PAdType>()
}
