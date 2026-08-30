package com.pocketpass.app.domain.model

import com.pocketpass.app.domain.state.PendingState
import kotlin.time.Instant

sealed interface AvatarReference {
    data class Bundled(val key: String) : AvatarReference {
        init {
            require(key.isNotBlank()) { "Bundled avatar key cannot be blank" }
        }
    }

    data class Remote(val url: String) : AvatarReference {
        init {
            require(url.isNotBlank()) { "Remote avatar URL cannot be blank" }
        }
    }
}

data class UserProfile(
    val userId: UserId,
    val displayName: String,
    val avatar: AvatarReference?,
    val username: String = "",
    val bio: String = "",
    val age: Int? = null,
    val countryCode: String? = null,
    val locationLabel: String? = null,
    val lastSeenAt: Instant? = null,
    val presence: PresenceStatus = PresenceStatus.Unknown,
    val updatedAt: Instant,
) {
    val greeting: String
        get() = bio

    init {
        require(age == null || age >= 0) { "Profile age cannot be negative" }
    }
}

enum class PresenceStatus {
    Online,
    Away,
    Offline,
    Unknown,
}

enum class FriendshipStatus {
    PendingIncoming,
    PendingOutgoing,
    Accepted,
    Blocked,
}

data class Friend(
    val ownerId: UserId,
    val profile: UserProfile,
    val status: FriendshipStatus = FriendshipStatus.Accepted,
    val lastInteractionAt: Instant?,
    val isOnline: Boolean = false,
)

enum class ConversationKind {
    Direct,
    Group,
}

enum class ConversationMemberRole {
    Owner,
    Member,
}

data class ConversationMember(
    val userId: UserId,
    val displayName: String,
    val avatar: AvatarReference?,
    val role: ConversationMemberRole = ConversationMemberRole.Member,
    val joinedAt: Instant,
)

data class ConversationSummary(
    val id: ConversationId,
    val title: String,
    val avatar: AvatarReference?,
    val latestMessagePreview: String,
    val latestMessageAt: Instant?,
    val unreadCount: Int,
    val kind: ConversationKind = ConversationKind.Direct,
    val members: List<ConversationMember> = emptyList(),
) {
    val isGroup: Boolean
        get() = kind == ConversationKind.Group

    val ownerId: UserId?
        get() = members.firstOrNull { it.role == ConversationMemberRole.Owner }?.userId

    val memberCount: Int
        get() = members.size

    fun member(userId: UserId): ConversationMember? =
        members.firstOrNull { it.userId == userId }

    fun othersThan(userId: UserId?): List<ConversationMember> =
        members.filterNot { it.userId == userId }

    init {
        require(unreadCount >= 0) { "Unread count cannot be negative" }
    }
}

data class MessageAttachment(
    val remotePath: String?,
    val mimeType: String,
    val localPath: String? = null,
) {
    init {
        require(remotePath != null || localPath != null) {
            "An attachment needs either a remote path or a local one"
        }
    }
}

data class Message(
    val id: MessageId,
    val conversationId: ConversationId,
    val senderId: UserId,
    val clientOperationId: ClientOperationId?,
    val body: String,
    val createdAt: Instant,
    val editedAt: Instant? = null,
    val deletedAt: Instant? = null,
    val pendingState: PendingState = PendingState.Synced,
    val attachment: MessageAttachment? = null,
)

data class SendMessageCommand(
    val accountId: UserId,
    val conversationId: ConversationId,
    val messageId: MessageId = MessageId.new(),
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
    val body: String,
    val clientCreatedAt: Instant,
    val attachment: MessageAttachment? = null,
) {
    init {
        require(body.isNotBlank()) { "Message body cannot be blank" }
    }
}

const val IMAGE_MESSAGE_PLACEHOLDER_BODY = "📷"
const val FORMER_MEMBER_LABEL = "Former member"
const val SELF_SENDER_LABEL = "You"

fun groupMessagePreview(
    body: String,
    senderId: UserId,
    accountId: UserId,
    members: List<ConversationMember>,
): String {
    val sender = when (senderId) {
        accountId -> SELF_SENDER_LABEL
        else -> members.firstOrNull { it.userId == senderId }?.displayName ?: FORMER_MEMBER_LABEL
    }
    return "$sender: $body"
}

data class ShopItem(
    val id: String,
    val slug: String,
    val name: String,
    val priceTokens: Int,
    val imageKey: String,
    val miiHatType: Int? = null,
) {
    init {
        require(priceTokens >= 0) { "An item cannot cost negative tokens" }
        require(miiHatType == null || miiHatType in 0..9) { "A hat item must map to a renderer hat" }
    }
}

data class ShopCategory(
    val id: String,
    val slug: String,
    val title: String,
    val subtitle: String,
    val iconKey: String,
    val items: List<ShopItem>,
)

data class OwnedShopItem(
    val itemId: String,
    val purchasedAt: Instant,
    val pricePaid: Int,
    val pending: Boolean,
)

sealed interface ShopPurchaseOutcome {
    data class Completed(
        val itemId: String,
        val balance: Int,
        val purchasedAt: Instant,
    ) : ShopPurchaseOutcome

    data object AlreadyOwned : ShopPurchaseOutcome

    data class Rejected(val reason: ShopPurchaseRejection) : ShopPurchaseOutcome
}

enum class ShopPurchaseRejection(val code: String) {
    InsufficientTokens("INSUFFICIENT_TOKENS"),
    ItemUnavailable("ITEM_UNAVAILABLE"),
}

data class ActivitySnapshot(
    val coinCount: Int,
    val puzzleCount: Int,
    val nearbyCount: Int,
    val locationCount: Int,
    val updatedAt: Instant,
) {
    init {
        require(coinCount >= 0)
        require(puzzleCount >= 0)
        require(nearbyCount >= 0)
        require(locationCount >= 0)
    }
}

enum class LeaderboardScope(val key: String) {
    Friends("friends"),
    Global("global"),
}

data class LeaderboardEntry(
    val userId: UserId,
    val displayName: String,
    val avatar: AvatarReference?,
    val trophyCount: Int,
    val encounterCount: Int,
) {
    init {
        require(trophyCount >= 0) { "Trophy count cannot be negative" }
        require(encounterCount >= 0) { "Encounter count cannot be negative" }
    }
}

data class FriendProfileStats(
    val encounterCount: Int,
    val trophyCount: Int,
) {
    init {
        require(encounterCount >= 0) { "Encounter count cannot be negative" }
        require(trophyCount >= 0) { "Trophy count cannot be negative" }
    }
}

data class NearbyEncounter(
    val id: EncounterId,
    val ownerId: UserId,
    val profile: UserProfile,
    val occurredAt: Instant,
    val resolvedAt: Instant,
)
