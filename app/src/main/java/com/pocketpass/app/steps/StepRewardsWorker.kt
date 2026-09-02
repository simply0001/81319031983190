package com.pocketpass.app.steps

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

fun interface StepRewardsWorkRunner {
    /** Reads the counter and reports it; false when the report should be retried. */
    suspend fun run(): Boolean
}

/** The container installs the runner at start-up; the worker only looks it up. */
object StepRewardsWorkerRuntime {
    @Volatile
    private var runner: StepRewardsWorkRunner? = null

    fun install(runner: StepRewardsWorkRunner) {
        this.runner = runner
    }

    internal fun currentRunner(): StepRewardsWorkRunner? = runner
}

class StepRewardsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val runner = StepRewardsWorkerRuntime.currentRunner() ?: return Result.success()
        return runCatching { runner.run() }.fold(
            onSuccess = { reported -> if (reported) Result.success() else Result.retry() },
            onFailure = { Result.retry() },
        )
    }
}

object StepRewardsScheduler {
    fun schedule(context: Context) {
        // No network constraint: the reading itself needs none, and a failed
        // report is simply retried.
        val request = PeriodicWorkRequestBuilder<StepRewardsWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private const val UNIQUE_WORK_NAME = "pocketpass-step-rewards"
}
