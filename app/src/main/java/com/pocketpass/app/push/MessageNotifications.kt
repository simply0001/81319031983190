package com.pocketpass.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pocketpass.app.MainActivity
import com.pocketpass.app.R

object MessageNotifications {
    const val CHANNEL_ID = "pocketpass_messages"
    const val ACTION_OPEN = "com.pocketpass.app.push.OPEN_CONVERSATION"
    const val EXTRA_ACCOUNT = "push_account_id"
    const val EXTRA_CONVERSATION = "push_conversation_id"
    private const val TAG_PREFIX = "pocketpass-message:"

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New direct and group messages"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            },
        )
    }

    fun permissionGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun allowed(context: Context): Boolean = permissionGranted(context) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE

    fun post(context: Context, payload: MessagePushPayload): Boolean {
        createChannel(context)
        if (!allowed(context)) return false
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN)
            .setData(Uri.Builder().scheme("pocketpass-message").authority(payload.recipientId).appendPath(payload.conversationId).build())
            .putExtra(EXTRA_ACCOUNT, payload.recipientId)
            .putExtra(EXTRA_CONVERSATION, payload.conversationId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_message_notification)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()
        return runCatching {
            NotificationManagerCompat.from(context).notify("$TAG_PREFIX${payload.recipientId}:${payload.conversationId}", 0, notification)
        }.isSuccess
    }

    fun cancelAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.tag?.startsWith(TAG_PREFIX) == true }
            .forEach { manager.cancel(it.tag, it.id) }
    }
}
