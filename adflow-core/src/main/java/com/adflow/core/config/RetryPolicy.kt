package com.adflow.core.config

import kotlin.math.pow

/**
 * Chính sách retry cho 1 lượt load: khi cả waterfall no-fill, chờ [delayForAttempt] rồi thử lại
 * toàn bộ waterfall, tối đa [maxRetries] chu kỳ.
 *
 * Mặc định HỮU HẠN 3 chu kỳ (backoff 5s/10s/20s) rồi kết thúc `Failed(willRetry = false)` - không
 * retry vô hạn nền. Placement không kẹt chết ở Failed: một lượt load mới (đếm lại từ 0) tự mở khi
 * có nhu cầu thật - view attach, show() self-heal, load()/reload() thủ công, app quay lại
 * foreground với placement preload. App muốn hành vi vô hạn vẫn set `maxRetries = Int.MAX_VALUE`.
 */
data class RetryPolicy(
    val initialDelayMs: Long = 5_000,
    val multiplier: Double = 2.0,
    val maxDelayMs: Long = 60_000,
    val maxRetries: Int = 3,
) {
    fun delayForAttempt(attempt: Int): Long {
        val raw = initialDelayMs * multiplier.pow((attempt - 1).coerceAtLeast(0))
        return raw.toLong().coerceAtMost(maxDelayMs)
    }

    companion object {
        val DEFAULT = RetryPolicy()
    }
}
