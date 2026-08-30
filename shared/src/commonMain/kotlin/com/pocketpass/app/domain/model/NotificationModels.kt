package com.pocketpass.app.domain.model

import kotlin.time.Instant

enum class NotificationKind {
    FriendRequest,
    FriendAccepted,
    Message,
    NearbyEncounter,
    System,
}

enum class FriendRequestNotificationStatus {
    Pending,
    Accepted,
    Declined,
}

sealed interface NotificationAction {
    data class RespondToFriendRequest(
        val requestId: String,
        val requester: UserProfile,
    ) : NotificationAction

    data class OpenConversation(
        val conversationId: ConversationId,
    ) : NotificationAction

    data object OpenFriends : NotificationAction
    data object OpenHome : NotificationAction
    data object None : NotificationAction
}

data class PocketPassNotification(
    val id: NotificationId,
    val recipientId: UserId,
    val kind: NotificationKind,
    val actor: UserProfile?,
    val friendRequestId: String?,
    val friendRequestStatus: FriendRequestNotificationStatus?,
    val conversationId: ConversationId?,
    val title: String,
    val body: String,
    val eventCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val readAt: Instant?,
    val deletedAt: Instant?,
) {
    init {
        require(title.isNotBlank()) { "Notification title cannot be blank" }
        require(eventCount > 0) { "Notification event count must be positive" }
    }

    val isUnread: Boolean
        get() = readAt == null

    val canDelete: Boolean
        get() = kind != NotificationKind.FriendRequest ||
            friendRequestStatus != FriendRequestNotificationStatus.Pending

    val action: NotificationAction
        get() = when {
            kind == NotificationKind.FriendRequest &&
                friendRequestStatus == FriendRequestNotificationStatus.Pending &&
                friendRequestId != null &&
                actor != null ->
                NotificationAction.RespondToFriendRequest(friendRequestId, actor)

            (kind == NotificationKind.Message || kind == NotificationKind.System) &&
                conversationId != null ->
                NotificationAction.OpenConversation(conversationId)

            kind == NotificationKind.FriendAccepted ->
                NotificationAction.OpenFriends

            kind == NotificationKind.NearbyEncounter ->
                NotificationAction.OpenHome

            else -> NotificationAction.None
        }
}
