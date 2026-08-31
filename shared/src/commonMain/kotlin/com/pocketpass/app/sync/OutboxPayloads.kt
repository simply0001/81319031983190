package com.pocketpass.app.sync

import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.MessageAttachment
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.UserId
import kotlin.io.encoding.Base64
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

private val OUTBOX_BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

// Payloads written before the multiplatform port used DataOutputStream.writeUTF, so queued
// operations decode only if these helpers keep that exact wire format: a two-byte big-endian
// byte count followed by CESU-8-style "modified UTF-8", where each UTF-16 unit (surrogate
// halves included) is encoded on its own and NUL becomes the two-byte form.
private fun Buffer.writeLengthPrefixedUtf(value: String) {
    val bytes = Buffer()
    for (character in value) {
        val code = character.code
        when {
            code in 0x0001..0x007F -> bytes.writeByte(code.toByte())
            code <= 0x07FF -> {
                bytes.writeByte((0xC0 or (code shr 6 and 0x1F)).toByte())
                bytes.writeByte((0x80 or (code and 0x3F)).toByte())
            }
            else -> {
                bytes.writeByte((0xE0 or (code shr 12 and 0x0F)).toByte())
                bytes.writeByte((0x80 or (code shr 6 and 0x3F)).toByte())
                bytes.writeByte((0x80 or (code and 0x3F)).toByte())
            }
        }
    }
    val encoded = bytes.readByteArray()
    require(encoded.size <= 0xFFFF) { "Encoded string is too long: ${encoded.size} bytes" }
    writeShort(encoded.size.toShort())
    write(encoded)
}

private fun Buffer.readLengthPrefixedUtf(): String {
    val length = readShort().toInt() and 0xFFFF
    val bytes = readByteArray(length)
    val builder = StringBuilder(length)
    var index = 0
    while (index < bytes.size) {
        val first = bytes[index].toInt() and 0xFF
        when {
            first and 0x80 == 0 -> {
                builder.append(first.toChar())
                index += 1
            }
            first shr 5 == 0b110 -> {
                require(index + 1 < bytes.size) { "Truncated modified UTF-8 sequence" }
                val second = bytes[index + 1].toInt()
                require(second and 0xC0 == 0x80) { "Malformed modified UTF-8 sequence" }
                builder.append(((first and 0x1F) shl 6 or (second and 0x3F)).toChar())
                index += 2
            }
            first shr 4 == 0b1110 -> {
                require(index + 2 < bytes.size) { "Truncated modified UTF-8 sequence" }
                val second = bytes[index + 1].toInt()
                val third = bytes[index + 2].toInt()
                require(second and 0xC0 == 0x80 && third and 0xC0 == 0x80) {
                    "Malformed modified UTF-8 sequence"
                }
                builder.append(
                    ((first and 0x0F) shl 12 or ((second and 0x3F) shl 6) or (third and 0x3F))
                        .toChar(),
                )
                index += 3
            }
            else -> throw IllegalArgumentException("Malformed modified UTF-8 sequence")
        }
    }
    return builder.toString()
}
