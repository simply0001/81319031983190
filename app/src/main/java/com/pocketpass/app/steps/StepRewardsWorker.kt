package com.pocketpass.app.steps

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

fun interface StepRewardsWorkRunner {
    suspend fun run(): Boolean
}

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
        val request = PeriodicWorkRequestBuilder<StepRewardsWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        scheduleNextMidnight(context)
    }

    fun scheduleNextMidnight(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        val triggerAtMillis = ZonedDateTime.now(zone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .plus(MIDNIGHT_MARGIN)
            .toEpochMilli()
        val pending = midnightIntent(appContext)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        runCatching {
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        appContext.getSystemService(AlarmManager::class.java)?.cancel(midnightIntent(appContext))
    }

    private fun midnightIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        MIDNIGHT_REQUEST_CODE,
        Intent(context, StepMidnightReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val UNIQUE_WORK_NAME = "pocketpass-step-rewards"
    private const val MIDNIGHT_REQUEST_CODE = 41
    private val MIDNIGHT_MARGIN: Duration = Duration.ofMinutes(1)
}
