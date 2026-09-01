@file:OptIn(ExperimentalForeignApi::class)

package com.pocketpass.app.state

import kotlinx.cinterop.ExperimentalForeignApi

import com.pocketpass.app.logPlatformInfo
import com.pocketpass.app.logPlatformWarning
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

/**
 * Opportunistic background sync through BGTaskScheduler. iOS decides when
 * (and whether) refreshes actually run, based on how often the app is used;
 * each run reconciles the account and drains the outboxes.
 *
 * register() must be called before didFinishLaunching returns, and the task
 * identifier must be listed under BGTaskSchedulerPermittedIdentifiers.
 */
class IosBackgroundRefresh(
    private val container: IosAppContainer,
) {
    fun register() {
        val registered = BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = TASK_IDENTIFIER,
            usingQueue = null,
        ) { task ->
            (task as? BGAppRefreshTask)?.let(::handle)
        }
        if (!registered) {
            logPlatformWarning(TAG, "Background refresh registration was rejected")
            return
        }
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = null,
        ) { _ -> schedule() }
    }

    fun schedule() {
        if (container.fixtureMode) return
        val request = BGAppRefreshTaskRequest(identifier = TASK_IDENTIFIER).apply {
            earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(REFRESH_INTERVAL_SECONDS)
        }
        // Fails on simulators and when Background App Refresh is off; both benign.
        val submitted = BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
        if (!submitted) {
            logPlatformInfo(TAG, "Background refresh request was not accepted")
        }
    }

    private fun handle(task: BGAppRefreshTask) {
        schedule()
        val job = container.applicationScope.launch {
            val ok = container.performBackgroundSync()
            task.setTaskCompletedWithSuccess(ok)
        }
        task.expirationHandler = { job.cancel() }
        job.invokeOnCompletion { cause ->
            if (cause != null) {
                task.setTaskCompletedWithSuccess(false)
            }
        }
    }

    private companion object {
        const val TAG = "PocketPassRefresh"
        const val TASK_IDENTIFIER = "xyz.pocketpass.refresh"
        const val REFRESH_INTERVAL_SECONDS = 15.0 * 60.0
    }
}
