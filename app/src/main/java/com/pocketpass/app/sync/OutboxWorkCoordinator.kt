package com.pocketpass.app.sync

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.pocketpass.app.domain.model.UserId
import java.util.concurrent.TimeUnit

class OutboxWorkCoordinator(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
) {
    fun enqueue(accountId: UserId) {
        val builder = outboxRequest(accountId)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MINIMUM_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        val request = builder.build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(accountId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueueFollowUp(accountId: UserId, delayMillis: Long) {
        val request = outboxRequest(accountId)
            .setInitialDelay(
                delayMillis.coerceAtLeast(MINIMUM_FOLLOW_UP_MILLIS),
                TimeUnit.MILLISECONDS,
            )
            .build()
        workManager.enqueueUniqueWork(
            followUpWorkName(accountId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(accountId: UserId) {
        workManager.cancelUniqueWork(uniqueWorkName(accountId))
        workManager.cancelUniqueWork(followUpWorkName(accountId))
    }

    private fun outboxRequest(accountId: UserId): OneTimeWorkRequest.Builder =
        OneTimeWorkRequestBuilder<PocketPassOutboxWorker>()
            .setInputData(
                Data.Builder()
                    .putString(PocketPassOutboxWorker.KEY_ACCOUNT_ID, accountId.value)
                    .build(),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(tag(accountId))

    private fun uniqueWorkName(accountId: UserId): String =
        "pocketpass-outbox:${accountId.value}"

    private fun followUpWorkName(accountId: UserId): String =
        "pocketpass-outbox-follow-up:${accountId.value}"

    private fun tag(accountId: UserId): String =
        "pocketpass-outbox-account:${accountId.value}"

    private companion object {
        const val MINIMUM_BACKOFF_SECONDS = 10L
        const val MINIMUM_FOLLOW_UP_MILLIS = 5_000L
    }
}
