@file:OptIn(ExperimentalForeignApi::class)

package com.pocketpass.app.steps

import com.pocketpass.app.widget.startOfLocalDayEpochMillis
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreMotion.CMAuthorizationStatusAuthorized
import platform.CoreMotion.CMAuthorizationStatusDenied
import platform.CoreMotion.CMAuthorizationStatusRestricted
import platform.CoreMotion.CMPedometer
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * CMPedometer as a [StepSource]. iOS keeps the step history itself, so a
 * reading is a query from local midnight to now and no ledger is needed.
 * The system Motion prompt appears on the first query; a later denial can
 * only be undone in the Settings app.
 */
class IosPedometerStepSource : StepSource {
    private val pedometer = CMPedometer()

    override val supported: Boolean = CMPedometer.isStepCountingAvailable()

    private val permissionState = MutableStateFlow(readPermission())
    override val permission: StateFlow<StepPermission> = permissionState

    private val liveSamples = MutableSharedFlow<StepSample>(extraBufferCapacity = 8)
    override val samples: Flow<StepSample> = liveSamples.asSharedFlow()

    private var live = false
    private var liveDay: String? = null

    override suspend fun sample(): StepSample? {
        if (!supported) return null
        val now = nowMillis()
        val steps = suspendCancellableCoroutine<Int?> { continuation ->
            pedometer.queryPedometerDataFromDate(
                start = startOfDay(now),
                toDate = date(now),
            ) { data, error ->
                val count = if (error == null) data?.numberOfSteps?.intValue else null
                if (continuation.isActive) continuation.resume(count)
            }
        }
        refreshPermission()
        return steps?.let { sampleOf(now, it) }
    }

    override fun setLive(active: Boolean) {
        if (active == live) return
        live = active
        if (!active) {
            pedometer.stopPedometerUpdates()
            return
        }
        if (!supported) return
        startLiveUpdates()
    }

    private fun startLiveUpdates() {
        val now = nowMillis()
        liveDay = localDayKey(now)
        pedometer.startPedometerUpdatesFromDate(startOfDay(now)) { data, error ->
            val at = nowMillis()
            if (error != null || data == null) {
                refreshPermission()
            } else if (localDayKey(at) != liveDay) {
                // Midnight passed: the running query still counts yesterday.
                pedometer.stopPedometerUpdates()
                if (live) startLiveUpdates()
            } else {
                liveSamples.tryEmit(sampleOf(at, data.numberOfSteps.intValue))
            }
        }
    }

    override fun requestPermission() {
        when (readPermission()) {
            StepPermission.Denied -> dispatch_async(dispatch_get_main_queue()) {
                val settings = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return@dispatch_async
                UIApplication.sharedApplication.openURL(settings, emptyMap<Any?, Any>(), null)
            }

            StepPermission.Granted, StepPermission.NotRequired -> refreshPermission()

            StepPermission.NotDetermined -> {
                // The first query is what shows the system prompt.
                val now = nowMillis()
                pedometer.queryPedometerDataFromDate(startOfDay(now), date(now)) { _, _ ->
                    refreshPermission()
                }
            }
        }
    }

    override fun refreshPermission() {
        permissionState.value = readPermission()
    }

    private fun readPermission(): StepPermission = when (CMPedometer.authorizationStatus()) {
        CMAuthorizationStatusAuthorized -> StepPermission.Granted
        CMAuthorizationStatusDenied, CMAuthorizationStatusRestricted -> StepPermission.Denied
        else -> StepPermission.NotDetermined
    }

    private fun sampleOf(nowEpochMillis: Long, steps: Int): StepSample = StepSample(
        localDay = localDayKey(nowEpochMillis),
        utcOffsetMinutes = localUtcOffsetMinutes(nowEpochMillis),
        stepsToday = steps.coerceAtLeast(0),
        sampledAtEpochMillis = nowEpochMillis,
    )

    private fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

    private fun date(epochMillis: Long): NSDate = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)

    private fun startOfDay(nowEpochMillis: Long): NSDate = date(startOfLocalDayEpochMillis(nowEpochMillis))
}
