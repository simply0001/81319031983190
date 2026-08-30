package com.pocketpass.app.domain.model

import kotlin.time.Instant

data class UpdateProfileCommand(
    val accountId: UserId,
    val profile: UserProfile,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
    val changedAt: Instant,
) {
    init {
        require(profile.userId == accountId) {
            "A profile update must target the authenticated account"
        }
    }
}

data class AccountSetupCommand(
    val accountId: UserId,
    val username: String,
    val displayName: String,
    val bio: String,
    val age: Int?,
    val countryCode: String,
    val changedAt: Instant,
) {
    init {
        require(username.isNotBlank()) { "Account setup requires a username" }
        require(displayName.isNotBlank()) { "Account setup requires a display name" }
        require(countryCode.length == 2) { "Account setup requires an ISO country" }
        require(age == null || age in 13..120) {
            "Account setup age must be between 13 and 120"
        }
    }
}

data class RenameProfileCommand(
    val accountId: UserId,
    val name: String,
    val changedAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "A rename requires a name" }
    }
}

data class SendFriendRequestCommand(
    val accountId: UserId,
    val addressee: UserProfile,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
    val requestedAt: Instant,
) {
    init {
        require(addressee.userId != accountId) {
            "A user cannot send a friend request to themselves"
        }
    }
}

data class RespondToFriendRequestCommand(
    val accountId: UserId,
    val requestId: String,
    val requester: UserProfile,
    val accept: Boolean,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
    val respondedAt: Instant,
) {
    init {
        require(requestId.isNotBlank()) { "Friend request id cannot be blank" }
        require(requester.userId != accountId) {
            "A user cannot respond to their own friend request"
        }
    }
}

data class RemoveFriendCommand(
    val accountId: UserId,
    val friendUserId: UserId,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
    val removedAt: Instant,
) {
    init {
        require(friendUserId != accountId) { "A user cannot remove themselves" }
    }
}

data class PurchaseShopItemCommand(
    val accountId: UserId,
    val itemId: String,
    val priceTokens: Int,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
    val requestedAt: Instant,
) {
    init {
        require(itemId.isNotBlank()) { "Shop item id cannot be blank" }
        require(priceTokens >= 0) { "An item cannot cost negative tokens" }
    }
}

data class SetUserBlockCommand(
    val accountId: UserId,
    val targetUserId: UserId,
    val blocked: Boolean,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
    val changedAt: Instant,
) {
    init {
        require(targetUserId != accountId) { "A user cannot block themselves" }
    }
}

data class MarkConversationReadCommand(
    val accountId: UserId,
    val conversationId: ConversationId,
    val clientOperationId: ClientOperationId = ClientOperationId.new(),
    val readAt: Instant,
)
