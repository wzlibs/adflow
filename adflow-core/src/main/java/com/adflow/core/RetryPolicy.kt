package com.adflow.core

import kotlin.math.pow

data class RetryPolicy(
    val initialDelayMs: Long = 5_000,
    val multiplier: Double = 2.0,
    val maxDelayMs: Long = 60_000,
    val maxRetries: Int = 5,
) {
    fun delayForAttempt(attempt: Int): Long {
        val raw = initialDelayMs * multiplier.pow((attempt - 1).coerceAtLeast(0))
        return raw.toLong().coerceAtMost(maxDelayMs)
    }

    companion object {
        val DEFAULT = RetryPolicy()
    }
}
