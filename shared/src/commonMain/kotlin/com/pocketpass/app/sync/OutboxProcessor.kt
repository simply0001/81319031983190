package com.pocketpass.app.sync

import com.pocketpass.app.data.local.dao.OutboxDao
import com.pocketpass.app.data.local.entity.PendingOperationEntity
import com.pocketpass.app.domain.model.UserId
import kotlin.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class OutboxDrainSummary(
    val acknowledged: Int,
    val retryableFailures: Int,
    val permanentFailures: Int,
    val staleCompletions: Int,
    val reachedBatchLimit: Boolean,
    val nextAttemptAtEpochMillis: Long?,
) {
    val needsRetry: Boolean
        get() = retryableFailures > 0 || reachedBatchLimit

    val hasRemainingWork: Boolean
        get() = nextAttemptAtEpochMillis != null
}

data class OutboxRetryPolicy(
    val baseDelayMillis: Long = 10_000L,
    val maximumDelayMillis: Long = 60L * 60L * 1_000L,
) {
    init {
        require(baseDelayMillis > 0) { "Base retry delay must be positive" }
        require(maximumDelayMillis >= baseDelayMillis) {
            "Maximum retry delay must be at least the base delay"
        }
    }

    fun delayMillis(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 30)
        val multiplier = 1L shl exponent
        val uncapped = if (baseDelayMillis > Long.MAX_VALUE / multiplier) {
            Long.MAX_VALUE
        } else {
            baseDelayMillis * multiplier
        }
        return min(uncapped, maximumDelayMillis)
    }
}

class OutboxProcessor(
    private val outboxDao: OutboxDao,
    private val executor: PendingOperationExecutor,
    private val clock: Clock = Clock.System,
    private val retryPolicy: OutboxRetryPolicy = OutboxRetryPolicy(),
    private val leaseDurationMillis: Long = 2L * 60L * 1_000L,
    private val finishedRetentionMillis: Long = 24L * 60L * 60L * 1_000L,
) {
    init {
        require(leaseDurationMillis > 0) { "Outbox lease must be positive" }
        require(finishedRetentionMillis >= 0) { "Outbox retention cannot be negative" }
    }

    suspend fun drain(
        accountId: UserId,
        maximumOperations: Int = DEFAULT_BATCH_SIZE,
    ): OutboxDrainSummary {
        require(maximumOperations > 0) { "Outbox batch size must be positive" }

        var acknowledged = 0
        var retryableFailures = 0
        var permanentFailures = 0
        var staleCompletions = 0
        var claimedCount = 0

        while (claimedCount < maximumOperations) {
            val claimedAt = clock.now().toEpochMilliseconds()
            val leaseToken = Random.nextBytes(16).joinToString("") { byte ->
                (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
            }
            val operation = outboxDao.claimNext(
                accountId = accountId.value,
                nowEpochMillis = claimedAt,
                leaseUntilEpochMillis = claimedAt + leaseDurationMillis,
                leaseToken = leaseToken,
            ) ?: break
            claimedCount += 1

            val result = try {
                executor.execute(operation)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    outboxDao.releaseClaim(
                        operationId = operation.operationId,
                        leaseToken = leaseToken,
                        nextAttemptAtEpochMillis = clock.now().toEpochMilliseconds(),
                    )
                }
                throw cancelled
            } catch (error: Throwable) {
                OutboxExecutionResult.RetryableFailure(
                    code = "UNEXPECTED_EXECUTOR_FAILURE",
                    message = error.message,
                )
            }

            val recorded = withContext(NonCancellable) {
                record(operation, leaseToken, result)
            }
            when {
                !recorded -> staleCompletions += 1
                result == OutboxExecutionResult.Acknowledged -> acknowledged += 1
                result is OutboxExecutionResult.RetryableFailure -> retryableFailures += 1
                result is OutboxExecutionResult.PermanentFailure -> permanentFailures += 1
            }
        }

        outboxDao.deleteFinishedBefore(
            accountId = accountId.value,
            completedBeforeEpochMillis = clock.now().toEpochMilliseconds() - finishedRetentionMillis,
        )
        return OutboxDrainSummary(
            acknowledged = acknowledged,
            retryableFailures = retryableFailures,
            permanentFailures = permanentFailures,
            staleCompletions = staleCompletions,
            reachedBatchLimit = claimedCount == maximumOperations,
            nextAttemptAtEpochMillis = outboxDao.nextAttemptAt(accountId.value),
        )
    }

    private suspend fun record(
        operation: PendingOperationEntity,
        leaseToken: String,
        result: OutboxExecutionResult,
    ): Boolean = when (result) {
        OutboxExecutionResult.Acknowledged -> outboxDao.markSucceeded(
            operationId = operation.operationId,
            leaseToken = leaseToken,
            completedAtEpochMillis = clock.now().toEpochMilliseconds(),
        )

        is OutboxExecutionResult.RetryableFailure -> outboxDao.markRetryable(
            operationId = operation.operationId,
            leaseToken = leaseToken,
            nextAttemptAtEpochMillis = clock.now().toEpochMilliseconds() +
                retryPolicy.delayMillis(operation.attemptCount),
            errorCode = result.code,
            errorMessage = result.message,
        )

        is OutboxExecutionResult.PermanentFailure -> outboxDao.markPermanentlyFailed(
            operationId = operation.operationId,
            leaseToken = leaseToken,
            completedAtEpochMillis = clock.now().toEpochMilliseconds(),
            errorCode = result.code,
            errorMessage = result.message,
        )
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 50
    }
}
