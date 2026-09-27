package com.pocketpass.app.sync

import com.pocketpass.app.domain.model.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class OutboxDrainSchedulerTest {
    @Test
    fun drainsAgainWhenTheNextRetryIsDue() = runTest {
        val drains = mutableListOf<Long>()
        val remaining = ArrayDeque(listOf<Long?>(5_000L, null))
        val scheduler = OutboxDrainScheduler(backgroundScope, testClock()) {
            drains += testScheduler.currentTime
            summary(nextAttemptAtEpochMillis = remaining.removeFirstOrNull())
        }

        scheduler.schedule(ACCOUNT)
        runCurrent()
        assertEquals(listOf(0L), drains)

        advanceTimeBy(4_999L)
        assertEquals(listOf(0L), drains)

        advanceTimeBy(2L)
        assertEquals(listOf(0L, 5_000L), drains)

        advanceTimeBy(60_000L)
        assertEquals(listOf(0L, 5_000L), drains)
    }

    @Test
    fun wakeUpsDuringADrainCollapseIntoOneFollowUp() = runTest {
        var drains = 0
        lateinit var scheduler: OutboxDrainScheduler
        scheduler = OutboxDrainScheduler(backgroundScope, testClock()) {
            drains += 1
            if (drains == 1) {
                scheduler.schedule(ACCOUNT)
                scheduler.schedule(ACCOUNT)
                delay(100L)
            }
            summary(nextAttemptAtEpochMillis = null)
        }

        scheduler.schedule(ACCOUNT)
        advanceTimeBy(1_000L)

        assertEquals(2, drains)
    }

    @Test
    fun aFailedDrainWaitsForTheNextWakeUp() = runTest {
        var drains = 0
        val scheduler = OutboxDrainScheduler(backgroundScope, testClock()) {
            drains += 1
            error("Database unavailable")
        }

        scheduler.schedule(ACCOUNT)
        runCurrent()
        advanceTimeBy(60_000L)
        assertEquals(1, drains)

        scheduler.schedule(ACCOUNT)
        runCurrent()
        assertEquals(2, drains)
    }

    private fun TestScope.testClock(): Clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(testScheduler.currentTime)
    }

    private fun summary(nextAttemptAtEpochMillis: Long?) = OutboxDrainSummary(
        acknowledged = 0,
        retryableFailures = 0,
        permanentFailures = 0,
        staleCompletions = 0,
        reachedBatchLimit = false,
        nextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
    )

    private companion object {
        val ACCOUNT = UserId("account")
    }
}
