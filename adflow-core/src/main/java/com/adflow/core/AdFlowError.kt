package com.adflow.core

/**
 * Lỗi chuẩn hóa của AdFlow. [code] là mã của AdFlow (xem [Codes]); [networkCode] là mã gốc từ
 * SDK mạng quảng cáo (ví dụ `LoadAdError.code` của AdMob) nếu có, để debug sâu.
 */
data class AdFlowError(
    val code: Int,
    val message: String,
    val networkCode: Int? = null,
) {
    object Codes {
        const val NO_FILL = 1
        const val DISABLED = 2
        const val CONSENT = 3
        const val RULE_REJECTED = 4
        const val SHOW_FAILED = 5
        const val EXPIRED = 6
    }
}
