package com.pocketpass.app.sync

import com.pocketpass.app.data.local.entity.LocalOperationKinds
import com.pocketpass.app.data.local.entity.PendingOperationEntity
import com.pocketpass.app.data.repository.MutationAcknowledgementReconciler
import com.pocketpass.app.data.repository.ProductionOperationKinds
import com.pocketpass.app.data.repository.ProductionOperationPayloadCodec
import com.pocketpass.app.data.repository.remote.ProductionRemoteDataSources
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.DeleteMessageCommand
import com.pocketpass.app.domain.model.EditMessageCommand
import com.pocketpass.app.domain.model.MarkConversationReadCommand
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.SendMessageCommand
import com.pocketpass.app.data.repository.RoomEncounterRepository
import com.pocketpass.app.data.repository.RoomShopRepository
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.domain.model.SubmitNearbyEncounterCommand
import com.pocketpass.app.domain.model.ShopPurchaseOutcome
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.nearby.NearbyProofOutboxStore
import com.pocketpass.app.nearby.NearbyProofPayloadCodec
import com.pocketpass.app.security.SecureStringStore

class RemotePendingOperationExecutor(
    private val remote: ProductionRemoteDataSources,
    private val reconciler: MutationAcknowledgementReconciler,
    private val nearbySecureStore: SecureStringStore? = null,
    private val encounterRepository: RoomEncounterRepository? = null,
    private val onEncounterResolved: (NearbyEncounter) -> Unit = {},
    private val onEncounterSubmitted: (SubmitNearbyEncounterCommand, NearbyEncounter?) -> Unit = { _, _ -> },
    private val shopRepository: RoomShopRepository? = null,
    private val messagePayloadCodec: MessageSendPayloadCodec =
        BinaryMessageSendPayloadCodec,
    private val conversationReadPayloadCodec: ConversationReadPayloadCodec =
        BinaryConversationReadPayloadCodec,
    private val messageEditPayloadCodec: MessageEditPayloadCodec =
        BinaryMessageEditPayloadCodec,
    private val messageDeletePayloadCodec: MessageDeletePayloadCodec =
        BinaryMessageDeletePayloadCodec,
) : PendingOperationExecutor {
    override suspend fun execute(
        operation: PendingOperationEntity,
    ): OutboxExecutionResult = when (operation.kind) {
        LocalOperationKinds.SEND_MESSAGE -> executeMessage(operation)
        LocalOperationKinds.MARK_CONVERSATION_READ -> executeConversationRead(operation)
        LocalOperationKinds.EDIT_MESSAGE -> executeMessageEdit(operation)
        LocalOperationKinds.DELETE_MESSAGE -> executeMessageDelete(operation)
        ProductionOperationKinds.UPDATE_PROFILE -> executeProfileUpdate(operation)
        ProductionOperationKinds.SEND_FRIEND_REQUEST -> executeFriendRequest(operation)
        ProductionOperationKinds.RESPOND_TO_FRIEND_REQUEST ->
            executeFriendRequestResponse(operation)

        ProductionOperationKinds.REMOVE_FRIEND -> executeFriendRemoval(operation)
        ProductionOperationKinds.SET_USER_BLOCK -> executeUserBlock(operation)
        ProductionOperationKinds.MARK_NOTIFICATION_READ ->
            executeMarkNotificationRead(operation)
        ProductionOperationKinds.MARK_ALL_NOTIFICATIONS_READ ->
            executeMarkAllNotificationsRead(operation)
        ProductionOperationKinds.DELETE_NOTIFICATION ->
            executeDeleteNotification(operation)
        NearbyProofOutboxStore.OPERATION_KIND -> executeNearbyEncounter(operation)
        ProductionOperationKinds.PURCHASE_SHOP_ITEM -> executePurchase(operation)
        else -> OutboxExecutionResult.PermanentFailure(
            code = "UNSUPPORTED_OPERATION",
            message = "Unsupported pending operation kind: ${operation.kind}",
        )
    }

    private suspend fun executePurchase(
        operation: PendingOperationEntity,
    ): OutboxExecutionResult {
        val command = decodeOrFailure {
            ProductionOperationPayloadCodec.decodePurchaseShopItem(
                payload = operation.payload,
                version = operation.payloadVersion,
            ).also {
                operation.requireIdentity(
                    accountId = it.accountId.value,
                    clientOperationId = it.clientOperationId.value,
                    aggregateId = it.itemId,
                )
            }
        } ?: run {
            reconciler.reconcileRejectedPurchase(
                accountId = UserId(operation.accountId),
                itemId = operation.aggregateId,
                operationId = operation.operationId,
            )
            return invalidPayload(operation)
        }

        return when (val result = remote.shop.purchaseItem(command)) {
            is RepositoryResult.Failure -> {
                val outboxResult = result.error.toOutboxResult()
                if (outboxResult is OutboxExecutionResult.PermanentFailure) {
                    reconciler.reconcileRejectedPurchase(
                        accountId = command.accountId,
                        itemId = command.itemId,
                        operationId = operation.operationId,
                    )
                }
                outboxResult
            }

            is RepositoryResult.Success -> when (val outcome = result.value) {
                is ShopPurchaseOutcome.Completed -> {
                    reconciler.reconcileAcknowledgedPurchase(
                        accountId = command.accountId,
                        itemId = command.itemId,
                        balance = outcome.balance,
                        purchasedAt = outcome.purchasedAt,
                    )
                    OutboxExecutionResult.Acknowledged
                }

                ShopPurchaseOutcome.AlreadyOwned -> {
                    reconciler.reconcileAcknowledgedPurchase(
                        accountId = command.accountId,
                        itemId = command.itemId,
                        balance = null,
                        purchasedAt = null,
                    )
                    shopRepository?.refreshOwnedItems(command.accountId)
                    shopRepository?.refreshTokenBalance(command.accountId)
                    OutboxExecutionResult.Acknowledged
                }

                is ShopPurchaseOutcome.Rejected -> {
                    reconciler.reconcileRejectedPurchase(
                        accountId = command.accountId,
                        itemId = command.itemId,
                        operationId = operation.operationId,
                    )
                    OutboxExecutionResult.PermanentFailure(
                        code = outcome.reason.code,
                        message = outcome.reason.name,
                    )
                }
            }
        }
    }

    private suspend fun executeNearbyEncounter(
        operation: PendingOperationEntity,
    ): OutboxExecutionResult {
        val secureEntryKey = decodeOrFailure {
            NearbyProofOutboxStore.requireSecureEntryKey(operation)
        } ?: return invalidPayload(operation)
        val secureStore = nearbySecureStore
            ?: return OutboxExecutionResult.PermanentFailure(
                code = "NEARBY_EXECUTOR_UNAVAILABLE",
                message = "Nearby encounter submission is unavailable",
            )
        val encounters = encounterRepository
            ?: return OutboxExecutionResult.PermanentFailure(
                code = "NEARBY_EXECUTOR_UNAVAILABLE",
                message = "Nearby encounter reconciliation is unavailable",
            )
        val encryptedPayload = secureStore.get(secureEntryKey)
            ?: return OutboxExecutionResult.PermanentFailure(
                code = "MISSING_PROTECTED_PAYLOAD",
                message = "The protected nearby receipt is unavailable",
            )
        val command = decodeOrFailure {
            NearbyProofPayloadCodec.decode(encryptedPayload).also {
                operation.requireIdentity(
                    accountId = it.accountId.value,
                    clientOperationId = it.clientOperationId.value,
                    aggregateId = it.encounterId.value,
                )
            }
        } ?: run {
            secureStore.remove(secureEntryKey)
            return invalidPayload(operation)
        }

        return when (val result = remote.encounters.submitEncounter(command)) {
            is RepositoryResult.Success -> {
                encounters.reconcile(result.value)
                secureStore.remove(secureEntryKey)
                onEncounterResolved(result.value)
                onEncounterSubmitted(command, result.value)
                OutboxExecutionResult.Acknowledged
            }

            is RepositoryResult.Failure -> {
                val outboxResult = result.error.toOutboxResult()
                if (outboxResult is OutboxExecutionResult.PermanentFailure) {
                    secureStore.remove(secureEntryKey)
                }
                onEncounterSubmitted(command, null)
                outboxResult
            }
        }
    }

    private suspend fun executeConversationRead(
        operation: PendingOperationEntity,
    ): OutboxExecutionResult {
        val command = decodeOrFailure {
            val payload = conversationReadPayloadCodec.decode(
                encoded = operation.payload,
                version = operation.payloadVersion,
            )
            operation.requireIdentity(
                accountId = payload.accountId.value,
                clientOperationId = operation.idempotencyKey,
                aggregateId = payload.conversationId.value,
            )
            MarkConversationReadCommand(
                accountId = payload.accountId,
                conversationId = payload.conversationId,
                clientOperationId = ClientOperationId(operation.idempotencyKey),
                readAt = payload.readAt,
            )
        } ?: return invalidPayload(operation)

        return remote.messages.markConversationRead(command).toOutboxResult()
    }

    private suspend fun executeMessage(
        operation: PendingOperationEntity,
    ): OutboxExecutionResult {
        val command = decodeOrFailure {
            val payload = messagePayloadCodec.decode(
                encoded = operation.payload,
                version = operation.payloadVersion,
            )
            operation.requireIdentity(
                accountId = payload.accountId.value,
                clientOperationId = operation.idempotencyKey,
                aggregateId = payload.messageId.value,
            )
            SendMessageCommand(
                accountId = payload.accountId,
                conversationId = payload.conversationId,
                messageId = payload.messageId,
                clientOperationId = ClientOperationId(operation.idempotencyKey),
                body = payload.body,
                clientCreatedAt = payload.clientCreatedAt,
                attachment = payload.attachment,
            )
        } ?: return invalidPayload(operation)

        return when (val result = remote.messages.sendMessage(command)) {
            is RepositoryResult.Failure -> result.error.toOutboxResult()
            is RepositoryResult.Success -> {
                val canonical = result.value
                if (
                    canonical.conversationId != command.conversationId ||
                    canonical.senderId != command.accountId ||
                    canonical.clientOperationId != command.clientOperationId
                ) {
                    return OutboxExecutionResult.PermanentFailure(
                        code = "REMOTE_RESPONSE_MISMATCH",
                        message = "Server message does not match the queued operation",
                    )
                }
                reconciler.reconcileAcknowledgedMessage(command.accountId, canonical)
                OutboxExecutionResult.Acknowledged
            }
        }
    }

    private suspend fun executeMessageEdit(
        operation: PendingOperationEntity,
    ): OutboxExecutionResult {
        val command = decodeOrFailure {
            val payload = messageEditPayloadCodec.decode(
                encoded = operation.payload,
                version = operation.payloadVersion,
            )
            operation.requireIdentity(
                accountId = payload.accountId.value,
                clientOperationId = operation.idempotencyKey,
                aggregateId = payload.messageId.value,
            )
            EditMessageCommand(
                accountId = payload.accountId,
                conversationId = payload.conversationId,
                messageId = payload.messageId,
                clientOperationId = ClientOperationId(operation.idempotencyKey),
                body = payload.body,
                editedAt = payload.editedAt,
            )
        } ?: return invalidPayload(operation)

        return when (val result = remote.messages.editMessage(command)) {
            is RepositoryResult.Failure -> result.error.toOutboxResult()
            is RepositoryResult.Success -> acknowledgeMessageMutation(
                accountId = command.accountId,
                conversationId = command.conversationId,
                messageId = command.messageId,
                canonical = result.value,
                expectDeleted = false,
            )
        }
    }

    private suspend fun executeMessageDelete(
        operation: PendingOperationEntity,
    ): OutboxExecutionResult {
        val command = decodeOrFailure {
            val payload = messageDeletePayloadCodec.decode(
                encoded = operation.payload,
                version = operation.payloadVersion,
            )
            operation.requireIdentity(
                accountId = payload.accountId.value,
                clientOperationId = operation.idempotencyKey,
                aggregateId = payload.messageId.value,
            )
            DeleteMessageCommand(
                accountId = payload.accountId,
                conversationId = payload.conversationId,
                messageId = payload.messageId,
                clientOperationId = ClientOperationId(operation.idempotencyKey),
                deletedAt = payload.deletedAt,
            )
        } ?: return invalidPayload(operation)

        return when (val result = remote.messages.deleteMessage(command)) {
            is RepositoryResult.Failure -> result.error.toOutboxResult()
            is RepositoryResult.Success -> acknowledgeMessageMutation(
                accountId = command.accountId,
                conversationId = command.conversationId,
                messageId = command.messageId,
                canonical = result.value,
                expectDeleted = true,
            )
        }
    }

    private suspend fun acknowledgeMessageMutation(
        accountId: UserId,
        conversationId: ConversationId,
        messageId: MessageId,
        canonical: Message,
        expectDeleted: Boolean,
    ): OutboxExecutionResult {
        if (
            canonical.id != messageId ||
            canonical.conversationId != conversationId ||
            canonical.senderId != accountId ||
            (expectDeleted && canonical.deletedAt == null)
        ) {
            return OutboxExecutionResult.PermanentFailure(
                code = "REMOTE_RESPONSE_MISMATCH",
                message = "Server message does not match the queued operation",
            )
        }
        reconciler.reconcileAcknowledgedMessage(accountId, canonical)
        return OutboxExecutionResult.Acknowledged
    }

    private suspend fun executeProfileUpdate(
        operation: PendingOperationEntity,
    ): OutboxExecutionResult {
        val command = decodeOrFailure {
            ProductionOperationPayloadCodec.decodeProfileUpdate(
                operation.payload,
                operation.payloadVersion,
            ).also {
                operation.requireIdentity(
                    accountId = it.accountId.value,
                    clientOperationId = it.clientOperationId.value,
                    aggregateId = it.profile.userId.value,
                )
            }
        } ?: return invalidPayload(operation)

        return when (val result = remote.profiles.updateProfile(command)) {
            is RepositoryResult.Failure -> result.error.toOutboxResult()
            is RepositoryResult.Success -> {
                if (result.value.userId != command.accountId) {
                    return OutboxExecutionResult.PermanentFailure(
                        code = "REMOTE_RESPONSE_MISMATCH",
                        message = "Server profile does not match the queued operation",
                    )
                }
                reconciler.reconcileAcknowledgedProfile(result.value)
                OutboxExecutionResult.Acknowledged
            }
        }
    }

    private suspend fun executeFriendRequest(
        operation: PendingOperationEntity,
    ): OutboxExecutionResult {
        val command = decodeOrFailure {
            ProductionOperationPayloadCodec.decodeSendFriendRequest(
                operation.payload,
                operation.payloadVersion,
            ).also {
                operation.requireIdentity(
                    accountId = it.accountId.value,
                    clientOperationId = it.clientOperationId.value,
                    aggregateId = it.addressee.userId.value,
                )
            }
        } ?: return invalidPayload(operation)
        return remote.friends.sendFriendRequest(command).toOutboxResult()
    }

    private suspend fun executeFriendRequestResponse(
        operation: PendingOperationEntity,
    ): OutboxExecutionResult {
        val command = decodeOrFailure {
            ProductionOperationPayloadCodec.decodeRespondToFriendRequest(
                operation.payload,
                operation.payloadVersion,
            ).also {
                operation.requireIdentity(
                    accountId = it.accountId.value,
                    clientOperationId = it.clientOperationId.value,
                    aggregateId = it.requester.userId.value,
                )
            }
        } ?: return invalidPayload(operation)
        return remote.friends.respondToFriendRequest(command).toOutboxResult()
    }

    private suspend fun executeFriendRemoval(
        operation: PendingOperationEntity,
    ): OutboxExecutionResult {
        val command = decodeOrFailure {
            ProductionOperationPayloadCodec.decodeRemoveFriend(
                operation.payload,
                operation.payloadVersion,
            ).also {
                operation.requireIdentity(
                    accountId = it.accountId.value,
                    clientOperationId = it.clientOperationId.value,
                    aggregateId = it.friendUserId.value,
                )
            }
        } ?: return invalidPayload(operation)
        return remote.friends.removeFriend(command).toOutboxResult()
    }

    private suspend fun executeUserBlock(
        operation: PendingOperationEntity,
    ): OutboxExecutionResult {
        val command = decodeOrFailure {
            ProductionOperationPayloadCodec.decodeSetUserBlock(
                operation.payload,
                operation.payloadVersion,
            ).also {
                operation.requireIdentity(
                    accountId = it.accountId.value,
                    clientOperationId = it.clientOperationId.value,
                    aggregateId = it.targetUserId.value,
                )
            }
        } ?: return invalidPayload(operation)
        return remote.friends.setUserBlocked(command).toOutboxResult()
    }

    private suspend fun executeMarkNotificationRead(
        operation: PendingOperationEntity,
    ): OutboxExecutionResult {
        val command = decodeOrFailure {
            ProductionOperationPayloadCodec.decodeMarkNotificationRead(
                operation.payload,
                operation.payloadVersion,
            ).also {
                operation.requireIdentity(
                    accountId = it.accountId.value,
                    clientOperationId = it.clientOperationId.value,
                    aggregateId = it.notificationId.value,
                )
            }
        } ?: return invalidPayload(operation)
        return remote.notifications.markNotificationRead(command).toOutboxResult()
    }

    private suspend fun executeMarkAllNotificationsRead(
        operation: PendingOperationEntity,
    ): OutboxExecutionResult {
        val command = decodeOrFailure {
            ProductionOperationPayloadCodec.decodeMarkAllNotificationsRead(
                operation.payload,
                operation.payloadVersion,
            ).also {
                operation.requireIdentity(
                    accountId = it.accountId.value,
                    clientOperationId = it.clientOperationId.value,
                    aggregateId = it.accountId.value,
                )
            }
        } ?: return invalidPayload(operation)
        return remote.notifications.markAllNotificationsRead(command).toOutboxResult()
    }

    private suspend fun executeDeleteNotification(
        operation: PendingOperationEntity,
    ): OutboxExecutionResult {
        val command = decodeOrFailure {
            ProductionOperationPayloadCodec.decodeDeleteNotification(
                operation.payload,
                operation.payloadVersion,
            ).also {
                operation.requireIdentity(
                    accountId = it.accountId.value,
                    clientOperationId = it.clientOperationId.value,
                    aggregateId = it.notificationId.value,
                )
            }
        } ?: return invalidPayload(operation)
        return remote.notifications.deleteNotification(command).toOutboxResult()
    }

    private fun invalidPayload(
        operation: PendingOperationEntity,
    ): OutboxExecutionResult.PermanentFailure =
        OutboxExecutionResult.PermanentFailure(
            code = "INVALID_PAYLOAD",
            message = "Queued ${operation.kind} payload is malformed or has mismatched identity",
        )
}

private inline fun <T> decodeOrFailure(block: () -> T): T? = try {
    block()
} catch (_: IllegalArgumentException) {
    null
} catch (_: IllegalStateException) {
    null
}

private fun PendingOperationEntity.requireIdentity(
    accountId: String,
    clientOperationId: String,
    aggregateId: String,
) {
    require(this.accountId == accountId) { "Account identity mismatch" }
    require(idempotencyKey == clientOperationId) { "Idempotency identity mismatch" }
    require(this.aggregateId == aggregateId) { "Aggregate identity mismatch" }
}

private fun RepositoryResult<Unit>.toOutboxResult(): OutboxExecutionResult = when (this) {
    is RepositoryResult.Success -> OutboxExecutionResult.Acknowledged
    is RepositoryResult.Failure -> error.toOutboxResult()
}

private fun RepositoryFailure.toOutboxResult(): OutboxExecutionResult =
    if (retryable) {
        OutboxExecutionResult.RetryableFailure(
            code = kind.name,
            message = message,
        )
    } else {
        OutboxExecutionResult.PermanentFailure(
            code = kind.name,
            message = message,
        )
    }
