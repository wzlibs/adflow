package com.adflow.core.config

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryPolicyTest {
    @Test
    fun `default policy backs off 5s 10s 20s 40s 60s`() {
        val policy = RetryPolicy.DEFAULT
        assertEquals(5_000L, policy.delayForAttempt(1))
        assertEquals(10_000L, policy.delayForAttempt(2))
        assertEquals(20_000L, policy.delayForAttempt(3))
        assertEquals(40_000L, policy.delayForAttempt(4))
        assertEquals(60_000L, policy.delayForAttempt(5))
    }

    @Test
    fun `delay is capped at maxDelayMs beyond the cap point`() {
        val policy = RetryPolicy.DEFAULT
        assertEquals(60_000L, policy.delayForAttempt(6))
        assertEquals(60_000L, policy.delayForAttempt(10))
    }

    @Test
    fun `default policy retries without limit`() {
        assertEquals(Int.MAX_VALUE, RetryPolicy.DEFAULT.maxRetries)
    }
}
