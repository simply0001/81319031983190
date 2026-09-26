package com.pocketpass.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles", primaryKeys = ["userId"])
data class ProfileEntity(
    val userId: String,
    val displayName: String,
    val avatarKind: String?,
    val avatarValue: String?,
    val username: String,
    val bio: String,
    val age: Int?,
    val countryCode: String?,
    val locationLabel: String?,
    val lastSeenAtEpochMillis: Long?,
    val presence: String,
    val updatedAtEpochMillis: Long,
    @androidx.room.ColumnInfo(defaultValue = "'default'")
    val chatBubbleColour: String = "default",
    val chatColourOperationId: String? = null,
    val chatColourError: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val blockMessages: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val blockInvites: Boolean = false,
)

@Entity(
    tableName = "friends",
    primaryKeys = ["ownerId", "friendUserId"],
    indices = [
        Index(value = ["ownerId"]),
        Index(value = ["friendUserId"]),
    ],
)
data class FriendEntity(
    val ownerId: String,
    val friendUserId: String,
    val displayName: String,
    val avatarKind: String?,
    val avatarValue: String?,
    val bio: String,
    val age: Int?,
    val countryCode: String?,
    val locationLabel: String?,
    val lastSeenAtEpochMillis: Long?,
    val presence: String,
    val profileUpdatedAtEpochMillis: Long,
    val friendshipStatus: String,
    val lastInteractionAtEpochMillis: Long?,
    val isOnline: Boolean,
)

@Entity(tableName = "friend_codes")
data class FriendCodeEntity(
    @PrimaryKey val accountId: String,
    val code: String,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "notifications",
    primaryKeys = ["accountId", "notificationId"],
    indices = [
        Index(value = ["accountId", "updatedAtEpochMillis"]),
        Index(value = ["accountId", "readAtEpochMillis"]),
        Index(value = ["friendRequestId"]),
        Index(value = ["conversationId"]),
    ],
)
data class NotificationEntity(
    val accountId: String,
    val notificationId: String,
    val kind: String,
    val actorUserId: String?,
    val actorDisplayName: String?,
    val actorAvatarKind: String?,
    val actorAvatarValue: String?,
    val actorUpdatedAtEpochMillis: Long?,
    val friendRequestId: String?,
    val friendRequestStatus: String?,
    val conversationId: String?,
    val title: String,
    val body: String,
    val eventCount: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val readAtEpochMillis: Long?,
    val deletedAtEpochMillis: Long?,
)

@Entity(
    tableName = "conversations",
    primaryKeys = ["accountId", "conversationId"],
    indices = [
        Index(value = ["accountId", "latestMessageAtEpochMillis"]),
    ],
)
data class ConversationEntity(
    val accountId: String,
    val conversationId: String,
    val title: String,
    val avatarKind: String?,
    val avatarValue: String?,
    val latestMessagePreview: String,
    val latestMessageAtEpochMillis: Long?,
    val unreadCount: Int,
    val kind: String = LocalConversationKinds.DIRECT,
)

@Entity(
    tableName = "conversation_members",
    primaryKeys = ["accountId", "conversationId", "userId"],
    indices = [
        Index(value = ["accountId", "conversationId"]),
    ],
)
data class ConversationMemberEntity(
    val accountId: String,
    val conversationId: String,
    val userId: String,
    val displayName: String,
    val avatarKind: String?,
    val avatarValue: String?,
    val role: String,
    val joinedAtEpochMillis: Long,
)

object LocalConversationKinds {
    const val DIRECT = "Direct"
    const val GROUP = "Group"
}

@Entity(
    tableName = "messages",
    primaryKeys = ["accountId", "messageId"],
    indices = [
        Index(value = ["accountId", "conversationId", "createdAtEpochMillis"]),
        Index(
            value = ["accountId", "clientOperationId"],
            unique = true,
        ),
    ],
)
data class MessageEntity(
    val accountId: String,
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val clientOperationId: String?,
    val body: String,
    val createdAtEpochMillis: Long,
    val editedAtEpochMillis: Long?,
    val deletedAtEpochMillis: Long?,
    val deliveryState: String,
    val pendingOperationId: String?,
    val deliveryAttempt: Int,
    val lastDeliveryError: String?,
    val attachmentPath: String? = null,
    val attachmentMime: String? = null,
    val attachmentLocalPath: String? = null,
)

@Entity(tableName = "shop_categories", primaryKeys = ["categoryId"])
data class ShopCategoryEntity(
    val categoryId: String,
    val slug: String,
    val title: String,
    val subtitle: String,
    val iconKey: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "shop_items",
    primaryKeys = ["itemId"],
    indices = [Index(value = ["categoryId", "sortOrder"])],
)
data class ShopItemEntity(
    val itemId: String,
    val categoryId: String,
    val slug: String,
    val name: String,
    val priceTokens: Int,
    val imageKey: String,
    val sortOrder: Int,
    val miiHatType: Int? = null,
)

@Entity(tableName = "token_balances", primaryKeys = ["accountId"])
data class TokenBalanceEntity(
    val accountId: String,
    val balance: Int,
)

@Entity(
    tableName = "owned_shop_items",
    primaryKeys = ["accountId", "itemId"],
    indices = [Index(value = ["accountId", "pendingOperationId"])],
)
data class OwnedShopItemEntity(
    val accountId: String,
    val itemId: String,
    val pricePaid: Int,
    val purchasedAtEpochMillis: Long,
    val pendingOperationId: String?,
)

@Entity(tableName = "supporter_status", primaryKeys = ["accountId"])
data class SupporterStatusEntity(
    val accountId: String,
    val activeUntilEpochMillis: Long,
)

@Entity(
    tableName = "leaderboard_entries",
    primaryKeys = ["accountId", "scope", "userId"],
    indices = [Index(value = ["accountId", "scope", "position"])],
)
data class LeaderboardEntryEntity(
    val accountId: String,
    val scope: String,
    val userId: String,
    val displayName: String,
    val avatarKind: String?,
    val avatarValue: String?,
    val trophyCount: Int,
    val encounterCount: Int,
    val position: Int,
)

@Entity(
    tableName = "achievement_states",
    primaryKeys = ["accountId", "achievementKey"],
    indices = [Index(value = ["accountId", "position"])],
)
data class AchievementStateEntity(
    val accountId: String,
    val achievementKey: String,
    val unlocked: Boolean,
    val unlockedAtEpochMillis: Long?,
    val progressPercent: Int,
    val position: Int,
)

@Entity(
    tableName = "bingo_cells",
    primaryKeys = ["accountId", "position"],
)
data class BingoCellEntity(
    val accountId: String,
    val position: Int,
    val slug: String,
    val goalText: String,
    val shortLabel: String,
    val completed: Boolean,
    val progressCurrent: Int,
    val progressTarget: Int,
)

@Entity(
    tableName = "puzzle_collections",
    primaryKeys = ["accountId"],
)
data class PuzzleCollectionEntity(
    val accountId: String,
    val currentPuzzleId: String?,
    val piecePriceTokens: Int,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "puzzles",
    primaryKeys = ["accountId", "puzzleId"],
    indices = [Index(value = ["accountId", "position"])],
)
data class PuzzleEntity(
    val accountId: String,
    val puzzleId: String,
    val position: Int,
    val kind: String,
    val title: String,
    val slug: String?,
    val imagePath: String?,
    val artworkKind: String,
    val artworkValue: String?,
    val columnCount: Int,
    val rowCount: Int,
    val startedAtEpochMillis: Long?,
    val completedAtEpochMillis: Long?,
    val completedByHandover: Boolean,
)

@Entity(
    tableName = "puzzle_pieces",
    primaryKeys = ["accountId", "puzzleId", "pieceIndex"],
    indices = [Index(value = ["accountId", "puzzleId"])],
)
data class PuzzlePieceEntity(
    val accountId: String,
    val puzzleId: String,
    val pieceIndex: Int,
)

@Entity(
    tableName = "world_tour_regions",
    primaryKeys = ["accountId", "countryCode"],
    indices = [Index(value = ["accountId", "position"])],
)
data class WorldTourRegionEntity(
    val accountId: String,
    val countryCode: String,
    val firstMetAtEpochMillis: Long,
    val position: Int,
)

@Entity(tableName = "passing_stats")
data class PassingStatsEntity(
    @PrimaryKey val accountId: String,
    val currentStreak: Int,
    val bestStreak: Int,
    val weekPasses: Int,
    val weekPeople: Int,
    val weekRegions: Int,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "nearby_encounters",
    primaryKeys = ["accountId", "encounterId"],
    indices = [
        Index(value = ["accountId", "occurredAtEpochMillis"]),
        Index(value = ["accountId", "remoteUserId"]),
    ],
)
data class NearbyEncounterEntity(
    val accountId: String,
    val encounterId: String,
    val remoteUserId: String,
    val displayName: String,
    val avatarKind: String?,
    val avatarValue: String?,
    val bio: String,
    val age: Int?,
    val countryCode: String?,
    val locationLabel: String?,
    val lastSeenAtEpochMillis: Long?,
    val profileUpdatedAtEpochMillis: Long,
    val occurredAtEpochMillis: Long,
    val resolvedAtEpochMillis: Long,
)

@Entity(
    tableName = "nearby_credentials",
    primaryKeys = ["accountId", "tokenHash"],
    indices = [
        Index(value = ["accountId", "expiresAtEpochMillis"]),
    ],
)
data class NearbyCredentialEntity(
    val accountId: String,
    val tokenHash: String,
    val secureEntryKey: String,
    val expiresAtEpochMillis: Long,
    val claimedAtEpochMillis: Long?,
)

@Entity(
    tableName = "pending_operations",
    indices = [
        Index(
            value = ["accountId", "idempotencyKey"],
            unique = true,
        ),
        Index(value = ["accountId", "state", "nextAttemptAtEpochMillis"]),
        Index(value = ["aggregateId"]),
    ],
)
data class PendingOperationEntity(
    @PrimaryKey val operationId: String,
    val accountId: String,
    val idempotencyKey: String,
    val kind: String,
    val aggregateId: String,
    val payload: String,
    val payloadVersion: Int,
    val state: String,
    val attemptCount: Int,
    val createdAtEpochMillis: Long,
    val nextAttemptAtEpochMillis: Long,
    val leaseUntilEpochMillis: Long?,
    val leaseToken: String?,
    val completedAtEpochMillis: Long?,
    val lastErrorCode: String?,
    val lastErrorMessage: String?,
) {
    init {
        require(payloadVersion > 0) { "Outbox payload version must be positive" }
        require(attemptCount >= 0) { "Outbox attempt count cannot be negative" }
    }
}

@Entity(tableName = "sync_cursors", primaryKeys = ["accountId", "stream"])
data class SyncCursorEntity(
    val accountId: String,
    val stream: String,
    val cursor: String,
    val updatedAtEpochMillis: Long,
)

object LocalAvatarKinds {
    const val BUNDLED = "BUNDLED"
    const val REMOTE = "REMOTE"
}

object LocalPuzzleKinds {
    const val OWN_PIIP = "OwnPiip"
    const val PANEL = "Panel"
}

object LocalPuzzleArtworkKinds {
    const val OWN_PORTRAIT = "OWN_PORTRAIT"
    const val FILE = "FILE"
    const val BUNDLED = "BUNDLED"
    const val REMOTE = "REMOTE"
}

object LocalDeliveryStates {
    const val SYNCED = "SYNCED"
    const val QUEUED = "QUEUED"
    const val SENDING = "SENDING"
    const val FAILED_RETRYABLE = "FAILED_RETRYABLE"
    const val FAILED_PERMANENT = "FAILED_PERMANENT"
}

object LocalOutboxStates {
    const val PENDING = "PENDING"
    const val IN_FLIGHT = "IN_FLIGHT"
    const val RETRYABLE = "RETRYABLE"
    const val SUCCEEDED = "SUCCEEDED"
    const val FAILED_PERMANENT = "FAILED_PERMANENT"
}

object LocalOperationKinds {
    const val SEND_MESSAGE = "SEND_MESSAGE"
    const val MARK_CONVERSATION_READ = "MARK_CONVERSATION_READ"
    const val EDIT_MESSAGE = "EDIT_MESSAGE"
    const val DELETE_MESSAGE = "DELETE_MESSAGE"
}
