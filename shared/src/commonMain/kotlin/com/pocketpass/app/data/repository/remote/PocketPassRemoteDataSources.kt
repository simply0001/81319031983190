package com.pocketpass.app.data.repository.remote

import com.pocketpass.app.domain.model.AchievementState
import com.pocketpass.app.domain.model.BingoCell
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.WorldTourRegion
import com.pocketpass.app.domain.model.PassingStats
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.FriendCode
import com.pocketpass.app.domain.model.DeleteMessageCommand
import com.pocketpass.app.domain.model.DeleteNotificationCommand
import com.pocketpass.app.domain.model.EditMessageCommand
import com.pocketpass.app.domain.model.MarkAllNotificationsReadCommand
import com.pocketpass.app.domain.model.MarkNotificationReadCommand
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.MarkConversationReadCommand
import com.pocketpass.app.domain.model.RemoveFriendCommand
import com.pocketpass.app.domain.model.RespondToFriendRequestCommand
import com.pocketpass.app.domain.model.SendFriendRequestCommand
import com.pocketpass.app.domain.model.SendMessageCommand
import com.pocketpass.app.domain.model.AddGroupMembersCommand
import com.pocketpass.app.domain.model.CreateGroupConversationCommand
import com.pocketpass.app.domain.model.LeaveGroupConversationCommand
import com.pocketpass.app.domain.model.RemoveGroupMemberCommand
import com.pocketpass.app.domain.model.RenameGroupConversationCommand
import com.pocketpass.app.domain.repository.groupChatsUnavailable
import com.pocketpass.app.domain.model.SetUserBlockCommand
import com.pocketpass.app.domain.model.SetProfileAgeCommand
import com.pocketpass.app.domain.model.SetProfileCountryCommand
import com.pocketpass.app.domain.model.PocketPassNotification
import com.pocketpass.app.domain.model.AccountSetupCommand
import com.pocketpass.app.domain.model.UpdateProfileCommand
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.model.IssuedNearbyCredential
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.domain.model.LeaderboardEntry
import com.pocketpass.app.domain.model.LeaderboardScope
import com.pocketpass.app.domain.model.OwnedShopItem
import com.pocketpass.app.domain.model.PurchaseShopItemCommand
import com.pocketpass.app.domain.model.RenameProfileCommand
import com.pocketpass.app.domain.model.ShopCategory
import com.pocketpass.app.domain.model.ShopPurchaseOutcome
import com.pocketpass.app.domain.model.SubmitNearbyEncounterCommand
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import kotlin.time.Instant
import com.pocketpass.app.domain.model.BuyPuzzlePieceCommand
import com.pocketpass.app.domain.model.PuzzleCollection
import com.pocketpass.app.domain.model.PuzzlePiecePurchaseOutcome

interface ProfileRemoteDataSource {
    suspend fun setInvitesPrivacy(command: com.pocketpass.app.domain.model.SetInvitesPrivacyCommand): RepositoryResult<UserProfile> =
        RepositoryResult.Failure(RepositoryFailure(RepositoryFailureKind.NotFound, "Invitation privacy is unavailable", retryable = false))
    suspend fun setMessagePrivacy(command: com.pocketpass.app.domain.model.SetMessagePrivacyCommand): RepositoryResult<UserProfile> =
        RepositoryResult.Failure(RepositoryFailure(RepositoryFailureKind.NotFound, "Message privacy is unavailable", retryable = false))

    suspend fun fetchAuthorProfiles(userIds: Set<UserId>): RepositoryResult<List<UserProfile>> {
        val profiles = mutableListOf<UserProfile>()
        for (id in userIds) when (val result = fetchProfile(id)) {
            is RepositoryResult.Failure -> return result
            is RepositoryResult.Success -> result.value?.let(profiles::add)
        }
        return RepositoryResult.Success(profiles)
    }

    suspend fun setChatBubbleColour(command: com.pocketpass.app.domain.model.SetChatBubbleColourCommand): RepositoryResult<UserProfile> =
        RepositoryResult.Failure(RepositoryFailure(RepositoryFailureKind.NotFound, "Chat colours are unavailable", retryable = false))

    suspend fun fetchProfile(userId: UserId): RepositoryResult<UserProfile?>

    suspend fun updateProfile(
        command: UpdateProfileCommand,
    ): RepositoryResult<UserProfile>

    suspend fun completeAccountSetup(
        command: AccountSetupCommand,
    ): RepositoryResult<UserProfile>

    suspend fun renameProfile(
        command: RenameProfileCommand,
    ): RepositoryResult<UserProfile>

    suspend fun setProfileAge(command: SetProfileAgeCommand): RepositoryResult<UserProfile> =
        RepositoryResult.Failure(RepositoryFailure(RepositoryFailureKind.NotFound, "Profile details are unavailable", retryable = false))

    suspend fun setProfileCountry(command: SetProfileCountryCommand): RepositoryResult<UserProfile> =
        RepositoryResult.Failure(RepositoryFailure(RepositoryFailureKind.NotFound, "Profile details are unavailable", retryable = false))

    suspend fun touchLastSeen(): RepositoryResult<Instant>
}

interface FriendsRemoteDataSource {
    suspend fun fetchFriends(accountId: UserId): RepositoryResult<List<Friend>>

    suspend fun fetchMyFriendCode(accountId: UserId): RepositoryResult<FriendCode> =
        RepositoryResult.Failure(
            com.pocketpass.app.domain.state.RepositoryFailure(
                kind = com.pocketpass.app.domain.state.RepositoryFailureKind.NotFound,
                message = "Friend code is unavailable",
                retryable = false,
            ),
        )

    suspend fun resolveFriendCode(
        accountId: UserId,
        friendCode: FriendCode,
    ): RepositoryResult<UserProfile> = RepositoryResult.Failure(
        com.pocketpass.app.domain.state.RepositoryFailure(
            kind = com.pocketpass.app.domain.state.RepositoryFailureKind.NotFound,
            message = "Friend code is unavailable",
            retryable = false,
        ),
    )

    suspend fun sendFriendRequest(
        command: SendFriendRequestCommand,
    ): RepositoryResult<Unit>

    suspend fun respondToFriendRequest(
        command: RespondToFriendRequestCommand,
    ): RepositoryResult<Unit>

    suspend fun removeFriend(
        command: RemoveFriendCommand,
    ): RepositoryResult<Unit>

    suspend fun setUserBlocked(
        command: SetUserBlockCommand,
    ): RepositoryResult<Unit>
}

interface NotificationRemoteDataSource {
    suspend fun fetchNotifications(
        accountId: UserId,
    ): RepositoryResult<List<PocketPassNotification>>

    suspend fun markNotificationRead(
        command: MarkNotificationReadCommand,
    ): RepositoryResult<Unit>

    suspend fun markAllNotificationsRead(
        command: MarkAllNotificationsReadCommand,
    ): RepositoryResult<Unit>

    suspend fun deleteNotification(
        command: DeleteNotificationCommand,
    ): RepositoryResult<Unit>
}

interface MessageRemoteDataSource {
    suspend fun fetchConversations(
        accountId: UserId,
    ): RepositoryResult<List<ConversationSummary>>

    suspend fun fetchMessages(
        accountId: UserId,
        conversationId: ConversationId,
    ): RepositoryResult<List<Message>>

    suspend fun fetchMessageChanges(
        accountId: UserId,
        conversationId: ConversationId,
        changedAfter: Instant,
    ): RepositoryResult<List<Message>> = fetchMessages(accountId, conversationId)

    suspend fun sendMessage(
        command: SendMessageCommand,
    ): RepositoryResult<Message>

    suspend fun editMessage(
        command: EditMessageCommand,
    ): RepositoryResult<Message>

    suspend fun deleteMessage(
        command: DeleteMessageCommand,
    ): RepositoryResult<Message>

    suspend fun markConversationRead(
        command: MarkConversationReadCommand,
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    suspend fun createGroupConversation(
        command: CreateGroupConversationCommand,
    ): RepositoryResult<ConversationId> = groupChatsUnavailable()

    suspend fun addGroupMembers(
        command: AddGroupMembersCommand,
    ): RepositoryResult<Unit> = groupChatsUnavailable()

    suspend fun removeGroupMember(
        command: RemoveGroupMemberCommand,
    ): RepositoryResult<Unit> = groupChatsUnavailable()

    suspend fun leaveGroupConversation(
        command: LeaveGroupConversationCommand,
    ): RepositoryResult<Unit> = groupChatsUnavailable()

    suspend fun renameGroupConversation(
        command: RenameGroupConversationCommand,
    ): RepositoryResult<Unit> = groupChatsUnavailable()
}

interface EncounterRemoteDataSource {
    suspend fun issueCredentials(
        accountId: UserId,
        signingPublicKeys: List<String>,
    ): RepositoryResult<List<IssuedNearbyCredential>>

    suspend fun fetchDeviceTagSecret(
        accountId: UserId,
    ): RepositoryResult<ByteArray>

    suspend fun fetchEncounters(
        accountId: UserId,
    ): RepositoryResult<List<NearbyEncounter>>

    suspend fun submitEncounter(
        command: SubmitNearbyEncounterCommand,
    ): RepositoryResult<NearbyEncounter>
}

interface ShopRemoteDataSource {
    suspend fun fetchCatalog(): RepositoryResult<List<ShopCategory>>

    suspend fun fetchTokenBalance(accountId: UserId): RepositoryResult<Int>

    suspend fun fetchOwnedItems(accountId: UserId): RepositoryResult<List<OwnedShopItem>>

    suspend fun fetchSupporterStatus(accountId: UserId): RepositoryResult<Instant?>

    suspend fun purchaseItem(
        command: PurchaseShopItemCommand,
    ): RepositoryResult<ShopPurchaseOutcome>
}

interface LeaderboardRemoteDataSource {
    suspend fun fetchLeaderboard(
        accountId: UserId,
        scope: LeaderboardScope,
    ): RepositoryResult<List<LeaderboardEntry>>
}

interface AchievementsRemoteDataSource {
    suspend fun fetchAchievements(
        accountId: UserId,
    ): RepositoryResult<List<AchievementState>>
}

interface WorldTourRemoteDataSource {
    suspend fun fetchRegions(
        accountId: UserId,
    ): RepositoryResult<List<WorldTourRegion>>
}

interface PassingStatsRemoteDataSource {
    suspend fun fetchStats(
        accountId: UserId,
        utcOffsetMinutes: Int,
    ): RepositoryResult<PassingStats>
}

interface BingoRemoteDataSource {
    suspend fun fetchBoard(
        accountId: UserId,
    ): RepositoryResult<List<BingoCell>>
}

interface PuzzleRemoteDataSource {
    suspend fun fetchCollection(
        accountId: UserId,
    ): RepositoryResult<PuzzleCollection>

    suspend fun buyPiece(
        command: BuyPuzzlePieceCommand,
    ): RepositoryResult<PuzzlePiecePurchaseOutcome>

    suspend fun downloadPanel(imagePath: String): RepositoryResult<ByteArray>
}

object EmptyPuzzleRemoteDataSource : PuzzleRemoteDataSource {
    override suspend fun fetchCollection(
        accountId: UserId,
    ): RepositoryResult<PuzzleCollection> = RepositoryResult.Success(PuzzleCollection.Empty)

    override suspend fun buyPiece(
        command: BuyPuzzlePieceCommand,
    ): RepositoryResult<PuzzlePiecePurchaseOutcome> = RepositoryResult.Failure(
        RepositoryFailure(
            kind = RepositoryFailureKind.Unavailable,
            message = "Puzzle pieces are unavailable",
            retryable = false,
        ),
    )

    override suspend fun downloadPanel(imagePath: String): RepositoryResult<ByteArray> =
        RepositoryResult.Failure(
            RepositoryFailure(
                kind = RepositoryFailureKind.Unavailable,
                message = "Puzzle artwork is unavailable",
                retryable = false,
            ),
        )
}

data class ProductionRemoteDataSources(
    val profiles: ProfileRemoteDataSource,
    val friends: FriendsRemoteDataSource,
    val messages: MessageRemoteDataSource,
    val notifications: NotificationRemoteDataSource = EmptyNotificationRemoteDataSource,
    val encounters: EncounterRemoteDataSource = EmptyEncounterRemoteDataSource,
    val shop: ShopRemoteDataSource = EmptyShopRemoteDataSource,
    val leaderboard: LeaderboardRemoteDataSource = EmptyLeaderboardRemoteDataSource,
    val achievements: AchievementsRemoteDataSource = EmptyAchievementsRemoteDataSource,
    val worldTour: WorldTourRemoteDataSource = EmptyWorldTourRemoteDataSource,
    val bingo: BingoRemoteDataSource = EmptyBingoRemoteDataSource,
    val puzzle: PuzzleRemoteDataSource = EmptyPuzzleRemoteDataSource,
    val passingStats: PassingStatsRemoteDataSource = EmptyPassingStatsRemoteDataSource,
    val stepRewards: StepRewardsRemoteDataSource = EmptyStepRewardsRemoteDataSource,
)

object EmptyShopRemoteDataSource : ShopRemoteDataSource {
    override suspend fun fetchCatalog(): RepositoryResult<List<ShopCategory>> =
        RepositoryResult.Success(emptyList())

    override suspend fun fetchTokenBalance(accountId: UserId): RepositoryResult<Int> =
        RepositoryResult.Success(0)

    override suspend fun fetchOwnedItems(
        accountId: UserId,
    ): RepositoryResult<List<OwnedShopItem>> = RepositoryResult.Success(emptyList())

    override suspend fun fetchSupporterStatus(accountId: UserId): RepositoryResult<Instant?> =
        RepositoryResult.Success(null)

    override suspend fun purchaseItem(
        command: PurchaseShopItemCommand,
    ): RepositoryResult<ShopPurchaseOutcome> = RepositoryResult.Failure(
        RepositoryFailure(
            kind = RepositoryFailureKind.Unavailable,
            message = "Shop purchases are unavailable",
            retryable = false,
        ),
    )
}

object EmptyLeaderboardRemoteDataSource : LeaderboardRemoteDataSource {
    override suspend fun fetchLeaderboard(
        accountId: UserId,
        scope: LeaderboardScope,
    ): RepositoryResult<List<LeaderboardEntry>> = RepositoryResult.Success(emptyList())
}

object EmptyAchievementsRemoteDataSource : AchievementsRemoteDataSource {
    override suspend fun fetchAchievements(
        accountId: UserId,
    ): RepositoryResult<List<AchievementState>> = RepositoryResult.Success(emptyList())
}

object EmptyWorldTourRemoteDataSource : WorldTourRemoteDataSource {
    override suspend fun fetchRegions(
        accountId: UserId,
    ): RepositoryResult<List<WorldTourRegion>> = RepositoryResult.Success(emptyList())
}

object EmptyPassingStatsRemoteDataSource : PassingStatsRemoteDataSource {
    override suspend fun fetchStats(
        accountId: UserId,
        utcOffsetMinutes: Int,
    ): RepositoryResult<PassingStats> = RepositoryResult.Failure(
        RepositoryFailure(
            kind = RepositoryFailureKind.Unavailable,
            message = "Passing stats are unavailable",
        ),
    )
}

object EmptyBingoRemoteDataSource : BingoRemoteDataSource {
    override suspend fun fetchBoard(
        accountId: UserId,
    ): RepositoryResult<List<BingoCell>> = RepositoryResult.Success(emptyList())
}

object EmptyEncounterRemoteDataSource : EncounterRemoteDataSource {
    override suspend fun issueCredentials(
        accountId: UserId,
        signingPublicKeys: List<String>,
    ): RepositoryResult<List<IssuedNearbyCredential>> = RepositoryResult.Failure(
        com.pocketpass.app.domain.state.RepositoryFailure(
            kind = com.pocketpass.app.domain.state.RepositoryFailureKind.Unavailable,
            message = "Nearby credential service is unavailable",
        ),
    )

    override suspend fun fetchDeviceTagSecret(
        accountId: UserId,
    ): RepositoryResult<ByteArray> = RepositoryResult.Failure(
        com.pocketpass.app.domain.state.RepositoryFailure(
            kind = com.pocketpass.app.domain.state.RepositoryFailureKind.Unavailable,
            message = "Nearby device tag service is unavailable",
        ),
    )

    override suspend fun fetchEncounters(
        accountId: UserId,
    ): RepositoryResult<List<NearbyEncounter>> = RepositoryResult.Success(emptyList())

    override suspend fun submitEncounter(
        command: SubmitNearbyEncounterCommand,
    ): RepositoryResult<NearbyEncounter> = RepositoryResult.Failure(
        com.pocketpass.app.domain.state.RepositoryFailure(
            kind = com.pocketpass.app.domain.state.RepositoryFailureKind.Unavailable,
            message = "Nearby encounter service is unavailable",
        ),
    )
}

object EmptyNotificationRemoteDataSource : NotificationRemoteDataSource {
    override suspend fun fetchNotifications(
        accountId: UserId,
    ): RepositoryResult<List<PocketPassNotification>> =
        RepositoryResult.Success(emptyList())

    override suspend fun markNotificationRead(
        command: MarkNotificationReadCommand,
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    override suspend fun markAllNotificationsRead(
        command: MarkAllNotificationsReadCommand,
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    override suspend fun deleteNotification(
        command: DeleteNotificationCommand,
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
}
