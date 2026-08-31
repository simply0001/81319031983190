package com.pocketpass.app.sync

import com.pocketpass.app.data.local.dao.OutboxEnqueueResult
import com.pocketpass.app.data.local.dao.OutboxDao
import com.pocketpass.app.data.local.entity.LocalDeliveryStates
import com.pocketpass.app.data.local.entity.LocalOperationKinds
import com.pocketpass.app.data.local.entity.LocalOutboxStates
import com.pocketpass.app.data.local.entity.MessageEntity
import com.pocketpass.app.data.local.entity.PendingOperationEntity
import com.pocketpass.app.domain.model.DeleteMessageCommand
import com.pocketpass.app.domain.model.EditMessageCommand
import com.pocketpass.app.domain.model.MarkConversationReadCommand
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.SendMessageCommand
import com.pocketpass.app.domain.model.UserId
import kotlin.time.Instant

object OutboxOperationKinds {
    const val SEND_MESSAGE = LocalOperationKinds.SEND_MESSAGE
    const val MARK_CONVERSATION_READ = LocalOperationKinds.MARK_CONVERSATION_READ
    const val EDIT_MESSAGE = LocalOperationKinds.EDIT_MESSAGE
    const val DELETE_MESSAGE = LocalOperationKinds.DELETE_MESSAGE
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
