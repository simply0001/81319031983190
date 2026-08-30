package com.pocketpass.app.data.supabase.realtime

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RealtimeDtoMapperTest {
    @Test
    fun mapsInsertRecordFromDatabaseBroadcast() {
        val result = MessageChangeBroadcastDto(
            operation = "INSERT",
            schema = "public",
            table = "messages",
            record = MessageBroadcastRecordDto(
                id = MESSAGE_ID,
                conversationId = CONVERSATION_ID,
            ),
        ).toMessageInvalidation(CONVERSATION_ID)

        assertEquals(
            MessageInvalidationDto(
                conversationId = CONVERSATION_ID,
                messageId = MESSAGE_ID,
                operation = MessageChangeOperation.Insert,
            ),
            result,
        )
    }

    @Test
    fun mapsDeleteUsingOldRecord() {
        val result = MessageChangeBroadcastDto(
            operation = "DELETE",
            schema = "public",
            table = "messages",
            oldRecord = MessageBroadcastRecordDto(
                id = MESSAGE_ID,
                conversationId = CONVERSATION_ID,
            ),
        ).toMessageInvalidation(CONVERSATION_ID)

        assertEquals(MessageChangeOperation.Delete, result?.operation)
    }

    @Test
    fun ignoresRecordFromAnotherConversation() {
        val result = MessageChangeBroadcastDto(
            operation = "UPDATE",
            schema = "public",
            table = "messages",
            record = MessageBroadcastRecordDto(
                id = MESSAGE_ID,
                conversationId = "other-conversation",
            ),
        ).toMessageInvalidation(CONVERSATION_ID)

        assertNull(result)
    }

    @Test
    fun membershipBroadcastMapsToConversationInvalidation() {
        val result = MessageChangeBroadcastDto(
            operation = "UPDATE",
            schema = "public",
            table = "conversation_members",
            record = MessageBroadcastRecordDto(
                conversationId = CONVERSATION_ID,
                userId = "2de26930-cf7b-4a09-b85e-19df68d42f93",
                leftAt = "2026-08-29T12:00:00Z",
            ),
        ).toConversationRealtimeEvent(CONVERSATION_ID)

        assertEquals(
            ConversationRealtimeEvent.ConversationInvalidated(
                ConversationInvalidationDto(
                    conversationId = CONVERSATION_ID,
                    table = ConversationChangeTable.Members,
                    operation = "UPDATE",
                ),
            ),
            result,
        )
    }

    @Test
    fun conversationUpdateUsesTheRecordIdAsConversationId() {
        val result = MessageChangeBroadcastDto(
            operation = "UPDATE",
            schema = "public",
            table = "conversations",
            record = MessageBroadcastRecordDto(id = CONVERSATION_ID, title = "Trip 2"),
        ).toConversationRealtimeEvent(CONVERSATION_ID)

        assertEquals(
            ConversationChangeTable.Conversation,
            (result as ConversationRealtimeEvent.ConversationInvalidated).invalidation.table,
        )
        assertNull(
            MessageChangeBroadcastDto(
                operation = "UPDATE",
                schema = "public",
                table = "conversations",
                record = MessageBroadcastRecordDto(id = "other-conversation", title = "Trip 2"),
            ).toConversationRealtimeEvent(CONVERSATION_ID),
        )
    }

    @Test
    fun messagesBroadcastStillMapsToMessageInvalidation() {
        val result = MessageChangeBroadcastDto(
            operation = "INSERT",
            schema = "public",
            table = "messages",
            record = MessageBroadcastRecordDto(id = MESSAGE_ID, conversationId = CONVERSATION_ID),
        ).toConversationRealtimeEvent(CONVERSATION_ID)

        assertEquals(
            MESSAGE_ID,
            (result as ConversationRealtimeEvent.MessageInvalidated).invalidation.messageId,
        )
    }

    @Test
    fun membershipRecordWithoutAMessageIdDecodes() {
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<MessageChangeBroadcastDto>(
            """
            {"operation":"INSERT","schema":"public","table":"conversation_members",
             "record":{"conversation_id":"$CONVERSATION_ID","user_id":"u","role":"member","joined_at":"x","left_at":null,"last_read_at":null},
             "old_record":null}
            """.trimIndent(),
        )

        assertEquals(CONVERSATION_ID, decoded.record?.conversationId)
        assertNull(decoded.record?.id)
    }

    companion object {
        private const val CONVERSATION_ID = "0f660d77-2c61-4615-ac04-bce9c20620dd"
        private const val MESSAGE_ID = "331ca698-a55a-4f02-8fa5-cf3be55566fa"
    }
}
