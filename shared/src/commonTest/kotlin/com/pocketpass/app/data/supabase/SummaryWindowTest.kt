package com.pocketpass.app.data.supabase

import com.pocketpass.app.data.supabase.dto.MessageDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject

class SummaryWindowTest {
    @Test
    fun anIncompleteWindowAlreadyHoldsEveryConversation() {
        val beyond = conversationsBeyondSummaryWindow(
            lastReadAtByConversation = mapOf(BUSY to null, QUIET to null),
            window = listOf(message("1", BUSY, "2026-09-27T10:00:00Z")),
            windowLimit = 2,
        )

        assertTrue(beyond.isEmpty())
    }

    @Test
    fun aFullWindowSendsQuietAndPartlyReadChatsToTheirOwnPage() {
        val beyond = conversationsBeyondSummaryWindow(
            lastReadAtByConversation = mapOf(
                BUSY to Instant.parse("2026-09-27T10:00:30Z"),
                UNREAD_BEYOND_WINDOW to Instant.parse("2026-09-27T09:00:00Z"),
                NEVER_READ to null,
                QUIET to Instant.parse("2026-09-27T11:00:00Z"),
                ONLY_DELETED to Instant.parse("2026-09-27T11:00:00Z"),
            ),
            window = listOf(
                message("1", BUSY, "2026-09-27T10:02:00Z"),
                message("2", UNREAD_BEYOND_WINDOW, "2026-09-27T10:01:30Z"),
                message("3", NEVER_READ, "2026-09-27T10:01:00Z"),
                message("4", ONLY_DELETED, "2026-09-27T10:00:45Z", deletedAt = "2026-09-27T10:05:00Z"),
                message("5", BUSY, "2026-09-27T10:00:00Z"),
            ),
            windowLimit = 5,
        )

        assertEquals(setOf(UNREAD_BEYOND_WINDOW, NEVER_READ, QUIET, ONLY_DELETED), beyond)
    }

    @Test
    fun aChatReadExactlyAtTheWindowStartIsComplete() {
        val beyond = conversationsBeyondSummaryWindow(
            lastReadAtByConversation = mapOf(BUSY to Instant.parse("2026-09-27T10:00:00Z")),
            window = listOf(
                message("1", BUSY, "2026-09-27T10:01:00Z"),
                message("2", BUSY, "2026-09-27T10:00:00Z"),
            ),
            windowLimit = 2,
        )

        assertTrue(beyond.isEmpty())
    }

    private fun message(
        id: String,
        conversationId: String,
        createdAt: String,
        deletedAt: String? = null,
    ) = MessageDto(
        id = id,
        conversationId = conversationId,
        senderId = SENDER,
        body = "Hello",
        metadata = JsonObject(emptyMap()),
        createdAt = createdAt,
        deletedAt = deletedAt,
    )

    private companion object {
        const val SENDER = "2de26930-cf7b-4a09-b85e-19df68d42f93"
        const val BUSY = "busy"
        const val QUIET = "quiet"
        const val NEVER_READ = "never-read"
        const val UNREAD_BEYOND_WINDOW = "unread-beyond-window"
        const val ONLY_DELETED = "only-deleted"
    }
}
