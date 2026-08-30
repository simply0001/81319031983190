package com.pocketpass.app.data.repository

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
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
        ) { writableDatabase ->
            writableDatabase.upsertProfile(command.profile)
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
        ) { writableDatabase ->
            writableDatabase.upsertFriend(optimisticFriend)
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
        ) { writableDatabase ->
            if (optimisticFriend == null) {
                writableDatabase.deleteFriend(
                    ownerId = command.accountId.value,
                    friendUserId = command.requester.userId.value,
                )
            } else {
                writableDatabase.upsertFriend(optimisticFriend)
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
    ) { writableDatabase ->
        writableDatabase.deleteFriend(
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
    ) { writableDatabase ->
        if (command.blocked) {
            writableDatabase.deleteFriend(
                ownerId = command.accountId.value,
                friendUserId = command.targetUserId.value,
            )
        }
    }

    suspend fun enqueuePurchase(
        command: PurchaseShopItemCommand,
    ): OptimisticMutationResult<Unit> = database.withTransaction {
        val writableDatabase = database.openHelper.writableDatabase
        val existing = writableDatabase.findOperation(
            accountId = command.accountId.value,
            idempotencyKey = command.clientOperationId.value,
        )
        if (existing == null) {
            if (writableDatabase.ownsShopItem(command.accountId.value, command.itemId)) {
                return@withTransaction OptimisticMutationResult.Conflict("Item is already owned")
            }
            if (writableDatabase.availableTokenBalance(command.accountId.value) < command.priceTokens) {
                return@withTransaction OptimisticMutationResult.Conflict("Not enough tokens")
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
        ) { database ->
            database.insert(
                "owned_shop_items",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("accountId", command.accountId.value)
                    put("itemId", command.itemId)
                    put("pricePaid", command.priceTokens)
                    put("purchasedAtEpochMillis", command.requestedAt.toEpochMilliseconds())
                    put("pendingOperationId", command.clientOperationId.value)
                },
            )
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
    ) { writableDatabase ->
        writableDatabase.execSQL(
            """
            UPDATE notifications
            SET readAtEpochMillis = COALESCE(readAtEpochMillis, ?)
            WHERE accountId = ? AND notificationId = ?
            """.trimIndent(),
            arrayOf<Any?>(
                command.readAt.toEpochMilliseconds(),
                command.accountId.value,
                command.notificationId.value,
            ),
        )
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
    ) { writableDatabase ->
        writableDatabase.execSQL(
            """
            UPDATE notifications
            SET readAtEpochMillis = COALESCE(readAtEpochMillis, ?)
            WHERE accountId = ? AND deletedAtEpochMillis IS NULL
            """.trimIndent(),
            arrayOf<Any?>(command.readAt.toEpochMilliseconds(), command.accountId.value),
        )
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
    ) { writableDatabase ->
        writableDatabase.execSQL(
            """
            UPDATE notifications
            SET deletedAtEpochMillis = COALESCE(deletedAtEpochMillis, ?)
            WHERE accountId = ? AND notificationId = ?
            """.trimIndent(),
            arrayOf<Any?>(
                command.deletedAt.toEpochMilliseconds(),
                command.accountId.value,
                command.notificationId.value,
            ),
        )
    }

    private suspend fun <T> enqueue(
        operation: OperationSpec,
        value: T,
        applyOptimisticMutation: (SupportSQLiteDatabase) -> Unit,
    ): OptimisticMutationResult<T> = database.withTransaction {
        val writableDatabase = database.openHelper.writableDatabase
        val existing = writableDatabase.findOperation(
            accountId = operation.accountId,
            idempotencyKey = operation.idempotencyKey,
        )
        if (existing != null) {
            return@withTransaction if (existing.matches(operation)) {
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

        applyOptimisticMutation(writableDatabase)
        writableDatabase.insertOperation(operation)
        OptimisticMutationResult.Enqueued(
            operationId = operation.operationId,
            value = value,
        )
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

private fun SupportSQLiteDatabase.ownsShopItem(
    accountId: String,
    itemId: String,
): Boolean = query(
    "SELECT 1 FROM owned_shop_items WHERE accountId = ? AND itemId = ? LIMIT 1",
    arrayOf(accountId, itemId),
).use { cursor -> cursor.moveToFirst() }

private fun SupportSQLiteDatabase.availableTokenBalance(accountId: String): Int = query(
    """
    SELECT balance - (
        SELECT COALESCE(SUM(pricePaid), 0) FROM owned_shop_items
        WHERE accountId = ? AND pendingOperationId IS NOT NULL
    ) FROM token_balances WHERE accountId = ? LIMIT 1
    """.trimIndent(),
    arrayOf(accountId, accountId),
).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

private fun SupportSQLiteDatabase.findOperation(
    accountId: String,
    idempotencyKey: String,
): ExistingOperation? = query(
    """
    SELECT operationId, kind, aggregateId, payload, payloadVersion
    FROM pending_operations
    WHERE accountId = ? AND idempotencyKey = ?
    LIMIT 1
    """.trimIndent(),
    arrayOf<Any?>(accountId, idempotencyKey),
).use { cursor ->
    if (!cursor.moveToFirst()) {
        null
    } else {
        ExistingOperation(
            operationId = cursor.getString(cursor.getColumnIndexOrThrow("operationId")),
            kind = cursor.getString(cursor.getColumnIndexOrThrow("kind")),
            aggregateId = cursor.getString(cursor.getColumnIndexOrThrow("aggregateId")),
            payload = cursor.getString(cursor.getColumnIndexOrThrow("payload")),
            payloadVersion = cursor.getInt(cursor.getColumnIndexOrThrow("payloadVersion")),
        )
    }
}

private fun SupportSQLiteDatabase.insertOperation(operation: OperationSpec) {
    val values = ContentValues().apply {
        put("operationId", operation.operationId)
        put("accountId", operation.accountId)
        put("idempotencyKey", operation.idempotencyKey)
        put("kind", operation.kind)
        put("aggregateId", operation.aggregateId)
        put("payload", operation.payload)
        put("payloadVersion", operation.payloadVersion)
        put("state", LocalOutboxStates.PENDING)
        put("attemptCount", 0)
        put("createdAtEpochMillis", operation.createdAtEpochMillis)
        put("nextAttemptAtEpochMillis", operation.createdAtEpochMillis)
        putNull("leaseUntilEpochMillis")
        putNull("leaseToken")
        putNull("completedAtEpochMillis")
        putNull("lastErrorCode")
        putNull("lastErrorMessage")
    }
    check(
        insert(
            "pending_operations",
            SQLiteDatabase.CONFLICT_ABORT,
            values,
        ) != -1L,
    ) {
        "Pending operation could not be inserted"
    }
}

private fun SupportSQLiteDatabase.upsertProfile(profile: UserProfile) {
    val entity = profile.toEntity()
    val values = ContentValues().apply {
        put("userId", entity.userId)
        put("displayName", entity.displayName)
        put("avatarKind", entity.avatarKind)
        put("avatarValue", entity.avatarValue)
        put("username", entity.username)
        put("bio", entity.bio)
        put("age", entity.age)
        put("countryCode", entity.countryCode)
        put("locationLabel", entity.locationLabel)
        put("lastSeenAtEpochMillis", entity.lastSeenAtEpochMillis)
        put("presence", entity.presence)
        put("updatedAtEpochMillis", entity.updatedAtEpochMillis)
    }
    check(insert("profiles", SQLiteDatabase.CONFLICT_REPLACE, values) != -1L) {
        "Optimistic profile could not be stored"
    }
}

private fun SupportSQLiteDatabase.upsertFriend(friend: Friend) {
    val entity = friend.toEntity()
    val values = ContentValues().apply {
        put("ownerId", entity.ownerId)
        put("friendUserId", entity.friendUserId)
        put("displayName", entity.displayName)
        put("avatarKind", entity.avatarKind)
        put("avatarValue", entity.avatarValue)
        put("bio", entity.bio)
        put("age", entity.age)
        put("countryCode", entity.countryCode)
        put("locationLabel", entity.locationLabel)
        put("lastSeenAtEpochMillis", entity.lastSeenAtEpochMillis)
        put("presence", entity.presence)
        put("profileUpdatedAtEpochMillis", entity.profileUpdatedAtEpochMillis)
        put("friendshipStatus", entity.friendshipStatus)
        put("lastInteractionAtEpochMillis", entity.lastInteractionAtEpochMillis)
        put("isOnline", entity.isOnline)
    }
    check(insert("friends", SQLiteDatabase.CONFLICT_REPLACE, values) != -1L) {
        "Optimistic friend state could not be stored"
    }
}

private fun SupportSQLiteDatabase.deleteFriend(
    ownerId: String,
    friendUserId: String,
) {
    delete(
        "friends",
        "ownerId = ? AND friendUserId = ?",
        arrayOf(ownerId, friendUserId),
    )
}
