package com.adflow.core.consent

import android.app.Activity
import com.adflow.core.AdFlowError
import kotlinx.coroutines.flow.StateFlow

/**
 * Cầu nối tới CMP (Google UMP...) cho GDPR. Primitive độc lập - app chọn Activity nào tiện để gọi
 * [requestIfNeeded] (thường là Activity đầu tiên), không có vị trí bắt buộc. Sau khi resolve, mọi
 * lượt load tự động tôn trọng consent - app không phải tự viết điều kiện check.
 */
interface ConsentManager {
    val status: StateFlow<ConsentStatus>

    val privacyOptionsRequirement: PrivacyOptionsRequirement

    fun canRequestAds(): Boolean

    /** Xin consent nếu cần (no-op nếu ngoài vùng yêu cầu) - hiện form qua [activity] khi cần.
     * [onComplete] nhận null nếu thành công. */
    fun requestIfNeeded(activity: Activity, onComplete: (AdFlowError?) -> Unit = {})

    /** Cho user xem lại/đổi lựa chọn consent - chỉ nên hiện lối vào khi
     * [privacyOptionsRequirement] là [PrivacyOptionsRequirement.REQUIRED]. */
    fun showPrivacyOptionsForm(activity: Activity, onComplete: (AdFlowError?) -> Unit = {})
}
