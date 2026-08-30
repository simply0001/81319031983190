package com.pocketpass.app.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pocketpass.app.MainActivity
import com.pocketpass.app.R

object UpdateNotifications {
    const val UPDATE_NOTIFICATION_ID = 7_203
    const val EXTRA_OPEN_APP_UPDATE = "pocketpass.update.OPEN_APP_UPDATE"

    private const val CHANNEL_ID = "pocketpass_updates"

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.update_channel_description)
            },
        )
    }

    fun postUpdateAvailable(context: Context, versionName: String): Boolean {
        createChannel(context)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val openIntent = openUpdateIntent(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nearby_notification)
            .setContentTitle(context.getString(R.string.update_available_title))
            .setContentText(
                context.getString(R.string.update_available_text, versionName),
            )
            .setContentIntent(openIntent)
            .addAction(
                0,
                context.getString(R.string.update_available_action),
                openIntent,
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(
            UPDATE_NOTIFICATION_ID,
            notification,
        )
        return true
    }

    private fun openUpdateIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_APP_UPDATE, true)
        }
        return PendingIntent.getActivity(
            context,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(UPDATE_NOTIFICATION_ID)
    }
}
