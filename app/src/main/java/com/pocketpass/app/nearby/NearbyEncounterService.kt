package com.pocketpass.app.nearby

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.pocketpass.app.PocketPassApplication
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.steps.StepRewardsPermissionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class NearbyEncounterService : Service(), NearbyEngineHost {
    private var engine: NearbyBleEngine? = null
    private var engineStartJob: Job? = null
    private var latestStartId = 0
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val encounterLedFlasher by lazy { ThorEncounterLedFlasher(this) }

    override fun onCreate() {
        super.onCreate()
        NearbyNotifications.createChannels(this)
        serviceScope.launch {
            if (!encounterLedFlasher.recoverIfNeeded()) {
                Log.w(TAG, "Pending Thor LED state restoration failed")
            }
        }
    }

    private val connectedDeviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE

    private fun healthTypeIfAllowed(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && StepRewardsPermissionPolicy.isGranted(this)) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        } else {
            0
        }

    private fun startForegroundWithTypes(types: Int) {
        ServiceCompat.startForeground(
            this,
            NearbyNotifications.SERVICE_NOTIFICATION_ID,
            NearbyNotifications.foregroundNotification(this),
            types,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val controller = (application as PocketPassApplication).container.nearby
        val foregrounded = runCatching {
            startForegroundWithTypes(connectedDeviceType or healthTypeIfAllowed())
        }.recoverCatching {
            startForegroundWithTypes(connectedDeviceType)
        }.onFailure { error ->
            Log.w(TAG, "Nearby could not enter the foreground", error)
            controller.onServiceStartFailed("Nearby could not start; open PocketPass to try again.")
        }.isSuccess
        if (!foregrounded) {
            stopEncounterRuntime()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent == null) {
            controller.attachEngineHost(this)
            serviceScope.launch {
                val restored = withTimeoutOrNull(RESTORE_TIMEOUT_MILLIS) {
                    runCatching { controller.restoreAfterSystemEvent() }.isSuccess
                } ?: false
                if (!restored) {
                    withContext(Dispatchers.Main) {
                        if (latestStartId == startId) {
                            stopEncounterRuntime()
                            stopSelf(startId)
                        }
                    }
                }
            }
            return START_STICKY
        }
        if (!controller.onServiceForegrounded()) {
            stopEncounterRuntime()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        controller.attachEngineHost(this)
        startEngineIfIdle()
        return START_STICKY
    }

    override fun restartEngine() {
        stopEngine()
        startEngineIfIdle()
    }

    override fun stopEngine() {
        engineStartJob?.cancel()
        engineStartJob = null
        engine?.stop()
        engine = null
    }

    private fun startEngineIfIdle() {
        if (engine != null || engineStartJob?.isActive == true) return
        val container = (application as PocketPassApplication).container
        val controller = container.nearby
        engineStartJob = serviceScope.launch(Dispatchers.Main.immediate) {
            var retryDelayMillis = INITIAL_RETRY_MILLIS
            while (engine == null && controller.shouldRun()) {
                val accountId = container.activeAccountId.value
                    ?: return@launch run {
                        Log.w(TAG, "Stopping because no authenticated account is active")
                        stopEncounterRuntime()
                        stopSelf(latestStartId)
                    }
                when (val refill = withContext(Dispatchers.Default) { container.nearbyCredentialPool.refill(accountId) }) {
                    is RepositoryResult.Failure -> {
                        Log.w(
                            TAG,
                            "Credential refill failed: ${refill.error.kind}",
                        )
                        controller.reportRuntime(
                            NearbyRuntimeStatus.Error,
                            refill.error.message
                                ?: "Anonymous encounter passes are unavailable.",
                        )
                        delay(retryDelayMillis)
                        retryDelayMillis = (retryDelayMillis * 2)
                            .coerceAtMost(MAXIMUM_RETRY_MILLIS)
                    }

                    is RepositoryResult.Success -> {
                        Log.i(TAG, "Anonymous credential pool is ready")
                        val deviceTagSecret = withContext(Dispatchers.Default) { container.nearbyDeviceTags.secret(accountId) }
                        if (!controller.shouldRun()) return@launch
                        if (deviceTagSecret == null) {
                            Log.w(TAG, "Device tag secret unavailable; own devices are not recognised")
                        }
                        engine = NearbyBleEngine(
                            context = this@NearbyEncounterService,
                            credentialPool = container.nearbyCredentialPool,
                            accountId = accountId,
                            deviceTagSecret = deviceTagSecret,
                            onProof = { proof ->
                                serviceScope.launch {
                                    when (container.submitNearbyProof(accountId, proof)) {
                                        NearbyReceiptVerdict.NotQueued -> {
                                            Log.w(
                                                TAG,
                                                "Encrypted encounter receipt was not queued",
                                            )
                                            controller.reportRuntime(
                                                NearbyRuntimeStatus.Error,
                                                "The encrypted encounter receipt could not be saved.",
                                            )
                                        }

                                        NearbyReceiptVerdict.AlreadyCountedToday -> {
                                            Log.i(
                                                TAG,
                                                "Encounter already counted today; LED pulse skipped",
                                            )
                                        }

                                        NearbyReceiptVerdict.Rejected -> {
                                            Log.i(TAG, "Encounter receipt rejected; LED pulse skipped")
                                        }

                                        NearbyReceiptVerdict.NewEncounter,
                                        NearbyReceiptVerdict.Unknown,
                                        -> {
                                            Log.i(TAG, "Encrypted encounter receipt queued")
                                            pulseEncounterLed()
                                        }
                                    }
                                }
                            },
                            onState = controller::reportRuntime,
                        ).also(NearbyBleEngine::start)
                    }
                }
            }
        }
    }

    private suspend fun pulseEncounterLed() {
        val container = (application as PocketPassApplication).container
        if (!container.settings.settings.first().encounterLedEnabled) return
        when (encounterLedFlasher.flash()) {
            ThorEncounterLedResult.Completed -> {
                Log.i(TAG, "Thor encounter LED pulse completed")
            }

            ThorEncounterLedResult.Unsupported -> Unit
            ThorEncounterLedResult.ActivationFailed -> {
                Log.w(TAG, "Thor encounter LED activation failed")
            }

            ThorEncounterLedResult.RestorationFailed -> {
                Log.w(TAG, "Thor encounter LED restoration failed")
            }
        }
    }

    override fun onDestroy() {
        stopEncounterRuntime()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopEncounterRuntime() {
        (application as PocketPassApplication).container.nearby.detachEngineHost(this)
        stopEngine()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    companion object {
        private const val TAG = "PocketPassNearby"
        private const val RESTORE_TIMEOUT_MILLIS = 30_000L
        private const val INITIAL_RETRY_MILLIS = 30_000L
        private const val MAXIMUM_RETRY_MILLIS = 15L * 60L * 1_000L
        const val ACTION_START = "com.pocketpass.app.nearby.START"
    }
}
