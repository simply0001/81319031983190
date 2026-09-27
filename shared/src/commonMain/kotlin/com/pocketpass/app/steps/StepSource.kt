package com.pocketpass.app.steps

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface StepSource {
    val supported: Boolean

    val permission: StateFlow<StepPermission>

    val samples: Flow<StepSample>

    suspend fun sample(): StepSample?

    fun setLive(active: Boolean)

    fun setBackgroundSampling(active: Boolean) = Unit

    fun requestPermission()

    fun requestPermissionAgain() = requestPermission()

    fun refreshPermission()
}
