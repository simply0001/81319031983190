package com.pocketpass.app.data.repository

import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.ShopCategory
import com.pocketpass.app.domain.model.ShopItem
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.ConversationKind
import com.pocketpass.app.domain.model.ConversationMember
import com.pocketpass.app.domain.model.ConversationMemberRole
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.AddGroupMembersCommand
import com.pocketpass.app.domain.model.CreateGroupConversationCommand
import com.pocketpass.app.domain.model.LeaveGroupConversationCommand
import com.pocketpass.app.domain.model.MAX_GROUP_MEMBERS
import com.pocketpass.app.domain.model.RemoveGroupMemberCommand
import com.pocketpass.app.domain.model.RenameGroupConversationCommand
import com.pocketpass.app.domain.model.groupMessagePreview
import com.pocketpass.app.domain.model.DeleteMessageCommand
import com.pocketpass.app.domain.model.DeleteNotificationCommand
import com.pocketpass.app.domain.model.EditMessageCommand
import com.pocketpass.app.domain.state.PendingState
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.FriendCode
import com.pocketpass.app.domain.model.FriendRequestNotificationStatus
import com.pocketpass.app.domain.model.FriendshipStatus
import com.pocketpass.app.domain.model.MarkAllNotificationsReadCommand
import com.pocketpass.app.domain.model.MarkNotificationReadCommand
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.MarkConversationReadCommand
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.AchievementCatalog
import com.pocketpass.app.domain.model.AchievementState
import com.pocketpass.app.domain.model.BingoCell
import com.pocketpass.app.domain.model.LeaderboardEntry
import com.pocketpass.app.domain.model.LeaderboardScope
import com.pocketpass.app.domain.model.WorldTourRegion
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.domain.model.EncounterId
import com.pocketpass.app.domain.model.IssuedNearbyCredential
import com.pocketpass.app.domain.model.SubmitNearbyEncounterCommand
import com.pocketpass.app.domain.model.NotificationId
import com.pocketpass.app.domain.model.NotificationKind
import com.pocketpass.app.domain.model.PocketPassNotification
import com.pocketpass.app.domain.model.OwnedShopItem
import com.pocketpass.app.domain.model.PresenceStatus
import com.pocketpass.app.domain.model.PurchaseShopItemCommand
import com.pocketpass.app.domain.model.RemoveFriendCommand
import com.pocketpass.app.domain.model.RenameProfileCommand
import com.pocketpass.app.domain.model.RespondToFriendRequestCommand
import com.pocketpass.app.domain.model.SendMessageCommand
import com.pocketpass.app.domain.model.SendFriendRequestCommand
import com.pocketpass.app.domain.model.SetUserBlockCommand
import com.pocketpass.app.domain.model.AccountSetupCommand
import com.pocketpass.app.domain.model.UpdateProfileCommand
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.repository.FriendsRepository
import com.pocketpass.app.domain.repository.EncounterRepository
import com.pocketpass.app.data.repository.remote.EncounterRemoteDataSource
import com.pocketpass.app.domain.repository.AchievementsRepository
import com.pocketpass.app.domain.repository.BingoRepository
import com.pocketpass.app.domain.repository.LeaderboardRepository
import com.pocketpass.app.domain.repository.WorldTourRepository
import com.pocketpass.app.domain.repository.ShopRepository
import com.pocketpass.app.domain.repository.MessageRepository
import com.pocketpass.app.domain.repository.MutableFriendsRepository
import com.pocketpass.app.domain.repository.MutableProfileRepository
import com.pocketpass.app.domain.repository.NotificationRepository
import com.pocketpass.app.domain.repository.PresenceRepository
import com.pocketpass.app.domain.repository.ProfileRepository
import com.pocketpass.app.domain.repository.SessionRepository
import com.pocketpass.app.domain.repository.SyncRepository
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.domain.state.SyncState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object FixtureData {
    val CurrentUserId = UserId("fixture-petah")
    val SpobUserId = UserId("spob")
    val SansUserId = UserId("sans")
    val SpobConversationId = ConversationId("spob")
    val SansConversationId = ConversationId("sans")
    val CrewConversationId = ConversationId("crew")
    val CurrentFriendCode = FriendCode("31415926")
    val SpobFriendCode = FriendCode("87654321")

    private val fixtureTime = Instant.parse("2026-01-01T12:46:00Z")

    val currentProfile = UserProfile(
        userId = CurrentUserId,
        displayName = "Petah Griffin",
        avatar = AvatarReference.Bundled("home_avatar_petah"),
        username = "petah.g",
        bio = "Hello! Nice to meet you!",
        age = 43,
        countryCode = "US",
        locationLabel = null,
        lastSeenAt = fixtureTime,
        presence = PresenceStatus.Online,
        updatedAt = fixtureTime,
    )

    val spobProfile = UserProfile(
        userId = SpobUserId,
        displayName = "spob",
        avatar = AvatarReference.Bundled("messages_avatar_spob"),
        bio = "",
        lastSeenAt = fixtureTime.minus((120).seconds),
        presence = PresenceStatus.Online,
        updatedAt = fixtureTime,
    )

    val sansProfile = UserProfile(
        userId = SansUserId,
        displayName = "sans",
        avatar = AvatarReference.Bundled("messages_avatar_sans"),
        bio = "",
        lastSeenAt = fixtureTime.minus((300).seconds),
        presence = PresenceStatus.Offline,
        updatedAt = fixtureTime,
    )

    val friends = listOf(
        fixtureFriend("matt-1", true, fixtureTime),
        fixtureFriend("matt-2", false, fixtureTime.minus((7_200).seconds)),
        fixtureFriend("matt-3", false, fixtureTime.minus((7_200).seconds)),
    )

    val crewMembers = listOf(
        fixtureMember(currentProfile, ConversationMemberRole.Owner, fixtureTime.minus((7_200).seconds)),
        fixtureMember(spobProfile, ConversationMemberRole.Member, fixtureTime.minus((7_100).seconds)),
        fixtureMember(sansProfile, ConversationMemberRole.Member, fixtureTime.minus((7_000).seconds)),
    )

    val conversations = listOf(
        ConversationSummary(
            id = SpobConversationId,
            title = "spob",
            avatar = AvatarReference.Bundled("messages_avatar_spob"),
            latestMessagePreview = "> open the door twin",
            latestMessageAt = fixtureTime.minus((120).seconds),
            unreadCount = 99,
            members = listOf(
                fixtureMember(currentProfile, ConversationMemberRole.Owner, fixtureTime.minus((7_200).seconds)),
                fixtureMember(spobProfile, ConversationMemberRole.Member, fixtureTime.minus((7_200).seconds)),
            ),
        ),
        ConversationSummary(
            id = CrewConversationId,
            title = "crew",
            avatar = null,
            latestMessagePreview = "You: same",
            latestMessageAt = fixtureTime.minus((200).seconds),
            unreadCount = 1,
            kind = ConversationKind.Group,
            members = crewMembers,
        ),
        ConversationSummary(
            id = SansConversationId,
            title = "sans",
            avatar = AvatarReference.Bundled("messages_avatar_sans"),
            latestMessagePreview = "> gaster blaster LOL",
            latestMessageAt = fixtureTime.minus((300).seconds),
            unreadCount = 2,
            members = listOf(
                fixtureMember(currentProfile, ConversationMemberRole.Owner, fixtureTime.minus((7_200).seconds)),
                fixtureMember(sansProfile, ConversationMemberRole.Member, fixtureTime.minus((7_200).seconds)),
            ),
        ),
    )

    val messages = mapOf(
        CrewConversationId to listOf(
            Message(
                id = MessageId("fixture-crew-1"),
                conversationId = CrewConversationId,
                senderId = SpobUserId,
                clientOperationId = null,
                body = "who's in tonight?",
                createdAt = fixtureTime.minus((360).seconds),
            ),
            Message(
                id = MessageId("fixture-crew-2"),
                conversationId = CrewConversationId,
                senderId = SpobUserId,
                clientOperationId = null,
                body = "8pm at mine",
                createdAt = fixtureTime.minus((330).seconds),
            ),
            Message(
                id = MessageId("fixture-crew-3"),
                conversationId = CrewConversationId,
                senderId = SansUserId,
                clientOperationId = null,
                body = "me too",
                createdAt = fixtureTime.minus((240).seconds),
            ),
            Message(
                id = MessageId("fixture-crew-4"),
                conversationId = CrewConversationId,
                senderId = CurrentUserId,
                clientOperationId = null,
                body = "same",
                createdAt = fixtureTime.minus((200).seconds),
            ),
        ),
        SpobConversationId to listOf(
            Message(
                id = MessageId("fixture-spob-incoming"),
                conversationId = SpobConversationId,
                senderId = SpobUserId,
                clientOperationId = null,
                body = "Hey bro",
                createdAt = fixtureTime.minus((180).seconds),
            ),
            Message(
                id = MessageId("fixture-spob-outgoing"),
                conversationId = SpobConversationId,
                senderId = CurrentUserId,
                clientOperationId = null,
                body = "yo",
                createdAt = fixtureTime.minus((120).seconds),
            ),
        ),
        SansConversationId to listOf(
            Message(
                id = MessageId("fixture-sans-message"),
                conversationId = SansConversationId,
                senderId = SansUserId,
                clientOperationId = null,
                body = "heya.",
                createdAt = fixtureTime.minus((300).seconds),
            ),
        ),
    )

    val tokenBalance = 75

    val shopCatalog = listOf(
        ShopCategory(
            id = "fixture-category-hats",
            slug = "hats",
            title = "Hats",
            subtitle = "Various headwear!",
            iconKey = "shop_category_hats",
            items = listOf(
                fixtureHat("baseball_cap", "Baseball Cap", 20, 0),
                fixtureHat("beanie", "Beanie", 30, 1),
                fixtureHat("top_hat", "Top Hat", 120, 2),
                fixtureHat("ribbons", "Ribbons", 40, 3),
                fixtureHat("bow", "Bow", 40, 4),
                fixtureHat("cat_ears", "Cat Ears", 100, 5),
                fixtureHat("straw_hat", "Straw Hat", 60, 6),
                fixtureHat("cat_hat", "Cat Hat", 70, 7),
                fixtureHat("bike_helmet", "Bike Helmet", 150, 8),
                fixtureHat("halo", "Halo", 90, 9),
            ),
        ),
    )

    val ownedShopItems = listOf(
        OwnedShopItem(
            itemId = "fixture-item-baseball_cap",
            purchasedAt = fixtureTime.minus((86_400).seconds),
            pricePaid = 20,
            pending = false,
        ),
    )

    private fun fixtureHat(slug: String, name: String, price: Int, hatType: Int) = ShopItem(
        id = "fixture-item-$slug",
        slug = slug,
        name = name,
        priceTokens = price,
        imageKey = "shop_item_$slug",
        miiHatType = hatType,
    )

    val leaderboard = listOf(
        LeaderboardEntry(
            userId = CurrentUserId,
            displayName = currentProfile.displayName,
            avatar = currentProfile.avatar,
            trophyCount = 12,
            encounterCount = 34,
        ),
    ) + friends.mapIndexed { index, friend ->
        LeaderboardEntry(
            userId = friend.profile.userId,
            displayName = friend.profile.displayName,
            avatar = friend.profile.avatar,
            trophyCount = 8 - index,
            encounterCount = 20 - index * 2,
        )
    }

    val encounters = friends.mapIndexed { index, friend ->
        NearbyEncounter(
            id = EncounterId("fixture-encounter-$index"),
            ownerId = CurrentUserId,
            profile = friend.profile,
            occurredAt = friend.lastInteractionAt ?: fixtureTime,
            resolvedAt = friend.lastInteractionAt ?: fixtureTime,
        )
    }

    val achievements = AchievementCatalog.definitions.mapIndexed { index, definition ->
        val unlocked = index % 3 == 0
        AchievementState(
            key = definition.key,
            unlocked = unlocked,
            unlockedAt = if (unlocked) fixtureTime else null,
            progressPercent = if (unlocked) 100 else (index * 17) % 100,
        )
    }

    val worldTourRegions = listOf("JP", "FR", "BR", "CA").mapIndexed { index, code ->
        WorldTourRegion(
            countryCode = code,
            firstMetAt = fixtureTime.minus((index * 86_400L).seconds),
        )
    }

    val bingoBoard = (0..24).filterNot { it == 12 }.mapIndexed { index, position ->
        BingoCell(
            position = position,
            slug = "fixture_goal_$position",
            text = "Fixture goal number ${position + 1}!",
            shortLabel = "Goal ${position + 1}",
            completed = index % 4 == 0,
            progressCurrent = if (index % 4 == 0) 3 else index % 3,
            progressTarget = 3,
        )
    }

    val requestProfile = UserProfile(
        userId = UserId("fixture-requester"),
        displayName = "Alex",
        avatar = AvatarReference.Bundled("friends_avatar_matt"),
        bio = "",
        lastSeenAt = fixtureTime.minus((60).seconds),
        presence = PresenceStatus.Online,
        updatedAt = fixtureTime,
    )

    val notifications = listOf(
        PocketPassNotification(
            id = NotificationId("fixture-friend-request"),
            recipientId = CurrentUserId,
            kind = NotificationKind.FriendRequest,
            actor = requestProfile,
            friendRequestId = "fixture-request-1",
            friendRequestStatus = FriendRequestNotificationStatus.Pending,
            conversationId = null,
            title = "Friend request",
            body = "Alex wants to be friends",
            eventCount = 1,
            createdAt = fixtureTime.minus((60).seconds),
            updatedAt = fixtureTime.minus((60).seconds),
            readAt = null,
            deletedAt = null,
        ),
        PocketPassNotification(
            id = NotificationId("fixture-message"),
            recipientId = CurrentUserId,
            kind = NotificationKind.Message,
            actor = spobProfile,
            friendRequestId = null,
            friendRequestStatus = null,
            conversationId = SpobConversationId,
            title = "spob",
            body = "> open the door twin",
            eventCount = 3,
            createdAt = fixtureTime.minus((300).seconds),
            updatedAt = fixtureTime.minus((120).seconds),
            readAt = null,
            deletedAt = null,
        ),
        PocketPassNotification(
            id = NotificationId("fixture-system"),
            recipientId = CurrentUserId,
            kind = NotificationKind.System,
            actor = null,
            friendRequestId = null,
            friendRequestStatus = null,
            conversationId = null,
            title = "Welcome to PocketPass",
            body = "Your notification inbox is ready.",
            eventCount = 1,
            createdAt = fixtureTime.minus((600).seconds),
            updatedAt = fixtureTime.minus((600).seconds),
            readAt = fixtureTime.minus((500).seconds),
            deletedAt = null,
        ),
    )

    private fun fixtureFriend(
        id: String,
        online: Boolean,
        lastInteractionAt: Instant,
    ): Friend = Friend(
        ownerId = CurrentUserId,
        profile = UserProfile(
            userId = UserId(id),
            displayName = "Matt",
            avatar = AvatarReference.Bundled("friends_avatar_matt"),
            bio = "",
            age = null,
            countryCode = null,
            locationLabel = null,
            lastSeenAt = lastInteractionAt,
            presence = if (online) PresenceStatus.Online else PresenceStatus.Offline,
            updatedAt = fixtureTime,
        ),
        lastInteractionAt = lastInteractionAt,
        isOnline = online,
    )

    fun fixtureMember(
        profile: UserProfile,
        role: ConversationMemberRole,
        joinedAt: Instant,
    ): ConversationMember = ConversationMember(
        userId = profile.userId,
        displayName = profile.displayName,
        avatar = profile.avatar,
        role = role,
        joinedAt = joinedAt,
    )

    fun profileFor(userId: UserId): UserProfile? = when (userId) {
        CurrentUserId -> currentProfile
        SpobUserId -> spobProfile
        SansUserId -> sansProfile
        else -> friends.firstOrNull { it.profile.userId == userId }?.profile
    }
}

class FixtureSessionRepository(
    private val initializedState: SessionState =
        SessionState.Authenticated(FixtureData.CurrentUserId),
) : SessionRepository {
    private val mutableSessionState = MutableStateFlow<SessionState>(SessionState.Initializing)
    override val sessionState: StateFlow<SessionState> = mutableSessionState.asStateFlow()

    override suspend fun initialize(): RepositoryResult<SessionState> {
        mutableSessionState.value = initializedState
        return RepositoryResult.Success(initializedState)
    }

    override suspend fun handleAuthCallback(
        callbackUri: String,
    ): RepositoryResult<SessionState> = RepositoryResult.Failure(
        RepositoryFailure(
            kind = RepositoryFailureKind.Validation,
            message = "Fixture sessions do not accept authentication callbacks",
            retryable = false,
        ),
    )

    override suspend fun signInWithDiscord(): RepositoryResult<Unit> =
        fixtureAuthenticationFailure()

    override suspend fun requestEmailOtp(
        email: String,
        createUser: Boolean,
    ): RepositoryResult<Unit> = fixtureAuthenticationFailure()

    override suspend fun verifyEmailOtp(
        email: String,
        sixDigitCode: String,
    ): RepositoryResult<SessionState> = fixtureAuthenticationFailure()

    override suspend fun signOut(): RepositoryResult<Unit> {
        mutableSessionState.value = SessionState.SignedOut
        return RepositoryResult.Success(Unit)
    }

    private fun fixtureAuthenticationFailure() = RepositoryResult.Failure(
        RepositoryFailure(
            kind = RepositoryFailureKind.Misconfigured,
            message = "Fixture builds open with the deterministic fixture account",
            retryable = false,
        ),
    )
}

class FixtureProfileRepository(
    initialProfiles: List<UserProfile> = (
        listOf(FixtureData.currentProfile, FixtureData.spobProfile) +
            FixtureData.friends.map(Friend::profile)
        ).distinctBy(UserProfile::userId),
) : MutableProfileRepository {
    private val profiles = MutableStateFlow(initialProfiles.associateBy(UserProfile::userId))

    override fun observeProfile(userId: UserId): Flow<UserProfile?> = profiles
        .map { it[userId] }
        .distinctUntilChanged()

    override suspend fun refreshProfile(userId: UserId): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun updateProfile(
        command: UpdateProfileCommand,
    ): RepositoryResult<UserProfile> {
        profiles.update { it + (command.profile.userId to command.profile) }
        return RepositoryResult.Success(command.profile)
    }

    override suspend fun completeAccountSetup(
        command: AccountSetupCommand,
    ): RepositoryResult<UserProfile> {
        val existing = profiles.value[command.accountId]
            ?: return RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.NotFound,
                    message = "No fixture profile for ${command.accountId.value}",
                    retryable = false,
                ),
            )
        val updated = existing.copy(
            username = command.username,
            displayName = command.displayName,
            bio = command.bio,
            age = command.age,
            countryCode = command.countryCode,
            updatedAt = command.changedAt,
        )
        profiles.update { it + (command.accountId to updated) }
        return RepositoryResult.Success(updated)
    }

    override suspend fun renameProfile(
        command: RenameProfileCommand,
    ): RepositoryResult<UserProfile> {
        val existing = profiles.value[command.accountId]
            ?: return RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.NotFound,
                    message = "No fixture profile for ${command.accountId.value}",
                    retryable = false,
                ),
            )
        val taken = profiles.value.values.any { other ->
            other.userId != command.accountId &&
                (
                    other.username.equals(command.name, ignoreCase = true) ||
                        other.displayName.equals(command.name, ignoreCase = true)
                    )
        }
        if (taken) {
            return RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.Conflict,
                    message = "That name is already taken",
                    retryable = false,
                ),
            )
        }
        val updated = existing.copy(
            username = command.name,
            displayName = command.name,
            updatedAt = command.changedAt,
        )
        profiles.update { it + (command.accountId to updated) }
        return RepositoryResult.Success(updated)
    }
}

class FixtureFriendsRepository(
    initialFriends: List<Friend> = FixtureData.friends,
) : MutableFriendsRepository {
    private val friends = MutableStateFlow(initialFriends.groupBy(Friend::ownerId))
    private val codes = MutableStateFlow(
        mapOf(
            FixtureData.CurrentUserId to FixtureData.CurrentFriendCode,
            FixtureData.SpobUserId to FixtureData.SpobFriendCode,
        ),
    )
    private val profilesByCode = mapOf(
        FixtureData.SpobFriendCode to FixtureData.spobProfile,
    )

    override fun observeFriends(accountId: UserId): Flow<List<Friend>> = friends
        .map { it[accountId].orEmpty() }
        .distinctUntilChanged()

    override suspend fun refreshFriends(accountId: UserId): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override fun observeMyFriendCode(accountId: UserId): Flow<FriendCode?> =
        codes.map { it[accountId] }.distinctUntilChanged()

    override suspend fun refreshMyFriendCode(accountId: UserId): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun resolveFriendCode(
        accountId: UserId,
        friendCode: FriendCode,
    ): RepositoryResult<UserProfile> {
        if (codes.value[accountId] == friendCode) {
            return RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.Validation,
                    message = "That is your friend code",
                    retryable = false,
                ),
            )
        }
        val profile = profilesByCode[friendCode]
            ?: return RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.NotFound,
                    message = "No PocketPass user is available with that code",
                    retryable = false,
                ),
            )
        return RepositoryResult.Success(profile)
    }

    override suspend fun sendFriendRequest(
        command: SendFriendRequestCommand,
    ): RepositoryResult<Friend> {
        val existing = friends.value[command.accountId]
            .orEmpty()
            .firstOrNull { it.profile.userId == command.addressee.userId }
        if (existing != null) return RepositoryResult.Success(existing)
        val pending = Friend(
            ownerId = command.accountId,
            profile = command.addressee,
            status = FriendshipStatus.PendingOutgoing,
            lastInteractionAt = command.requestedAt,
            isOnline = command.addressee.presence == PresenceStatus.Online,
        )
        friends.update { current ->
            current + (command.accountId to (current[command.accountId].orEmpty() + pending))
        }
        return RepositoryResult.Success(pending)
    }

    override suspend fun respondToFriendRequest(
        command: RespondToFriendRequestCommand,
    ): RepositoryResult<Friend?> {
        val accepted = command.accept.takeIf { it }?.let {
            Friend(
                ownerId = command.accountId,
                profile = command.requester,
                status = FriendshipStatus.Accepted,
                lastInteractionAt = command.respondedAt,
                isOnline = command.requester.presence == PresenceStatus.Online,
            )
        }
        friends.update { current ->
            val withoutRequester = current[command.accountId]
                .orEmpty()
                .filterNot { it.profile.userId == command.requester.userId }
            current + (
                command.accountId to if (accepted == null) {
                    withoutRequester
                } else {
                    withoutRequester + accepted
                }
            )
        }
        return RepositoryResult.Success(accepted)
    }

    override suspend fun removeFriend(command: RemoveFriendCommand): RepositoryResult<Unit> {
        friends.update { current ->
            current + (
                command.accountId to current[command.accountId]
                    .orEmpty()
                    .filterNot { it.profile.userId == command.friendUserId }
            )
        }
        return RepositoryResult.Success(Unit)
    }

    override suspend fun setUserBlocked(command: SetUserBlockCommand): RepositoryResult<Unit> {
        if (command.blocked) {
            removeFriend(
                RemoveFriendCommand(
                    accountId = command.accountId,
                    friendUserId = command.targetUserId,
                    removedAt = command.changedAt,
                ),
            )
        }
        return RepositoryResult.Success(Unit)
    }
}

class FixtureNotificationRepository(
    initial: List<PocketPassNotification> = FixtureData.notifications,
    private val clock: () -> Instant = Clock.System::now,
) : NotificationRepository {
    private val notifications = MutableStateFlow(
        initial.groupBy(PocketPassNotification::recipientId),
    )

    override fun observeNotifications(
        accountId: UserId,
    ): Flow<List<PocketPassNotification>> = notifications
        .map { rows ->
            rows[accountId]
                .orEmpty()
                .filter { it.deletedAt == null }
                .sortedByDescending(PocketPassNotification::updatedAt)
        }
        .distinctUntilChanged()

    override suspend fun refreshNotifications(accountId: UserId): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun markRead(
        command: MarkNotificationReadCommand,
    ): RepositoryResult<Unit> {
        update(command.accountId) { notification ->
            if (notification.id == command.notificationId) {
                notification.copy(readAt = notification.readAt ?: command.readAt)
            } else {
                notification
            }
        }
        return RepositoryResult.Success(Unit)
    }

    override suspend fun markAllRead(
        command: MarkAllNotificationsReadCommand,
    ): RepositoryResult<Unit> {
        update(command.accountId) { notification ->
            notification.copy(readAt = notification.readAt ?: command.readAt)
        }
        return RepositoryResult.Success(Unit)
    }

    override suspend fun delete(
        command: DeleteNotificationCommand,
    ): RepositoryResult<Unit> {
        val target = notifications.value[command.accountId]
            .orEmpty()
            .firstOrNull { it.id == command.notificationId }
            ?: return RepositoryResult.Success(Unit)
        if (!target.canDelete) {
            return RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.Conflict,
                    message = "Respond to this friend request before deleting it",
                    retryable = false,
                ),
            )
        }
        update(command.accountId) { notification ->
            if (notification.id == command.notificationId) {
                notification.copy(deletedAt = command.deletedAt)
            } else {
                notification
            }
        }
        return RepositoryResult.Success(Unit)
    }

    override suspend fun recordFriendRequestResponse(
        accountId: UserId,
        requestId: String,
        accepted: Boolean,
        respondedAt: Instant,
    ): RepositoryResult<Unit> {
        update(accountId) { notification ->
            if (notification.friendRequestId == requestId) {
                notification.copy(
                    friendRequestStatus = if (accepted) {
                        FriendRequestNotificationStatus.Accepted
                    } else {
                        FriendRequestNotificationStatus.Declined
                    },
                    readAt = notification.readAt ?: respondedAt,
                    updatedAt = maxOf(notification.updatedAt, respondedAt),
                )
            } else {
                notification
            }
        }
        return RepositoryResult.Success(Unit)
    }

    fun updateFriendRequest(
        accountId: UserId,
        requestId: String,
        accepted: Boolean,
    ) {
        update(accountId) { notification ->
            if (notification.friendRequestId == requestId) {
                notification.copy(
                    friendRequestStatus = if (accepted) {
                        FriendRequestNotificationStatus.Accepted
                    } else {
                        FriendRequestNotificationStatus.Declined
                    },
                    readAt = notification.readAt ?: clock(),
                    updatedAt = clock(),
                )
            } else {
                notification
            }
        }
    }

    private fun update(
        accountId: UserId,
        transform: (PocketPassNotification) -> PocketPassNotification,
    ) {
        notifications.update { current ->
            current + (accountId to current[accountId].orEmpty().map(transform))
        }
    }
}

class FixtureMessageRepository(
    accountId: UserId = FixtureData.CurrentUserId,
    initialConversations: List<ConversationSummary> = FixtureData.conversations,
    initialMessages: Map<ConversationId, List<Message>> = FixtureData.messages,
    private val clock: () -> Instant = Clock.System::now,
) : MessageRepository {
    private data class ConversationKey(
        val accountId: UserId,
        val conversationId: ConversationId,
    )

    private val mutationMutex = Mutex()
    private val conversations = MutableStateFlow(mapOf(accountId to initialConversations))
    private val messages = MutableStateFlow(
        initialMessages.mapKeys { (conversationId, _) ->
            ConversationKey(accountId, conversationId)
        },
    )
    private val createdGroups = mutableMapOf<ClientOperationId, ConversationId>()

    override suspend fun createGroupConversation(
        command: CreateGroupConversationCommand,
    ): RepositoryResult<ConversationId> = mutationMutex.withLock {
        createdGroups[command.clientOperationId]?.let {
            return@withLock RepositoryResult.Success(it)
        }
        val conversationId = ConversationId("group-${command.clientOperationId.value}")
        val createdAt = clock()
        val members = listOf(fixtureMember(command.accountId, ConversationMemberRole.Owner, createdAt)) +
            command.memberIds.map { fixtureMember(it, ConversationMemberRole.Member, createdAt) }
        val group = ConversationSummary(
            id = conversationId,
            title = command.title.trim(),
            avatar = null,
            latestMessagePreview = "",
            latestMessageAt = createdAt,
            unreadCount = 0,
            kind = ConversationKind.Group,
            members = members,
        )
        conversations.update { allAccounts ->
            allAccounts + (command.accountId to (listOf(group) + allAccounts[command.accountId].orEmpty()))
        }
        createdGroups[command.clientOperationId] = conversationId
        RepositoryResult.Success(conversationId)
    }

    override suspend fun addGroupMembers(
        command: AddGroupMembersCommand,
    ): RepositoryResult<Unit> = mutateGroup(
        accountId = command.accountId,
        conversationId = command.conversationId,
        requireOwner = false,
    ) { group ->
        val existing = group.members.map { it.userId }.toSet()
        val additions = command.memberIds.filterNot { it in existing }
        if (group.members.size + additions.size > MAX_GROUP_MEMBERS) {
            RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.Conflict,
                    message = "Group conversations are limited to $MAX_GROUP_MEMBERS members",
                    retryable = false,
                ),
            )
        } else {
            val joinedAt = clock()
            RepositoryResult.Success(
                group.copy(
                    members = group.members + additions.map {
                        fixtureMember(it, ConversationMemberRole.Member, joinedAt)
                    },
                ),
            )
        }
    }

    override suspend fun removeGroupMember(
        command: RemoveGroupMemberCommand,
    ): RepositoryResult<Unit> = mutateGroup(
        accountId = command.accountId,
        conversationId = command.conversationId,
        requireOwner = true,
    ) { group ->
        if (group.member(command.userId) == null) {
            RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.NotFound,
                    message = "That person is not in the group",
                    retryable = false,
                ),
            )
        } else {
            RepositoryResult.Success(
                group.copy(members = group.members.filterNot { it.userId == command.userId }),
            )
        }
    }

    override suspend fun renameGroupConversation(
        command: RenameGroupConversationCommand,
    ): RepositoryResult<Unit> = mutateGroup(
        accountId = command.accountId,
        conversationId = command.conversationId,
        requireOwner = true,
    ) { group -> RepositoryResult.Success(group.copy(title = command.title.trim())) }

    override suspend fun leaveGroupConversation(
        command: LeaveGroupConversationCommand,
    ): RepositoryResult<Unit> = mutationMutex.withLock {
        val rows = conversations.value[command.accountId].orEmpty()
        if (rows.none { it.id == command.conversationId && it.isGroup }) {
            return@withLock RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.NotFound,
                    message = "Group does not exist",
                    retryable = false,
                ),
            )
        }
        conversations.update { allAccounts ->
            allAccounts + (command.accountId to rows.filterNot { it.id == command.conversationId })
        }
        messages.update { it - ConversationKey(command.accountId, command.conversationId) }
        RepositoryResult.Success(Unit)
    }

    private suspend fun mutateGroup(
        accountId: UserId,
        conversationId: ConversationId,
        requireOwner: Boolean,
        transform: (ConversationSummary) -> RepositoryResult<ConversationSummary>,
    ): RepositoryResult<Unit> = mutationMutex.withLock {
        val rows = conversations.value[accountId].orEmpty()
        val group = rows.firstOrNull { it.id == conversationId && it.isGroup }
            ?: return@withLock RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.NotFound,
                    message = "Group does not exist",
                    retryable = false,
                ),
            )
        if (requireOwner && group.ownerId != accountId) {
            return@withLock RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.Forbidden,
                    message = "Only the group owner can do that",
                    retryable = false,
                ),
            )
        }
        when (val updated = transform(group)) {
            is RepositoryResult.Failure -> updated
            is RepositoryResult.Success -> {
                conversations.update { allAccounts ->
                    allAccounts + (
                        accountId to rows.map { if (it.id == conversationId) updated.value else it }
                        )
                }
                RepositoryResult.Success(Unit)
            }
        }
    }

    private fun fixtureMember(
        userId: UserId,
        role: ConversationMemberRole,
        joinedAt: Instant,
    ): ConversationMember {
        val profile = FixtureData.profileFor(userId)
        return ConversationMember(
            userId = userId,
            displayName = profile?.displayName ?: userId.value,
            avatar = profile?.avatar,
            role = role,
            joinedAt = joinedAt,
        )
    }

    override fun observeConversations(
        accountId: UserId,
    ): Flow<List<ConversationSummary>> = conversations
        .map { it[accountId].orEmpty() }
        .distinctUntilChanged()

    override fun observeMessages(
        accountId: UserId,
        conversationId: ConversationId,
    ): Flow<List<Message>> = messages
        .map { rows ->
            rows[ConversationKey(accountId, conversationId)]
                .orEmpty()
                .filter { it.deletedAt == null }
        }
        .distinctUntilChanged()

    override suspend fun refreshConversations(
        accountId: UserId,
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    override suspend fun refreshMessages(
        accountId: UserId,
        conversationId: ConversationId,
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    override suspend fun sendMessage(
        command: SendMessageCommand,
    ): RepositoryResult<Message> = mutationMutex.withLock {
        val key = ConversationKey(command.accountId, command.conversationId)
        val currentMessages = messages.value[key].orEmpty()
        currentMessages
            .firstOrNull { it.clientOperationId == command.clientOperationId }
            ?.let { return@withLock RepositoryResult.Success(it) }

        val conversationExists = conversations.value[command.accountId]
            .orEmpty()
            .any { it.id == command.conversationId }
        if (!conversationExists) {
            return@withLock RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.NotFound,
                    message = "Conversation does not exist",
                    retryable = false,
                ),
            )
        }

        val sentAt = clock()
        val sent = Message(
            id = command.messageId,
            conversationId = command.conversationId,
            senderId = command.accountId,
            clientOperationId = command.clientOperationId,
            body = command.body,
            createdAt = sentAt,
        )
        messages.update { it + (key to (currentMessages + sent)) }
        conversations.update { allAccounts ->
            val updated = allAccounts[command.accountId].orEmpty().map { conversation ->
                if (conversation.id == command.conversationId) {
                    conversation.copy(
                        latestMessagePreview = conversation.previewFor(sent, command.accountId),
                        latestMessageAt = sentAt,
                    )
                } else {
                    conversation
                }
            }.sortedWith(
                compareByDescending<ConversationSummary> { it.latestMessageAt }
                    .thenBy { it.id.value },
            )
            allAccounts + (command.accountId to updated)
        }

        RepositoryResult.Success(sent)
    }

    override suspend fun editMessage(
        command: EditMessageCommand,
    ): RepositoryResult<Message> = mutateOwnMessage(
        accountId = command.accountId,
        conversationId = command.conversationId,
        messageId = command.messageId,
    ) { message -> message.copy(body = command.body, editedAt = clock()) }

    override suspend fun deleteMessage(
        command: DeleteMessageCommand,
    ): RepositoryResult<Message> = mutateOwnMessage(
        accountId = command.accountId,
        conversationId = command.conversationId,
        messageId = command.messageId,
    ) { message -> message.copy(deletedAt = clock()) }

    private suspend fun mutateOwnMessage(
        accountId: UserId,
        conversationId: ConversationId,
        messageId: MessageId,
        transform: (Message) -> Message,
    ): RepositoryResult<Message> = mutationMutex.withLock {
        val key = ConversationKey(accountId, conversationId)
        val currentMessages = messages.value[key].orEmpty()
        val existing = currentMessages.firstOrNull { it.id == messageId }
            ?: return@withLock RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.NotFound,
                    message = "Message does not exist",
                    retryable = false,
                ),
            )
        if (
            existing.senderId != accountId ||
            existing.deletedAt != null ||
            existing.pendingState != PendingState.Synced
        ) {
            return@withLock RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.Conflict,
                    message = "Only your delivered messages can be changed",
                    retryable = false,
                ),
            )
        }

        val updated = transform(existing)
        val updatedMessages = currentMessages.map { if (it.id == messageId) updated else it }
        messages.update { it + (key to updatedMessages) }
        val latest = updatedMessages
            .filter { it.deletedAt == null }
            .maxByOrNull(Message::createdAt)
        conversations.update { allAccounts ->
            val rows = allAccounts[accountId].orEmpty().map { conversation ->
                if (conversation.id == conversationId) {
                    conversation.copy(
                        latestMessagePreview = latest?.let { conversation.previewFor(it, accountId) }.orEmpty(),
                        latestMessageAt = latest?.createdAt ?: conversation.latestMessageAt,
                    )
                } else {
                    conversation
                }
            }.sortedWith(
                compareByDescending<ConversationSummary> { it.latestMessageAt }
                    .thenBy { it.id.value },
            )
            allAccounts + (accountId to rows)
        }

        RepositoryResult.Success(updated)
    }

    override suspend fun markConversationRead(
        command: MarkConversationReadCommand,
    ): RepositoryResult<Unit> = mutationMutex.withLock {
        val accountConversations = conversations.value[command.accountId].orEmpty()
        if (accountConversations.none { it.id == command.conversationId }) {
            return@withLock RepositoryResult.Failure(
                RepositoryFailure(
                    kind = RepositoryFailureKind.NotFound,
                    message = "Conversation does not exist",
                    retryable = false,
                ),
            )
        }
        conversations.update { allAccounts ->
            allAccounts + (
                command.accountId to accountConversations.map { conversation ->
                    if (conversation.id == command.conversationId) {
                        conversation.copy(unreadCount = 0)
                    } else {
                        conversation
                    }
                }
            )
        }
        RepositoryResult.Success(Unit)
    }
}

private fun ConversationSummary.previewFor(message: Message, accountId: UserId): String =
    if (isGroup) {
        groupMessagePreview(
            body = message.body,
            senderId = message.senderId,
            accountId = accountId,
            members = members,
        )
    } else {
        message.body
    }

class FixtureSyncRepository(
    private val clock: () -> Instant = Clock.System::now,
) : SyncRepository {
    private val mutableSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: StateFlow<SyncState> = mutableSyncState.asStateFlow()

    override suspend fun synchronize(accountId: UserId): RepositoryResult<Unit> {
        mutableSyncState.value = SyncState.Running(clock())
        mutableSyncState.value = SyncState.Succeeded(clock())
        return RepositoryResult.Success(Unit)
    }
}

class FixtureShopRepository(
    catalog: List<ShopCategory> = FixtureData.shopCatalog,
    balance: Int = FixtureData.tokenBalance,
    owned: List<OwnedShopItem> = FixtureData.ownedShopItems,
    supporterUntil: Instant? = null,
    private val now: () -> Instant = Clock.System::now,
) : ShopRepository {
    private val categories = MutableStateFlow(catalog)
    private val tokens = MutableStateFlow<Int?>(balance)
    private val ownedItems = MutableStateFlow(owned)
    private val supporter = MutableStateFlow(supporterUntil)

    override fun observeCatalog(): Flow<List<ShopCategory>> = categories

    override fun observeTokenBalance(accountId: UserId): Flow<Int?> = tokens

    override fun observeOwnedItems(accountId: UserId): Flow<List<OwnedShopItem>> = ownedItems

    override fun observeOwnedHatTypes(accountId: UserId): Flow<Set<Int>> =
        combine(categories, ownedItems, supporter) { catalog, owned, until ->
            val ownedIds = owned.filterNot(OwnedShopItem::pending).map(OwnedShopItem::itemId).toSet()
            val active = until != null && until > now()
            catalog.flatMap(ShopCategory::items)
                .filter { active || it.id in ownedIds }
                .mapNotNull(ShopItem::miiHatType)
                .toSet()
        }

    override fun observeSupporterUntil(accountId: UserId): Flow<Instant?> = supporter

    override suspend fun refresh(accountId: UserId): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun refreshTokenBalance(accountId: UserId): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun refreshOwnedItems(accountId: UserId): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun refreshSupporterStatus(accountId: UserId): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun purchase(command: PurchaseShopItemCommand): RepositoryResult<Unit> {
        val balance = tokens.value ?: 0
        if (ownedItems.value.any { it.itemId == command.itemId }) {
            return conflict("Item is already owned")
        }
        if (balance < command.priceTokens) {
            return conflict("Not enough tokens")
        }
        tokens.value = balance - command.priceTokens
        ownedItems.value = ownedItems.value + OwnedShopItem(
            itemId = command.itemId,
            purchasedAt = command.requestedAt,
            pricePaid = command.priceTokens,
            pending = false,
        )
        return RepositoryResult.Success(Unit)
    }

    private fun conflict(reason: String): RepositoryResult<Unit> = RepositoryResult.Failure(
        RepositoryFailure(
            kind = RepositoryFailureKind.Conflict,
            message = reason,
            retryable = false,
        ),
    )
}

class FixtureLeaderboardRepository(
    initial: List<LeaderboardEntry> = FixtureData.leaderboard,
) : LeaderboardRepository {
    private val entries = MutableStateFlow(initial)

    override fun observeLeaderboard(
        accountId: UserId,
        scope: LeaderboardScope,
    ): Flow<List<LeaderboardEntry>> = entries

    override suspend fun refresh(
        accountId: UserId,
        scope: LeaderboardScope,
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
}

class FixtureAchievementsRepository(
    initial: List<AchievementState> = FixtureData.achievements,
) : AchievementsRepository {
    private val states = MutableStateFlow(initial)

    override fun observeAchievements(accountId: UserId): Flow<List<AchievementState>> = states

    override suspend fun refresh(accountId: UserId): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)
}

class FixtureWorldTourRepository(
    initial: List<WorldTourRegion> = FixtureData.worldTourRegions,
) : WorldTourRepository {
    private val regions = MutableStateFlow(initial)

    override fun observeRegions(accountId: UserId): Flow<List<WorldTourRegion>> = regions

    override suspend fun refresh(accountId: UserId): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)
}

class FixtureBingoRepository(
    initial: List<BingoCell> = FixtureData.bingoBoard,
) : BingoRepository {
    private val cells = MutableStateFlow(initial)

    override fun observeBoard(accountId: UserId): Flow<List<BingoCell>> = cells

    override suspend fun refresh(accountId: UserId): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)
}

class FixtureEncounterRepository(
    initial: List<NearbyEncounter> = FixtureData.encounters,
) : EncounterRepository {
    private val encounters = MutableStateFlow(initial)

    override fun observeRecent(accountId: UserId): Flow<List<NearbyEncounter>> = encounters

    override suspend fun refresh(accountId: UserId): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)
}

class FixtureEncounterRemoteDataSource : EncounterRemoteDataSource {
    override suspend fun issueCredentials(
        accountId: UserId,
        signingPublicKeys: List<String>,
    ): RepositoryResult<List<IssuedNearbyCredential>> =
        RepositoryResult.Success(
            signingPublicKeys.map { publicKey ->
                IssuedNearbyCredential(
                    token = Random.nextBytes(16)
                        .joinToString("") { byte ->
                            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
                        },
                    signingPublicKey = publicKey,
                    expiresAt = Clock.System.now().plus((7 * 24 * 60 * 60).seconds),
                )
            },
        )

    override suspend fun fetchEncounters(
        accountId: UserId,
    ): RepositoryResult<List<NearbyEncounter>> =
        RepositoryResult.Success(FixtureData.encounters)

    override suspend fun submitEncounter(
        command: SubmitNearbyEncounterCommand,
    ): RepositoryResult<NearbyEncounter> = RepositoryResult.Failure(
        RepositoryFailure(
            kind = RepositoryFailureKind.Unavailable,
            message = "Fixture encounters cannot be resolved by the server",
        ),
    )
}

class FixturePresenceRepository(
    initial: Map<UserId, PresenceStatus> = FixtureData.friends.associate {
        it.profile.userId to it.profile.presence
    },
) : PresenceRepository {
    private val presence = MutableStateFlow(initial)

    override fun observePresence(
        userIds: Set<UserId>,
    ): Flow<Map<UserId, PresenceStatus>> = presence.map { current ->
        current.filterKeys { it in userIds }
    }

    override suspend fun setLocalPresence(
        accountId: UserId,
        status: PresenceStatus,
    ): RepositoryResult<Unit> {
        presence.update { it + (accountId to status) }
        return RepositoryResult.Success(Unit)
    }
}

data class FixtureRepositoryBundle(
    val session: SessionRepository = FixtureSessionRepository(),
    val profiles: ProfileRepository = FixtureProfileRepository(),
    val friends: MutableFriendsRepository = FixtureFriendsRepository(),
    val notifications: NotificationRepository = FixtureNotificationRepository(),
    val messages: MessageRepository = FixtureMessageRepository(),
    val shop: ShopRepository = FixtureShopRepository(),
    val leaderboard: LeaderboardRepository = FixtureLeaderboardRepository(),
    val achievements: AchievementsRepository = FixtureAchievementsRepository(),
    val worldTour: WorldTourRepository = FixtureWorldTourRepository(),
    val bingo: BingoRepository = FixtureBingoRepository(),
    val encounters: EncounterRepository = FixtureEncounterRepository(),
    val presence: PresenceRepository = FixturePresenceRepository(),
    val sync: SyncRepository = FixtureSyncRepository(),
)
