package com.pocketpass.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import com.pocketpass.app.PocketPassApplication

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val holder = (context.applicationContext as PocketPassApplication)
            .container
            .appUpdate
        when (
            val status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE,
            )
        ) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java,
                )
                if (confirm == null) {
                    holder.onInstallFailed("Couldn't open the install prompt.", aborted = false)
                    return
                }
                (context.applicationContext as PocketPassApplication)
                    .container
                    .pendingInstallConfirmation
                    .value = confirm
            }

            PackageInstaller.STATUS_SUCCESS -> Unit

            else -> holder.onInstallFailed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                aborted = status == PackageInstaller.STATUS_FAILURE_ABORTED,
            )
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.pocketpass.app.update.INSTALL_STATUS"
    }
}
