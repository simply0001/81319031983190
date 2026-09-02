package com.pocketpass.app.steps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocketpass.app.widget.startOfLocalDayEpochMillis
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private val Context.stepLedgerDataStore by preferencesDataStore(name = "pocketpass_steps")

/**
 * The hardware step counter as a [StepSource]. The sensor only reports steps
 * since boot, so every reading goes through [StepDayLedger], whose state is
 * kept in a small DataStore so days survive process restarts. The runtime
 * permission prompt belongs to the activity, which collects
 * [permissionRequests].
 */
class AndroidStepCounterSource(
    context: Context,
    private val scope: CoroutineScope,
) : StepSource {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    override val supported: Boolean = sensor != null

    private val permissionState = MutableStateFlow(readPermission())
    override val permission: StateFlow<StepPermission> = permissionState

    private val permissionRequestEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val permissionRequests = permissionRequestEvents.asSharedFlow()

    private val liveSamples = MutableSharedFlow<StepSample>(extraBufferCapacity = 8)
    override val samples: Flow<StepSample> = liveSamples.asSharedFlow()

    private val ledger = Mutex()
    private val liveLock = Any()
    private var liveListener: SensorEventListener? = null

    override suspend fun sample(): StepSample? {
        val stepSensor = sensor ?: return null
        val manager = sensorManager ?: return null
        if (!StepRewardsPermissionPolicy.isGranted(appContext)) return null
        // The counter reports its current value shortly after registration;
        // in the background Android may deliver nothing, hence the timeout.
        val counter = withTimeoutOrNull(SAMPLE_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<Long> { continuation ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        manager.unregisterListener(this)
                        if (continuation.isActive) continuation.resume(event.values[0].toLong())
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                manager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
                continuation.invokeOnCancellation { manager.unregisterListener(listener) }
            }
        } ?: return null
        return record(counter)
    }

    override fun setLive(active: Boolean) {
        val manager = sensorManager ?: return
        val stepSensor = sensor ?: return
        synchronized(liveLock) {
            if (!active) {
                liveListener?.let(manager::unregisterListener)
                liveListener = null
                return
            }
            if (liveListener != null || !StepRewardsPermissionPolicy.isGranted(appContext)) return
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val counter = event.values[0].toLong()
                    scope.launch { record(counter)?.let(liveSamples::tryEmit) }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            liveListener = listener
            manager.registerListener(
                listener,
                stepSensor,
                SensorManager.SENSOR_DELAY_NORMAL,
                LIVE_REPORT_LATENCY_MICROS,
            )
        }
    }

    override fun requestPermission() {
        permissionRequestEvents.tryEmit(Unit)
    }

    override fun refreshPermission() {
        permissionState.value = readPermission()
    }

    private fun readPermission(): StepPermission = when {
        StepRewardsPermissionPolicy.requiredPermission() == null -> StepPermission.NotRequired
        StepRewardsPermissionPolicy.isGranted(appContext) -> StepPermission.Granted
        // Android cannot tell "never asked" from "denied" without an activity;
        // both lead to the same prompt.
        else -> StepPermission.NotDetermined
    }

    private suspend fun record(counter: Long): StepSample? = ledger.withLock {
        val now = System.currentTimeMillis()
        val next = StepDayLedger.advance(
            previous = loadLedger(),
            counter = counter,
            nowEpochMillis = now,
            bootEpochMillis = now - SystemClock.elapsedRealtime(),
            dayStartEpochMillis = startOfLocalDayEpochMillis(now),
        )
        saveLedger(next)
        StepSample(
            localDay = localDayKey(now),
            utcOffsetMinutes = localUtcOffsetMinutes(now),
            stepsToday = next.stepsToday,
            sampledAtEpochMillis = now,
        )
    }

    private suspend fun loadLedger(): StepLedgerState? {
        val preferences = appContext.stepLedgerDataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()
        return StepLedgerState(
            dayStartEpochMillis = preferences[Keys.dayStart] ?: return null,
            bootEpochMillis = preferences[Keys.bootEpoch] ?: return null,
            lastCounter = preferences[Keys.lastCounter] ?: return null,
            lastSampleEpochMillis = preferences[Keys.lastSampleAt] ?: return null,
            stepsToday = preferences[Keys.stepsToday] ?: 0,
        )
    }

    private suspend fun saveLedger(state: StepLedgerState) {
        appContext.stepLedgerDataStore.edit { preferences ->
            preferences[Keys.dayStart] = state.dayStartEpochMillis
            preferences[Keys.bootEpoch] = state.bootEpochMillis
            preferences[Keys.lastCounter] = state.lastCounter
            preferences[Keys.lastSampleAt] = state.lastSampleEpochMillis
            preferences[Keys.stepsToday] = state.stepsToday
        }
    }

    private object Keys {
        val dayStart = longPreferencesKey("day_start")
        val bootEpoch = longPreferencesKey("boot_epoch")
        val lastCounter = longPreferencesKey("last_counter")
        val lastSampleAt = longPreferencesKey("last_sample_at")
        val stepsToday = intPreferencesKey("steps_today")
    }

    private companion object {
        const val SAMPLE_TIMEOUT_MILLIS = 5_000L
        const val LIVE_REPORT_LATENCY_MICROS = 5_000_000
    }
}
