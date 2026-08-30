package com.pocketpass.app.sync

import com.pocketpass.app.data.local.dao.OutboxEnqueueResult
import com.pocketpass.app.data.local.dao.OutboxDao
import com.pocketpass.app.data.local.entity.LocalDeliveryStates
import com.pocketpass.app.data.local.entity.LocalOperationKinds
import com.pocketpass.app.data.local.entity.LocalOutboxStates
import com.pocketpass.app.data.local.entity.MessageEntity
import com.pocketpass.app.data.local.entity.PendingOperationEntity
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.DeleteMessageCommand
import com.pocketpass.app.domain.model.EditMessageCommand
import com.pocketpass.app.domain.model.MarkConversationReadCommand
import com.pocketpass.app.domain.model.MessageAttachment
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.SendMessageCommand
import com.pocketpass.app.domain.model.UserId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.time.Instant
import java.util.Base64

object OutboxOperationKinds {
    const val SEND_MESSAGE = LocalOperationKinds.SEND_MESSAGE
    const val MARK_CONVERSATION_READ = LocalOperationKinds.MARK_CONVERSATION_READ
    const val EDIT_MESSAGE = LocalOperationKinds.EDIT_MESSAGE
    const val DELETE_MESSAGE = LocalOperationKinds.DELETE_MESSAGE
}

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
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeUTF(payload.accountId.value)
                output.writeUTF(payload.conversationId.value)
                output.writeUTF(payload.messageId.value)
                output.writeUTF(payload.body)
                output.writeLong(payload.clientCreatedAt.toEpochMilliseconds())
                val attachment = payload.attachment
                output.writeBoolean(attachment != null)
                if (attachment != null) {
                    output.writeUTF(attachment.mimeType)
                    output.writeUTF(attachment.remotePath.orEmpty())
                    output.writeUTF(attachment.localPath.orEmpty())
                }
            }
            buffer.toByteArray()
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun decode(encoded: String, version: Int): MessageSendPayload {
        require(version in SUPPORTED_VERSIONS) {
            "Unsupported SEND_MESSAGE payload version: $version"
        }
        val bytes = Base64.getUrlDecoder().decode(encoded)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val accountId = UserId(input.readUTF())
            val conversationId = ConversationId(input.readUTF())
            val messageId = MessageId(input.readUTF())
            val body = input.readUTF()
            val clientCreatedAt = Instant.fromEpochMilliseconds(input.readLong())
            val attachment = if (version >= 2 && input.readBoolean()) {
                val mimeType = input.readUTF()
                val remotePath = input.readUTF().ifEmpty { null }
                val localPath = input.readUTF().ifEmpty { null }
                MessageAttachment(
                    remotePath = remotePath,
                    mimeType = mimeType,
                    localPath = localPath,
                )
            } else {
                null
            }
            MessageSendPayload(
                accountId = accountId,
                conversationId = conversationId,
                messageId = messageId,
                body = body,
                clientCreatedAt = clientCreatedAt,
                attachment = attachment,
            )
        }
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
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeUTF(payload.accountId.value)
                output.writeUTF(payload.conversationId.value)
                output.writeLong(payload.readAt.toEpochMilliseconds())
            }
            buffer.toByteArray()
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun decode(encoded: String, version: Int): ConversationReadPayload {
        require(version == this.version) {
            "Unsupported MARK_CONVERSATION_READ payload version: $version"
        }
        val bytes = Base64.getUrlDecoder().decode(encoded)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            ConversationReadPayload(
                accountId = UserId(input.readUTF()),
                conversationId = ConversationId(input.readUTF()),
                readAt = Instant.fromEpochMilliseconds(input.readLong()),
            )
        }
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
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeUTF(payload.accountId.value)
                output.writeUTF(payload.conversationId.value)
                output.writeUTF(payload.messageId.value)
                output.writeUTF(payload.body)
                output.writeLong(payload.editedAt.toEpochMilliseconds())
            }
            buffer.toByteArray()
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun decode(encoded: String, version: Int): MessageEditPayload {
        require(version == this.version) {
            "Unsupported EDIT_MESSAGE payload version: $version"
        }
        val bytes = Base64.getUrlDecoder().decode(encoded)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            MessageEditPayload(
                accountId = UserId(input.readUTF()),
                conversationId = ConversationId(input.readUTF()),
                messageId = MessageId(input.readUTF()),
                body = input.readUTF(),
                editedAt = Instant.fromEpochMilliseconds(input.readLong()),
            )
        }
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
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeUTF(payload.accountId.value)
                output.writeUTF(payload.conversationId.value)
                output.writeUTF(payload.messageId.value)
                output.writeLong(payload.deletedAt.toEpochMilliseconds())
            }
            buffer.toByteArray()
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun decode(encoded: String, version: Int): MessageDeletePayload {
        require(version == this.version) {
            "Unsupported DELETE_MESSAGE payload version: $version"
        }
        val bytes = Base64.getUrlDecoder().decode(encoded)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            MessageDeletePayload(
                accountId = UserId(input.readUTF()),
                conversationId = ConversationId(input.readUTF()),
                messageId = MessageId(input.readUTF()),
                deletedAt = Instant.fromEpochMilliseconds(input.readLong()),
            )
        }
    }
}

class MessageOutboxStore(
    private val outboxDao: OutboxDao,
    private val payloadCodec: MessageSendPayloadCodec = BinaryMessageSendPayloadCodec,
    private val readPayloadCodec: ConversationReadPayloadCodec =
        BinaryConversationReadPayloadCodec,
    private val editPayloadCodec: MessageEditPayloadCodec = BinaryMessageEditPayloadCodec,
    private val deletePayloadCodec: MessageDeletePayloadCodec =
        BinaryMessageDeletePayloadCodec,
) {
    suspend fun enqueueEdit(command: EditMessageCommand): Boolean {
        val operation = messageOperation(
            command.clientOperationId.value,
            command.accountId,
            OutboxOperationKinds.EDIT_MESSAGE,
            command.messageId,
            editPayloadCodec.encode(
                MessageEditPayload(
                    accountId = command.accountId,
                    conversationId = command.conversationId,
                    messageId = command.messageId,
                    body = command.body,
                    editedAt = command.editedAt,
                ),
            ),
            editPayloadCodec.version,
            command.editedAt,
        )
        return outboxDao.enqueueOperation(operation) != -1L
    }

    suspend fun enqueueDelete(command: DeleteMessageCommand): Boolean {
        val operation = messageOperation(
            command.clientOperationId.value,
            command.accountId,
            OutboxOperationKinds.DELETE_MESSAGE,
            command.messageId,
            deletePayloadCodec.encode(
                MessageDeletePayload(
                    accountId = command.accountId,
                    conversationId = command.conversationId,
                    messageId = command.messageId,
                    deletedAt = command.deletedAt,
                ),
            ),
            deletePayloadCodec.version,
            command.deletedAt,
        )
        return outboxDao.enqueueOperation(operation) != -1L
    }

    private fun messageOperation(
        operationId: String,
        accountId: UserId,
        kind: String,
        messageId: MessageId,
        payload: String,
        payloadVersion: Int,
        createdAt: Instant,
    ) = PendingOperationEntity(
        operationId = operationId,
        accountId = accountId.value,
        idempotencyKey = operationId,
        kind = kind,
        aggregateId = messageId.value,
        payload = payload,
        payloadVersion = payloadVersion,
        state = LocalOutboxStates.PENDING,
        attemptCount = 0,
        createdAtEpochMillis = createdAt.toEpochMilliseconds(),
        nextAttemptAtEpochMillis = createdAt.toEpochMilliseconds(),
        leaseUntilEpochMillis = null,
        leaseToken = null,
        completedAtEpochMillis = null,
        lastErrorCode = null,
        lastErrorMessage = null,
    )

    suspend fun enqueue(command: SendMessageCommand): OutboxEnqueueResult {
        val operationId = command.clientOperationId.value
        val message = MessageEntity(
            accountId = command.accountId.value,
            messageId = command.messageId.value,
            conversationId = command.conversationId.value,
            senderId = command.accountId.value,
            clientOperationId = command.clientOperationId.value,
            body = command.body,
            createdAtEpochMillis = command.clientCreatedAt.toEpochMilliseconds(),
            editedAtEpochMillis = null,
            deletedAtEpochMillis = null,
            deliveryState = LocalDeliveryStates.QUEUED,
            pendingOperationId = operationId,
            deliveryAttempt = 0,
            lastDeliveryError = null,
            attachmentPath = command.attachment?.remotePath,
            attachmentMime = command.attachment?.mimeType,
            attachmentLocalPath = command.attachment?.localPath,
        )
        val operation = PendingOperationEntity(
            operationId = operationId,
            accountId = command.accountId.value,
            idempotencyKey = command.clientOperationId.value,
            kind = OutboxOperationKinds.SEND_MESSAGE,
            aggregateId = command.messageId.value,
            payload = payloadCodec.encode(
                MessageSendPayload(
                    accountId = command.accountId,
                    conversationId = command.conversationId,
                    messageId = command.messageId,
                    body = command.body,
                    clientCreatedAt = command.clientCreatedAt,
                    attachment = command.attachment,
                ),
            ),
            payloadVersion = payloadCodec.version,
            state = LocalOutboxStates.PENDING,
            attemptCount = 0,
            createdAtEpochMillis = command.clientCreatedAt.toEpochMilliseconds(),
            nextAttemptAtEpochMillis = command.clientCreatedAt.toEpochMilliseconds(),
            leaseUntilEpochMillis = null,
            leaseToken = null,
            completedAtEpochMillis = null,
            lastErrorCode = null,
            lastErrorMessage = null,
        )
        return outboxDao.enqueueOutgoingMessage(message, operation)
    }

    suspend fun enqueueRead(command: MarkConversationReadCommand): Boolean {
        val operationId = command.clientOperationId.value
        val operation = PendingOperationEntity(
            operationId = operationId,
            accountId = command.accountId.value,
            idempotencyKey = operationId,
            kind = OutboxOperationKinds.MARK_CONVERSATION_READ,
            aggregateId = command.conversationId.value,
            payload = readPayloadCodec.encode(
                ConversationReadPayload(
                    accountId = command.accountId,
                    conversationId = command.conversationId,
                    readAt = command.readAt,
                ),
            ),
            payloadVersion = readPayloadCodec.version,
            state = LocalOutboxStates.PENDING,
            attemptCount = 0,
            createdAtEpochMillis = command.readAt.toEpochMilliseconds(),
            nextAttemptAtEpochMillis = command.readAt.toEpochMilliseconds(),
            leaseUntilEpochMillis = null,
            leaseToken = null,
            completedAtEpochMillis = null,
            lastErrorCode = null,
            lastErrorMessage = null,
        )
        return outboxDao.enqueueOperation(operation) != -1L
    }
}

sealed interface OutboxExecutionResult {
    data object Acknowledged : OutboxExecutionResult

    data class RetryableFailure(
        val code: String,
        val message: String? = null,
    ) : OutboxExecutionResult

    data class PermanentFailure(
        val code: String,
        val message: String? = null,
    ) : OutboxExecutionResult
}

fun interface PendingOperationExecutor {
    suspend fun execute(operation: PendingOperationEntity): OutboxExecutionResult
}
