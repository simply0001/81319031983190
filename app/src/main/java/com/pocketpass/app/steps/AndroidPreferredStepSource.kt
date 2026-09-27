package com.pocketpass.app.steps

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AndroidStepPermissionRequest { HealthConnect, ActivityRecognition }

internal fun nextStepPermissionRequest(
    healthAvailable: Boolean,
    healthGranted: Boolean,
    healthDeclined: Boolean,
    sensorSupported: Boolean,
    sensorGranted: Boolean,
    forceHealthRetry: Boolean = false,
): AndroidStepPermissionRequest? = when {
    healthGranted -> null
    healthAvailable && !healthGranted && (forceHealthRetry || !healthDeclined) ->
        AndroidStepPermissionRequest.HealthConnect
    sensorSupported && !sensorGranted -> AndroidStepPermissionRequest.ActivityRecognition
    else -> null
}

internal fun combineStepSamples(primary: StepSample, other: StepSample?): StepSample =
    if (other?.localDay == primary.localDay && other.stepsToday > primary.stepsToday) {
        primary.copy(stepsToday = other.stepsToday)
    } else primary

internal fun effectiveStepPermission(
    healthGranted: Boolean,
    healthAvailable: Boolean,
    sensorSupported: Boolean,
    sensorPermission: StepPermission,
): StepPermission = when {
    healthGranted || (sensorSupported && sensorPermission.isReadable()) -> StepPermission.Granted
    sensorSupported -> sensorPermission
    healthAvailable -> StepPermission.NotDetermined
    else -> StepPermission.NotRequired
}

private fun StepPermission.isReadable(): Boolean =
    this == StepPermission.Granted || this == StepPermission.NotRequired

class AndroidPreferredStepSource(context: Context, private val scope: CoroutineScope) : StepSource {
    private val appContext = context.applicationContext
    private val sensor = AndroidStepCounterSource(appContext, scope)
    private val health = HealthConnectStepReader(appContext)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val healthPromptInFlight = AtomicBoolean(false)
    private val sensorPromptInFlight = AtomicBoolean(false)

    @Volatile private var healthGranted = false
    @Volatile private var foreground = false
    @Volatile private var latestSensor: StepSample? = null
    @Volatile private var latestHealth: StepSample? = null
    private var healthPoll: Job? = null

    override val supported: Boolean = health.available || sensor.supported
    private val permissionState = MutableStateFlow(
        effectiveStepPermission(false, health.available, sensor.supported, sensor.permission.value),
    )
    override val permission: StateFlow<StepPermission> = permissionState
    private val liveSamples = MutableSharedFlow<StepSample>(extraBufferCapacity = 8)
    override val samples: Flow<StepSample> = liveSamples.asSharedFlow()
    private val permissionRequestEvents = MutableSharedFlow<AndroidStepPermissionRequest>(extraBufferCapacity = 2)
    val permissionRequests = permissionRequestEvents.asSharedFlow()

    init {
        scope.launch {
            sensor.samples.collect { sample ->
                latestSensor = sample
                liveSamples.emit(combineStepSamples(sample, latestHealth))
            }
        }
        refreshPermission()
    }

    override suspend fun sample(): StepSample? {
        val healthSample = if (foreground && healthGranted) readHealth() else null
        val sensorSample = if (sensor.permission.value.isReadable()) sensor.sample() else null
        latestHealth = healthSample ?: latestHealth
        latestSensor = sensorSample ?: latestSensor
        return when {
            healthSample != null -> combineStepSamples(healthSample, sensorSample ?: latestSensor)
            sensorSample != null -> combineStepSamples(sensorSample, latestHealth)
            else -> null
        }
    }

    override fun setLive(active: Boolean) {
        foreground = active
        sensor.setLive(active)
        updateHealthPoll()
    }

    override fun setBackgroundSampling(active: Boolean) {
        sensor.setBackgroundSampling(active)
    }

    suspend fun recordBoot() = sensor.recordBoot()

    override fun requestPermission() = requestPermission(forceHealthRetry = false)

    override fun requestPermissionAgain() = requestPermission(forceHealthRetry = true)

    private fun requestPermission(forceHealthRetry: Boolean) {
        scope.launch {
            refreshPermissionNow()
            when (nextStepPermissionRequest(
                healthAvailable = health.available,
                healthGranted = healthGranted,
                healthDeclined = preferences.getBoolean(DECLINED_KEY, false),
                sensorSupported = sensor.supported,
                sensorGranted = sensor.permission.value.isReadable(),
                forceHealthRetry = forceHealthRetry,
            )) {
                AndroidStepPermissionRequest.HealthConnect -> {
                    if (healthPromptInFlight.compareAndSet(false, true)) {
                        permissionRequestEvents.emit(AndroidStepPermissionRequest.HealthConnect)
                    }
                }
                AndroidStepPermissionRequest.ActivityRecognition -> requestSensorPermission()
                null -> Unit
            }
        }
    }

    fun onHealthPermissionResult(granted: Boolean) {
        healthPromptInFlight.set(false)
        preferences.edit().putBoolean(DECLINED_KEY, !granted).apply()
        scope.launch {
            refreshPermissionNow()
            if (!granted && sensor.supported && !sensor.permission.value.isReadable()) {
                requestSensorPermission()
            }
        }
    }

    fun onSensorPermissionResult() {
        sensorPromptInFlight.set(false)
        refreshPermission()
    }

    override fun refreshPermission() {
        scope.launch { refreshPermissionNow() }
    }

    private suspend fun refreshPermissionNow() {
        sensor.refreshPermission()
        healthGranted = try {
            health.hasReadPermission()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Could not check Health Connect step permission", error)
            false
        }
        if (!healthGranted) latestHealth = null
        permissionState.value = effectiveStepPermission(
            healthGranted = healthGranted,
            healthAvailable = health.available,
            sensorSupported = sensor.supported,
            sensorPermission = sensor.permission.value,
        )
        updateHealthPoll()
    }

    private suspend fun requestSensorPermission() {
        if (sensorPromptInFlight.compareAndSet(false, true)) {
            permissionRequestEvents.emit(AndroidStepPermissionRequest.ActivityRecognition)
        }
    }

    @Synchronized
    private fun updateHealthPoll() {
        if (!foreground || !healthGranted) {
            healthPoll?.cancel()
            healthPoll = null
            return
        }
        if (healthPoll?.isActive == true) return
        healthPoll = scope.launch {
            while (isActive) {
                readHealth()?.let { sample ->
                    latestHealth = sample
                    liveSamples.emit(combineStepSamples(sample, latestSensor))
                }
                delay(HEALTH_POLL_MILLIS)
            }
        }
    }

    private suspend fun readHealth(): StepSample? = try {
        health.sample()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.w(TAG, "Health Connect step read failed; using device sensor when available", error)
        if (error is SecurityException) refreshPermission()
        null
    }

    private companion object {
        const val TAG = "PocketPassSteps"
        const val PREFERENCES_NAME = "pocketpass_health_connect"
        const val DECLINED_KEY = "read_steps_declined"
        const val HEALTH_POLL_MILLIS = 60_000L
    }
}
