package com.pocketpass.app.boards

data class BoardDestination(val boardId: String, val threadId: String? = null)

data class BoardPushPayload(val recipientId: String, val bindingId: String, val destination: BoardDestination, val notificationId: String, val eventCount: Long) {
    fun shouldShow(account: String?, binding: String?, foreground: Boolean, seen: Long): Boolean =
        recipientId == account && bindingId == binding && !foreground && eventCount > seen
    companion object {
        private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        fun parse(data: Map<String, String>): BoardPushPayload? {
            if(data["type"] != "board" || data["version"] != "1") return null
            val ids = listOf("recipient_id", "binding_id", "board_id", "notification_id").map { data[it]?.takeIf(uuid::matches) ?: return null }
            val thread = data["thread_id"]?.takeIf { it.isNotBlank() }
            if(thread != null && !uuid.matches(thread)) return null
            val count = data["event_count"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
            return BoardPushPayload(ids[0],ids[1],BoardDestination(ids[2],thread),ids[3],count)
        }
    }
}
