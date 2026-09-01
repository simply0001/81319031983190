package com.pocketpass.app.nearby

import com.pocketpass.app.data.LocalSettings
import com.pocketpass.app.data.SettingsRepository
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.logPlatformWarning
import com.pocketpass.app.state.NearbyActions
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationRestricted
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS counterpart of NearbyLifecycleController: watches the preference,
 * onboarding flag and account, and runs the CoreBluetooth engine while all
 * three allow it. There is no Android-style permission activity: the system
 * Bluetooth prompt appears the first time the engine starts, and a denial
 * surfaces as a repair prompt pointing at Settings.
 */
class IosNearbyController(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    settings: StateFlow<LocalSettings>,
    private val activeAccountId: StateFlow<UserId?>,
    private val credentialPool: NearbyCredentialPool,
    private val submitProof: suspend (UserId, NearbyEncounterProof) -> NearbyReceiptVerdict,
    private val onEncounter: () -> Unit,
) : NearbyActions {
    private val runtime = MutableStateFlow(NearbyRuntimeState())
    private val permissionUi = MutableStateFlow(NearbyPermissionUiState())
    private var latestSettings = settings.value
    private var engine: IosNearbyBleEngine? = null
    private var engineStartJob: Job? = null

    override val state: StateFlow<NearbyFeatureState> = combine(
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
            combine(settings, activeAccountId) { localSettings, accountId ->
                latestSettings = localSettings
                Triple(
                    localSettings.nearbyEnabled && localSettings.nearbyOnboardingCompleted,
                    accountId,
                    bluetoothBlocked(),
                )
            }
                .distinctUntilChanged()
                .collect { (shouldRun, accountId, blocked) ->
                    evaluate(shouldRun && !blocked, accountId)
                }
        }
    }

    override fun onNearbyPreferenceChanged(enabled: Boolean) {
        scope.launch {
            if (!enabled) {
                settingsRepository.setNearby(false)
                stopEngine()
                permissionUi.value = NearbyPermissionUiState()
                runtime.value = NearbyRuntimeState(NearbyRuntimeStatus.Disabled)
                return@launch
            }
            if (!latestSettings.nearbyOnboardingCompleted) {
                permissionUi.value = NearbyPermissionUiState(visible = true)
                return@launch
            }
            if (bluetoothBlocked()) {
                permissionUi.value = NearbyPermissionUiState(
                    visible = true,
                    isRepair = true,
                    error = "Allow Bluetooth for PocketPass in the iOS Settings app.",
                )
                return@launch
            }
            settingsRepository.setNearby(true)
        }
    }

    override fun requestPermissions() {
        permissionUi.update { it.copy(error = null) }
        requestNotificationAuthorization()
        scope.launch {
            settingsRepository.setNearbyOnboardingCompleted(true)
            settingsRepository.setNearby(true)
            permissionUi.value = NearbyPermissionUiState()
        }
    }

    override fun skipOnboarding() {
        scope.launch {
            settingsRepository.setNearby(false)
            settingsRepository.setNearbyOnboardingCompleted(true)
            permissionUi.value = NearbyPermissionUiState()
            stopEngine()
            runtime.value = NearbyRuntimeState(NearbyRuntimeStatus.Disabled)
        }
    }

    override fun onAppOpened(openRepair: Boolean) {
        if (openRepair) {
            permissionUi.value = NearbyPermissionUiState(visible = true, isRepair = true)
        }
    }

    override fun onPermissionResult() = Unit

    private fun evaluate(shouldRun: Boolean, accountId: UserId?) {
        if (!shouldRun || accountId == null) {
            stopEngine()
            runtime.value = NearbyRuntimeState(NearbyRuntimeStatus.Disabled)
            return
        }
        if (engine != null || engineStartJob?.isActive == true) return
        engineStartJob = scope.launch {
            var retryDelayMillis = INITIAL_RETRY_MILLIS
            while (engine == null && currentCoroutineContext().isActive) {
                when (val refill = credentialPool.refill(accountId)) {
                    is RepositoryResult.Failure -> {
                        logPlatformWarning(TAG, "Credential refill failed: ${refill.error.kind}")
                        reportRuntime(
                            NearbyRuntimeStatus.Error,
                            refill.error.message
                                ?: "Anonymous encounter passes are unavailable.",
                        )
                        delay(retryDelayMillis)
                        retryDelayMillis = (retryDelayMillis * 2)
                            .coerceAtMost(MAXIMUM_RETRY_MILLIS)
                    }

                    is RepositoryResult.Success -> {
                        engine = IosNearbyBleEngine(
                            credentialPool = credentialPool,
                            accountId = accountId,
                            scope = scope,
                            onProof = { proof -> submit(accountId, proof) },
                            onState = ::reportRuntime,
                        ).also(IosNearbyBleEngine::start)
                    }
                }
            }
        }
    }

    private fun submit(accountId: UserId, proof: NearbyEncounterProof) {
        scope.launch {
            when (submitProof(accountId, proof)) {
                NearbyReceiptVerdict.NotQueued -> reportRuntime(
                    NearbyRuntimeStatus.Error,
                    "The encrypted encounter receipt could not be saved.",
                )

                NearbyReceiptVerdict.AlreadyCountedToday -> Unit

                NearbyReceiptVerdict.NewEncounter,
                NearbyReceiptVerdict.Unknown,
                -> onEncounter()
            }
        }
    }

    private fun stopEngine() {
        engineStartJob?.cancel()
        engineStartJob = null
        engine?.stop()
        engine = null
    }

    private fun reportRuntime(
        status: NearbyRuntimeStatus,
        detail: String?,
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

    private fun bluetoothBlocked(): Boolean {
        val authorization = CBManager.authorization
        return authorization == CBManagerAuthorizationDenied ||
            authorization == CBManagerAuthorizationRestricted
    }

    private fun requestNotificationAuthorization() {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
        ) { _, _ -> }
    }

    private companion object {
        const val TAG = "PocketPassNearby"
        const val INITIAL_RETRY_MILLIS = 30_000L
        const val MAXIMUM_RETRY_MILLIS = 15L * 60L * 1_000L
    }
}

/** Local "you passed someone" banner for backgrounded street-pass hits. */
@OptIn(ExperimentalUuidApi::class)
fun postEncounterNotification() {
    val content = UNMutableNotificationContent().apply {
        setTitle("PocketPass")
        setBody("You crossed paths with another PocketPass player!")
        setSound(UNNotificationSound.defaultSound)
    }
    val request = UNNotificationRequest.requestWithIdentifier(
        identifier = "encounter-${Uuid.random()}",
        content = content,
        trigger = null,
    )
    UNUserNotificationCenter.currentNotificationCenter()
        .addNotificationRequest(request) { _ -> }
}
