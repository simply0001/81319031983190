package com.pocketpass.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pocketpass.app.MainActivity
import com.pocketpass.app.R
import com.pocketpass.app.boards.BoardPushPayload

object BoardNotifications {
    const val CHANNEL_ID = "pocketpass_boards"
    const val ACTION_OPEN = "com.pocketpass.app.push.OPEN_BOARD"
    const val EXTRA_BOARD = "push_board_id"
    const val EXTRA_THREAD = "push_board_thread_id"
    private const val PREFIX = "pocketpass-board:"
    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID,"Boards",NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Activity in your joined PocketPass boards"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            })
    }
    fun allowed(context: Context): Boolean = MessageNotifications.permissionGranted(context) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    fun post(context: Context, payload: BoardPushPayload) {
        createChannel(context)
        if(!allowed(context)) return
        val target = payload.destination
        val intent = Intent(context,MainActivity::class.java).setAction(ACTION_OPEN)
            .setData(Uri.Builder().scheme("pocketpass-board").authority(payload.recipientId).appendPath(target.boardId).appendPath(target.threadId.orEmpty()).build())
            .putExtra(MessageNotifications.EXTRA_ACCOUNT,payload.recipientId).putExtra(EXTRA_BOARD,target.boardId).putExtra(EXTRA_THREAD,target.threadId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context,CHANNEL_ID).setSmallIcon(R.drawable.ic_message_notification)
            .setContentTitle("PocketPass Boards").setContentText("There is new activity in your boards.")
            .setContentIntent(pending).setCategory(NotificationCompat.CATEGORY_SOCIAL).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(context).notify("$PREFIX${payload.recipientId}:${target.threadId ?: target.boardId}",0,notification) }
    }
    fun cancelAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.tag?.startsWith(PREFIX) == true }.forEach { manager.cancel(it.tag,it.id) }
    }
}
