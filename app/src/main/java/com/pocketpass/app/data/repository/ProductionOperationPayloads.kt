package com.pocketpass.app.data.repository

import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.PresenceStatus
import com.pocketpass.app.domain.model.DeleteNotificationCommand
import com.pocketpass.app.domain.model.MarkAllNotificationsReadCommand
import com.pocketpass.app.domain.model.MarkNotificationReadCommand
import com.pocketpass.app.domain.model.NotificationId
import com.pocketpass.app.domain.model.PurchaseShopItemCommand
import com.pocketpass.app.domain.model.RemoveFriendCommand
import com.pocketpass.app.domain.model.RespondToFriendRequestCommand
import com.pocketpass.app.domain.model.SendFriendRequestCommand
import com.pocketpass.app.domain.model.SetUserBlockCommand
import com.pocketpass.app.domain.model.UpdateProfileCommand
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ProductionOperationKinds {
    const val UPDATE_PROFILE = "UPDATE_PROFILE"
    const val SEND_FRIEND_REQUEST = "SEND_FRIEND_REQUEST"
    const val RESPOND_TO_FRIEND_REQUEST = "RESPOND_TO_FRIEND_REQUEST"
    const val REMOVE_FRIEND = "REMOVE_FRIEND"
    const val SET_USER_BLOCK = "SET_USER_BLOCK"
    const val MARK_NOTIFICATION_READ = "MARK_NOTIFICATION_READ"
    const val MARK_ALL_NOTIFICATIONS_READ = "MARK_ALL_NOTIFICATIONS_READ"
    const val DELETE_NOTIFICATION = "DELETE_NOTIFICATION"
    const val PURCHASE_SHOP_ITEM = "PURCHASE_SHOP_ITEM"
}

object ProductionOperationPayloadCodec {
    const val VERSION = 1

    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encode(command: UpdateProfileCommand): String = json.encodeToString(
        ProfileUpdateWire(
            accountId = command.accountId.value,
            profile = command.profile.toWire(),
            clientOperationId = command.clientOperationId.value,
            changedAtEpochMillis = command.changedAt.toEpochMilliseconds(),
        ),
    )

    fun decodeProfileUpdate(
        payload: String,
        version: Int,
    ): UpdateProfileCommand {
        requireSupportedVersion(version, ProductionOperationKinds.UPDATE_PROFILE)
        val wire = json.decodeFromString<ProfileUpdateWire>(payload)
        return UpdateProfileCommand(
            accountId = UserId(wire.accountId),
            profile = wire.profile.toDomain(),
            clientOperationId = ClientOperationId(wire.clientOperationId),
            changedAt = Instant.fromEpochMilliseconds(wire.changedAtEpochMillis),
        )
    }

    fun encode(command: SendFriendRequestCommand): String = json.encodeToString(
        SendFriendRequestWire(
            accountId = command.accountId.value,
            addressee = command.addressee.toWire(),
            clientOperationId = command.clientOperationId.value,
            requestedAtEpochMillis = command.requestedAt.toEpochMilliseconds(),
        ),
    )

    fun decodeSendFriendRequest(
        payload: String,
        version: Int,
    ): SendFriendRequestCommand {
        requireSupportedVersion(version, ProductionOperationKinds.SEND_FRIEND_REQUEST)
        val wire = json.decodeFromString<SendFriendRequestWire>(payload)
        return SendFriendRequestCommand(
            accountId = UserId(wire.accountId),
            addressee = wire.addressee.toDomain(),
            clientOperationId = ClientOperationId(wire.clientOperationId),
            requestedAt = Instant.fromEpochMilliseconds(wire.requestedAtEpochMillis),
        )
    }

    fun encode(command: RespondToFriendRequestCommand): String = json.encodeToString(
        RespondToFriendRequestWire(
            accountId = command.accountId.value,
            requestId = command.requestId,
            requester = command.requester.toWire(),
            accept = command.accept,
            clientOperationId = command.clientOperationId.value,
            respondedAtEpochMillis = command.respondedAt.toEpochMilliseconds(),
        ),
    )

    fun decodeRespondToFriendRequest(
        payload: String,
        version: Int,
    ): RespondToFriendRequestCommand {
        requireSupportedVersion(version, ProductionOperationKinds.RESPOND_TO_FRIEND_REQUEST)
        val wire = json.decodeFromString<RespondToFriendRequestWire>(payload)
        return RespondToFriendRequestCommand(
            accountId = UserId(wire.accountId),
            requestId = wire.requestId,
            requester = wire.requester.toDomain(),
            accept = wire.accept,
            clientOperationId = ClientOperationId(wire.clientOperationId),
            respondedAt = Instant.fromEpochMilliseconds(wire.respondedAtEpochMillis),
        )
    }

    fun encode(command: RemoveFriendCommand): String = json.encodeToString(
        RemoveFriendWire(
            accountId = command.accountId.value,
            friendUserId = command.friendUserId.value,
            clientOperationId = command.clientOperationId.value,
            removedAtEpochMillis = command.removedAt.toEpochMilliseconds(),
        ),
    )

    fun decodeRemoveFriend(
        payload: String,
        version: Int,
    ): RemoveFriendCommand {
        requireSupportedVersion(version, ProductionOperationKinds.REMOVE_FRIEND)
        val wire = json.decodeFromString<RemoveFriendWire>(payload)
        return RemoveFriendCommand(
            accountId = UserId(wire.accountId),
            friendUserId = UserId(wire.friendUserId),
            clientOperationId = ClientOperationId(wire.clientOperationId),
            removedAt = Instant.fromEpochMilliseconds(wire.removedAtEpochMillis),
        )
    }

    fun encode(command: PurchaseShopItemCommand): String = json.encodeToString(
        PurchaseShopItemWire(
            accountId = command.accountId.value,
            itemId = command.itemId,
            priceTokens = command.priceTokens,
            clientOperationId = command.clientOperationId.value,
            requestedAtEpochMillis = command.requestedAt.toEpochMilliseconds(),
        ),
    )

    fun decodePurchaseShopItem(
        payload: String,
        version: Int,
    ): PurchaseShopItemCommand {
        requireSupportedVersion(version, ProductionOperationKinds.PURCHASE_SHOP_ITEM)
        val wire = json.decodeFromString<PurchaseShopItemWire>(payload)
        return PurchaseShopItemCommand(
            accountId = UserId(wire.accountId),
            itemId = wire.itemId,
            priceTokens = wire.priceTokens,
            clientOperationId = ClientOperationId(wire.clientOperationId),
            requestedAt = Instant.fromEpochMilliseconds(wire.requestedAtEpochMillis),
        )
    }

    fun encode(command: SetUserBlockCommand): String = json.encodeToString(
        SetUserBlockWire(
            accountId = command.accountId.value,
            targetUserId = command.targetUserId.value,
            blocked = command.blocked,
            clientOperationId = command.clientOperationId.value,
            changedAtEpochMillis = command.changedAt.toEpochMilliseconds(),
        ),
    )

    fun decodeSetUserBlock(
        payload: String,
        version: Int,
    ): SetUserBlockCommand {
        requireSupportedVersion(version, ProductionOperationKinds.SET_USER_BLOCK)
        val wire = json.decodeFromString<SetUserBlockWire>(payload)
        return SetUserBlockCommand(
            accountId = UserId(wire.accountId),
            targetUserId = UserId(wire.targetUserId),
            blocked = wire.blocked,
            clientOperationId = ClientOperationId(wire.clientOperationId),
            changedAt = Instant.fromEpochMilliseconds(wire.changedAtEpochMillis),
        )
    }

    fun encode(command: MarkNotificationReadCommand): String = json.encodeToString(
        MarkNotificationReadWire(
            accountId = command.accountId.value,
            notificationId = command.notificationId.value,
            clientOperationId = command.clientOperationId.value,
            readAtEpochMillis = command.readAt.toEpochMilliseconds(),
        ),
    )

    fun decodeMarkNotificationRead(
        payload: String,
        version: Int,
    ): MarkNotificationReadCommand {
        requireSupportedVersion(version, ProductionOperationKinds.MARK_NOTIFICATION_READ)
        val wire = json.decodeFromString<MarkNotificationReadWire>(payload)
        return MarkNotificationReadCommand(
            accountId = UserId(wire.accountId),
            notificationId = NotificationId(wire.notificationId),
            clientOperationId = ClientOperationId(wire.clientOperationId),
            readAt = Instant.fromEpochMilliseconds(wire.readAtEpochMillis),
        )
    }

    fun encode(command: MarkAllNotificationsReadCommand): String = json.encodeToString(
        MarkAllNotificationsReadWire(
            accountId = command.accountId.value,
            clientOperationId = command.clientOperationId.value,
            readAtEpochMillis = command.readAt.toEpochMilliseconds(),
        ),
    )

    fun decodeMarkAllNotificationsRead(
        payload: String,
        version: Int,
    ): MarkAllNotificationsReadCommand {
        requireSupportedVersion(version, ProductionOperationKinds.MARK_ALL_NOTIFICATIONS_READ)
        val wire = json.decodeFromString<MarkAllNotificationsReadWire>(payload)
        return MarkAllNotificationsReadCommand(
            accountId = UserId(wire.accountId),
            clientOperationId = ClientOperationId(wire.clientOperationId),
            readAt = Instant.fromEpochMilliseconds(wire.readAtEpochMillis),
        )
    }

    fun encode(command: DeleteNotificationCommand): String = json.encodeToString(
        DeleteNotificationWire(
            accountId = command.accountId.value,
            notificationId = command.notificationId.value,
            clientOperationId = command.clientOperationId.value,
            deletedAtEpochMillis = command.deletedAt.toEpochMilliseconds(),
        ),
    )

    fun decodeDeleteNotification(
        payload: String,
        version: Int,
    ): DeleteNotificationCommand {
        requireSupportedVersion(version, ProductionOperationKinds.DELETE_NOTIFICATION)
        val wire = json.decodeFromString<DeleteNotificationWire>(payload)
        return DeleteNotificationCommand(
            accountId = UserId(wire.accountId),
            notificationId = NotificationId(wire.notificationId),
            clientOperationId = ClientOperationId(wire.clientOperationId),
            deletedAt = Instant.fromEpochMilliseconds(wire.deletedAtEpochMillis),
        )
    }

    private fun requireSupportedVersion(version: Int, kind: String) {
        require(version == VERSION) { "Unsupported $kind payload version: $version" }
    }
}

@Serializable
private data class ProfileWire(
    val userId: String,
    val displayName: String,
    val avatarKind: String?,
    val avatarValue: String?,
    val bio: String,
    val age: Int?,
    val countryCode: String?,
    val locationLabel: String?,
    val lastSeenAtEpochMillis: Long?,
    val presence: String,
    val updatedAtEpochMillis: Long,
)

@Serializable
private data class ProfileUpdateWire(
    val accountId: String,
    val profile: ProfileWire,
    val clientOperationId: String,
    val changedAtEpochMillis: Long,
)

@Serializable
private data class SendFriendRequestWire(
    val accountId: String,
    val addressee: ProfileWire,
    val clientOperationId: String,
    val requestedAtEpochMillis: Long,
)

@Serializable
private data class RespondToFriendRequestWire(
    val accountId: String,
    val requestId: String,
    val requester: ProfileWire,
    val accept: Boolean,
    val clientOperationId: String,
    val respondedAtEpochMillis: Long,
)

@Serializable
private data class RemoveFriendWire(
    val accountId: String,
    val friendUserId: String,
    val clientOperationId: String,
    val removedAtEpochMillis: Long,
)

@Serializable
private data class PurchaseShopItemWire(
    val accountId: String,
    val itemId: String,
    val priceTokens: Int,
    val clientOperationId: String,
    val requestedAtEpochMillis: Long,
)

@Serializable
private data class SetUserBlockWire(
    val accountId: String,
    val targetUserId: String,
    val blocked: Boolean,
    val clientOperationId: String,
    val changedAtEpochMillis: Long,
)

@Serializable
private data class MarkNotificationReadWire(
    val accountId: String,
    val notificationId: String,
    val clientOperationId: String,
    val readAtEpochMillis: Long,
)

@Serializable
private data class MarkAllNotificationsReadWire(
    val accountId: String,
    val clientOperationId: String,
    val readAtEpochMillis: Long,
)

@Serializable
private data class DeleteNotificationWire(
    val accountId: String,
    val notificationId: String,
    val clientOperationId: String,
    val deletedAtEpochMillis: Long,
)

private fun UserProfile.toWire(): ProfileWire {
    val (avatarKind, avatarValue) = when (val currentAvatar = avatar) {
        null -> null to null
        is AvatarReference.Bundled -> "BUNDLED" to currentAvatar.key
        is AvatarReference.Remote -> "REMOTE" to currentAvatar.url
    }
    return ProfileWire(
        userId = userId.value,
        displayName = displayName,
        avatarKind = avatarKind,
        avatarValue = avatarValue,
        bio = bio,
        age = age,
        countryCode = countryCode,
        locationLabel = locationLabel,
        lastSeenAtEpochMillis = lastSeenAt?.toEpochMilliseconds(),
        presence = presence.name,
        updatedAtEpochMillis = updatedAt.toEpochMilliseconds(),
    )
}

private fun ProfileWire.toDomain(): UserProfile = UserProfile(
    userId = UserId(userId),
    displayName = displayName,
    avatar = when {
        avatarKind == "BUNDLED" && avatarValue != null -> AvatarReference.Bundled(avatarValue)
        avatarKind == "REMOTE" && avatarValue != null -> AvatarReference.Remote(avatarValue)
        else -> null
    },
    bio = bio,
    age = age,
    countryCode = countryCode,
    locationLabel = locationLabel,
    lastSeenAt = lastSeenAtEpochMillis?.let(Instant::fromEpochMilliseconds),
    presence = PresenceStatus.entries.firstOrNull { it.name == presence }
        ?: PresenceStatus.Unknown,
    updatedAt = Instant.fromEpochMilliseconds(updatedAtEpochMillis),
)
