package com.pocketpass.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.pocketpass.app.push.MessageNotifications
import com.pocketpass.app.push.MessagePushPayload
import org.junit.Assert.*
import org.junit.Test

class MessageNotificationsTest {
    @Test fun messagesReplacePerConversationAndRemainSeparateAcrossChats() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        val payload = MessagePushPayload(
            "10000000-0000-4000-8000-000000000001", "10000000-0000-4000-8000-000000000002",
            "10000000-0000-4000-8000-000000000003", "10000000-0000-4000-8000-000000000004",
            1, "Test chat", "Test notification",
        )
        MessageNotifications.cancelAll(context)
        try {
            assertTrue(MessageNotifications.post(context, payload))
            assertTrue(MessageNotifications.post(context, payload.copy(eventCount = 2, body = "Updated")))
            assertTrue(MessageNotifications.post(context, payload.copy(conversationId = "10000000-0000-4000-8000-000000000005")))
            Thread.sleep(500)
            val posted = manager.activeNotifications.filter { it.tag?.startsWith("pocketpass-message:") == true }
            assertEquals(2, posted.size)
            assertNotEquals(posted[0].notification.contentIntent, posted[1].notification.contentIntent)
            assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(MessageNotifications.CHANNEL_ID).importance)
        } finally {
            MessageNotifications.cancelAll(context)
        }
    }
}
