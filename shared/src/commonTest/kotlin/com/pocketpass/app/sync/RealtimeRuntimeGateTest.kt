package com.pocketpass.app.sync

import com.pocketpass.app.domain.model.UserId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RealtimeRuntimeGateTest {
    private val accountId = UserId("account")

    @Test
    fun realtimeRunsOnlyForForegroundAuthenticatedAppWithNetwork() {
        assertTrue(
            RealtimeRuntimeGate(
                accountId = accountId,
                appForeground = true,
                networkAvailable = true,
                networkGeneration = 1L,
            ).shouldRun,
        )
        assertFalse(
            RealtimeRuntimeGate(
                accountId = null,
                appForeground = true,
                networkAvailable = true,
                networkGeneration = 1L,
            ).shouldRun,
        )
        assertFalse(
            RealtimeRuntimeGate(
                accountId = accountId,
                appForeground = false,
                networkAvailable = true,
                networkGeneration = 1L,
            ).shouldRun,
        )
        assertFalse(
            RealtimeRuntimeGate(
                accountId = accountId,
                appForeground = true,
                networkAvailable = false,
                networkGeneration = 1L,
            ).shouldRun,
        )
    }

    @Test
    fun networkGenerationChangeForcesRuntimeRebuild() {
        val first = RealtimeRuntimeGate(
            accountId = accountId,
            appForeground = true,
            networkAvailable = true,
            networkGeneration = 1L,
        )
        val reconnected = first.copy(networkGeneration = 2L)

        assertNotEquals(first, reconnected)
        assertTrue(reconnected.shouldRun)
    }
}
