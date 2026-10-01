package com.pocketpass.app.boards

data class BoardDestination(val boardId: String, val threadId: String? = null)

data class BoardPushPayload(
    val recipientId: String,
    val bindingId: String,
    val destination: BoardDestination,
    val notificationId: String,
    val eventCount: Long,
    val kind: String = "activity",
    val actorName: String = "",
    val boardName: String = "",
) {
    fun shouldShow(account: String?, binding: String?, foreground: Boolean, seen: Long): Boolean =
        recipientId == account && bindingId == binding && !foreground && eventCount > seen

    val title: String get() = boardName.trim().ifEmpty { "PocketPass Boards" }

    val text: String get() {
        val actor = actorName.trim()
        if (actor.isEmpty()) return "There is new activity in your boards."
        val many = eventCount > 1
        return when (kind) {
            "mention" -> if (many) "$eventCount new mentions, latest from $actor" else "$actor mentioned you"
            "reply" -> if (many) "$eventCount replies to your note, latest from $actor" else "$actor replied to your note"
            "yeah" -> if (many) "$eventCount Yeahs, latest from $actor" else "$actor gave your note a Yeah"
            "note" -> "New note from $actor"
            else -> if (many) "$eventCount new updates, latest from $actor" else "New activity from $actor"
        }
    }

    companion object {
        private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        fun parse(data: Map<String, String>): BoardPushPayload? {
            if(data["type"] != "board" || data["version"] != "1") return null
            val ids = listOf("recipient_id", "binding_id", "board_id", "notification_id").map { data[it]?.takeIf(uuid::matches) ?: return null }
            val thread = data["thread_id"]?.takeIf { it.isNotBlank() }
            if(thread != null && !uuid.matches(thread)) return null
            val count = data["event_count"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
            return BoardPushPayload(ids[0],ids[1],BoardDestination(ids[2],thread),ids[3],count,
                kind = data["kind"]?.takeIf { it.isNotBlank() } ?: "activity",
                actorName = data["actor_name"].orEmpty().take(80),
                boardName = data["board_name"].orEmpty().take(80))
        }
    }
}
