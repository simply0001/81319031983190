package com.pocketpass.app.steps

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The platform's pedometer. It answers only three things: whether a counter
 * exists, whether the app may read it, and how many steps it counted so far
 * in the current local day. Everything else lives in [StepRewardsTracker].
 */
interface StepSource {
    val supported: Boolean

    val permission: StateFlow<StepPermission>

    /** Live readings while [setLive] is on, so an open screen can tick. */
    val samples: Flow<StepSample>

    /** One reading, or null when the counter cannot be read right now. */
    suspend fun sample(): StepSample?

    fun setLive(active: Boolean)

    /** Starts the platform's permission flow; the outcome arrives through [permission]. */
    fun requestPermission()

    /** Re-reads the permission state after the platform reported a result. */
    fun refreshPermission()
}

object UnsupportedStepSource : StepSource {
    override val supported: Boolean = false
    override val permission: StateFlow<StepPermission> =
        MutableStateFlow(StepPermission.NotRequired)
    override val samples: Flow<StepSample> = emptyFlow()
    override suspend fun sample(): StepSample? = null
    override fun setLive(active: Boolean) = Unit
    override fun requestPermission() = Unit
    override fun refreshPermission() = Unit
}
