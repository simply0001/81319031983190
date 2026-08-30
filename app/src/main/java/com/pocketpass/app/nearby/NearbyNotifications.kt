package com.pocketpass.app.nearby

import android.Manifest
import android.app.Notification
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

object NearbyNotifications {
    const val SERVICE_NOTIFICATION_ID = 7_201
    const val REPAIR_NOTIFICATION_ID = 7_202
    const val EXTRA_OPEN_REPAIR = "pocketpass.nearby.OPEN_REPAIR"
    const val EXTRA_OPEN_ENCOUNTER = "pocketpass.nearby.OPEN_ENCOUNTER"

    private const val SERVICE_CHANNEL_ID = "pocketpass_nearby_service"
    private const val ALERT_CHANNEL_ID = "pocketpass_nearby_alerts"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL_ID,
                context.getString(R.string.nearby_service_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(
                    R.string.nearby_service_channel_description,
                )
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                context.getString(R.string.nearby_alert_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(
                    R.string.nearby_alert_channel_description,
                )
            },
        )
    }

    fun foregroundNotification(context: Context): Notification {
        createChannels(context)
        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nearby_notification)
            .setContentTitle(context.getString(R.string.nearby_service_title))
            .setContentText(context.getString(R.string.nearby_service_text))
            .setContentIntent(openAppIntent(context, repair = false))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun postRepair(context: Context) {
        createChannels(context)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nearby_notification)
            .setContentTitle(context.getString(R.string.nearby_repair_title))
            .setContentText(context.getString(R.string.nearby_repair_text))
            .setContentIntent(openAppIntent(context, repair = true))
            .addAction(
                0,
                context.getString(R.string.nearby_repair_action),
                openAppIntent(context, repair = true),
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(REPAIR_NOTIFICATION_ID, notification)
    }

    fun cancelRepair(context: Context) {
        NotificationManagerCompat.from(context).cancel(REPAIR_NOTIFICATION_ID)
    }

    fun postEncounter(
        context: Context,
        displayName: String,
        notificationKey: String,
    ) {
        createChannels(context)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val openIntent = openEncounterIntent(context)
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nearby_notification)
            .setContentTitle(context.getString(R.string.nearby_encounter_title))
            .setContentText(
                context.getString(R.string.nearby_encounter_text, displayName),
            )
            .setContentIntent(openIntent)
            .addAction(
                0,
                context.getString(R.string.nearby_encounter_action),
                openIntent,
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(
            notificationKey.hashCode(),
            notification,
        )
    }

    private fun openAppIntent(context: Context, repair: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (repair) putExtra(EXTRA_OPEN_REPAIR, true)
        }
        return PendingIntent.getActivity(
            context,
            if (repair) 2 else 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openEncounterIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ENCOUNTER, true)
        }
        return PendingIntent.getActivity(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
