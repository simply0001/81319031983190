package com.pocketpass.app.nearby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pocketpass.app.PocketPassApplication
import kotlinx.coroutines.launch

class NearbyBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val pending = goAsync()
        val application = context.applicationContext as PocketPassApplication
        application.container.applicationScope.launchWithCompletion(pending) {
            application.container.nearby.restoreAfterSystemEvent()
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchWithCompletion(
    pending: BroadcastReceiver.PendingResult,
    block: suspend () -> Unit,
) {
    launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}
