package com.pocketpass.app.push

data class MessagePushPayload(
    val recipientId: String,
    val bindingId: String,
    val conversationId: String,
    val notificationId: String,
    val eventCount: Long,
    val title: String,
    val body: String,
) {
    fun shouldShow(accountId: String?, binding: String?, enabled: Boolean, foreground: Boolean, seen: Long): Boolean =
        enabled && !foreground && recipientId == accountId && bindingId == binding && eventCount > seen

    companion object {
        private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        fun parse(data: Map<String, String>): MessagePushPayload? {
            if (data["type"] != "message" || data["version"] != "1") return null
            val ids = listOf("recipient_id", "binding_id", "conversation_id", "notification_id")
                .map { data[it]?.takeIf(uuid::matches) ?: return null }
            val count = data["event_count"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
            val title = data["title"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return MessagePushPayload(ids[0], ids[1], ids[2], ids[3], count, title.take(120), data["body"].orEmpty().take(300))
        }
    }
}
