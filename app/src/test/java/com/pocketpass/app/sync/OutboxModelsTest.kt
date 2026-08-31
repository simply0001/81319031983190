package com.pocketpass.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class OutboxModelsTest {
    @Test
    fun retryPolicyUsesCappedExponentialDelays() {
        val policy = OutboxRetryPolicy(
            baseDelayMillis = 1_000,
            maximumDelayMillis = 5_000,
        )

        assertEquals(1_000, policy.delayMillis(attempt = 1))
        assertEquals(2_000, policy.delayMillis(attempt = 2))
        assertEquals(4_000, policy.delayMillis(attempt = 3))
        assertEquals(5_000, policy.delayMillis(attempt = 4))
        assertEquals(5_000, policy.delayMillis(attempt = 100))
    }
}
