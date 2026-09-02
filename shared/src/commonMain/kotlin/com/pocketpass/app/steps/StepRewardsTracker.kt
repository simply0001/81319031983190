package com.pocketpass.app.steps

import com.pocketpass.app.data.LocalSettings
import com.pocketpass.app.data.SettingsRepository
import com.pocketpass.app.data.repository.remote.StepRewardsRemoteDataSource
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.state.StepRewardsActions
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The step-reward feature above the platform pedometer: decides whether
 * tracking is on, keeps today's count and the tokens the server confirmed
 * for it, and reports the count whenever it is worth more than has been
 * paid. The server owns the payout; this only tells it what the device saw.
 */
class StepRewardsTracker(
    private val settingsRepository: SettingsRepository,
    settings: StateFlow<LocalSettings>,
    private val activeAccountId: StateFlow<UserId?>,
    private val source: StepSource,
    private val remote: StepRewardsRemoteDataSource,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) : StepRewardsActions {
    private data class ClaimKey(val accountId: String, val localDay: String)

    private val mutableState = MutableStateFlow(
        StepRewardsState(
            status = if (source.supported) StepRewardsStatus.Disabled else StepRewardsStatus.Unsupported,
        ),
    )
    override val state: StateFlow<StepRewardsState> = mutableState

    private val lock = Mutex()
    private var claimedFor: ClaimKey? = null
    private var settledTarget = 0
    private var lastAttemptEpochMillis = 0L
    private var foreground = false

    init {
        scope.launch {
            combine(settings, activeAccountId, source.permission) { local, account, permission ->
                Triple(local.stepRewardsEnabled, account, permission)
            }.distinctUntilChanged().collect { (enabled, account, permission) ->
                val status = statusFor(enabled, account, permission)
                val changed = mutableState.value.status != status
                mutableState.update { current ->
                    if (current.status == status) current else current.copy(status = status, claimError = null)
                }
                if (status != StepRewardsStatus.Tracking && status != StepRewardsStatus.NeedsPermission) {
                    lock.withLock { clearProgress() }
                }
                updateLive()
                if (changed && status == StepRewardsStatus.Tracking) sampleAndClaim()
            }
        }
        scope.launch {
            source.samples.collect { sample -> accept(sample) }
        }
    }

    override fun onPreferenceChanged(enabled: Boolean) {
        scope.launch {
            settingsRepository.setStepRewardsEnabled(enabled)
            if (!enabled) return@launch
            source.refreshPermission()
            if (source.permission.value.needsPrompt()) source.requestPermission()
        }
    }

    override fun requestPermission() {
        source.requestPermission()
    }

    override fun onPermissionResult() {
        source.refreshPermission()
    }

    override fun setForeground(foreground: Boolean) {
        this.foreground = foreground
        updateLive()
        if (foreground && mutableState.value.status == StepRewardsStatus.Tracking) {
            scope.launch { sampleAndClaim() }
        }
    }

    override suspend fun sampleAndClaim(): Boolean {
        if (mutableState.value.status != StepRewardsStatus.Tracking) return true
        // A counter that cannot be read right now (the app is in the
        // background) is not a failure; the next reading catches up.
        val sample = source.sample() ?: return true
        return accept(sample)
    }

    private fun statusFor(enabled: Boolean, account: UserId?, permission: StepPermission): StepRewardsStatus =
        when {
            !source.supported -> StepRewardsStatus.Unsupported
            !enabled || account == null -> StepRewardsStatus.Disabled
            permission.needsPrompt() -> StepRewardsStatus.NeedsPermission
            else -> StepRewardsStatus.Tracking
        }

    private fun StepPermission.needsPrompt(): Boolean =
        this == StepPermission.NotDetermined || this == StepPermission.Denied

    private fun updateLive() {
        source.setLive(foreground && mutableState.value.status == StepRewardsStatus.Tracking)
    }

    private fun clearProgress() {
        claimedFor = null
        settledTarget = 0
        mutableState.update { it.copy(localDay = null, stepsToday = 0, tokensToday = 0, claimError = null) }
    }

    /** Records a reading and reports it when it is worth more than the server has paid. */
    private suspend fun accept(sample: StepSample): Boolean = lock.withLock {
        val account = activeAccountId.value ?: return@withLock true
        if (mutableState.value.status != StepRewardsStatus.Tracking) return@withLock true

        val key = ClaimKey(account.value, sample.localDay)
        if (claimedFor != key) {
            claimedFor = key
            settledTarget = 0
            mutableState.update { it.copy(tokensToday = 0, claimError = null) }
        }
        mutableState.update { it.copy(localDay = sample.localDay, stepsToday = sample.stepsToday) }

        val target = tokensForSteps(sample.stepsToday)
        if (target == 0 || target <= settledTarget) return@withLock true
        val now = clock.now().toEpochMilliseconds()
        if (mutableState.value.claimError != null && now - lastAttemptEpochMillis < RETRY_SPACING_MILLIS) {
            return@withLock false
        }
        lastAttemptEpochMillis = now
        when (
            val result = remote.reportDailySteps(
                accountId = account,
                localDay = sample.localDay,
                steps = sample.stepsToday,
                utcOffsetMinutes = sample.utcOffsetMinutes,
            )
        ) {
            is RepositoryResult.Success -> {
                settledTarget = target
                if (claimedFor == key) {
                    mutableState.update {
                        it.copy(tokensToday = result.value.tokensAwarded, claimError = null)
                    }
                }
                true
            }

            is RepositoryResult.Failure -> {
                // A rejected day (clock or time-zone mismatch) will not be
                // accepted on retry, so stop asking until the count grows.
                if (!result.error.retryable) settledTarget = target
                mutableState.update {
                    it.copy(claimError = result.error.message ?: "Steps could not be reported")
                }
                false
            }
        }
    }

    private companion object {
        const val RETRY_SPACING_MILLIS = 30_000L
    }
}
