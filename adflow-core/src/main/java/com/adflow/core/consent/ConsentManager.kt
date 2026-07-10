package com.adflow.core.consent

import android.app.Activity
import com.adflow.core.AdFlowCore
import com.adflow.core.AdFlowError
import com.adflow.core.engine.SimpleCachedAdLoaderBase

/**
 * Cầu nối tới 1 CMP (Consent Management Platform, ví dụ Google UMP) cho GDPR/quyền riêng tư.
 * Đây là 1 primitive độc lập - app tự quyết định gọi [requestConsentIfNeeded] ở Activity nào/lúc
 * nào tuỳ ý, không có vị trí bắt buộc. Sau khi resolve, implementation tự cập nhật
 * [AdFlowCore.consentAllowsAdRequests] - mọi placement tự động tôn trọng consent ngay trong `load()`
 * ([SimpleCachedAdLoaderBase.load]), app không cần tự viết điều kiện check riêng.
 */
interface ConsentManager {
    fun getConsentStatus(): ConsentStatus

    fun getPrivacyOptionsRequirement(): PrivacyOptionsRequirement

    fun canRequestAds(): Boolean

    /** Xin consent nếu cần (no-op nếu không thuộc khu vực cần, ví dụ ngoài EEA/UK) - hiển thị form
     * qua [activity] khi cần. [onComplete] nhận `null` nếu thành công, hoặc lỗi nếu có. */
    fun requestConsentIfNeeded(activity: Activity, onComplete: (AdFlowError?) -> Unit)

    /** Cho user xem lại/đổi lựa chọn consent đã có - chỉ nên hiển thị lối vào này khi
     * [getPrivacyOptionsRequirement] trả về [PrivacyOptionsRequirement.REQUIRED]. */
    fun showPrivacyOptionsForm(activity: Activity, onComplete: (AdFlowError?) -> Unit)
}
