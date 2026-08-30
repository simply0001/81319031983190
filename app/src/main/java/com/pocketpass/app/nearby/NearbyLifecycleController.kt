package com.pocketpass.app.nearby

import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Build
import androidx.core.content.ContextCompat
import com.pocketpass.app.data.LocalSettings
import com.pocketpass.app.data.SettingsRepository
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.SessionState
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NearbyPermissionUiState(
    val visible: Boolean = false,
    val isRepair: Boolean = false,
    val error: String? = null,
)

data class NearbyFeatureState(
    val runtime: NearbyRuntimeState = NearbyRuntimeState(),
    val permissionUi: NearbyPermissionUiState = NearbyPermissionUiState(),
)

private const val TAG = "PocketPassNearby"

class NearbyLifecycleController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    settings: StateFlow<LocalSettings>,
    private val sessionState: StateFlow<SessionState>,
    private val scope: CoroutineScope,
) {
    private val runtime = MutableStateFlow(NearbyRuntimeState())
    private val permissionUi = MutableStateFlow(NearbyPermissionUiState())
    private val permissionRequestEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var latestSettings = settings.value
    private var latestSession = sessionState.value
    private var appOpenRepairCheck: Job? = null
    private val serviceHandshake = Any()
    private var startInFlight = false
    private var stopDeferred = false

    val permissionRequests = permissionRequestEvents.asSharedFlow()
    val state: StateFlow<NearbyFeatureState> = combine(
        runtime,
        permissionUi,
        ::NearbyFeatureState,
    ).stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = NearbyFeatureState(),
    )

    init {
        scope.launch {
            combine(
                settings,
                sessionState,
            ) { localSettings, session ->
                localSettings to session
            }.distinctUntilChanged().collect { (localSettings, session) ->
                latestSettings = localSettings
                latestSession = session
                evaluate()
            }
        }
    }

    fun onAppOpened(openRepair: Boolean) {
        if (openRepair) {
            appOpenRepairCheck?.cancel()
            openRepairFlow()
            return
        }
        appOpenRepairCheck?.cancel()
        appOpenRepairCheck = scope.launch {
            restorePersistedState()
            evaluate()
            surfaceOperationalRepairIfNeeded()
        }
    }

    suspend fun restoreAfterSystemEvent() {
        restorePersistedState()
        evaluate()
    }

    fun requestPermissions() {
        permissionUi.update { it.copy(error = null) }
        permissionRequestEvents.tryEmit(Unit)
    }

    fun onPermissionResult() {
        val missing = NearbyPermissionPolicy.missingBlePermissions(context)
        val notificationMissing =
            NearbyPermissionPolicy.isNotificationPermissionMissing(context)
        if (missing.isEmpty()) {
            scope.launch {
                settingsRepository.setNearbyOnboardingCompleted(true)
                settingsRepository.setNearby(true)
                permissionUi.value = if (notificationMissing) {
                    NearbyPermissionUiState(
                        visible = true,
                        isRepair = true,
                        error = "Nearby Encounters is on. Allow notifications to receive encounter alerts.",
                    )
                } else {
                    NearbyPermissionUiState()
                }
                NearbyNotifications.cancelRepair(context)
                evaluate()
            }
        } else {
            permissionUi.update {
                it.copy(
                    visible = true,
                    error = when {
                        Build.VERSION.SDK_INT == Build.VERSION_CODES.R &&
                            missing.contains(
                                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                            ) ->
                            "Allow location all the time so encounters can work with the screen off."

                        else ->
                            "PocketPass still needs Nearby Devices permission to exchange passes."
                    } + if (notificationMissing) {
                        " Notification permission is also disabled."
                    } else {
                        ""
                    },
                )
            }
            evaluate()
        }
    }

    fun skipOnboarding() {
        scope.launch {
            settingsRepository.setNearby(false)
            settingsRepository.setNearbyOnboardingCompleted(true)
            permissionUi.value = NearbyPermissionUiState()
            stopService()
            runtime.value = NearbyRuntimeState(NearbyRuntimeStatus.Disabled)
        }
    }

    fun onNearbyPreferenceChanged(enabled: Boolean) {
        scope.launch {
            if (!enabled) {
                settingsRepository.setNearby(false)
                stopService()
                permissionUi.value = NearbyPermissionUiState()
                runtime.value = NearbyRuntimeState(NearbyRuntimeStatus.Disabled)
                return@launch
            }
            if (
                !latestSettings.nearbyOnboardingCompleted ||
                NearbyPermissionPolicy.missingBlePermissions(context).isNotEmpty()
            ) {
                permissionUi.value = NearbyPermissionUiState(
                    visible = true,
                    isRepair = latestSettings.nearbyOnboardingCompleted,
                )
                return@launch
            }
            settingsRepository.setNearby(true)
            evaluate()
        }
    }

    fun openRepairFlow() {
        permissionUi.value = NearbyPermissionUiState(
            visible = true,
            isRepair = true,
        )
    }

    fun reportRuntime(
        status: NearbyRuntimeStatus,
        detail: String? = null,
        activeExchangeCount: Int = runtime.value.activeExchangeCount,
        lastEncounterAt: Instant? = runtime.value.lastEncounterAt,
    ) {
        runtime.value = NearbyRuntimeState(
            status = status,
            detail = detail,
            activeExchangeCount = activeExchangeCount,
            lastEncounterAt = lastEncounterAt,
        )
    }

    fun shouldRun(): Boolean =
        latestSettings.nearbyEnabled &&
            latestSettings.nearbyOnboardingCompleted &&
            activeAccountId() != null &&
            NearbyPermissionPolicy.missingBlePermissions(context).isEmpty() &&
            NearbyPermissionPolicy.supportsBle(context) &&
            NearbyPermissionPolicy.isBluetoothEnabled(context) &&
            NearbyPermissionPolicy.isLegacyLocationEnabled(context)

    private fun evaluate() {
        val accountId = activeAccountId()
        when {
            accountId == null || !latestSettings.nearbyEnabled -> {
                stopService(
                    if (accountId == null) "session ${latestSession::class.simpleName}" else "disabled",
                )
                runtime.value = NearbyRuntimeState(NearbyRuntimeStatus.Disabled)
            }

            !latestSettings.nearbyOnboardingCompleted -> {
                stopService("onboarding incomplete")
                permissionUi.value = NearbyPermissionUiState(visible = true)
                runtime.value = NearbyRuntimeState(NearbyRuntimeStatus.NeedsOnboarding)
            }

            !NearbyPermissionPolicy.supportsBle(context) -> {
                stopService("BLE unsupported")
                runtime.value = NearbyRuntimeState(
                    NearbyRuntimeStatus.Unsupported,
                    "Bluetooth Low Energy is unavailable on this device.",
                )
            }

            NearbyPermissionPolicy.missingBlePermissions(context).isNotEmpty() -> {
                stopService("permissions missing")
                runtime.value = NearbyRuntimeState(NearbyRuntimeStatus.NeedsPermissions)
            }

            !NearbyPermissionPolicy.isBluetoothEnabled(context) ||
                !NearbyPermissionPolicy.isLegacyLocationEnabled(context) -> {
                stopService("bluetooth or location off")
                runtime.value = NearbyRuntimeState(
                    NearbyRuntimeStatus.BluetoothOff,
                    "Turn on Bluetooth" +
                        if (!NearbyPermissionPolicy.isLegacyLocationEnabled(context)) {
                            " and Location"
                        } else {
                            ""
                        } +
                        " to use Nearby Encounters.",
                )
            }

            else -> startService()
        }
    }

    private fun startService() {
        if (runtime.value.status == NearbyRuntimeStatus.Running ||
            runtime.value.status == NearbyRuntimeStatus.Starting
        ) {
            return
        }
        runtime.value = NearbyRuntimeState(NearbyRuntimeStatus.Starting)
        synchronized(serviceHandshake) {
            startInFlight = true
            stopDeferred = false
        }
        val started = runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NearbyEncounterService::class.java)
                    .setAction(NearbyEncounterService.ACTION_START),
            )
        }.onFailure { error ->
            Log.w(TAG, "Nearby foreground start refused", error)
        }.isSuccess
        if (!started) {
            synchronized(serviceHandshake) { startInFlight = false }
            runtime.value = NearbyRuntimeState(
                NearbyRuntimeStatus.Disabled,
                "Nearby could not start in the background; open PocketPass to try again.",
            )
        }
    }

    private fun stopService(reason: String = "requested") {
        synchronized(serviceHandshake) {
            if (startInFlight) {
                stopDeferred = true
                return
            }
        }
        Log.i(TAG, "Stopping nearby service: $reason")
        context.stopService(Intent(context, NearbyEncounterService::class.java))
    }

    fun onServiceForegrounded(): Boolean {
        val stopRequested = synchronized(serviceHandshake) {
            startInFlight = false
            stopDeferred.also { stopDeferred = false }
        }
        return !stopRequested && shouldRun()
    }

    fun onServiceStartFailed(detail: String) {
        synchronized(serviceHandshake) {
            startInFlight = false
            stopDeferred = false
        }
        runtime.value = NearbyRuntimeState(NearbyRuntimeStatus.Disabled, detail)
    }

    private fun activeAccountId(): UserId? = when (val state = latestSession) {
        is SessionState.Authenticated -> state.userId
        is SessionState.OfflineWithCachedSession -> state.userId
        else -> null
    }

    private fun hasOperationalProblem(): Boolean =
        NearbyPermissionPolicy.missingBlePermissions(context).isNotEmpty() ||
            NearbyPermissionPolicy.isNotificationPermissionMissing(context) ||
            !NearbyPermissionPolicy.isBluetoothEnabled(context) ||
            !NearbyPermissionPolicy.isLegacyLocationEnabled(context)

    private suspend fun restorePersistedState() {
        latestSettings = settingsRepository.settings.first()
        latestSession = sessionState.first { it !is SessionState.Initializing }
    }

    private fun surfaceOperationalRepairIfNeeded() {
        if (
            !latestSettings.nearbyEnabled ||
            !latestSettings.nearbyOnboardingCompleted ||
            activeAccountId() == null ||
            !hasOperationalProblem()
        ) {
            return
        }
        if (NearbyPermissionPolicy.isNotificationPermissionMissing(context)) {
            permissionUi.value = NearbyPermissionUiState(
                visible = true,
                isRepair = true,
                error = "Allow PocketPass notifications so encounter alerts can appear.",
            )
        } else if (latestSettings.nearbyRepairAlertsEnabled) {
            NearbyNotifications.postRepair(context)
        }
    }
}
