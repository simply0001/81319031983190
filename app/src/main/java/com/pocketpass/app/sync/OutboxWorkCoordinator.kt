package com.pocketpass.app.sync

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
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
        val builder = OneTimeWorkRequestBuilder<PocketPassOutboxWorker>()
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
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MINIMUM_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .addTag(tag(accountId))
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

    fun cancel(accountId: UserId) {
        workManager.cancelUniqueWork(uniqueWorkName(accountId))
    }

    private fun uniqueWorkName(accountId: UserId): String =
        "pocketpass-outbox:${accountId.value}"

    private fun tag(accountId: UserId): String =
        "pocketpass-outbox-account:${accountId.value}"

    private companion object {
        const val MINIMUM_BACKOFF_SECONDS = 10L
    }
}
