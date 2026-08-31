package com.pocketpass.app.data.repository

import androidx.room.PooledConnection
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteStatement
import com.pocketpass.app.data.local.PocketPassDatabase
import com.pocketpass.app.data.local.entity.LocalOutboxStates
import com.pocketpass.app.data.local.toEntity
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.DeleteNotificationCommand
import com.pocketpass.app.domain.model.MarkAllNotificationsReadCommand
import com.pocketpass.app.domain.model.MarkNotificationReadCommand
import com.pocketpass.app.domain.model.FriendshipStatus
import com.pocketpass.app.domain.model.PresenceStatus
import com.pocketpass.app.domain.model.PurchaseShopItemCommand
import com.pocketpass.app.domain.model.RemoveFriendCommand
import com.pocketpass.app.domain.model.RespondToFriendRequestCommand
import com.pocketpass.app.domain.model.SendFriendRequestCommand
import com.pocketpass.app.domain.model.SetUserBlockCommand
import com.pocketpass.app.domain.model.UpdateProfileCommand
import com.pocketpass.app.domain.model.UserProfile

sealed interface OptimisticMutationResult<out T> {
    data class Enqueued<T>(
        val operationId: String,
        val value: T,
    ) : OptimisticMutationResult<T>

    data class AlreadyEnqueued<T>(
        val operationId: String,
        val value: T,
    ) : OptimisticMutationResult<T>

    data class Conflict(
        val reason: String,
    ) : OptimisticMutationResult<Nothing>
}

class ProductionMutationStore(
    private val database: PocketPassDatabase,
) {
    suspend fun enqueueProfileUpdate(
        command: UpdateProfileCommand,
    ): OptimisticMutationResult<UserProfile> {
        val payload = ProductionOperationPayloadCodec.encode(command)
        return enqueue(
            operation = OperationSpec(
                operationId = command.clientOperationId.value,
                accountId = command.accountId.value,
                idempotencyKey = command.clientOperationId.value,
                kind = ProductionOperationKinds.UPDATE_PROFILE,
                aggregateId = command.profile.userId.value,
                payload = payload,
                createdAtEpochMillis = command.changedAt.toEpochMilliseconds(),
            ),
            value = command.profile,
        ) {
            upsertProfile(command.profile)
        }
    }

    suspend fun enqueueFriendRequest(
        command: SendFriendRequestCommand,
    ): OptimisticMutationResult<Friend> {
        val optimisticFriend = Friend(
            ownerId = command.accountId,
            profile = command.addressee,
            status = FriendshipStatus.PendingOutgoing,
            lastInteractionAt = command.requestedAt,
            isOnline = command.addressee.presence == PresenceStatus.Online,
        )
        return enqueue(
            operation = OperationSpec(
                operationId = command.clientOperationId.value,
                accountId = command.accountId.value,
                idempotencyKey = command.clientOperationId.value,
                kind = ProductionOperationKinds.SEND_FRIEND_REQUEST,
                aggregateId = command.addressee.userId.value,
                payload = ProductionOperationPayloadCodec.encode(command),
                createdAtEpochMillis = command.requestedAt.toEpochMilliseconds(),
            ),
            value = optimisticFriend,
        ) {
            upsertFriend(optimisticFriend)
        }
    }

    suspend fun enqueueFriendRequestResponse(
        command: RespondToFriendRequestCommand,
    ): OptimisticMutationResult<Friend?> {
        val optimisticFriend = if (command.accept) {
            Friend(
                ownerId = command.accountId,
                profile = command.requester,
                status = FriendshipStatus.Accepted,
                lastInteractionAt = command.respondedAt,
                isOnline = command.requester.presence == PresenceStatus.Online,
            )
        } else {
            null
        }
        return enqueue(
            operation = OperationSpec(
                operationId = command.clientOperationId.value,
                accountId = command.accountId.value,
                idempotencyKey = command.clientOperationId.value,
                kind = ProductionOperationKinds.RESPOND_TO_FRIEND_REQUEST,
                aggregateId = command.requester.userId.value,
                payload = ProductionOperationPayloadCodec.encode(command),
                createdAtEpochMillis = command.respondedAt.toEpochMilliseconds(),
            ),
            value = optimisticFriend,
        ) {
            if (optimisticFriend == null) {
                deleteFriend(
                    ownerId = command.accountId.value,
                    friendUserId = command.requester.userId.value,
                )
            } else {
                upsertFriend(optimisticFriend)
            }
        }
    }

    suspend fun enqueueFriendRemoval(
        command: RemoveFriendCommand,
    ): OptimisticMutationResult<Unit> = enqueue(
        operation = OperationSpec(
            operationId = command.clientOperationId.value,
            accountId = command.accountId.value,
            idempotencyKey = command.clientOperationId.value,
            kind = ProductionOperationKinds.REMOVE_FRIEND,
            aggregateId = command.friendUserId.value,
            payload = ProductionOperationPayloadCodec.encode(command),
            createdAtEpochMillis = command.removedAt.toEpochMilliseconds(),
        ),
        value = Unit,
    ) {
        deleteFriend(
            ownerId = command.accountId.value,
            friendUserId = command.friendUserId.value,
        )
    }

    suspend fun enqueueUserBlock(
        command: SetUserBlockCommand,
    ): OptimisticMutationResult<Unit> = enqueue(
        operation = OperationSpec(
            operationId = command.clientOperationId.value,
            accountId = command.accountId.value,
            idempotencyKey = command.clientOperationId.value,
            kind = ProductionOperationKinds.SET_USER_BLOCK,
            aggregateId = command.targetUserId.value,
            payload = ProductionOperationPayloadCodec.encode(command),
            createdAtEpochMillis = command.changedAt.toEpochMilliseconds(),
        ),
        value = Unit,
    ) {
        if (command.blocked) {
            deleteFriend(
                ownerId = command.accountId.value,
                friendUserId = command.targetUserId.value,
            )
        }
    }

    suspend fun enqueuePurchase(
        command: PurchaseShopItemCommand,
    ): OptimisticMutationResult<Unit> = database.useWriterConnection { transactor ->
        transactor.immediateTransaction {
            val existing = findOperation(
                accountId = command.accountId.value,
                idempotencyKey = command.clientOperationId.value,
            )
            if (existing == null) {
                if (ownsShopItem(command.accountId.value, command.itemId)) {
                    return@immediateTransaction OptimisticMutationResult.Conflict("Item is already owned")
                }
                if (availableTokenBalance(command.accountId.value) < command.priceTokens) {
                    return@immediateTransaction OptimisticMutationResult.Conflict("Not enough tokens")
                }
            }
            enqueue(
                operation = OperationSpec(
                    operationId = command.clientOperationId.value,
                    accountId = command.accountId.value,
                    idempotencyKey = command.clientOperationId.value,
                    kind = ProductionOperationKinds.PURCHASE_SHOP_ITEM,
                    aggregateId = command.itemId,
                    payload = ProductionOperationPayloadCodec.encode(command),
                    createdAtEpochMillis = command.requestedAt.toEpochMilliseconds(),
                ),
                value = Unit,
            ) {
                usePrepared(
                    """
                    INSERT OR ABORT INTO owned_shop_items
                        (accountId, itemId, pricePaid, purchasedAtEpochMillis, pendingOperationId)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                ) { statement ->
                    statement.bindText(1, command.accountId.value)
                    statement.bindText(2, command.itemId)
                    statement.bindLong(3, command.priceTokens.toLong())
                    statement.bindLong(4, command.requestedAt.toEpochMilliseconds())
                    statement.bindText(5, command.clientOperationId.value)
                    statement.step()
                }
            }
        }
    }

    suspend fun enqueueMarkNotificationRead(
        command: MarkNotificationReadCommand,
    ): OptimisticMutationResult<Unit> = enqueue(
        operation = OperationSpec(
            operationId = command.clientOperationId.value,
            accountId = command.accountId.value,
            idempotencyKey = command.clientOperationId.value,
            kind = ProductionOperationKinds.MARK_NOTIFICATION_READ,
            aggregateId = command.notificationId.value,
            payload = ProductionOperationPayloadCodec.encode(command),
            createdAtEpochMillis = command.readAt.toEpochMilliseconds(),
        ),
        value = Unit,
    ) {
        usePrepared(
            """
            UPDATE notifications
            SET readAtEpochMillis = COALESCE(readAtEpochMillis, ?)
            WHERE accountId = ? AND notificationId = ?
            """.trimIndent(),
        ) { statement ->
            statement.bindLong(1, command.readAt.toEpochMilliseconds())
            statement.bindText(2, command.accountId.value)
            statement.bindText(3, command.notificationId.value)
            statement.step()
        }
    }

    suspend fun enqueueMarkAllNotificationsRead(
        command: MarkAllNotificationsReadCommand,
    ): OptimisticMutationResult<Unit> = enqueue(
        operation = OperationSpec(
            operationId = command.clientOperationId.value,
            accountId = command.accountId.value,
            idempotencyKey = command.clientOperationId.value,
            kind = ProductionOperationKinds.MARK_ALL_NOTIFICATIONS_READ,
            aggregateId = command.accountId.value,
            payload = ProductionOperationPayloadCodec.encode(command),
            createdAtEpochMillis = command.readAt.toEpochMilliseconds(),
        ),
        value = Unit,
    ) {
        usePrepared(
            """
            UPDATE notifications
            SET readAtEpochMillis = COALESCE(readAtEpochMillis, ?)
            WHERE accountId = ? AND deletedAtEpochMillis IS NULL
            """.trimIndent(),
        ) { statement ->
            statement.bindLong(1, command.readAt.toEpochMilliseconds())
            statement.bindText(2, command.accountId.value)
            statement.step()
        }
    }

    suspend fun enqueueDeleteNotification(
        command: DeleteNotificationCommand,
    ): OptimisticMutationResult<Unit> = enqueue(
        operation = OperationSpec(
            operationId = command.clientOperationId.value,
            accountId = command.accountId.value,
            idempotencyKey = command.clientOperationId.value,
            kind = ProductionOperationKinds.DELETE_NOTIFICATION,
            aggregateId = command.notificationId.value,
            payload = ProductionOperationPayloadCodec.encode(command),
            createdAtEpochMillis = command.deletedAt.toEpochMilliseconds(),
        ),
        value = Unit,
    ) {
        usePrepared(
            """
            UPDATE notifications
            SET deletedAtEpochMillis = COALESCE(deletedAtEpochMillis, ?)
            WHERE accountId = ? AND notificationId = ?
            """.trimIndent(),
        ) { statement ->
            statement.bindLong(1, command.deletedAt.toEpochMilliseconds())
            statement.bindText(2, command.accountId.value)
            statement.bindText(3, command.notificationId.value)
            statement.step()
        }
    }

    private suspend fun <T> enqueue(
        operation: OperationSpec,
        value: T,
        applyOptimisticMutation: suspend PooledConnection.() -> Unit,
    ): OptimisticMutationResult<T> = database.useWriterConnection { transactor ->
        transactor.immediateTransaction {
            val existing = findOperation(
                accountId = operation.accountId,
                idempotencyKey = operation.idempotencyKey,
            )
            if (existing != null) {
                return@immediateTransaction if (existing.matches(operation)) {
                    OptimisticMutationResult.AlreadyEnqueued(
                        operationId = existing.operationId,
                        value = value,
                    )
                } else {
                    OptimisticMutationResult.Conflict(
                        reason = "Idempotency key is already attached to another local operation",
                    )
                }
            }

            applyOptimisticMutation()
            insertOperation(operation)
            OptimisticMutationResult.Enqueued(
                operationId = operation.operationId,
                value = value,
            )
        }
    }
}

private data class OperationSpec(
    val operationId: String,
    val accountId: String,
    val idempotencyKey: String,
    val kind: String,
    val aggregateId: String,
    val payload: String,
    val createdAtEpochMillis: Long,
    val payloadVersion: Int = ProductionOperationPayloadCodec.VERSION,
)

private data class ExistingOperation(
    val operationId: String,
    val kind: String,
    val aggregateId: String,
    val payload: String,
    val payloadVersion: Int,
) {
    fun matches(specification: OperationSpec): Boolean =
        operationId == specification.operationId &&
            kind == specification.kind &&
            aggregateId == specification.aggregateId &&
            payload == specification.payload &&
            payloadVersion == specification.payloadVersion
}

private fun SQLiteStatement.bindTextOrNull(index: Int, value: String?) {
    if (value == null) bindNull(index) else bindText(index, value)
}

private fun SQLiteStatement.bindLongOrNull(index: Int, value: Long?) {
    if (value == null) bindNull(index) else bindLong(index, value)
}

private suspend fun PooledConnection.ownsShopItem(
    accountId: String,
    itemId: String,
): Boolean = usePrepared(
    "SELECT 1 FROM owned_shop_items WHERE accountId = ? AND itemId = ? LIMIT 1",
) { statement ->
    statement.bindText(1, accountId)
    statement.bindText(2, itemId)
    statement.step()
}

private suspend fun PooledConnection.availableTokenBalance(accountId: String): Int = usePrepared(
    """
    SELECT balance - (
        SELECT COALESCE(SUM(pricePaid), 0) FROM owned_shop_items
        WHERE accountId = ? AND pendingOperationId IS NOT NULL
    ) FROM token_balances WHERE accountId = ? LIMIT 1
    """.trimIndent(),
) { statement ->
    statement.bindText(1, accountId)
    statement.bindText(2, accountId)
    if (statement.step()) statement.getLong(0).toInt() else 0
}

private suspend fun PooledConnection.findOperation(
    accountId: String,
    idempotencyKey: String,
): ExistingOperation? = usePrepared(
    """
    SELECT operationId, kind, aggregateId, payload, payloadVersion
    FROM pending_operations
    WHERE accountId = ? AND idempotencyKey = ?
    LIMIT 1
    """.trimIndent(),
) { statement ->
    statement.bindText(1, accountId)
    statement.bindText(2, idempotencyKey)
    if (!statement.step()) {
        null
    } else {
        ExistingOperation(
            operationId = statement.getText(0),
            kind = statement.getText(1),
            aggregateId = statement.getText(2),
            payload = statement.getText(3),
            payloadVersion = statement.getLong(4).toInt(),
        )
    }
}

private suspend fun PooledConnection.insertOperation(operation: OperationSpec) {
    usePrepared(
        """
        INSERT OR ABORT INTO pending_operations
            (operationId, accountId, idempotencyKey, kind, aggregateId, payload,
             payloadVersion, state, attemptCount, createdAtEpochMillis,
             nextAttemptAtEpochMillis, leaseUntilEpochMillis, leaseToken,
             completedAtEpochMillis, lastErrorCode, lastErrorMessage)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, NULL)
        """.trimIndent(),
    ) { statement ->
        statement.bindText(1, operation.operationId)
        statement.bindText(2, operation.accountId)
        statement.bindText(3, operation.idempotencyKey)
        statement.bindText(4, operation.kind)
        statement.bindText(5, operation.aggregateId)
        statement.bindText(6, operation.payload)
        statement.bindLong(7, operation.payloadVersion.toLong())
        statement.bindText(8, LocalOutboxStates.PENDING)
        statement.bindLong(9, 0L)
        statement.bindLong(10, operation.createdAtEpochMillis)
        statement.bindLong(11, operation.createdAtEpochMillis)
        statement.step()
    }
}

private suspend fun PooledConnection.upsertProfile(profile: UserProfile) {
    val entity = profile.toEntity()
    usePrepared(
        """
        INSERT OR REPLACE INTO profiles
            (userId, displayName, avatarKind, avatarValue, username, bio, age,
             countryCode, locationLabel, lastSeenAtEpochMillis, presence, updatedAtEpochMillis)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
    ) { statement ->
        statement.bindText(1, entity.userId)
        statement.bindText(2, entity.displayName)
        statement.bindTextOrNull(3, entity.avatarKind)
        statement.bindTextOrNull(4, entity.avatarValue)
        statement.bindText(5, entity.username)
        statement.bindText(6, entity.bio)
        statement.bindLongOrNull(7, entity.age?.toLong())
        statement.bindTextOrNull(8, entity.countryCode)
        statement.bindTextOrNull(9, entity.locationLabel)
        statement.bindLongOrNull(10, entity.lastSeenAtEpochMillis)
        statement.bindText(11, entity.presence)
        statement.bindLong(12, entity.updatedAtEpochMillis)
        statement.step()
    }
}

private suspend fun PooledConnection.upsertFriend(friend: Friend) {
    val entity = friend.toEntity()
    usePrepared(
        """
        INSERT OR REPLACE INTO friends
            (ownerId, friendUserId, displayName, avatarKind, avatarValue, bio, age,
             countryCode, locationLabel, lastSeenAtEpochMillis, presence,
             profileUpdatedAtEpochMillis, friendshipStatus, lastInteractionAtEpochMillis, isOnline)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
    ) { statement ->
        statement.bindText(1, entity.ownerId)
        statement.bindText(2, entity.friendUserId)
        statement.bindText(3, entity.displayName)
        statement.bindTextOrNull(4, entity.avatarKind)
        statement.bindTextOrNull(5, entity.avatarValue)
        statement.bindText(6, entity.bio)
        statement.bindLongOrNull(7, entity.age?.toLong())
        statement.bindTextOrNull(8, entity.countryCode)
        statement.bindTextOrNull(9, entity.locationLabel)
        statement.bindLongOrNull(10, entity.lastSeenAtEpochMillis)
        statement.bindText(11, entity.presence)
        statement.bindLong(12, entity.profileUpdatedAtEpochMillis)
        statement.bindText(13, entity.friendshipStatus)
        statement.bindLongOrNull(14, entity.lastInteractionAtEpochMillis)
        statement.bindLong(15, if (entity.isOnline) 1L else 0L)
        statement.step()
    }
}

private suspend fun PooledConnection.deleteFriend(
    ownerId: String,
    friendUserId: String,
) {
    usePrepared(
        "DELETE FROM friends WHERE ownerId = ? AND friendUserId = ?",
    ) { statement ->
        statement.bindText(1, ownerId)
        statement.bindText(2, friendUserId)
        statement.step()
    }
}
