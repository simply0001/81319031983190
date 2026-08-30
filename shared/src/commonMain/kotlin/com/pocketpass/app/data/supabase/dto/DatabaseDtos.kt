package com.pocketpass.app.data.supabase.dto

import com.pocketpass.app.domain.model.AchievementState
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.BingoCell
import com.pocketpass.app.domain.model.WorldTourRegion
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.ConversationMember
import com.pocketpass.app.domain.model.ConversationMemberRole
import com.pocketpass.app.domain.model.LeaderboardEntry
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.MessageAttachment
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.state.PendingState
import com.pocketpass.app.data.supabase.MESSAGE_ATTACHMENT_KEY
import com.pocketpass.app.data.supabase.parseSupabaseInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class ProfileDto(
    @SerialName("user_id")
    val userId: String,
    val username: String,
    @SerialName("display_name")
    val displayName: String,
    val bio: String = "",
    @SerialName("avatar_path")
    val avatarPath: String? = null,
    val age: Int? = null,
    @SerialName("country_code")
    val countryCode: String? = null,
    @SerialName("last_seen_at")
    val lastSeenAt: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class FriendRequestDto(
    val id: String,
    @SerialName("requester_id")
    val requesterId: String,
    @SerialName("addressee_id")
    val addresseeId: String,
    val status: String,
    @SerialName("client_operation_id")
    val clientOperationId: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("responded_at")
    val respondedAt: String? = null,
)

@Serializable
data class FriendCodeDto(
    val code: String,
)

@Serializable
data class ResolvedFriendCodeDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("display_name")
    val displayName: String,
    val bio: String = "",
    @SerialName("avatar_path")
    val avatarPath: String? = null,
    val age: Int? = null,
    @SerialName("country_code")
    val countryCode: String? = null,
    @SerialName("last_seen_at")
    val lastSeenAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class ShopCategoryDto(
    val id: String,
    val slug: String,
    val title: String,
    val subtitle: String,
    @SerialName("icon_key")
    val iconKey: String,
    @SerialName("sort_order")
    val sortOrder: Int,
)

@Serializable
data class ShopItemDto(
    val id: String,
    @SerialName("category_id")
    val categoryId: String,
    val slug: String,
    val name: String,
    @SerialName("price_tokens")
    val priceTokens: Int,
    @SerialName("image_key")
    val imageKey: String,
    @SerialName("sort_order")
    val sortOrder: Int,
    @SerialName("mii_hat_type")
    val miiHatType: Int? = null,
)

@Serializable
data class LeaderboardEntryDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("avatar_path")
    val avatarPath: String? = null,
    @SerialName("trophy_count")
    val trophyCount: Long,
    @SerialName("encounter_count")
    val encounterCount: Long,
)

@Serializable
data class TokenBalanceDto(
    @SerialName("user_id")
    val userId: String,
    val balance: Int,
)

@Serializable
data class OwnedShopItemDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("item_id")
    val itemId: String,
    @SerialName("client_operation_id")
    val clientOperationId: String,
    @SerialName("price_paid")
    val pricePaid: Int,
    @SerialName("purchased_at")
    val purchasedAt: String,
)

@Serializable
data class SupporterStatusDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("active_until")
    val activeUntil: String,
)

@Serializable
data class ShopPurchaseReceiptDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("item_id")
    val itemId: String,
    @SerialName("price_paid")
    val pricePaid: Int,
    val balance: Int,
    @SerialName("purchased_at")
    val purchasedAt: String,
)

@Serializable
data class AchievementDto(
    @SerialName("achievement_key")
    val achievementKey: String,
    val unlocked: Boolean,
    @SerialName("unlocked_at")
    val unlockedAt: String? = null,
    @SerialName("progress_percent")
    val progressPercent: Int,
)

@Serializable
data class WorldTourRegionDto(
    @SerialName("country_code")
    val countryCode: String,
    @SerialName("first_met_at")
    val firstMetAt: String,
)

@Serializable
data class BingoCellDto(
    @SerialName("cell_position")
    val position: Int,
    val slug: String,
    @SerialName("goal_text")
    val goalText: String,
    @SerialName("short_label")
    val shortLabel: String,
    val completed: Boolean,
    @SerialName("progress_current")
    val progressCurrent: Int,
    @SerialName("progress_target")
    val progressTarget: Int,
)

@Serializable
data class NotificationDto(
    val id: String,
    @SerialName("recipient_id")
    val recipientId: String,
    val kind: String,
    @SerialName("actor_id")
    val actorId: String? = null,
    @SerialName("friend_request_id")
    val friendRequestId: String? = null,
    @SerialName("friend_request_status")
    val friendRequestStatus: String? = null,
    @SerialName("conversation_id")
    val conversationId: String? = null,
    val title: String,
    val body: String,
    @SerialName("event_count")
    val eventCount: Int,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("read_at")
    val readAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
)

@Serializable
data class FriendshipDto(
    @SerialName("user_low")
    val userLow: String,
    @SerialName("user_high")
    val userHigh: String,
    @SerialName("created_by")
    val createdBy: String,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
data class UserBlockDto(
    @SerialName("blocker_id")
    val blockerId: String,
    @SerialName("blocked_id")
    val blockedId: String,
    @SerialName("created_at")
    val createdAt: String,
)

@Serializable
data class ConversationDto(
    val id: String,
    val kind: String,
    @SerialName("created_by")
    val createdBy: String,
    val title: String? = null,
    @SerialName("direct_user_low")
    val directUserLow: String? = null,
    @SerialName("direct_user_high")
    val directUserHigh: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
data class ConversationMemberDto(
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("user_id")
    val userId: String,
    val role: String,
    @SerialName("joined_at")
    val joinedAt: String,
    @SerialName("left_at")
    val leftAt: String? = null,
    @SerialName("last_read_at")
    val lastReadAt: String? = null,
)

@Serializable
data class MessageDto(
    val id: String,
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("sender_id")
    val senderId: String,
    @SerialName("client_operation_id")
    val clientOperationId: String? = null,
    val body: String,
    @SerialName("reply_to_id")
    val replyToId: String? = null,
    val metadata: JsonObject,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("edited_at")
    val editedAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
)

@Serializable
data class InteractionEventDto(
    val id: String,
    @SerialName("actor_id")
    val actorId: String,
    @SerialName("subject_user_id")
    val subjectUserId: String? = null,
    @SerialName("event_type")
    val eventType: String,
    @SerialName("client_operation_id")
    val clientOperationId: String,
    val payload: JsonObject,
    @SerialName("occurred_at")
    val occurredAt: String,
    @SerialName("created_at")
    val createdAt: String,
)

fun ProfileDto.toDomain(
    avatarUrlForPath: (String) -> String,
): UserProfile =
    UserProfile(
        userId = UserId(userId),
        displayName = displayName,
        avatar = avatarPath?.let { AvatarReference.Remote(avatarUrlForPath(it)) },
        username = username,
        bio = bio,
        age = age,
        countryCode = countryCode,
        lastSeenAt = lastSeenAt?.let(::parseSupabaseInstant),
        updatedAt = parseSupabaseInstant(updatedAt),
    )

fun LeaderboardEntryDto.toDomain(
    avatarUrlForPath: (String) -> String,
): LeaderboardEntry =
    LeaderboardEntry(
        userId = UserId(userId),
        displayName = displayName,
        avatar = avatarPath?.let { AvatarReference.Remote(avatarUrlForPath(it)) },
        trophyCount = trophyCount.toInt(),
        encounterCount = encounterCount.toInt(),
    )

fun AchievementDto.toDomain(): AchievementState = AchievementState(
    key = achievementKey,
    unlocked = unlocked,
    unlockedAt = unlockedAt?.let(::parseSupabaseInstant),
    progressPercent = progressPercent.coerceIn(0, 100),
)

fun WorldTourRegionDto.toDomain(): WorldTourRegion = WorldTourRegion(
    countryCode = countryCode,
    firstMetAt = parseSupabaseInstant(firstMetAt),
)

fun BingoCellDto.toDomain(): BingoCell = BingoCell(
    position = position,
    slug = slug,
    text = goalText,
    shortLabel = shortLabel,
    completed = completed,
    progressCurrent = progressCurrent.coerceAtLeast(0),
    progressTarget = progressTarget.coerceAtLeast(1),
)

fun MessageDto.toDomain(
    resolveAttachmentUrl: (String) -> String = { it },
): Message =
    Message(
        id = MessageId(id),
        conversationId = ConversationId(conversationId),
        senderId = UserId(senderId),
        clientOperationId = clientOperationId?.let(::ClientOperationId),
        body = body,
        createdAt = parseSupabaseInstant(createdAt),
        editedAt = editedAt?.let(::parseSupabaseInstant),
        deletedAt = deletedAt?.let(::parseSupabaseInstant),
        pendingState = PendingState.Synced,
        attachment = metadata.parseMessageAttachment(resolveAttachmentUrl),
    )

fun ConversationMemberDto.toDomain(profile: UserProfile?): ConversationMember =
    ConversationMember(
        userId = UserId(userId),
        displayName = profile?.displayName ?: "Member",
        avatar = profile?.avatar,
        role = if (role == "owner") ConversationMemberRole.Owner else ConversationMemberRole.Member,
        joinedAt = parseSupabaseInstant(joinedAt),
    )

private fun JsonObject.parseMessageAttachment(
    resolveAttachmentUrl: (String) -> String,
): MessageAttachment? {
    val attachment = this[MESSAGE_ATTACHMENT_KEY] as? JsonObject ?: return null
    val path = (attachment["path"] as? JsonPrimitive)?.contentOrNull ?: return null
    val mimeType = (attachment["mime_type"] as? JsonPrimitive)?.contentOrNull ?: return null
    if (path.isBlank() || mimeType.isBlank()) return null
    return MessageAttachment(
        remotePath = resolveAttachmentUrl(path),
        mimeType = mimeType,
    )
}
