package com.pocketpass.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.pocketpass.app.data.local.entity.AchievementStateEntity
import com.pocketpass.app.data.local.entity.BingoCellEntity
import com.pocketpass.app.data.local.entity.ConversationEntity
import com.pocketpass.app.data.local.entity.ConversationMemberEntity
import com.pocketpass.app.data.local.entity.FriendEntity
import com.pocketpass.app.data.local.entity.FriendCodeEntity
import com.pocketpass.app.data.local.entity.LeaderboardEntryEntity
import com.pocketpass.app.data.local.entity.LocalOperationKinds
import com.pocketpass.app.data.local.entity.MessageEntity
import com.pocketpass.app.data.local.entity.NearbyCredentialEntity
import com.pocketpass.app.data.local.entity.NearbyEncounterEntity
import com.pocketpass.app.data.local.entity.NotificationEntity
import com.pocketpass.app.data.local.entity.PendingOperationEntity
import com.pocketpass.app.data.local.entity.OwnedShopItemEntity
import com.pocketpass.app.data.local.entity.ProfileEntity
import com.pocketpass.app.data.local.entity.ShopCategoryEntity
import com.pocketpass.app.data.local.entity.ShopItemEntity
import com.pocketpass.app.data.local.entity.SupporterStatusEntity
import com.pocketpass.app.data.local.entity.SyncCursorEntity
import com.pocketpass.app.data.local.entity.TokenBalanceEntity
import com.pocketpass.app.data.local.entity.WorldTourRegionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE userId = :userId LIMIT 1")
    fun observe(userId: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: String): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE userId = :userId")
    suspend fun delete(userId: String)
}

@Dao
interface FriendDao {
    @Query(
        """
        SELECT * FROM friends
        WHERE ownerId = :ownerId
        ORDER BY isOnline DESC, displayName COLLATE NOCASE ASC, friendUserId ASC
        """,
    )
    fun observeForOwner(ownerId: String): Flow<List<FriendEntity>>

    @Upsert
    suspend fun upsertAll(friends: List<FriendEntity>)

    @Query("DELETE FROM friends WHERE ownerId = :ownerId")
    suspend fun deleteForOwner(ownerId: String)
}

@Dao
interface FriendCodeDao {
    @Query("SELECT * FROM friend_codes WHERE accountId = :accountId LIMIT 1")
    fun observe(accountId: String): Flow<FriendCodeEntity?>

    @Upsert
    suspend fun upsert(friendCode: FriendCodeEntity)

    @Query("DELETE FROM friend_codes WHERE accountId = :accountId")
    suspend fun delete(accountId: String)
}

@Dao
abstract class NotificationDao {
    @Query(
        """
        SELECT * FROM notifications
        WHERE accountId = :accountId AND deletedAtEpochMillis IS NULL
        ORDER BY updatedAtEpochMillis DESC, notificationId ASC
        """,
    )
    abstract fun observeForAccount(accountId: String): Flow<List<NotificationEntity>>

    @Query(
        """
        SELECT * FROM notifications
        WHERE accountId = :accountId AND notificationId = :notificationId
        LIMIT 1
        """,
    )
    abstract suspend fun get(
        accountId: String,
        notificationId: String,
    ): NotificationEntity?

    @Query(
        """
        SELECT * FROM notifications
        WHERE accountId = :accountId
          AND kind = 'NearbyEncounter'
          AND readAtEpochMillis IS NULL
          AND deletedAtEpochMillis IS NULL
        ORDER BY createdAtEpochMillis ASC, notificationId ASC
        """,
    )
    abstract suspend fun getUnreadNearbyForAccount(
        accountId: String,
    ): List<NotificationEntity>

    @Query(
        """
        SELECT DISTINCT conversationId FROM notifications
        WHERE accountId = :accountId
          AND conversationId IS NOT NULL
          AND deletedAtEpochMillis IS NULL
          AND (
            (kind = 'System' AND readAtEpochMillis IS NULL)
            OR (
              kind = 'Message'
              AND conversationId NOT IN (
                SELECT conversationId FROM conversations WHERE accountId = :accountId
              )
            )
          )
        """,
    )
    abstract suspend fun conversationIdsNeedingRefresh(accountId: String): List<String>

    @Query("SELECT * FROM notifications WHERE accountId = :accountId")
    protected abstract suspend fun getAllForAccount(
        accountId: String,
    ): List<NotificationEntity>

    @Upsert
    protected abstract suspend fun upsertAll(notifications: List<NotificationEntity>)

    @Query(
        """
        DELETE FROM notifications
        WHERE accountId = :accountId
          AND notificationId NOT IN (:retainedIds)
        """,
    )
    protected abstract suspend fun deleteExcept(
        accountId: String,
        retainedIds: List<String>,
    )

    @Query("DELETE FROM notifications WHERE accountId = :accountId")
    abstract suspend fun deleteForAccount(accountId: String)

    @Query(
        """
        UPDATE notifications
        SET readAtEpochMillis = COALESCE(readAtEpochMillis, :readAtEpochMillis)
        WHERE accountId = :accountId AND notificationId = :notificationId
        """,
    )
    abstract suspend fun markRead(
        accountId: String,
        notificationId: String,
        readAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE notifications
        SET readAtEpochMillis = COALESCE(readAtEpochMillis, :readAtEpochMillis)
        WHERE accountId = :accountId AND deletedAtEpochMillis IS NULL
        """,
    )
    abstract suspend fun markAllRead(
        accountId: String,
        readAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE notifications
        SET deletedAtEpochMillis = COALESCE(deletedAtEpochMillis, :deletedAtEpochMillis)
        WHERE accountId = :accountId AND notificationId = :notificationId
        """,
    )
    abstract suspend fun markDeleted(
        accountId: String,
        notificationId: String,
        deletedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE notifications
        SET friendRequestStatus = :status,
            readAtEpochMillis = COALESCE(readAtEpochMillis, :respondedAtEpochMillis),
            updatedAtEpochMillis = MAX(updatedAtEpochMillis, :respondedAtEpochMillis)
        WHERE accountId = :accountId AND friendRequestId = :requestId
        """,
    )
    abstract suspend fun recordFriendRequestResponse(
        accountId: String,
        requestId: String,
        status: String,
        respondedAtEpochMillis: Long,
    ): Int

    @Transaction
    open suspend fun replaceFromRemote(
        accountId: String,
        remoteRows: List<NotificationEntity>,
    ) {
        val localRows = getAllForAccount(accountId)
            .associateBy(NotificationEntity::notificationId)
        val mergedRows = remoteRows.map { remote ->
            val local = localRows[remote.notificationId] ?: return@map remote
            if (remote.updatedAtEpochMillis > local.updatedAtEpochMillis) {
                remote
            } else {
                remote.copy(
                    friendRequestStatus =
                        local.friendRequestStatus ?: remote.friendRequestStatus,
                    readAtEpochMillis =
                        local.readAtEpochMillis ?: remote.readAtEpochMillis,
                    deletedAtEpochMillis =
                        local.deletedAtEpochMillis ?: remote.deletedAtEpochMillis,
                )
            }
        }
        upsertAll(mergedRows)
        if (remoteRows.isEmpty()) {
            deleteForAccount(accountId)
        } else {
            deleteExcept(
                accountId = accountId,
                retainedIds = remoteRows.map(NotificationEntity::notificationId),
            )
        }
    }
}

@Dao
interface ConversationDao {
    @Query(
        """
        SELECT * FROM conversations
        WHERE accountId = :accountId
        ORDER BY latestMessageAtEpochMillis DESC, conversationId ASC
        """,
    )
    fun observeForAccount(accountId: String): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT * FROM conversations
        WHERE accountId = :accountId AND conversationId = :conversationId
        LIMIT 1
        """,
    )
    suspend fun get(accountId: String, conversationId: String): ConversationEntity?

    @Upsert
    suspend fun upsertAll(conversations: List<ConversationEntity>)

    @Query(
        """
        UPDATE conversations
        SET latestMessagePreview = :preview,
            latestMessageAtEpochMillis = :createdAtEpochMillis
        WHERE accountId = :accountId AND conversationId = :conversationId
        """,
    )
    suspend fun updateLatestMessage(
        accountId: String,
        conversationId: String,
        preview: String,
        createdAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE conversations
        SET unreadCount = 0
        WHERE accountId = :accountId AND conversationId = :conversationId
        """,
    )
    suspend fun markRead(accountId: String, conversationId: String): Int

    @Query(
        """
        DELETE FROM conversations
        WHERE accountId = :accountId AND conversationId = :conversationId
        """,
    )
    suspend fun delete(accountId: String, conversationId: String)

    @Query(
        """
        DELETE FROM conversations
        WHERE accountId = :accountId AND conversationId NOT IN (:retainedIds)
        """,
    )
    suspend fun deleteExcept(accountId: String, retainedIds: List<String>)
}

@Dao
interface ConversationMemberDao {
    @Query(
        """
        SELECT * FROM conversation_members
        WHERE accountId = :accountId
        ORDER BY joinedAtEpochMillis ASC, userId ASC
        """,
    )
    fun observeForAccount(accountId: String): Flow<List<ConversationMemberEntity>>

    @Query(
        """
        SELECT * FROM conversation_members
        WHERE accountId = :accountId AND conversationId = :conversationId
        ORDER BY joinedAtEpochMillis ASC, userId ASC
        """,
    )
    suspend fun getForConversation(
        accountId: String,
        conversationId: String,
    ): List<ConversationMemberEntity>

    @Upsert
    suspend fun upsertAll(members: List<ConversationMemberEntity>)

    @Query(
        """
        DELETE FROM conversation_members
        WHERE accountId = :accountId AND conversationId = :conversationId
        """,
    )
    suspend fun deleteForConversation(accountId: String, conversationId: String)

    @Query("DELETE FROM conversation_members WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)
}

@Dao
interface MessageDao {
    @Query(
        """
        SELECT * FROM messages
        WHERE accountId = :accountId AND conversationId = :conversationId
          AND deletedAtEpochMillis IS NULL
        ORDER BY createdAtEpochMillis ASC, messageId ASC
        """,
    )
    fun observeForConversation(
        accountId: String,
        conversationId: String,
    ): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE accountId = :accountId AND messageId = :messageId
        LIMIT 1
        """,
    )
    suspend fun get(accountId: String, messageId: String): MessageEntity?

    @Query(
        """
        SELECT * FROM messages
        WHERE accountId = :accountId AND conversationId = :conversationId
        """,
    )
    suspend fun getAllForConversation(
        accountId: String,
        conversationId: String,
    ): List<MessageEntity>

    @Query(
        """
        SELECT * FROM messages
        WHERE accountId = :accountId AND conversationId = :conversationId
          AND deletedAtEpochMillis IS NULL
        ORDER BY createdAtEpochMillis DESC, messageId DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestVisible(
        accountId: String,
        conversationId: String,
    ): MessageEntity?

    @Query(
        """
        UPDATE messages
        SET body = :body, editedAtEpochMillis = :editedAtEpochMillis
        WHERE accountId = :accountId AND messageId = :messageId
        """,
    )
    suspend fun applyEdit(
        accountId: String,
        messageId: String,
        body: String,
        editedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE messages
        SET deletedAtEpochMillis = COALESCE(deletedAtEpochMillis, :deletedAtEpochMillis)
        WHERE accountId = :accountId AND messageId = :messageId
        """,
    )
    suspend fun markDeleted(
        accountId: String,
        messageId: String,
        deletedAtEpochMillis: Long,
    ): Int

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query(
        """
        DELETE FROM messages
        WHERE accountId = :accountId AND conversationId = :conversationId
        """,
    )
    suspend fun deleteForConversation(accountId: String, conversationId: String)

    @Query(
        """
        DELETE FROM messages
        WHERE accountId = :accountId AND conversationId NOT IN (:retainedConversationIds)
        """,
    )
    suspend fun deleteExceptConversations(
        accountId: String,
        retainedConversationIds: List<String>,
    )
}

@Dao
interface ShopDao {
    @Query("SELECT * FROM shop_categories ORDER BY sortOrder ASC, categoryId ASC")
    fun observeCategories(): Flow<List<ShopCategoryEntity>>

    @Query("SELECT * FROM shop_items ORDER BY sortOrder ASC, itemId ASC")
    fun observeItems(): Flow<List<ShopItemEntity>>

    @Query("SELECT balance FROM token_balances WHERE accountId = :accountId LIMIT 1")
    fun observeBalance(accountId: String): Flow<Int?>

    @Upsert
    suspend fun upsertBalance(balance: TokenBalanceEntity)

    @Transaction
    suspend fun replaceCatalog(
        categories: List<ShopCategoryEntity>,
        items: List<ShopItemEntity>,
    ) {
        deleteAllItems()
        deleteAllCategories()
        insertCategories(categories)
        insertItems(items)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<ShopCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ShopItemEntity>)

    @Query("DELETE FROM shop_categories")
    suspend fun deleteAllCategories()

    @Query("DELETE FROM shop_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM token_balances WHERE accountId = :accountId")
    suspend fun deleteBalance(accountId: String)

    @Query(
        """
        SELECT * FROM owned_shop_items
        WHERE accountId = :accountId
        ORDER BY purchasedAtEpochMillis ASC, itemId ASC
        """,
    )
    fun observeOwnedItems(accountId: String): Flow<List<OwnedShopItemEntity>>

    @Query(
        """
        SELECT item.miiHatType FROM owned_shop_items AS owned
        INNER JOIN shop_items AS item ON item.itemId = owned.itemId
        WHERE owned.accountId = :accountId
            AND owned.pendingOperationId IS NULL
            AND item.miiHatType IS NOT NULL
        """,
    )
    fun observeConfirmedHatTypes(accountId: String): Flow<List<Int>>

    @Query(
        """
        SELECT balance - (
            SELECT COALESCE(SUM(pricePaid), 0) FROM owned_shop_items
            WHERE accountId = :accountId AND pendingOperationId IS NOT NULL
        ) FROM token_balances WHERE accountId = :accountId LIMIT 1
        """,
    )
    fun observeAvailableBalance(accountId: String): Flow<Int?>

    @Query("SELECT activeUntilEpochMillis FROM supporter_status WHERE accountId = :accountId LIMIT 1")
    fun observeSupporterUntil(accountId: String): Flow<Long?>

    @Upsert
    suspend fun upsertSupporterStatus(status: SupporterStatusEntity)

    @Query("DELETE FROM supporter_status WHERE accountId = :accountId")
    suspend fun deleteSupporterStatus(accountId: String)

    @Upsert
    suspend fun upsertOwnedItems(items: List<OwnedShopItemEntity>)

    @Query(
        """
        UPDATE owned_shop_items
        SET pendingOperationId = NULL,
            purchasedAtEpochMillis = COALESCE(:purchasedAtEpochMillis, purchasedAtEpochMillis)
        WHERE accountId = :accountId AND itemId = :itemId
        """,
    )
    suspend fun markOwnedItemSynced(
        accountId: String,
        itemId: String,
        purchasedAtEpochMillis: Long?,
    )

    @Query(
        """
        DELETE FROM owned_shop_items
        WHERE accountId = :accountId AND itemId = :itemId AND pendingOperationId = :pendingOperationId
        """,
    )
    suspend fun deletePendingOwnedItem(
        accountId: String,
        itemId: String,
        pendingOperationId: String,
    )

    @Query(
        """
        DELETE FROM owned_shop_items
        WHERE accountId = :accountId AND pendingOperationId IS NULL AND itemId NOT IN (:itemIds)
        """,
    )
    suspend fun deleteConfirmedOwnedItemsNotIn(accountId: String, itemIds: List<String>)

    @Transaction
    suspend fun replaceOwnedItemsFromRemote(
        accountId: String,
        items: List<OwnedShopItemEntity>,
    ) {
        deleteConfirmedOwnedItemsNotIn(accountId, items.map(OwnedShopItemEntity::itemId))
        if (items.isNotEmpty()) upsertOwnedItems(items)
    }
}

@Dao
interface LeaderboardDao {
    @Query(
        """
        SELECT * FROM leaderboard_entries
        WHERE accountId = :accountId AND scope = :scope
        ORDER BY position ASC
        """,
    )
    fun observeEntries(
        accountId: String,
        scope: String,
    ): Flow<List<LeaderboardEntryEntity>>

    @Transaction
    suspend fun replaceEntries(
        accountId: String,
        scope: String,
        entries: List<LeaderboardEntryEntity>,
    ) {
        deleteEntries(accountId, scope)
        insertEntries(entries)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<LeaderboardEntryEntity>)

    @Query("DELETE FROM leaderboard_entries WHERE accountId = :accountId AND scope = :scope")
    suspend fun deleteEntries(accountId: String, scope: String)
}

@Dao
interface AchievementDao {
    @Query(
        """
        SELECT * FROM achievement_states
        WHERE accountId = :accountId
        ORDER BY position ASC
        """,
    )
    fun observeStates(accountId: String): Flow<List<AchievementStateEntity>>

    @Transaction
    suspend fun replaceStates(
        accountId: String,
        states: List<AchievementStateEntity>,
    ) {
        deleteStates(accountId)
        insertStates(states)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStates(states: List<AchievementStateEntity>)

    @Query("DELETE FROM achievement_states WHERE accountId = :accountId")
    suspend fun deleteStates(accountId: String)
}

@Dao
interface BingoDao {
    @Query(
        """
        SELECT * FROM bingo_cells
        WHERE accountId = :accountId
        ORDER BY position ASC
        """,
    )
    fun observeBoard(accountId: String): Flow<List<BingoCellEntity>>

    @Transaction
    suspend fun replaceBoard(
        accountId: String,
        cells: List<BingoCellEntity>,
    ) {
        deleteBoard(accountId)
        insertCells(cells)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCells(cells: List<BingoCellEntity>)

    @Query("DELETE FROM bingo_cells WHERE accountId = :accountId")
    suspend fun deleteBoard(accountId: String)
}

@Dao
interface WorldTourDao {
    @Query(
        """
        SELECT * FROM world_tour_regions
        WHERE accountId = :accountId
        ORDER BY position ASC
        """,
    )
    fun observeRegions(accountId: String): Flow<List<WorldTourRegionEntity>>

    @Transaction
    suspend fun replaceRegions(
        accountId: String,
        regions: List<WorldTourRegionEntity>,
    ) {
        deleteRegions(accountId)
        insertRegions(regions)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegions(regions: List<WorldTourRegionEntity>)

    @Query("DELETE FROM world_tour_regions WHERE accountId = :accountId")
    suspend fun deleteRegions(accountId: String)
}

@Dao
interface NearbyEncounterDao {
    @Query(
        """
        SELECT * FROM nearby_encounters
        WHERE accountId = :accountId
        ORDER BY occurredAtEpochMillis DESC, encounterId ASC
        LIMIT :limit
        """,
    )
    fun observeRecent(
        accountId: String,
        limit: Int = 100,
    ): Flow<List<NearbyEncounterEntity>>

    @Upsert
    suspend fun upsertEncounter(encounter: NearbyEncounterEntity)

    @Query(
        """
        SELECT * FROM nearby_credentials
        WHERE accountId = :accountId
          AND claimedAtEpochMillis IS NULL
          AND expiresAtEpochMillis > :nowEpochMillis
        ORDER BY expiresAtEpochMillis ASC
        LIMIT 1
        """,
    )
    suspend fun getAvailableCredential(
        accountId: String,
        nowEpochMillis: Long,
    ): NearbyCredentialEntity?

    @Query(
        """
        SELECT COUNT(*) FROM nearby_credentials
        WHERE accountId = :accountId
          AND claimedAtEpochMillis IS NULL
          AND expiresAtEpochMillis > :nowEpochMillis
        """,
    )
    suspend fun availableCredentialCount(
        accountId: String,
        nowEpochMillis: Long,
    ): Int

    @Upsert
    suspend fun upsertCredentials(credentials: List<NearbyCredentialEntity>)

    @Query(
        """
        UPDATE nearby_credentials
        SET claimedAtEpochMillis = :claimedAtEpochMillis
        WHERE accountId = :accountId
          AND tokenHash = :tokenHash
          AND claimedAtEpochMillis IS NULL
        """,
    )
    suspend fun claimCredential(
        accountId: String,
        tokenHash: String,
        claimedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE nearby_credentials
        SET claimedAtEpochMillis = NULL
        WHERE accountId = :accountId
          AND tokenHash = :tokenHash
        """,
    )
    suspend fun releaseCredential(
        accountId: String,
        tokenHash: String,
    ): Int

    @Query(
        """
        SELECT * FROM nearby_credentials
        WHERE accountId = :accountId
          AND (expiresAtEpochMillis <= :nowEpochMillis OR claimedAtEpochMillis IS NOT NULL)
        """,
    )
    suspend fun getExpiredOrClaimedCredentials(
        accountId: String,
        nowEpochMillis: Long,
    ): List<NearbyCredentialEntity>

    @Query(
        """
        SELECT secureEntryKey FROM nearby_credentials
        WHERE accountId = :accountId
        """,
    )
    suspend fun getCredentialSecureEntryKeys(accountId: String): List<String>

    @Query(
        """
        DELETE FROM nearby_credentials
        WHERE accountId = :accountId
          AND (expiresAtEpochMillis <= :nowEpochMillis OR claimedAtEpochMillis IS NOT NULL)
        """,
    )
    suspend fun deleteExpiredOrClaimed(
        accountId: String,
        nowEpochMillis: Long,
    )

    @Query("DELETE FROM nearby_credentials WHERE accountId = :accountId")
    suspend fun deleteCredentialsForAccount(accountId: String)

    @Query("DELETE FROM nearby_encounters WHERE accountId = :accountId")
    suspend fun deleteEncountersForAccount(accountId: String)
}

sealed interface OutboxEnqueueResult {
    data class Enqueued(
        val operationId: String,
        val messageId: String,
    ) : OutboxEnqueueResult

    data class AlreadyEnqueued(
        val operationId: String,
        val messageId: String,
    ) : OutboxEnqueueResult

    data class Conflict(val reason: String) : OutboxEnqueueResult
}

@Dao
abstract class OutboxDao {
    @Query(
        """
        SELECT * FROM pending_operations
        WHERE accountId = :accountId
        ORDER BY createdAtEpochMillis ASC, operationId ASC
        """,
    )
    abstract fun observeForAccount(accountId: String): Flow<List<PendingOperationEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM pending_operations
        WHERE accountId = :accountId
          AND state IN ('PENDING', 'IN_FLIGHT', 'RETRYABLE')
        """,
    )
    abstract suspend fun pendingCount(accountId: String): Int

    @Query(
        """
        SELECT * FROM pending_operations
        WHERE operationId = :operationId
        LIMIT 1
        """,
    )
    abstract suspend fun get(operationId: String): PendingOperationEntity?

    @Query(
        """
        SELECT payload FROM pending_operations
        WHERE accountId = :accountId AND kind = :kind
        """,
    )
    abstract suspend fun getPayloadReferences(
        accountId: String,
        kind: String,
    ): List<String>

    @Query(
        """
        SELECT * FROM pending_operations
        WHERE accountId = :accountId AND idempotencyKey = :idempotencyKey
        LIMIT 1
        """,
    )
    protected abstract suspend fun findByIdempotencyKey(
        accountId: String,
        idempotencyKey: String,
    ): PendingOperationEntity?

    @Query(
        """
        SELECT * FROM messages
        WHERE accountId = :accountId AND clientOperationId = :clientOperationId
        LIMIT 1
        """,
    )
    protected abstract suspend fun findMessageByClientOperation(
        accountId: String,
        clientOperationId: String,
    ): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertOperation(operation: PendingOperationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun enqueueOperation(operation: PendingOperationEntity): Long

    @Transaction
    open suspend fun enqueueOutgoingMessage(
        message: MessageEntity,
        operation: PendingOperationEntity,
    ): OutboxEnqueueResult {
        require(message.accountId == operation.accountId) {
            "Message and outbox operation must belong to the same account"
        }
        require(message.messageId == operation.aggregateId) {
            "Outbox aggregate must be the optimistic message"
        }
        require(message.clientOperationId == operation.idempotencyKey) {
            "Message client operation must be the outbox idempotency key"
        }

        val existingMessage = findMessageByClientOperation(
            accountId = message.accountId,
            clientOperationId = operation.idempotencyKey,
        )
        val existingOperation = findByIdempotencyKey(
            accountId = operation.accountId,
            idempotencyKey = operation.idempotencyKey,
        )

        if (existingMessage != null || existingOperation != null) {
            return if (
                existingMessage != null &&
                existingOperation != null &&
                existingMessage.messageId == message.messageId &&
                existingMessage.conversationId == message.conversationId &&
                existingMessage.senderId == message.senderId &&
                existingMessage.body == message.body &&
                existingMessage.createdAtEpochMillis == message.createdAtEpochMillis &&
                existingOperation.operationId == operation.operationId &&
                existingOperation.kind == operation.kind &&
                existingOperation.aggregateId == operation.aggregateId &&
                existingOperation.payloadVersion == operation.payloadVersion &&
                existingOperation.payload == operation.payload
            ) {
                OutboxEnqueueResult.AlreadyEnqueued(
                    operationId = existingOperation.operationId,
                    messageId = existingMessage.messageId,
                )
            } else {
                OutboxEnqueueResult.Conflict(
                    reason = "Idempotency key is already attached to another local operation",
                )
            }
        }

        insertMessage(message)
        insertOperation(operation)
        return OutboxEnqueueResult.Enqueued(
            operationId = operation.operationId,
            messageId = message.messageId,
        )
    }

    @Query(
        """
        SELECT * FROM pending_operations
        WHERE accountId = :accountId
          AND nextAttemptAtEpochMillis <= :nowEpochMillis
          AND (
              state IN ('PENDING', 'RETRYABLE')
              OR (
                  state = 'IN_FLIGHT'
                  AND COALESCE(leaseUntilEpochMillis, 0) <= :nowEpochMillis
              )
          )
        ORDER BY createdAtEpochMillis ASC, operationId ASC
        LIMIT 1
        """,
    )
    protected abstract suspend fun findNextEligible(
        accountId: String,
        nowEpochMillis: Long,
    ): PendingOperationEntity?

    @Query(
        """
        UPDATE pending_operations
        SET state = 'IN_FLIGHT',
            attemptCount = attemptCount + 1,
            leaseUntilEpochMillis = :leaseUntilEpochMillis,
            leaseToken = :leaseToken,
            lastErrorCode = NULL,
            lastErrorMessage = NULL
        WHERE operationId = :operationId
          AND nextAttemptAtEpochMillis <= :nowEpochMillis
          AND (
              state IN ('PENDING', 'RETRYABLE')
              OR (
                  state = 'IN_FLIGHT'
                  AND COALESCE(leaseUntilEpochMillis, 0) <= :nowEpochMillis
              )
          )
        """,
    )
    protected abstract suspend fun claim(
        operationId: String,
        nowEpochMillis: Long,
        leaseUntilEpochMillis: Long,
        leaseToken: String,
    ): Int

    @Query(
        """
        UPDATE messages
        SET deliveryState = 'SENDING',
            deliveryAttempt = :attempt,
            lastDeliveryError = NULL
        WHERE accountId = :accountId AND messageId = :messageId
        """,
    )
    protected abstract suspend fun markMessageSending(
        accountId: String,
        messageId: String,
        attempt: Int,
    )

    @Transaction
    open suspend fun claimNext(
        accountId: String,
        nowEpochMillis: Long,
        leaseUntilEpochMillis: Long,
        leaseToken: String,
    ): PendingOperationEntity? {
        val candidate = findNextEligible(accountId, nowEpochMillis) ?: return null
        if (
            claim(
                operationId = candidate.operationId,
                nowEpochMillis = nowEpochMillis,
                leaseUntilEpochMillis = leaseUntilEpochMillis,
                leaseToken = leaseToken,
            ) != 1
        ) {
            return null
        }

        val claimed = get(candidate.operationId) ?: return null
        if (claimed.kind == LocalOperationKinds.SEND_MESSAGE) {
            markMessageSending(
                accountId = claimed.accountId,
                messageId = claimed.aggregateId,
                attempt = claimed.attemptCount,
            )
        }
        return claimed
    }

    @Query(
        """
        UPDATE pending_operations
        SET state = 'SUCCEEDED',
            leaseUntilEpochMillis = NULL,
            leaseToken = NULL,
            completedAtEpochMillis = :completedAtEpochMillis,
            lastErrorCode = NULL,
            lastErrorMessage = NULL
        WHERE operationId = :operationId
          AND state = 'IN_FLIGHT'
          AND leaseToken = :leaseToken
        """,
    )
    protected abstract suspend fun completeClaim(
        operationId: String,
        leaseToken: String,
        completedAtEpochMillis: Long,
    ): Int

    @Query(
        """
        UPDATE messages
        SET deliveryState = 'SYNCED',
            pendingOperationId = NULL,
            lastDeliveryError = NULL
        WHERE accountId = :accountId AND messageId = :messageId
        """,
    )
    protected abstract suspend fun markMessageSynced(
        accountId: String,
        messageId: String,
    )

    @Transaction
    open suspend fun markSucceeded(
        operationId: String,
        leaseToken: String,
        completedAtEpochMillis: Long,
    ): Boolean {
        val operation = get(operationId) ?: return false
        if (
            completeClaim(
                operationId = operationId,
                leaseToken = leaseToken,
                completedAtEpochMillis = completedAtEpochMillis,
            ) != 1
        ) {
            return false
        }
        if (operation.kind == LocalOperationKinds.SEND_MESSAGE) {
            markMessageSynced(operation.accountId, operation.aggregateId)
        }
        return true
    }

    @Query(
        """
        UPDATE pending_operations
        SET state = 'RETRYABLE',
            nextAttemptAtEpochMillis = :nextAttemptAtEpochMillis,
            leaseUntilEpochMillis = NULL,
            leaseToken = NULL,
            lastErrorCode = :errorCode,
            lastErrorMessage = :errorMessage
        WHERE operationId = :operationId
          AND state = 'IN_FLIGHT'
          AND leaseToken = :leaseToken
        """,
    )
    protected abstract suspend fun releaseForRetry(
        operationId: String,
        leaseToken: String,
        nextAttemptAtEpochMillis: Long,
        errorCode: String,
        errorMessage: String?,
    ): Int

    @Query(
        """
        UPDATE messages
        SET deliveryState = 'FAILED_RETRYABLE',
            lastDeliveryError = :errorMessage
        WHERE accountId = :accountId AND messageId = :messageId
        """,
    )
    protected abstract suspend fun markMessageRetryable(
        accountId: String,
        messageId: String,
        errorMessage: String?,
    )

    @Transaction
    open suspend fun markRetryable(
        operationId: String,
        leaseToken: String,
        nextAttemptAtEpochMillis: Long,
        errorCode: String,
        errorMessage: String?,
    ): Boolean {
        val operation = get(operationId) ?: return false
        if (
            releaseForRetry(
                operationId = operationId,
                leaseToken = leaseToken,
                nextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
                errorCode = errorCode,
                errorMessage = errorMessage,
            ) != 1
        ) {
            return false
        }
        if (operation.kind == LocalOperationKinds.SEND_MESSAGE) {
            markMessageRetryable(operation.accountId, operation.aggregateId, errorMessage)
        }
        return true
    }

    @Query(
        """
        UPDATE pending_operations
        SET state = 'FAILED_PERMANENT',
            leaseUntilEpochMillis = NULL,
            leaseToken = NULL,
            completedAtEpochMillis = :completedAtEpochMillis,
            lastErrorCode = :errorCode,
            lastErrorMessage = :errorMessage
        WHERE operationId = :operationId
          AND state = 'IN_FLIGHT'
          AND leaseToken = :leaseToken
        """,
    )
    protected abstract suspend fun failClaim(
        operationId: String,
        leaseToken: String,
        completedAtEpochMillis: Long,
        errorCode: String,
        errorMessage: String?,
    ): Int

    @Query(
        """
        UPDATE messages
        SET deliveryState = 'FAILED_PERMANENT',
            lastDeliveryError = :errorMessage
        WHERE accountId = :accountId AND messageId = :messageId
        """,
    )
    protected abstract suspend fun markMessagePermanentlyFailed(
        accountId: String,
        messageId: String,
        errorMessage: String?,
    )

    @Transaction
    open suspend fun markPermanentlyFailed(
        operationId: String,
        leaseToken: String,
        completedAtEpochMillis: Long,
        errorCode: String,
        errorMessage: String?,
    ): Boolean {
        val operation = get(operationId) ?: return false
        if (
            failClaim(
                operationId = operationId,
                leaseToken = leaseToken,
                completedAtEpochMillis = completedAtEpochMillis,
                errorCode = errorCode,
                errorMessage = errorMessage,
            ) != 1
        ) {
            return false
        }
        if (operation.kind == LocalOperationKinds.SEND_MESSAGE) {
            markMessagePermanentlyFailed(
                operation.accountId,
                operation.aggregateId,
                errorMessage,
            )
        }
        return true
    }
}

@Dao
interface SyncCursorDao {
    @Query(
        """
        SELECT * FROM sync_cursors
        WHERE accountId = :accountId AND stream = :stream
        LIMIT 1
        """,
    )
    suspend fun get(accountId: String, stream: String): SyncCursorEntity?

    @Upsert
    suspend fun upsert(cursor: SyncCursorEntity)
}
