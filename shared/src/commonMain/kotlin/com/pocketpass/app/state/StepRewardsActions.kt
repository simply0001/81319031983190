package com.pocketpass.app.state

import com.pocketpass.app.steps.StepRewardsState
import com.pocketpass.app.steps.StepRewardsStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the state loop needs from the step-reward feature on this platform. */
interface StepRewardsActions {
    val state: StateFlow<StepRewardsState>

    fun onPreferenceChanged(enabled: Boolean)

    fun requestPermission()

    fun onPermissionResult()

    fun setForeground(foreground: Boolean)

    /** Reads the counter once and reports it; false when the report failed. */
    suspend fun sampleAndClaim(): Boolean
}

/** No pedometer on this platform: the setting stays hidden and nothing runs. */
object InactiveStepRewards : StepRewardsActions {
    override val state: StateFlow<StepRewardsState> =
        MutableStateFlow(StepRewardsState(status = StepRewardsStatus.Unsupported))
    override fun onPreferenceChanged(enabled: Boolean) = Unit
    override fun requestPermission() = Unit
    override fun onPermissionResult() = Unit
    override fun setForeground(foreground: Boolean) = Unit
    override suspend fun sampleAndClaim(): Boolean = true
}
