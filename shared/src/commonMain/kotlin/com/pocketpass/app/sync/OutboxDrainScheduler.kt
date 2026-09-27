package com.pocketpass.app.sync

import com.pocketpass.app.data.repository.PendingOperationScheduler
import com.pocketpass.app.domain.model.UserId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class OutboxDrainScheduler(
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val drain: suspend (UserId) -> OutboxDrainSummary?,
) : PendingOperationScheduler {
    private val wakeUpsLock = Mutex()
    private val wakeUps = mutableMapOf<UserId, Channel<Unit>>()

    override fun schedule(accountId: UserId) {
        scope.launch { wakeUpsFor(accountId).trySend(Unit) }
    }

    private suspend fun wakeUpsFor(accountId: UserId): Channel<Unit> = wakeUpsLock.withLock {
        wakeUps.getOrPut(accountId) {
            Channel<Unit>(Channel.CONFLATED).also { wakeUp ->
                scope.launch { drainWhenWoken(accountId, wakeUp) }
            }
        }
    }

    private suspend fun drainWhenWoken(
        accountId: UserId,
        wakeUp: ReceiveChannel<Unit>,
    ) {
        var followUpDelayMillis: Long? = null
        while (currentCoroutineContext().isActive) {
            val delayMillis = followUpDelayMillis
            if (delayMillis == null) {
                wakeUp.receive()
            } else {
                withTimeoutOrNull(delayMillis) { wakeUp.receive() }
            }
            followUpDelayMillis = followUpDelayAfter(drainOrNull(accountId))
        }
    }

    private suspend fun drainOrNull(accountId: UserId): OutboxDrainSummary? = try {
        drain(accountId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }

    private fun followUpDelayAfter(summary: OutboxDrainSummary?): Long? {
        val nextAttemptAt = summary?.nextAttemptAtEpochMillis ?: return null
        return (nextAttemptAt - clock.now().toEpochMilliseconds())
            .coerceAtLeast(MINIMUM_FOLLOW_UP_DELAY_MILLIS)
    }

    private companion object {
        const val MINIMUM_FOLLOW_UP_DELAY_MILLIS = 1_000L
    }
}
