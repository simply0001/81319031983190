package com.pocketpass.app.domain.repository

import com.pocketpass.app.domain.model.AchievementState
import com.pocketpass.app.domain.model.BingoCell
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.FriendCode
import com.pocketpass.app.domain.model.LeaderboardEntry
import com.pocketpass.app.domain.model.LeaderboardScope
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.domain.model.MarkConversationReadCommand
import com.pocketpass.app.domain.model.MarkAllNotificationsReadCommand
import com.pocketpass.app.domain.model.MarkNotificationReadCommand
import com.pocketpass.app.domain.model.DeleteMessageCommand
import com.pocketpass.app.domain.model.DeleteNotificationCommand
import com.pocketpass.app.domain.model.EditMessageCommand
import com.pocketpass.app.domain.model.AddGroupMembersCommand
import com.pocketpass.app.domain.model.CreateGroupConversationCommand
import com.pocketpass.app.domain.model.LeaveGroupConversationCommand
import com.pocketpass.app.domain.model.RemoveGroupMemberCommand
import com.pocketpass.app.domain.model.RenameGroupConversationCommand
import com.pocketpass.app.domain.model.PocketPassNotification
import com.pocketpass.app.domain.model.PresenceStatus
import com.pocketpass.app.domain.model.SendMessageCommand
import com.pocketpass.app.domain.model.OwnedShopItem
import com.pocketpass.app.domain.model.PurchaseShopItemCommand
import com.pocketpass.app.domain.model.ShopCategory
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.model.WorldTourRegion
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.domain.state.SyncState
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

interface SessionRepository {
    val sessionState: StateFlow<SessionState>

    suspend fun initialize(): RepositoryResult<SessionState>

    suspend fun handleAuthCallback(callbackUri: String): RepositoryResult<SessionState>

    suspend fun signInWithDiscord(): RepositoryResult<Unit>

    suspend fun requestEmailOtp(
        email: String,
        createUser: Boolean = true,
    ): RepositoryResult<Unit>

    suspend fun verifyEmailOtp(
        email: String,
        sixDigitCode: String,
    ): RepositoryResult<SessionState>

    suspend fun signOut(): RepositoryResult<Unit>
}

fun interface AccountDeleter {
    suspend fun deleteAccount(accountId: UserId): RepositoryResult<Unit>
}

interface FriendProfileStatsSource {
    suspend fun fetchFriendProfileStats(
        friendUserId: UserId,
    ): RepositoryResult<com.pocketpass.app.domain.model.FriendProfileStats>

    suspend fun openDirectConversation(
        friendUserId: UserId,
        clientOperationId: com.pocketpass.app.domain.model.ClientOperationId,
    ): RepositoryResult<ConversationId>
}

interface ConnectedAppsSource {
    suspend fun fetchConnectedApps(): RepositoryResult<List<com.pocketpass.app.domain.model.ConnectedApp>>

    suspend fun revokeConnectedApp(clientId: String): RepositoryResult<Boolean>

    suspend fun fetchOAuthConsent(
        authorizationId: String,
    ): RepositoryResult<com.pocketpass.app.domain.model.OAuthConsentRequest>

    suspend fun decideOAuthConsent(
        authorizationId: String,
        approve: Boolean,
    ): RepositoryResult<String>
}

interface ProfileRepository {
    fun observeProfile(userId: UserId): Flow<UserProfile?>

    suspend fun refreshProfile(userId: UserId): RepositoryResult<Unit>
}

interface FriendsRepository {
    fun observeFriends(accountId: UserId): Flow<List<Friend>>

    fun observeMyFriendCode(accountId: UserId): Flow<FriendCode?> =
        kotlinx.coroutines.flow.flowOf(null)

    suspend fun refreshFriends(accountId: UserId): RepositoryResult<Unit>

    suspend fun refreshMyFriendCode(accountId: UserId): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    suspend fun resolveFriendCode(
        accountId: UserId,
        friendCode: FriendCode,
    ): RepositoryResult<UserProfile> = RepositoryResult.Failure(
        com.pocketpass.app.domain.state.RepositoryFailure(
            kind = com.pocketpass.app.domain.state.RepositoryFailureKind.NotFound,
            message = "No PocketPass user is available with that code",
            retryable = false,
        ),
    )
}

interface NotificationRepository {
    fun observeNotifications(accountId: UserId): Flow<List<PocketPassNotification>>

    suspend fun refreshNotifications(accountId: UserId): RepositoryResult<Unit>

    suspend fun markRead(
        command: MarkNotificationReadCommand,
    ): RepositoryResult<Unit>

    suspend fun markAllRead(
        command: MarkAllNotificationsReadCommand,
    ): RepositoryResult<Unit>

    suspend fun delete(
        command: DeleteNotificationCommand,
    ): RepositoryResult<Unit>

    suspend fun recordFriendRequestResponse(
        accountId: UserId,
        requestId: String,
        accepted: Boolean,
        respondedAt: Instant,
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)
}

interface MessageRepository {
    fun observeConversations(accountId: UserId): Flow<List<ConversationSummary>>

    fun observeMessages(
        accountId: UserId,
        conversationId: ConversationId,
    ): Flow<List<Message>>

    suspend fun refreshConversations(accountId: UserId): RepositoryResult<Unit>

    suspend fun refreshMessages(
        accountId: UserId,
        conversationId: ConversationId,
    ): RepositoryResult<Unit>

    suspend fun sendMessage(command: SendMessageCommand): RepositoryResult<Message>

    suspend fun editMessage(command: EditMessageCommand): RepositoryResult<Message>

    suspend fun deleteMessage(command: DeleteMessageCommand): RepositoryResult<Message>

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

fun <T> groupChatsUnavailable(): RepositoryResult<T> = RepositoryResult.Failure(
    com.pocketpass.app.domain.state.RepositoryFailure(
        kind = com.pocketpass.app.domain.state.RepositoryFailureKind.Unavailable,
        message = "Group chats are unavailable",
        retryable = false,
    ),
)

interface ShopRepository {
    fun observeCatalog(): Flow<List<ShopCategory>>

    fun observeTokenBalance(accountId: UserId): Flow<Int?>

    fun observeOwnedItems(accountId: UserId): Flow<List<OwnedShopItem>>

    fun observeOwnedHatTypes(accountId: UserId): Flow<Set<Int>>

    fun observeSupporterUntil(accountId: UserId): Flow<Instant?>

    suspend fun refresh(accountId: UserId): RepositoryResult<Unit>

    suspend fun refreshTokenBalance(accountId: UserId): RepositoryResult<Unit>

    suspend fun refreshOwnedItems(accountId: UserId): RepositoryResult<Unit>

    suspend fun refreshSupporterStatus(accountId: UserId): RepositoryResult<Unit>

    suspend fun purchase(command: PurchaseShopItemCommand): RepositoryResult<Unit>
}

interface LeaderboardRepository {
    fun observeLeaderboard(
        accountId: UserId,
        scope: LeaderboardScope,
    ): Flow<List<LeaderboardEntry>>

    suspend fun refresh(
        accountId: UserId,
        scope: LeaderboardScope,
    ): RepositoryResult<Unit>
}

interface AchievementsRepository {
    fun observeAchievements(accountId: UserId): Flow<List<AchievementState>>

    suspend fun refresh(accountId: UserId): RepositoryResult<Unit>
}

interface WorldTourRepository {
    fun observeRegions(accountId: UserId): Flow<List<WorldTourRegion>>

    suspend fun refresh(accountId: UserId): RepositoryResult<Unit>
}

interface BingoRepository {
    fun observeBoard(accountId: UserId): Flow<List<BingoCell>>

    suspend fun refresh(accountId: UserId): RepositoryResult<Unit>
}

interface EncounterRepository {
    fun observeRecent(accountId: UserId): Flow<List<NearbyEncounter>>

    suspend fun refresh(accountId: UserId): RepositoryResult<Unit>
}

interface SyncRepository {
    val syncState: StateFlow<SyncState>

    suspend fun synchronize(accountId: UserId): RepositoryResult<Unit>
}

interface PresenceRepository {
    fun observePresence(
        userIds: Set<UserId>,
    ): Flow<Map<UserId, PresenceStatus>>

    fun observeTypingConversations(): Flow<Map<ConversationId, Set<UserId>>> =
        flowOf(emptyMap())

    suspend fun setLocalPresence(
        accountId: UserId,
        status: PresenceStatus,
    ): RepositoryResult<Unit>
}

interface SyncCoordinator {
    val syncState: StateFlow<SyncState>

    fun schedule(accountId: UserId)

    suspend fun reconcile(accountId: UserId): RepositoryResult<Unit>

    fun cancel(accountId: UserId)
}
