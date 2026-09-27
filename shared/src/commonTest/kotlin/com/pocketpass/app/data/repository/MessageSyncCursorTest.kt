package com.pocketpass.app.data.repository

import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MessageSyncCursorTest {
    @Test
    fun latestChangeCoversEditsAndDeletes() {
        val created = Instant.parse("2026-09-27T10:00:00Z")
        val edited = Instant.parse("2026-09-27T10:05:00Z")
        val deleted = Instant.parse("2026-09-27T10:07:00.123456Z")

        assertEquals(created, message(created).latestChangeAt())
        assertEquals(edited, message(created, editedAt = edited).latestChangeAt())
        assertEquals(deleted, message(created, editedAt = edited, deletedAt = deleted).latestChangeAt())
    }

    @Test
    fun cursorsRoundTripAndRejectGarbage() {
        val seenThrough = Instant.parse("2026-09-27T10:07:00.123456Z")

        assertEquals(seenThrough, parseCursorOrNull(seenThrough.toString()))
        assertNull(parseCursorOrNull("full_sync"))
    }

    @Test
    fun everyConversationHasItsOwnStream() {
        val stream = messageCursorStream(ConversationId("conversation"))

        assertTrue(stream.startsWith(MESSAGE_CURSOR_STREAM_PREFIX))
        assertEquals(stream, messageCursorStream(ConversationId("conversation")))
        assertTrue(stream != messageCursorStream(ConversationId("other")))
    }

    private fun message(
        createdAt: Instant,
        editedAt: Instant? = null,
        deletedAt: Instant? = null,
    ) = Message(
        id = MessageId("message"),
        conversationId = ConversationId("conversation"),
        senderId = UserId("sender"),
        clientOperationId = null,
        body = "Hello",
        createdAt = createdAt,
        editedAt = editedAt,
        deletedAt = deletedAt,
    )
}
