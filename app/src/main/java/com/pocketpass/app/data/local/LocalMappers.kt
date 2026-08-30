package com.pocketpass.app.data.local

import com.pocketpass.app.data.local.entity.AchievementStateEntity
import com.pocketpass.app.data.local.entity.BingoCellEntity
import com.pocketpass.app.data.local.entity.ConversationEntity
import com.pocketpass.app.data.local.entity.ConversationMemberEntity
import com.pocketpass.app.data.local.entity.LocalConversationKinds
import com.pocketpass.app.data.local.entity.FriendEntity
import com.pocketpass.app.data.local.entity.NotificationEntity
import com.pocketpass.app.data.local.entity.LeaderboardEntryEntity
import com.pocketpass.app.data.local.entity.LocalAvatarKinds
import com.pocketpass.app.data.local.entity.LocalDeliveryStates
import com.pocketpass.app.data.local.entity.MessageEntity
import com.pocketpass.app.data.local.entity.NearbyEncounterEntity
import com.pocketpass.app.data.local.entity.OwnedShopItemEntity
import com.pocketpass.app.data.local.entity.ProfileEntity
import com.pocketpass.app.data.local.entity.ShopCategoryEntity
import com.pocketpass.app.data.local.entity.ShopItemEntity
import com.pocketpass.app.data.local.entity.WorldTourRegionEntity
import com.pocketpass.app.domain.model.AchievementState
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.BingoCell
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.ConversationKind
import com.pocketpass.app.domain.model.ConversationMember
import com.pocketpass.app.domain.model.ConversationMemberRole
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.FriendshipStatus
import com.pocketpass.app.domain.model.FriendRequestNotificationStatus
import com.pocketpass.app.domain.model.LeaderboardEntry
import com.pocketpass.app.domain.model.LeaderboardScope
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.MessageAttachment
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.domain.model.EncounterId
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.NotificationId
import com.pocketpass.app.domain.model.NotificationKind
import com.pocketpass.app.domain.model.PocketPassNotification
import com.pocketpass.app.domain.model.OwnedShopItem
import com.pocketpass.app.domain.model.PresenceStatus
import com.pocketpass.app.domain.model.ShopCategory
import com.pocketpass.app.domain.model.ShopItem
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.model.WorldTourRegion
import com.pocketpass.app.domain.state.PendingState
import kotlin.time.Instant

fun ProfileEntity.toDomain(): UserProfile = UserProfile(
    userId = UserId(userId),
    displayName = displayName,
    avatar = avatarFromColumns(avatarKind, avatarValue),
    username = username,
    bio = bio,
    age = age,
    countryCode = countryCode,
    locationLabel = locationLabel,
    lastSeenAt = lastSeenAtEpochMillis?.let(Instant::fromEpochMilliseconds),
    presence = PresenceStatus.entries.firstOrNull { it.name == presence }
        ?: PresenceStatus.Unknown,
    updatedAt = Instant.fromEpochMilliseconds(updatedAtEpochMillis),
)

fun ShopItemEntity.toDomain(): ShopItem = ShopItem(
    id = itemId,
    slug = slug,
    name = name,
    priceTokens = priceTokens,
    imageKey = imageKey,
    miiHatType = miiHatType,
)

fun OwnedShopItemEntity.toDomain(): OwnedShopItem = OwnedShopItem(
    itemId = itemId,
    purchasedAt = Instant.fromEpochMilliseconds(purchasedAtEpochMillis),
    pricePaid = pricePaid,
    pending = pendingOperationId != null,
)

fun ShopCategoryEntity.toDomain(items: List<ShopItem>): ShopCategory = ShopCategory(
    id = categoryId,
    slug = slug,
    title = title,
    subtitle = subtitle,
    iconKey = iconKey,
    items = items,
)

fun LeaderboardEntryEntity.toDomain(): LeaderboardEntry = LeaderboardEntry(
    userId = UserId(userId),
    displayName = displayName,
    avatar = avatarFromColumns(avatarKind, avatarValue),
    trophyCount = trophyCount,
    encounterCount = encounterCount,
)

fun LeaderboardEntry.toEntity(
    accountId: UserId,
    scope: LeaderboardScope,
    position: Int,
): LeaderboardEntryEntity {
    val avatarColumns = avatar.toColumns()
    return LeaderboardEntryEntity(
        accountId = accountId.value,
        scope = scope.key,
        userId = userId.value,
        displayName = displayName,
        avatarKind = avatarColumns.first,
        avatarValue = avatarColumns.second,
        trophyCount = trophyCount,
        encounterCount = encounterCount,
        position = position,
    )
}

fun AchievementStateEntity.toDomain(): AchievementState = AchievementState(
    key = achievementKey,
    unlocked = unlocked,
    unlockedAt = unlockedAtEpochMillis?.let(Instant::fromEpochMilliseconds),
    progressPercent = progressPercent,
)

fun AchievementState.toEntity(
    accountId: UserId,
    position: Int,
): AchievementStateEntity = AchievementStateEntity(
    accountId = accountId.value,
    achievementKey = key,
    unlocked = unlocked,
    unlockedAtEpochMillis = unlockedAt?.toEpochMilliseconds(),
    progressPercent = progressPercent,
    position = position,
)

fun BingoCellEntity.toDomain(): BingoCell = BingoCell(
    position = position,
    slug = slug,
    text = goalText,
    shortLabel = shortLabel,
    completed = completed,
    progressCurrent = progressCurrent,
    progressTarget = progressTarget,
)

fun BingoCell.toEntity(accountId: UserId): BingoCellEntity = BingoCellEntity(
    accountId = accountId.value,
    position = position,
    slug = slug,
    goalText = text,
    shortLabel = shortLabel,
    completed = completed,
    progressCurrent = progressCurrent,
    progressTarget = progressTarget,
)

fun WorldTourRegionEntity.toDomain(): WorldTourRegion = WorldTourRegion(
    countryCode = countryCode,
    firstMetAt = Instant.fromEpochMilliseconds(firstMetAtEpochMillis),
)

fun WorldTourRegion.toEntity(
    accountId: UserId,
    position: Int,
): WorldTourRegionEntity = WorldTourRegionEntity(
    accountId = accountId.value,
    countryCode = countryCode,
    firstMetAtEpochMillis = firstMetAt.toEpochMilliseconds(),
    position = position,
)

fun NearbyEncounterEntity.toDomain(): NearbyEncounter = NearbyEncounter(
    id = EncounterId(encounterId),
    ownerId = UserId(accountId),
    profile = UserProfile(
        userId = UserId(remoteUserId),
        displayName = displayName,
        avatar = avatarFromColumns(avatarKind, avatarValue),
        bio = bio,
        age = age,
        countryCode = countryCode,
        locationLabel = locationLabel,
        lastSeenAt = lastSeenAtEpochMillis?.let(Instant::fromEpochMilliseconds),
        updatedAt = Instant.fromEpochMilliseconds(profileUpdatedAtEpochMillis),
    ),
    occurredAt = Instant.fromEpochMilliseconds(occurredAtEpochMillis),
    resolvedAt = Instant.fromEpochMilliseconds(resolvedAtEpochMillis),
)

fun NearbyEncounter.toEntity(): NearbyEncounterEntity {
    val avatarColumns = profile.avatar.toColumns()
    return NearbyEncounterEntity(
        accountId = ownerId.value,
        encounterId = id.value,
        remoteUserId = profile.userId.value,
        displayName = profile.displayName,
        avatarKind = avatarColumns?.first,
        avatarValue = avatarColumns?.second,
        bio = profile.bio,
        age = profile.age,
        countryCode = profile.countryCode,
        locationLabel = profile.locationLabel,
        lastSeenAtEpochMillis = profile.lastSeenAt?.toEpochMilliseconds(),
        profileUpdatedAtEpochMillis = profile.updatedAt.toEpochMilliseconds(),
        occurredAtEpochMillis = occurredAt.toEpochMilliseconds(),
        resolvedAtEpochMillis = resolvedAt.toEpochMilliseconds(),
    )
}

fun UserProfile.toEntity(): ProfileEntity {
    val avatarColumns = avatar.toColumns()
    return ProfileEntity(
        userId = userId.value,
        displayName = displayName,
        avatarKind = avatarColumns.first,
        avatarValue = avatarColumns.second,
        username = username,
        bio = bio,
        age = age,
        countryCode = countryCode,
        locationLabel = locationLabel,
        lastSeenAtEpochMillis = lastSeenAt?.toEpochMilliseconds(),
        presence = presence.name,
        updatedAtEpochMillis = updatedAt.toEpochMilliseconds(),
    )
}

fun FriendEntity.toDomain(): Friend = Friend(
    ownerId = UserId(ownerId),
    profile = UserProfile(
        userId = UserId(friendUserId),
        displayName = displayName,
        avatar = avatarFromColumns(avatarKind, avatarValue),
        bio = bio,
        age = age,
        countryCode = countryCode,
        locationLabel = locationLabel,
        lastSeenAt = lastSeenAtEpochMillis?.let(Instant::fromEpochMilliseconds),
        presence = PresenceStatus.entries.firstOrNull { it.name == presence }
            ?: PresenceStatus.Unknown,
        updatedAt = Instant.fromEpochMilliseconds(profileUpdatedAtEpochMillis),
    ),
    status = FriendshipStatus.entries.firstOrNull { it.name == friendshipStatus }
        ?: FriendshipStatus.Accepted,
    lastInteractionAt = lastInteractionAtEpochMillis?.let(Instant::fromEpochMilliseconds),
    isOnline = isOnline,
)

fun Friend.toEntity(): FriendEntity {
    val avatarColumns = profile.avatar.toColumns()
    return FriendEntity(
        ownerId = ownerId.value,
        friendUserId = profile.userId.value,
        displayName = profile.displayName,
        avatarKind = avatarColumns.first,
        avatarValue = avatarColumns.second,
        bio = profile.bio,
        age = profile.age,
        countryCode = profile.countryCode,
        locationLabel = profile.locationLabel,
        lastSeenAtEpochMillis = profile.lastSeenAt?.toEpochMilliseconds(),
        presence = profile.presence.name,
        profileUpdatedAtEpochMillis = profile.updatedAt.toEpochMilliseconds(),
        friendshipStatus = status.name,
        lastInteractionAtEpochMillis = lastInteractionAt?.toEpochMilliseconds(),
        isOnline = isOnline,
    )
}

fun NotificationEntity.toDomain(): PocketPassNotification =
    PocketPassNotification(
        id = NotificationId(notificationId),
        recipientId = UserId(accountId),
        kind = NotificationKind.entries.firstOrNull { it.name == kind }
            ?: NotificationKind.System,
        actor = actorUserId?.let { userId ->
            UserProfile(
                userId = UserId(userId),
                displayName = actorDisplayName.orEmpty(),
                avatar = avatarFromColumns(actorAvatarKind, actorAvatarValue),
                updatedAt = Instant.fromEpochMilliseconds(actorUpdatedAtEpochMillis ?: 0L),
            )
        },
        friendRequestId = friendRequestId,
        friendRequestStatus = friendRequestStatus?.let { status ->
            FriendRequestNotificationStatus.entries.firstOrNull { it.name == status }
        },
        conversationId = conversationId?.let(::ConversationId),
        title = title,
        body = body,
        eventCount = eventCount,
        createdAt = Instant.fromEpochMilliseconds(createdAtEpochMillis),
        updatedAt = Instant.fromEpochMilliseconds(updatedAtEpochMillis),
        readAt = readAtEpochMillis?.let(Instant::fromEpochMilliseconds),
        deletedAt = deletedAtEpochMillis?.let(Instant::fromEpochMilliseconds),
    )

fun PocketPassNotification.toEntity(): NotificationEntity {
    val avatarColumns = actor?.avatar.toColumns()
    return NotificationEntity(
        accountId = recipientId.value,
        notificationId = id.value,
        kind = kind.name,
        actorUserId = actor?.userId?.value,
        actorDisplayName = actor?.displayName,
        actorAvatarKind = avatarColumns?.first,
        actorAvatarValue = avatarColumns?.second,
        actorUpdatedAtEpochMillis = actor?.updatedAt?.toEpochMilliseconds(),
        friendRequestId = friendRequestId,
        friendRequestStatus = friendRequestStatus?.name,
        conversationId = conversationId?.value,
        title = title,
        body = body,
        eventCount = eventCount,
        createdAtEpochMillis = createdAt.toEpochMilliseconds(),
        updatedAtEpochMillis = updatedAt.toEpochMilliseconds(),
        readAtEpochMillis = readAt?.toEpochMilliseconds(),
        deletedAtEpochMillis = deletedAt?.toEpochMilliseconds(),
    )
}

fun ConversationEntity.toDomain(
    members: List<ConversationMember> = emptyList(),
): ConversationSummary = ConversationSummary(
    id = ConversationId(conversationId),
    title = title,
    avatar = avatarFromColumns(avatarKind, avatarValue),
    latestMessagePreview = latestMessagePreview,
    latestMessageAt = latestMessageAtEpochMillis?.let(Instant::fromEpochMilliseconds),
    unreadCount = unreadCount,
    kind = if (kind == LocalConversationKinds.GROUP) ConversationKind.Group else ConversationKind.Direct,
    members = members,
)

fun ConversationSummary.toEntity(accountId: UserId): ConversationEntity {
    val avatarColumns = avatar.toColumns()
    return ConversationEntity(
        accountId = accountId.value,
        conversationId = id.value,
        title = title,
        avatarKind = avatarColumns.first,
        avatarValue = avatarColumns.second,
        latestMessagePreview = latestMessagePreview,
        latestMessageAtEpochMillis = latestMessageAt?.toEpochMilliseconds(),
        unreadCount = unreadCount,
        kind = if (isGroup) LocalConversationKinds.GROUP else LocalConversationKinds.DIRECT,
    )
}

fun ConversationMemberEntity.toDomain(): ConversationMember = ConversationMember(
    userId = UserId(userId),
    displayName = displayName,
    avatar = avatarFromColumns(avatarKind, avatarValue),
    role = if (role == ConversationMemberRole.Owner.name) {
        ConversationMemberRole.Owner
    } else {
        ConversationMemberRole.Member
    },
    joinedAt = Instant.fromEpochMilliseconds(joinedAtEpochMillis),
)

fun ConversationMember.toEntity(
    accountId: UserId,
    conversationId: ConversationId,
): ConversationMemberEntity {
    val avatarColumns = avatar.toColumns()
    return ConversationMemberEntity(
        accountId = accountId.value,
        conversationId = conversationId.value,
        userId = userId.value,
        displayName = displayName,
        avatarKind = avatarColumns.first,
        avatarValue = avatarColumns.second,
        role = role.name,
        joinedAtEpochMillis = joinedAt.toEpochMilliseconds(),
    )
}

fun MessageEntity.toDomain(): Message = Message(
    id = MessageId(messageId),
    conversationId = ConversationId(conversationId),
    senderId = UserId(senderId),
    clientOperationId = clientOperationId?.let(::ClientOperationId),
    body = body,
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMillis),
    editedAt = editedAtEpochMillis?.let(Instant::fromEpochMilliseconds),
    deletedAt = deletedAtEpochMillis?.let(Instant::fromEpochMilliseconds),
    pendingState = when (deliveryState) {
        LocalDeliveryStates.QUEUED -> PendingState.Queued(
            operationId = localOperationId(),
        )

        LocalDeliveryStates.SENDING -> PendingState.Sending(
            operationId = localOperationId(),
            attempt = deliveryAttempt,
        )

        LocalDeliveryStates.FAILED_RETRYABLE -> PendingState.Failed(
            operationId = localOperationId(),
            retryable = true,
            message = lastDeliveryError,
        )

        LocalDeliveryStates.FAILED_PERMANENT -> PendingState.Failed(
            operationId = localOperationId(),
            retryable = false,
            message = lastDeliveryError,
        )

        else -> PendingState.Synced
    },
    attachment = attachmentMime?.let { mime ->
        if (attachmentPath == null && attachmentLocalPath == null) {
            null
        } else {
            MessageAttachment(
                remotePath = attachmentPath,
                mimeType = mime,
                localPath = attachmentLocalPath,
            )
        }
    },
)

private fun MessageEntity.localOperationId(): String =
    pendingOperationId ?: clientOperationId ?: messageId

fun Message.toEntity(accountId: UserId): MessageEntity {
    val delivery = when (val pending = pendingState) {
        PendingState.Synced -> DeliveryColumns(
            state = LocalDeliveryStates.SYNCED,
            operationId = null,
            attempt = 0,
            error = null,
        )

        is PendingState.Queued -> DeliveryColumns(
            state = LocalDeliveryStates.QUEUED,
            operationId = pending.operationId,
            attempt = 0,
            error = null,
        )

        is PendingState.Sending -> DeliveryColumns(
            state = LocalDeliveryStates.SENDING,
            operationId = pending.operationId,
            attempt = pending.attempt,
            error = null,
        )

        is PendingState.Failed -> DeliveryColumns(
            state = if (pending.retryable) {
                LocalDeliveryStates.FAILED_RETRYABLE
            } else {
                LocalDeliveryStates.FAILED_PERMANENT
            },
            operationId = pending.operationId,
            attempt = 0,
            error = pending.message,
        )
    }
    return MessageEntity(
        accountId = accountId.value,
        messageId = id.value,
        conversationId = conversationId.value,
        senderId = senderId.value,
        clientOperationId = clientOperationId?.value,
        body = body,
        createdAtEpochMillis = createdAt.toEpochMilliseconds(),
        editedAtEpochMillis = editedAt?.toEpochMilliseconds(),
        deletedAtEpochMillis = deletedAt?.toEpochMilliseconds(),
        deliveryState = delivery.state,
        pendingOperationId = delivery.operationId,
        deliveryAttempt = delivery.attempt,
        lastDeliveryError = delivery.error,
        attachmentPath = attachment?.remotePath,
        attachmentMime = attachment?.mimeType,
        attachmentLocalPath = attachment?.localPath,
    )
}

private data class DeliveryColumns(
    val state: String,
    val operationId: String?,
    val attempt: Int,
    val error: String?,
)

private fun AvatarReference?.toColumns(): Pair<String?, String?> = when (this) {
    null -> null to null
    is AvatarReference.Bundled -> LocalAvatarKinds.BUNDLED to key
    is AvatarReference.Remote -> LocalAvatarKinds.REMOTE to url
}

private fun avatarFromColumns(kind: String?, value: String?): AvatarReference? {
    if (kind == null || value == null) return null
    return when (kind) {
        LocalAvatarKinds.BUNDLED -> AvatarReference.Bundled(value)
        LocalAvatarKinds.REMOTE -> AvatarReference.Remote(value)
        else -> null
    }
}
