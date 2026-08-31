package com.pocketpass.app.sync

import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.MessageAttachment
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.UserId
import kotlin.time.Instant
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

data class MessageSendPayload(
    val accountId: UserId,
    val conversationId: ConversationId,
    val messageId: MessageId,
    val body: String,
    val clientCreatedAt: Instant,
    val attachment: MessageAttachment? = null,
)

interface MessageSendPayloadCodec {
    val version: Int

    fun encode(payload: MessageSendPayload): String

    fun decode(encoded: String, version: Int): MessageSendPayload
}

object BinaryMessageSendPayloadCodec : MessageSendPayloadCodec {
    override val version: Int = 2

    override fun encode(payload: MessageSendPayload): String {
        val buffer = Buffer().apply {
            writeLengthPrefixedUtf(payload.accountId.value)
            writeLengthPrefixedUtf(payload.conversationId.value)
            writeLengthPrefixedUtf(payload.messageId.value)
            writeLengthPrefixedUtf(payload.body)
            writeLong(payload.clientCreatedAt.toEpochMilliseconds())
            val attachment = payload.attachment
            writeByte(if (attachment != null) 1 else 0)
            if (attachment != null) {
                writeLengthPrefixedUtf(attachment.mimeType)
                writeLengthPrefixedUtf(attachment.remotePath.orEmpty())
                writeLengthPrefixedUtf(attachment.localPath.orEmpty())
            }
        }
        return OUTBOX_BASE64.encode(buffer.readByteArray())
    }

    override fun decode(encoded: String, version: Int): MessageSendPayload {
        require(version in SUPPORTED_VERSIONS) {
            "Unsupported SEND_MESSAGE payload version: $version"
        }
        val input = Buffer().apply { write(OUTBOX_BASE64.decode(encoded)) }
        val accountId = UserId(input.readLengthPrefixedUtf())
        val conversationId = ConversationId(input.readLengthPrefixedUtf())
        val messageId = MessageId(input.readLengthPrefixedUtf())
        val body = input.readLengthPrefixedUtf()
        val clientCreatedAt = Instant.fromEpochMilliseconds(input.readLong())
        val attachment = if (version >= 2 && input.readByte() != 0.toByte()) {
            val mimeType = input.readLengthPrefixedUtf()
            val remotePath = input.readLengthPrefixedUtf().ifEmpty { null }
            val localPath = input.readLengthPrefixedUtf().ifEmpty { null }
            MessageAttachment(
                remotePath = remotePath,
                mimeType = mimeType,
                localPath = localPath,
            )
        } else {
            null
        }
        return MessageSendPayload(
            accountId = accountId,
            conversationId = conversationId,
            messageId = messageId,
            body = body,
            clientCreatedAt = clientCreatedAt,
            attachment = attachment,
        )
    }

    private val SUPPORTED_VERSIONS = 1..2
}

data class ConversationReadPayload(
    val accountId: UserId,
    val conversationId: ConversationId,
    val readAt: Instant,
)

interface ConversationReadPayloadCodec {
    val version: Int

    fun encode(payload: ConversationReadPayload): String

    fun decode(encoded: String, version: Int): ConversationReadPayload
}

object BinaryConversationReadPayloadCodec : ConversationReadPayloadCodec {
    override val version: Int = 1

    override fun encode(payload: ConversationReadPayload): String {
        val buffer = Buffer().apply {
            writeLengthPrefixedUtf(payload.accountId.value)
            writeLengthPrefixedUtf(payload.conversationId.value)
            writeLong(payload.readAt.toEpochMilliseconds())
        }
        return OUTBOX_BASE64.encode(buffer.readByteArray())
    }

    override fun decode(encoded: String, version: Int): ConversationReadPayload {
        require(version == this.version) {
            "Unsupported MARK_CONVERSATION_READ payload version: $version"
        }
        val input = Buffer().apply { write(OUTBOX_BASE64.decode(encoded)) }
        return ConversationReadPayload(
            accountId = UserId(input.readLengthPrefixedUtf()),
            conversationId = ConversationId(input.readLengthPrefixedUtf()),
            readAt = Instant.fromEpochMilliseconds(input.readLong()),
        )
    }
}

data class MessageEditPayload(
    val accountId: UserId,
    val conversationId: ConversationId,
    val messageId: MessageId,
    val body: String,
    val editedAt: Instant,
)

interface MessageEditPayloadCodec {
    val version: Int

    fun encode(payload: MessageEditPayload): String

    fun decode(encoded: String, version: Int): MessageEditPayload
}

object BinaryMessageEditPayloadCodec : MessageEditPayloadCodec {
    override val version: Int = 1

    override fun encode(payload: MessageEditPayload): String {
        val buffer = Buffer().apply {
            writeLengthPrefixedUtf(payload.accountId.value)
            writeLengthPrefixedUtf(payload.conversationId.value)
            writeLengthPrefixedUtf(payload.messageId.value)
            writeLengthPrefixedUtf(payload.body)
            writeLong(payload.editedAt.toEpochMilliseconds())
        }
        return OUTBOX_BASE64.encode(buffer.readByteArray())
    }

    override fun decode(encoded: String, version: Int): MessageEditPayload {
        require(version == this.version) {
            "Unsupported EDIT_MESSAGE payload version: $version"
        }
        val input = Buffer().apply { write(OUTBOX_BASE64.decode(encoded)) }
        return MessageEditPayload(
            accountId = UserId(input.readLengthPrefixedUtf()),
            conversationId = ConversationId(input.readLengthPrefixedUtf()),
            messageId = MessageId(input.readLengthPrefixedUtf()),
            body = input.readLengthPrefixedUtf(),
            editedAt = Instant.fromEpochMilliseconds(input.readLong()),
        )
    }
}

data class MessageDeletePayload(
    val accountId: UserId,
    val conversationId: ConversationId,
    val messageId: MessageId,
    val deletedAt: Instant,
)

interface MessageDeletePayloadCodec {
    val version: Int

    fun encode(payload: MessageDeletePayload): String

    fun decode(encoded: String, version: Int): MessageDeletePayload
}

object BinaryMessageDeletePayloadCodec : MessageDeletePayloadCodec {
    override val version: Int = 1

    override fun encode(payload: MessageDeletePayload): String {
        val buffer = Buffer().apply {
            writeLengthPrefixedUtf(payload.accountId.value)
            writeLengthPrefixedUtf(payload.conversationId.value)
            writeLengthPrefixedUtf(payload.messageId.value)
            writeLong(payload.deletedAt.toEpochMilliseconds())
        }
        return OUTBOX_BASE64.encode(buffer.readByteArray())
    }

    override fun decode(encoded: String, version: Int): MessageDeletePayload {
        require(version == this.version) {
            "Unsupported DELETE_MESSAGE payload version: $version"
        }
        val input = Buffer().apply { write(OUTBOX_BASE64.decode(encoded)) }
        return MessageDeletePayload(
            accountId = UserId(input.readLengthPrefixedUtf()),
            conversationId = ConversationId(input.readLengthPrefixedUtf()),
            messageId = MessageId(input.readLengthPrefixedUtf()),
            deletedAt = Instant.fromEpochMilliseconds(input.readLong()),
        )
    }
}

