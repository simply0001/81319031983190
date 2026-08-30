package com.pocketpass.app.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pocketpass.app.BuildConfig
import com.pocketpass.app.data.DataStoreSettingsRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

const val UPDATE_MANIFEST_URL = "https://links.pocketpass.xyz/updates/latest.json"

class AppUpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val settingsRepository = DataStoreSettingsRepository(applicationContext)
        val settings = settingsRepository.settings.first()
        if (!settings.updateAlertsEnabled) return Result.success()
        val manifest = runCatching {
            Json { ignoreUnknownKeys = true }
                .decodeFromString<UpdateManifest>(OkHttpUpdateTransport().fetch(UPDATE_MANIFEST_URL))
        }.getOrElse { return Result.retry() }
        if (!manifest.isNewerThan(BuildConfig.VERSION_CODE) || !manifest.isInstallable()) {
            return Result.success()
        }
        if (manifest.versionCode <= settings.lastNotifiedUpdateVersionCode) return Result.success()
        val posted = UpdateNotifications.postUpdateAvailable(
            applicationContext,
            manifest.versionName ?: manifest.versionCode.toString(),
        )
        if (posted) settingsRepository.setLastNotifiedUpdateVersionCode(manifest.versionCode)
        return Result.success()
    }
}

object AppUpdateCheckScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AppUpdateCheckWorker>(3, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private const val UNIQUE_WORK_NAME = "pocketpass-update-check"
}
