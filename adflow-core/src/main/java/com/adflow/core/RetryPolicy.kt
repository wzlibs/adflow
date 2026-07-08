package com.adflow.core

import kotlin.math.pow

data class RetryPolicy(
    val initialDelayMs: Long = 5_000,
    val multiplier: Double = 2.0,
    val maxDelayMs: Long = 60_000,
    // Không giới hạn theo mặc định - waterfall cứ backoff (tối đa maxDelayMs) và thử lại mãi cho
    // tới khi có fill, thay vì bỏ cuộc hẳn. Int.MAX_VALUE thay vì làm optional/nullable để giữ
    // nguyên logic so sánh "retryAttempt > maxRetries" ở RetryingAdLoader, không cần đặc cách.
    val maxRetries: Int = Int.MAX_VALUE,
) {
    fun delayForAttempt(attempt: Int): Long {
        val raw = initialDelayMs * multiplier.pow((attempt - 1).coerceAtLeast(0))
        return raw.toLong().coerceAtMost(maxDelayMs)
    }

    companion object {
        val DEFAULT = RetryPolicy()
    }
}
