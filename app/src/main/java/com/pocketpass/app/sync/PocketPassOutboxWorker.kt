package com.pocketpass.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pocketpass.app.domain.model.UserId

fun interface OutboxWorkRunner {
    suspend fun run(accountId: UserId): OutboxDrainSummary
}

object OutboxWorkerRuntime {
    @Volatile
    private var runner: OutboxWorkRunner? = null

    fun install(runner: OutboxWorkRunner) {
        this.runner = runner
    }

    internal fun currentRunner(): OutboxWorkRunner? = runner
}

class PocketPassOutboxWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val rawAccountId = inputData.getString(KEY_ACCOUNT_ID)
            ?: return Result.failure()
        val runner = OutboxWorkerRuntime.currentRunner()
            ?: return Result.failure()

        val accountId = UserId(rawAccountId)
        return runCatching {
            runner.run(accountId)
        }.fold(
            onSuccess = { summary ->
                if (summary.needsRetry) {
                    Result.retry()
                } else {
                    summary.nextAttemptAtEpochMillis?.let { nextAttemptAt ->
                        OutboxWorkCoordinator(applicationContext).enqueueFollowUp(
                            accountId = accountId,
                            delayMillis = nextAttemptAt - System.currentTimeMillis(),
                        )
                    }
                    Result.success()
                }
            },
            onFailure = { error ->
                if (error is IllegalArgumentException) Result.failure() else Result.retry()
            },
        )
    }

    companion object {
        const val KEY_ACCOUNT_ID = "pocketpass.sync.account_id"
    }
}

class DatabaseOutboxWorkRunner(
    private val processor: OutboxProcessor,
) : OutboxWorkRunner {
    override suspend fun run(accountId: UserId): OutboxDrainSummary =
        processor.drain(accountId)
}
