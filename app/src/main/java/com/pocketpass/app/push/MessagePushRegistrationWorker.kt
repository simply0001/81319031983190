package com.pocketpass.app.push

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pocketpass.app.BuildConfig
import com.pocketpass.app.PocketPassApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

class MessagePushRegistrationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as PocketPassApplication).container.messagePush.synchronizeRegistration()
        Result.success()
    } catch (_: TimeoutCancellationException) {
        Result.retry()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Log.w("PocketPassPush", "Message notification registration will retry")
        Result.retry()
    }

    companion object {
        private const val IMMEDIATE = "pocketpass-message-push-registration"
        private const val PERIODIC = "pocketpass-message-push-refresh"
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun enqueue(context: Context) {
            if (!BuildConfig.FIREBASE_CONFIGURED) return
            WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<MessagePushRegistrationWorker>().setConstraints(network).build())
        }

        fun schedule(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(PERIODIC)
                return
            }
            work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MessagePushRegistrationWorker>(12, TimeUnit.HOURS).setConstraints(network).build())
        }
    }
}
