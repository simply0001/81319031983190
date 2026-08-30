package com.pocketpass.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ApkInstaller {
    suspend fun commit(context: Context, apk: File, manifest: UpdateManifest) =
        withContext(Dispatchers.IO) {
            check(apk.isFile) { "The downloaded update is missing. Download it again." }
            check(sha256Of(apk).equals(manifest.apkSha256, ignoreCase = true)) {
                "The downloaded update failed verification. Download it again."
            }
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
            }
            val sessionId = installer.createSession(params)
            try {
                installer.openSession(sessionId).use { session ->
                    session.openWrite("PocketPass.apk", 0, apk.length()).use { output ->
                        apk.inputStream().use { it.copyTo(output) }
                        session.fsync(output)
                    }
                    val statusIntent = Intent(context, UpdateInstallReceiver::class.java)
                        .setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS)
                        .setPackage(context.packageName)
                    val pending = PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        statusIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(pending.intentSender)
                }
            } catch (error: Throwable) {
                runCatching { installer.abandonSession(sessionId) }
                throw error
            }
        }
}
