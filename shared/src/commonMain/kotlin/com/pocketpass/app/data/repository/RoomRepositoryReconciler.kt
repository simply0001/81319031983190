package com.pocketpass.app.data.repository

import androidx.room.PooledConnection
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.pocketpass.app.data.local.PocketPassDatabase
import com.pocketpass.app.data.local.entity.LocalConversationKinds
import com.pocketpass.app.data.local.entity.LocalOperationKinds
import com.pocketpass.app.data.local.entity.LocalOutboxStates
import com.pocketpass.app.data.local.entity.MessageEntity
import com.pocketpass.app.data.local.entity.TokenBalanceEntity
import com.pocketpass.app.data.local.toDomain
import com.pocketpass.app.data.local.toEntity
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.groupMessagePreview
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import kotlin.time.Instant

interface MutationAcknowledgementReconciler {
    suspend fun reconcileAcknowledgedProfile(profile: UserProfile)

    suspend fun reconcileAcknowledgedMessage(
        accountId: UserId,
        message: Message,
    )

    suspend fun reconcileAcknowledgedPurchase(
        accountId: UserId,
        itemId: String,
        balance: Int?,
        purchasedAt: Instant?,
    )

    suspend fun reconcileRejectedPurchase(
        accountId: UserId,
        itemId: String,
        operationId: String,
    )
}

class RoomRepositoryReconciler(
    private val database: PocketPassDatabase,
) : MutationAcknowledgementReconciler {
    suspend fun reconcileProfile(
        userId: UserId,
        remoteProfile: UserProfile?,
    ) {
        require(remoteProfile == null || remoteProfile.userId == userId) {
            "Remote profile id does not match the requested profile"
        }
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val hasPendingUpdate = activeAggregateIds(
                    accountId = userId.value,
                    kinds = setOf(ProductionOperationKinds.UPDATE_PROFILE),
                ).contains(userId.value)
                if (hasPendingUpdate) return@immediateTransaction

                if (remoteProfile == null) {
                    database.profileDao().delete(userId.value)
                } else {
                    database.profileDao().upsert(remoteProfile.toEntity())
                }
            }
        }
    }

    override suspend fun reconcileAcknowledgedProfile(profile: UserProfile) {
        database.profileDao().upsert(profile.toEntity())
    }

    override suspend fun reconcileAcknowledgedPurchase(
        accountId: UserId,
        itemId: String,
        balance: Int?,
        purchasedAt: Instant?,
    ) {
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                database.shopDao().markOwnedItemSynced(
                    accountId = accountId.value,
                    itemId = itemId,
                    purchasedAtEpochMillis = purchasedAt?.toEpochMilliseconds(),
                )
                if (balance != null) {
                    database.shopDao().upsertBalance(
                        TokenBalanceEntity(accountId = accountId.value, balance = balance),
                    )
                }
            }
        }
    }

    override suspend fun reconcileRejectedPurchase(
        accountId: UserId,
        itemId: String,
        operationId: String,
    ) {
        database.shopDao().deletePendingOwnedItem(
            accountId = accountId.value,
            itemId = itemId,
            pendingOperationId = operationId,
        )
    }

    suspend fun reconcileFriends(
        accountId: UserId,
        remoteFriends: List<Friend>,
    ) {
        require(remoteFriends.all { it.ownerId == accountId }) {
            "Every remote friend must belong to the requested account"
        }
        val canonicalById = remoteFriends.associateBy { it.profile.userId.value }
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val protectedIds = activeAggregateIds(
                    accountId = accountId.value,
                    kinds = FRIEND_MUTATION_KINDS,
                )

                val safeCanonicalRows = canonicalById
                    .filterKeys { it !in protectedIds }
                    .values
                    .map(Friend::toEntity)
                if (safeCanonicalRows.isNotEmpty()) {
                    database.friendDao().upsertAll(safeCanonicalRows)
                }

                val retainedIds = canonicalById.keys + protectedIds
                deleteFriendsMissingFromSnapshot(
                    ownerId = accountId.value,
                    retainedIds = retainedIds,
                )
            }
        }
    }

    suspend fun upsertConversations(
        accountId: UserId,
        conversations: List<ConversationSummary>,
    ) {
        require(conversations.map { it.id }.distinct().size == conversations.size) {
            "Remote conversation ids must be unique"
        }
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val retainedIds = conversations.map { it.id.value }
                if (conversations.isNotEmpty()) {
                    database.conversationDao().upsertAll(
                        conversations.map { it.toEntity(accountId) },
                    )
                }
                database.conversationMemberDao().deleteForAccount(accountId.value)
                val members = conversations.flatMap { conversation ->
                    conversation.members.map { it.toEntity(accountId, conversation.id) }
                }
                if (members.isNotEmpty()) {
                    database.conversationMemberDao().upsertAll(members)
                }
                database.conversationDao().deleteExcept(accountId.value, retainedIds)
                database.messageDao().deleteExceptConversations(accountId.value, retainedIds)
            }
        }
    }

    suspend fun upsertMessages(
        accountId: UserId,
        messages: List<Message>,
        authoritative: Boolean = false,
    ) {
        require(messages.map { it.id }.distinct().size == messages.size) {
            "Remote message ids must be unique"
        }
        if (messages.isEmpty()) return
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                messages.forEach { message ->
                    val clientOperationId = message.clientOperationId?.value
                        ?: return@forEach
                    usePrepared(
                        "DELETE FROM messages WHERE accountId = ? AND clientOperationId = ? AND messageId <> ?",
                    ) { statement ->
                        statement.bindText(1, accountId.value)
                        statement.bindText(2, clientOperationId)
                        statement.bindText(3, message.id.value)
                        statement.step()
                    }
                }
                val protectedIds = if (authoritative) {
                    emptySet()
                } else {
                    activeAggregateIds(
                        accountId = accountId.value,
                        kinds = MESSAGE_MUTATION_KINDS,
                    )
                }
                val localById = messages
                    .map { it.conversationId.value }
                    .distinct()
                    .flatMap { conversationId ->
                        database.messageDao().getAllForConversation(accountId.value, conversationId)
                    }
                    .associateBy(MessageEntity::messageId)
                database.messageDao().upsertAll(
                    messages.map { message ->
                        val remote = message.toEntity(accountId)
                        val local = localById[remote.messageId]
                        if (local == null || remote.messageId !in protectedIds) {
                            remote
                        } else {
                            remote.copy(
                                body = local.body,
                                editedAtEpochMillis = local.editedAtEpochMillis,
                                deletedAtEpochMillis = local.deletedAtEpochMillis
                                    ?: remote.deletedAtEpochMillis,
                            )
                        }
                    },
                )
            }
        }
    }

    override suspend fun reconcileAcknowledgedMessage(
        accountId: UserId,
        message: Message,
    ) {
        upsertMessages(accountId, listOf(message), authoritative = true)
        refreshConversationPreview(accountId, message.conversationId)
    }

    suspend fun refreshConversationPreview(
        accountId: UserId,
        conversationId: ConversationId,
    ) {
        val conversation = database.conversationDao()
            .get(accountId.value, conversationId.value)
            ?: return
        val latest = database.messageDao()
            .getLatestVisible(accountId.value, conversationId.value)
        val latestAt = latest?.createdAtEpochMillis
            ?: conversation.latestMessageAtEpochMillis
            ?: return
        val preview = when {
            latest == null -> ""
            conversation.kind == LocalConversationKinds.GROUP -> groupMessagePreview(
                body = latest.body,
                senderId = UserId(latest.senderId),
                accountId = accountId,
                members = database.conversationMemberDao()
                    .getForConversation(accountId.value, conversationId.value)
                    .map { it.toDomain() },
            )
            else -> latest.body
        }
        database.conversationDao().updateLatestMessage(
            accountId = accountId.value,
            conversationId = conversationId.value,
            preview = preview,
            createdAtEpochMillis = latestAt,
        )
    }

    private companion object {
        val FRIEND_MUTATION_KINDS = setOf(
            ProductionOperationKinds.SEND_FRIEND_REQUEST,
            ProductionOperationKinds.RESPOND_TO_FRIEND_REQUEST,
            ProductionOperationKinds.REMOVE_FRIEND,
            ProductionOperationKinds.SET_USER_BLOCK,
        )
        val MESSAGE_MUTATION_KINDS = setOf(
            LocalOperationKinds.EDIT_MESSAGE,
            LocalOperationKinds.DELETE_MESSAGE,
        )
    }
}

internal suspend fun PooledConnection.activeAggregateIds(
    accountId: String,
    kinds: Set<String>,
): Set<String> {
    if (kinds.isEmpty()) return emptySet()
    val kindPlaceholders = List(kinds.size) { "?" }.joinToString()
    return usePrepared(
        """
        SELECT aggregateId
        FROM pending_operations
        WHERE accountId = ?
          AND kind IN ($kindPlaceholders)
          AND state IN (?, ?, ?)
        """.trimIndent(),
    ) { statement ->
        var index = 1
        statement.bindText(index++, accountId)
        kinds.forEach { kind -> statement.bindText(index++, kind) }
        statement.bindText(index++, LocalOutboxStates.PENDING)
        statement.bindText(index++, LocalOutboxStates.IN_FLIGHT)
        statement.bindText(index++, LocalOutboxStates.RETRYABLE)
        buildSet {
            while (statement.step()) {
                add(statement.getText(0))
            }
        }
    }
}

private suspend fun PooledConnection.deleteFriendsMissingFromSnapshot(
    ownerId: String,
    retainedIds: Set<String>,
) {
    if (retainedIds.isEmpty()) {
        usePrepared("DELETE FROM friends WHERE ownerId = ?") { statement ->
            statement.bindText(1, ownerId)
            statement.step()
        }
        return
    }
    val placeholders = List(retainedIds.size) { "?" }.joinToString()
    usePrepared(
        "DELETE FROM friends WHERE ownerId = ? AND friendUserId NOT IN ($placeholders)",
    ) { statement ->
        var index = 1
        statement.bindText(index++, ownerId)
        retainedIds.forEach { retained -> statement.bindText(index++, retained) }
        statement.step()
    }
}
