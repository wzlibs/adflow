package com.adflow.core.config

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `delayForAttempt doubles each attempt and caps at maxDelayMs`() {
        val policy = RetryPolicy(initialDelayMs = 5_000, multiplier = 2.0, maxDelayMs = 60_000, maxRetries = 10)

        assertEquals(5_000L, policy.delayForAttempt(1))
        assertEquals(10_000L, policy.delayForAttempt(2))
        assertEquals(20_000L, policy.delayForAttempt(3))
        assertEquals(40_000L, policy.delayForAttempt(4))
        assertEquals(60_000L, policy.delayForAttempt(5)) // 80_000 bị cap về maxDelayMs
        assertEquals(60_000L, policy.delayForAttempt(6))
    }

    @Test
    fun `default policy is 3 finite retries starting at 5s`() {
        val policy = RetryPolicy.DEFAULT

        assertEquals(3, policy.maxRetries)
        assertEquals(5_000L, policy.delayForAttempt(1))
    }
}
