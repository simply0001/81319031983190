package com.pocketpass.app.state

import com.pocketpass.app.steps.StepRewardsState
import kotlinx.coroutines.flow.StateFlow

interface StepRewardsActions {
    val state: StateFlow<StepRewardsState>

    fun onPreferenceChanged(enabled: Boolean)

    fun requestPermission()

    fun onPermissionResult()

    fun setForeground(foreground: Boolean)

    suspend fun sampleAndClaim(): Boolean
}
