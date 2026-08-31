package com.pocketpass.app.sync

import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class OutboxPayloadsTest {
    @Test
    fun binaryMessagePayloadRoundTripsDelimiterAndUnicodeContent() {
        val payload = MessageSendPayload(
            accountId = UserId("account:one"),
            conversationId = ConversationId("conversation/one"),
            messageId = MessageId("message-one"),
            body = "Hello | PocketPass 👋\nsecond line",
            clientCreatedAt = Instant.parse("2026-03-04T05:06:07Z"),
        )

        val encoded = BinaryMessageSendPayloadCodec.encode(payload)
        val decoded = BinaryMessageSendPayloadCodec.decode(
            encoded = encoded,
            version = BinaryMessageSendPayloadCodec.version,
        )

        assertEquals(payload, decoded)
    }

    @Test
    fun binaryMessagePayloadMatchesTheDataOutputStreamGoldenVector() {
        val payload = MessageSendPayload(
            accountId = UserId("a"),
            conversationId = ConversationId("c"),
            messageId = MessageId("m"),
            body = "hi " + '\uD83D' + '\uDC4B' + " é",
            clientCreatedAt = Instant.parse("2026-03-04T05:06:07Z"),
        )

        assertEquals(
            "AAFhAAFjAAFtAAxoaSDtoL3tsYsgw6kAAAGctz0yGAA",
            BinaryMessageSendPayloadCodec.encode(payload),
        )
    }

    @Test
    fun conversationReadPayloadRoundTripsExactly() {
        val payload = ConversationReadPayload(
            accountId = UserId("account-one"),
            conversationId = ConversationId("conversation-one"),
            readAt = Instant.parse("2026-03-04T05:06:07.123Z"),
        )

        val encoded = BinaryConversationReadPayloadCodec.encode(payload)
        val decoded = BinaryConversationReadPayloadCodec.decode(
            encoded = encoded,
            version = BinaryConversationReadPayloadCodec.version,
        )

        assertEquals(payload, decoded)
    }

    @Test
    fun messageEditPayloadRoundTripsExactly() {
        val payload = MessageEditPayload(
            accountId = UserId("account-one"),
            conversationId = ConversationId("conversation-one"),
            messageId = MessageId("message-one"),
            body = "Edited | text 👋\nsecond line",
            editedAt = Instant.parse("2026-03-04T05:06:07.123Z"),
        )

        val decoded = BinaryMessageEditPayloadCodec.decode(
            encoded = BinaryMessageEditPayloadCodec.encode(payload),
            version = BinaryMessageEditPayloadCodec.version,
        )

        assertEquals(payload, decoded)
    }

    @Test
    fun messageDeletePayloadRoundTripsExactly() {
        val payload = MessageDeletePayload(
            accountId = UserId("account-one"),
            conversationId = ConversationId("conversation-one"),
            messageId = MessageId("message-one"),
            deletedAt = Instant.parse("2026-03-04T05:06:07.123Z"),
        )

        val decoded = BinaryMessageDeletePayloadCodec.decode(
            encoded = BinaryMessageDeletePayloadCodec.encode(payload),
            version = BinaryMessageDeletePayloadCodec.version,
        )

        assertEquals(payload, decoded)
    }

    @Test
    fun messageMutationCodecsRejectUnknownVersions() {
        val edit = BinaryMessageEditPayloadCodec.encode(
            MessageEditPayload(
                accountId = UserId("account-one"),
                conversationId = ConversationId("conversation-one"),
                messageId = MessageId("message-one"),
                body = "Edited",
                editedAt = Instant.parse("2026-03-04T05:06:07Z"),
            ),
        )
        val delete = BinaryMessageDeletePayloadCodec.encode(
            MessageDeletePayload(
                accountId = UserId("account-one"),
                conversationId = ConversationId("conversation-one"),
                messageId = MessageId("message-one"),
                deletedAt = Instant.parse("2026-03-04T05:06:07Z"),
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            BinaryMessageEditPayloadCodec.decode(edit, version = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            BinaryMessageDeletePayloadCodec.decode(delete, version = 0)
        }
    }
}
