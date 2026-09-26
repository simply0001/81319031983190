package com.pocketpass.app.steps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pocketpass.app.PocketPassApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class StepMidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        StepRewardsScheduler.scheduleNextMidnight(context)
        val pending = goAsync()
        val container = (context.applicationContext as PocketPassApplication).container
        container.applicationScope.launch {
            try {
                withTimeoutOrNull(READING_BUDGET_MILLIS) {
                    container.stepRewards.state.first { it.status == StepRewardsStatus.Tracking }
                    container.stepRewards.sampleAndClaim()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val READING_BUDGET_MILLIS = 9_000L
    }
}
