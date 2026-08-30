package com.pocketpass.app.model

import androidx.annotation.RawRes
import androidx.navigation3.runtime.NavKey
import com.pocketpass.app.auth.AuthEvent
import com.pocketpass.app.auth.AuthUiState
import com.pocketpass.app.domain.model.ActivitySnapshot
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.ConversationMember
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.FORMER_MEMBER_LABEL
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.MAX_GROUP_MEMBERS
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.model.isValidProfileName
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.domain.state.SyncState
import com.pocketpass.app.feature.AccountSetupEvent
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.mii.MiiEditorEvent
import com.pocketpass.app.mii.MiiEditorUiState
import com.pocketpass.app.nearby.NearbyPermissionUiState
import com.pocketpass.app.nearby.NearbyRuntimeState
import com.pocketpass.app.update.AppUpdateUiState
import kotlinx.serialization.Serializable

@Serializable
enum class PocketPassDestination {
    Messages,
    Friends,
    Home,
    Activities,
    Settings,
}

@Serializable
sealed interface PocketPassRoute : NavKey {
    @Serializable
    data class Root(val destination: PocketPassDestination) : PocketPassRoute

    @Serializable
    data class MessageDetail(val conversationId: String) : PocketPassRoute

    @Serializable
    data object NewGroup : PocketPassRoute

    @Serializable
    data object Accessibility : PocketPassRoute

    @Serializable
    data object Social : PocketPassRoute

    @Serializable
    data object NotificationSettings : PocketPassRoute

    @Serializable
    data object AppUpdate : PocketPassRoute
}

enum class ActivityVariant {
    Default,
    Shuffled,
}

enum class HomeMood {
    Happy,
    Sad,
    Neutral,
    Party,
    Playful,
    Cool,
}

enum class ThemeMode {
    Light,
    System,
    Dark,
}

enum class RecentInteractionsSort(val key: String) {
    LatestEncounter("latest"),
    OldestEncounter("oldest"),
    NameAZ("name"),
}

enum class MessageComposerAction {
    Emoji,
    Image,
    File,
}

enum class FriendsOverlay {
    None,
    AddFriend,
    Notifications,
}

enum class ShopItemStatus {
    Available,
    Unaffordable,
    Purchasing,
    Owned,
    Unlocked,
}

data class ShopUiState(
    val visible: Boolean = false,
    val categories: List<com.pocketpass.app.domain.model.ShopCategory> = emptyList(),
    val tokenBalance: Int = 0,
    val refreshError: String? = null,
    val ownedItemIds: Set<String> = emptySet(),
    val unlockedItemIds: Set<String> = emptySet(),
    val purchasingItemIds: Set<String> = emptySet(),
    val purchaseError: String? = null,
    val buyPromptItemId: String? = null,
) {
    val items: List<com.pocketpass.app.domain.model.ShopItem>
        get() = categories.flatMap { it.items }

    val buyPromptItem: com.pocketpass.app.domain.model.ShopItem?
        get() = buyPromptItemId?.let(::item)

    fun item(id: String): com.pocketpass.app.domain.model.ShopItem? =
        items.firstOrNull { it.id == id }

    fun statusOf(item: com.pocketpass.app.domain.model.ShopItem): ShopItemStatus = when {
        item.id in ownedItemIds -> ShopItemStatus.Owned
        item.id in unlockedItemIds -> ShopItemStatus.Unlocked
        item.id in purchasingItemIds -> ShopItemStatus.Purchasing
        item.priceTokens <= tokenBalance -> ShopItemStatus.Available
        else -> ShopItemStatus.Unaffordable
    }
}

enum class GameTarget {
    PuzzleSwap,
    Bingo,
    WorldTour,
}

data class GamesUiState(
    val visible: Boolean = false,
    val activeGame: GameTarget? = null,
    val bingoGoalIndex: Int? = null,
    val worldTourRegionsVisible: Boolean = false,
)

data class LeaderboardUiState(
    val visible: Boolean = false,
    val settingsVisible: Boolean = false,
    val scope: com.pocketpass.app.domain.model.LeaderboardScope =
        com.pocketpass.app.domain.model.LeaderboardScope.Friends,
    val entries: List<com.pocketpass.app.domain.model.LeaderboardEntry> = emptyList(),
    val refreshError: String? = null,
)

data class AchievementsUiState(
    val visible: Boolean = false,
    val achievements: List<com.pocketpass.app.domain.model.AchievementState> = emptyList(),
    val refreshError: String? = null,
)

data class ConnectedAppsUiState(
    val enabled: Boolean = false,
    val visible: Boolean = false,
    val loading: Boolean = false,
    val apps: List<com.pocketpass.app.domain.model.ConnectedApp> = emptyList(),
    val error: String? = null,
    val revokeClientId: String? = null,
    val revokeInProgress: Boolean = false,
    val revokeError: String? = null,
) {
    val revokeTarget: com.pocketpass.app.domain.model.ConnectedApp?
        get() = revokeClientId?.let { id -> apps.firstOrNull { it.clientId == id } }
}

data class OAuthConsentUiState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val request: com.pocketpass.app.domain.model.OAuthConsentRequest? = null,
    val error: String? = null,
    val deciding: Boolean = false,
)

data class WorldTourUiState(
    val regions: List<com.pocketpass.app.domain.model.WorldTourRegion> = emptyList(),
    val refreshError: String? = null,
)

data class BingoUiState(
    val cells: List<com.pocketpass.app.domain.model.BingoCell> = emptyList(),
    val refreshError: String? = null,
)

const val BIO_MAX_LENGTH = 50

data class BioEditorUiState(
    val visible: Boolean = false,
    val draft: String = "",
    val saving: Boolean = false,
    val error: String? = null,
)

data class NameEditorUiState(
    val visible: Boolean = false,
    val draft: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val errorShakeNonce: Int = 0,
) {
    val valid: Boolean
        get() = isValidProfileName(draft)
}

data class GroupComposerState(
    val title: String = "",
    val selectedMemberIds: Set<UserId> = emptySet(),
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = title.isNotBlank() && selectedMemberIds.isNotEmpty() && !submitting

    val remainingSlots: Int
        get() = (MAX_GROUP_MEMBERS - 1 - selectedMemberIds.size).coerceAtLeast(0)
}

enum class ProfileViewerSource {
    RecentInteraction,
    Friend,
}

enum class ProfileFriendRequestState {
    Hidden,
    Available,
    Sending,
    Pending,
    Friends,
    Unavailable,
    Failed,
}

data class ProfileViewerUiState(
    val selectedUserId: String? = null,
    val source: ProfileViewerSource? = null,
    val profile: UserProfile? = null,
    val isOnline: Boolean = false,
    val unavailable: Boolean = false,
    val friendRequestState: ProfileFriendRequestState =
        ProfileFriendRequestState.Hidden,
    val friendRequestError: String? = null,
    val stats: com.pocketpass.app.domain.model.FriendProfileStats? = null,
    val statsPending: Boolean = false,
    val actionInProgress: Boolean = false,
    val actionError: String? = null,
) {
    val visible: Boolean
        get() = selectedUserId != null && source != null
}

data class StatusInfo(
    val time: String = "12:46",
    val batteryPercent: Int = 99,
    val batteryCharging: Boolean = false,
    val wifiConnected: Boolean = true,
    val wifiSignalLevel: Int = 2,
)

data class PocketPassUiState(
    val routes: List<PocketPassRoute> =
        listOf(PocketPassRoute.Root(PocketPassDestination.Home)),
    val activityVariant: ActivityVariant = ActivityVariant.Default,
    val nearbyEnabled: Boolean = true,
    val nearbyRuntime: NearbyRuntimeState = NearbyRuntimeState(),
    val nearbyPermissionUi: NearbyPermissionUiState = NearbyPermissionUiState(),
    val soundLevel: Float = 0.45f,
    val sfxLevel: Float = 0.6f,
    val themeMode: ThemeMode = ThemeMode.System,
    val recentInteractionsSort: RecentInteractionsSort =
        RecentInteractionsSort.LatestEncounter,
    val friendsSort: RecentInteractionsSort =
        RecentInteractionsSort.LatestEncounter,
    val moodEmojisEnabled: Boolean = true,
    val encounterLedEnabled: Boolean = true,
    val encounterLedSupported: Boolean = false,
    val encounterAlertsEnabled: Boolean = true,
    val nearbyRepairAlertsEnabled: Boolean = true,
    val updateAlertsEnabled: Boolean = true,
    val accountSetup: AccountSetupUiState = AccountSetupUiState(),
    val profile: UserProfile? = null,
    val homeMood: HomeMood = HomeMood.Happy,
    val homeMoodPickerExpanded: Boolean = false,
    val homeMoodSelectionCount: Int = 0,
    val homeMoodActive: Boolean = false,
    val bioEditor: BioEditorUiState = BioEditorUiState(),
    val nameEditor: NameEditorUiState = NameEditorUiState(),
    val activitySnapshot: ActivitySnapshot? = null,
    val recentInteractions: List<NearbyEncounter> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val conversations: List<ConversationSummary> = emptyList(),
    val selectedConversationId: ConversationId? = null,
    val selectedConversation: ConversationSummary? = null,
    val selectedMessages: List<Message> = emptyList(),
    val messageDraft: String = "",
    val typingConversationIds: Set<String> = emptySet(),
    val messageActionRailExpanded: Boolean = false,
    val messageSendInProgress: Boolean = false,
    val messageOperationError: String? = null,
    val messageActionMessageId: String? = null,
    val editingMessageId: String? = null,
    val groupComposer: GroupComposerState? = null,
    val groupInfoOpen: Boolean = false,
    val groupOperationInProgress: Boolean = false,
    val groupOperationError: String? = null,
    val conversationNotice: String? = null,
    val selectedMembersById: Map<UserId, ConversationMember> = emptyMap(),
    val typingUserIds: Set<UserId> = emptySet(),
    val isGroupOwner: Boolean = false,
    val canAddGroupMembers: Boolean = false,
    val messageTotalCount: Int = 0,
    val unreadConversationCount: Int = 0,
    val onlineFriendCount: Int = 0,
    val friendsLoading: Boolean = true,
    val friendsRefreshing: Boolean = false,
    val friendsRefreshError: String? = null,
    val friendsOverlay: FriendsOverlay = FriendsOverlay.None,
    val profileViewer: ProfileViewerUiState = ProfileViewerUiState(),
    val myFriendCode: com.pocketpass.app.domain.model.FriendCode? = null,
    val friendCodeEntry: String = "",
    val friendCodeSubmitting: Boolean = false,
    val friendCodeMessage: String? = null,
    val friendCodeError: String? = null,
    val notifications: List<com.pocketpass.app.domain.model.PocketPassNotification> = emptyList(),
    val notificationOperationError: String? = null,
    val messageBadgeOverride: String? = null,
    val auth: AuthUiState = AuthUiState(),
    val sessionState: SessionState = SessionState.Initializing,
    val syncState: SyncState = SyncState.Idle,
    val integrityCompromised: Boolean = false,
    val miiEditorEnabled: Boolean = false,
    val pretendoImportEnabled: Boolean = false,
    val miiEditor: MiiEditorUiState = MiiEditorUiState(),
    val miiSlotsVisible: Boolean = false,
    val connectedApps: ConnectedAppsUiState = ConnectedAppsUiState(),
    val oauthConsent: OAuthConsentUiState = OAuthConsentUiState(),
    val miiDeleteSlot: Int? = null,
    val miiDeleteInProgress: Boolean = false,
    val miiDeleteError: String? = null,
    val themePickerExpanded: Boolean = false,
    val sortMenuOpen: Boolean = false,
    val shop: ShopUiState = ShopUiState(),
    val games: GamesUiState = GamesUiState(),
    val leaderboard: LeaderboardUiState = LeaderboardUiState(),
    val achievements: AchievementsUiState = AchievementsUiState(),
    val worldTour: WorldTourUiState = WorldTourUiState(),
    val bingo: BingoUiState = BingoUiState(),
    val deleteAccountVisible: Boolean = false,
    val deleteAccountInProgress: Boolean = false,
    val deleteAccountError: String? = null,
    val removeFriendPromptVisible: Boolean = false,
    val appUpdate: AppUpdateUiState = AppUpdateUiState(),
    val status: StatusInfo = StatusInfo(),
) {
    val rootDestination: PocketPassDestination
        get() = routes.firstOrNull()
            .let { it as? PocketPassRoute.Root }
            ?.destination
            ?: PocketPassDestination.Home

    val messageBadgeText: String
        get() = messageBadgeOverride
            ?: unreadConversationCount.coerceAtLeast(0).toString()

    val unreadNotificationCount: Int
        get() = notifications.count { it.isUnread }

    fun senderDisplayName(userId: UserId): String =
        selectedMembersById[userId]?.displayName
            ?: friends.firstOrNull { it.profile.userId == userId }?.profile?.displayName
            ?: FORMER_MEMBER_LABEL
}

sealed interface PocketPassEvent {
    data class Auth(val event: AuthEvent) : PocketPassEvent
    data class AccountSetup(val event: AccountSetupEvent) : PocketPassEvent
    data class Mii(val event: MiiEditorEvent) : PocketPassEvent
    data object OpenMiiEditor : PocketPassEvent
    data object OpenMiiSlots : PocketPassEvent
    data object CloseMiiSlots : PocketPassEvent
    data object OpenConnectedApps : PocketPassEvent
    data object CloseConnectedApps : PocketPassEvent
    data class OpenRevokeConnectedApp(val clientId: String) : PocketPassEvent
    data object CloseRevokeConnectedApp : PocketPassEvent
    data object ConfirmRevokeConnectedApp : PocketPassEvent
    data object DismissOAuthConsent : PocketPassEvent
    data object ApproveOAuthConsent : PocketPassEvent
    data object DenyOAuthConsent : PocketPassEvent
    data object OpenThemePicker : PocketPassEvent
    data object CloseThemePicker : PocketPassEvent
    data object ToggleSortMenu : PocketPassEvent
    data object CloseSortMenu : PocketPassEvent
    data class EditMiiSlot(val slot: Int) : PocketPassEvent
    data class OpenDeleteMiiSlot(val slot: Int) : PocketPassEvent
    data object CloseDeleteMiiSlot : PocketPassEvent
    data object ConfirmDeleteMiiSlot : PocketPassEvent
    data class SetActiveMiiSlot(val slot: Int) : PocketPassEvent
    data class SelectDestination(val destination: PocketPassDestination) : PocketPassEvent
    data class OpenMessage(val conversationId: String) : PocketPassEvent
    data class UpdateMessageDraft(val value: String) : PocketPassEvent
    data object SendMessage : PocketPassEvent
    data object ToggleMessageActions : PocketPassEvent
    data class RetryMessage(val messageId: String) : PocketPassEvent
    data class SelectMessageAction(val action: MessageComposerAction) : PocketPassEvent
    data class OpenMessageActions(val messageId: String) : PocketPassEvent
    data object CloseMessageActions : PocketPassEvent
    data object EditSelectedMessage : PocketPassEvent
    data object DeleteSelectedMessage : PocketPassEvent
    data object CancelMessageEdit : PocketPassEvent
    data object OpenNewGroup : PocketPassEvent
    data object CloseNewGroup : PocketPassEvent
    data class ToggleGroupMember(val userId: String) : PocketPassEvent
    data class UpdateGroupTitle(val value: String) : PocketPassEvent
    data object CreateGroup : PocketPassEvent
    data object OpenGroupInfo : PocketPassEvent
    data object CloseGroupInfo : PocketPassEvent
    data class AddGroupMembers(val userIds: List<String>) : PocketPassEvent
    data class RemoveGroupMember(val userId: String) : PocketPassEvent
    data object LeaveGroup : PocketPassEvent
    data class RenameGroup(val title: String) : PocketPassEvent
    data object DismissConversationNotice : PocketPassEvent
    data object Back : PocketPassEvent
    data object OpenShop : PocketPassEvent
    data object CloseShop : PocketPassEvent
    data class OpenBuyShopItem(val itemId: String) : PocketPassEvent
    data object CloseBuyShopItem : PocketPassEvent
    data object ConfirmBuyShopItem : PocketPassEvent
    data class WearShopItem(val itemId: String) : PocketPassEvent
    data object OpenGames : PocketPassEvent
    data object CloseGames : PocketPassEvent
    data class OpenGame(val game: GameTarget) : PocketPassEvent
    data class SelectBingoSquare(val index: Int) : PocketPassEvent
    data object CloseBingoSquare : PocketPassEvent
    data object OpenWorldTourRegions : PocketPassEvent
    data object CloseWorldTourRegions : PocketPassEvent
    data object OpenLeaderboard : PocketPassEvent
    data object CloseLeaderboard : PocketPassEvent
    data object OpenLeaderboardSettings : PocketPassEvent
    data object CloseLeaderboardSettings : PocketPassEvent
    data class SetLeaderboardScope(
        val scope: com.pocketpass.app.domain.model.LeaderboardScope,
    ) : PocketPassEvent
    data object OpenAchievements : PocketPassEvent
    data object CloseAchievements : PocketPassEvent
    data object ShuffleActivities : PocketPassEvent
    data object ToggleHomeMoodPicker : PocketPassEvent
    data class SelectHomeMood(val mood: HomeMood) : PocketPassEvent
    data object CloseHomeMoodPicker : PocketPassEvent
    data object OpenBioEditor : PocketPassEvent
    data class UpdateBioDraft(val value: String) : PocketPassEvent
    data object SaveBio : PocketPassEvent
    data object CloseBioEditor : PocketPassEvent
    data object OpenNameEditor : PocketPassEvent
    data class UpdateNameDraft(val value: String) : PocketPassEvent
    data object SaveName : PocketPassEvent
    data object CloseNameEditor : PocketPassEvent
    data class SetMessageBadgeText(val text: String) : PocketPassEvent
    data class SetNearby(val enabled: Boolean) : PocketPassEvent
    data object RequestNearbyPermissions : PocketPassEvent
    data object SkipNearbyPermissions : PocketPassEvent
    data class SetSoundLevel(val level: Float) : PocketPassEvent
    data class SetSfxLevel(val level: Float) : PocketPassEvent
    data class SetThemeMode(val mode: ThemeMode) : PocketPassEvent
    data class SetRecentInteractionsSort(
        val sort: RecentInteractionsSort,
    ) : PocketPassEvent
    data class SetFriendsSort(
        val sort: RecentInteractionsSort,
    ) : PocketPassEvent
    data object OpenAccessibility : PocketPassEvent
    data object OpenSocial : PocketPassEvent
    data object OpenNotificationSettings : PocketPassEvent
    data object OpenAppUpdate : PocketPassEvent
    data object CheckForAppUpdate : PocketPassEvent
    data object DownloadAppUpdate : PocketPassEvent
    data object InstallAppUpdate : PocketPassEvent
    data class SetMoodEmojisEnabled(val enabled: Boolean) : PocketPassEvent
    data class SetEncounterLedEnabled(val enabled: Boolean) : PocketPassEvent
    data class SetEncounterAlertsEnabled(val enabled: Boolean) : PocketPassEvent
    data class SetNearbyRepairAlertsEnabled(val enabled: Boolean) : PocketPassEvent
    data class SetUpdateAlertsEnabled(val enabled: Boolean) : PocketPassEvent
    data object ResetSettings : PocketPassEvent
    data object OpenDeleteAccount : PocketPassEvent
    data object CloseDeleteAccount : PocketPassEvent
    data object ConfirmDeleteAccount : PocketPassEvent
    data object SignOut : PocketPassEvent
    data object OpenAddFriend : PocketPassEvent
    data object RefreshFriends : PocketPassEvent
    data class OpenUserProfile(
        val userId: String,
        val source: ProfileViewerSource,
    ) : PocketPassEvent
    data object CloseUserProfile : PocketPassEvent
    data object SendProfileFriendRequest : PocketPassEvent
    data object RemoveProfileFriend : PocketPassEvent
    data object OpenRemoveFriend : PocketPassEvent
    data object CloseRemoveFriend : PocketPassEvent
    data object MessageProfileFriend : PocketPassEvent
    data object ToggleNotifications : PocketPassEvent
    data object CloseFriendsOverlay : PocketPassEvent
    data class UpdateFriendCode(val value: String) : PocketPassEvent
    data object SubmitFriendCode : PocketPassEvent
    data class OpenNotification(val notificationId: String) : PocketPassEvent
    data class RespondToNotificationFriendRequest(
        val notificationId: String,
        val accept: Boolean,
    ) : PocketPassEvent
    data class DeleteNotification(val notificationId: String) : PocketPassEvent
    data object MarkAllNotificationsRead : PocketPassEvent
    data object ClearAllNotifications : PocketPassEvent
    data class StatusChanged(val status: StatusInfo) : PocketPassEvent
}

sealed interface PocketPassExtensionTarget {
    data object HomeMore : PocketPassExtensionTarget
    data object Shop : PocketPassExtensionTarget
    data object Leaderboard : PocketPassExtensionTarget
    data object Notifications : PocketPassExtensionTarget
    data class MessageComposer(
        val conversationId: String,
        val action: MessageComposerAction,
    ) : PocketPassExtensionTarget
}

fun interface PocketPassExtensions {
    fun open(target: PocketPassExtensionTarget)

    companion object {
        val None = PocketPassExtensions { }
    }
}

object PocketPassReducer {
    fun reduce(state: PocketPassUiState, event: PocketPassEvent): PocketPassUiState =
        when (event) {
            is PocketPassEvent.Auth -> state
            is PocketPassEvent.AccountSetup -> state
            is PocketPassEvent.Mii -> state

            PocketPassEvent.OpenMiiEditor,
            is PocketPassEvent.EditMiiSlot,
            PocketPassEvent.OpenConnectedApps,
            PocketPassEvent.CloseConnectedApps,
            is PocketPassEvent.OpenRevokeConnectedApp,
            PocketPassEvent.CloseRevokeConnectedApp,
            PocketPassEvent.ConfirmRevokeConnectedApp,
            PocketPassEvent.DismissOAuthConsent,
            PocketPassEvent.ApproveOAuthConsent,
            PocketPassEvent.DenyOAuthConsent,
            -> state

            PocketPassEvent.OpenMiiSlots -> state.copy(miiSlotsVisible = true)
            PocketPassEvent.CloseMiiSlots -> state.copy(
                miiSlotsVisible = false,
                miiDeleteSlot = null,
                miiDeleteError = null,
            )

            PocketPassEvent.OpenThemePicker -> state.copy(themePickerExpanded = true)
            PocketPassEvent.CloseThemePicker -> state.copy(themePickerExpanded = false)
            PocketPassEvent.ToggleSortMenu -> state.copy(sortMenuOpen = !state.sortMenuOpen)
            PocketPassEvent.CloseSortMenu -> state.copy(sortMenuOpen = false)

            is PocketPassEvent.SetActiveMiiSlot -> state

            is PocketPassEvent.OpenDeleteMiiSlot -> state.copy(
                miiDeleteSlot = event.slot,
                miiDeleteError = null,
            )

            PocketPassEvent.CloseDeleteMiiSlot -> state.copy(
                miiDeleteSlot = null,
                miiDeleteError = null,
            )

            PocketPassEvent.ConfirmDeleteMiiSlot -> state.copy(
                miiDeleteInProgress = true,
                miiDeleteError = null,
            )

            is PocketPassEvent.SelectDestination -> state.copy(
                routes = listOf(PocketPassRoute.Root(event.destination)),
                shop = state.shop.copy(buyPromptItemId = null),
                miiSlotsVisible = false,
                removeFriendPromptVisible = false,
                miiDeleteSlot = null,
                miiDeleteError = null,
                themePickerExpanded = false,
                sortMenuOpen = false,
            )

            is PocketPassEvent.OpenMessage -> {
                if (state.rootDestination != PocketPassDestination.Messages) {
                    state
                } else {
                    state.copy(
                        routes = state.routes + PocketPassRoute.MessageDetail(event.conversationId),
                    )
                }
            }

            PocketPassEvent.OpenNewGroup -> {
                if (
                    state.rootDestination != PocketPassDestination.Messages ||
                    state.routes.lastOrNull() !is PocketPassRoute.Root
                ) {
                    state
                } else {
                    state.copy(routes = state.routes + PocketPassRoute.NewGroup)
                }
            }

            PocketPassEvent.CloseNewGroup ->
                if (state.routes.lastOrNull() == PocketPassRoute.NewGroup) {
                    state.copy(routes = state.routes.dropLast(1))
                } else {
                    state
                }

            is PocketPassEvent.UpdateMessageDraft,
            PocketPassEvent.SendMessage,
            PocketPassEvent.ToggleMessageActions,
            is PocketPassEvent.RetryMessage,
            is PocketPassEvent.SelectMessageAction,
            is PocketPassEvent.OpenMessageActions,
            PocketPassEvent.CloseMessageActions,
            PocketPassEvent.EditSelectedMessage,
            PocketPassEvent.DeleteSelectedMessage,
            PocketPassEvent.CancelMessageEdit,
            is PocketPassEvent.ToggleGroupMember,
            is PocketPassEvent.UpdateGroupTitle,
            PocketPassEvent.CreateGroup,
            PocketPassEvent.OpenGroupInfo,
            PocketPassEvent.CloseGroupInfo,
            is PocketPassEvent.AddGroupMembers,
            is PocketPassEvent.RemoveGroupMember,
            PocketPassEvent.LeaveGroup,
            is PocketPassEvent.RenameGroup,
            PocketPassEvent.DismissConversationNotice,
            PocketPassEvent.ToggleHomeMoodPicker,
            is PocketPassEvent.SelectHomeMood,
            PocketPassEvent.CloseHomeMoodPicker,
            PocketPassEvent.OpenBioEditor,
            is PocketPassEvent.UpdateBioDraft,
            PocketPassEvent.SaveBio,
            PocketPassEvent.CloseBioEditor,
            PocketPassEvent.OpenNameEditor,
            is PocketPassEvent.UpdateNameDraft,
            PocketPassEvent.SaveName,
            PocketPassEvent.CloseNameEditor,
            PocketPassEvent.OpenShop,
            PocketPassEvent.OpenGames,
            PocketPassEvent.CloseGames,
            is PocketPassEvent.OpenGame,
            is PocketPassEvent.SelectBingoSquare,
            PocketPassEvent.CloseBingoSquare,
            PocketPassEvent.OpenWorldTourRegions,
            PocketPassEvent.CloseWorldTourRegions,
            PocketPassEvent.OpenLeaderboard,
            PocketPassEvent.CloseLeaderboard,
            PocketPassEvent.OpenLeaderboardSettings,
            PocketPassEvent.CloseLeaderboardSettings,
            is PocketPassEvent.SetLeaderboardScope,
            PocketPassEvent.OpenAchievements,
            PocketPassEvent.CloseAchievements,
            PocketPassEvent.OpenAddFriend,
            PocketPassEvent.RefreshFriends,
            PocketPassEvent.SendProfileFriendRequest,
            PocketPassEvent.MessageProfileFriend,
            PocketPassEvent.ToggleNotifications,
            PocketPassEvent.CloseFriendsOverlay,
            is PocketPassEvent.UpdateFriendCode,
            PocketPassEvent.SubmitFriendCode,
            is PocketPassEvent.OpenNotification,
            is PocketPassEvent.RespondToNotificationFriendRequest,
            is PocketPassEvent.DeleteNotification,
            PocketPassEvent.MarkAllNotificationsRead,
            PocketPassEvent.ClearAllNotifications,
            PocketPassEvent.RequestNearbyPermissions,
            PocketPassEvent.SkipNearbyPermissions,
            -> state

            is PocketPassEvent.OpenUserProfile,
            PocketPassEvent.CloseUserProfile,
            PocketPassEvent.RemoveProfileFriend,
            PocketPassEvent.CloseRemoveFriend,
            -> state.copy(removeFriendPromptVisible = false)

            PocketPassEvent.OpenRemoveFriend -> state.copy(removeFriendPromptVisible = true)

            is PocketPassEvent.OpenBuyShopItem -> {
                val item = state.shop.item(event.itemId)
                if (item != null && state.shop.statusOf(item) == ShopItemStatus.Available) {
                    state.copy(shop = state.shop.copy(buyPromptItemId = event.itemId))
                } else {
                    state
                }
            }

            PocketPassEvent.CloseBuyShopItem,
            PocketPassEvent.ConfirmBuyShopItem,
            is PocketPassEvent.WearShopItem,
            PocketPassEvent.CloseShop,
            -> state.copy(shop = state.shop.copy(buyPromptItemId = null))

            PocketPassEvent.Back -> when {
                state.shop.buyPromptItemId != null ->
                    state.copy(shop = state.shop.copy(buyPromptItemId = null))
                state.removeFriendPromptVisible -> state.copy(removeFriendPromptVisible = false)
                state.miiDeleteSlot != null && !state.miiDeleteInProgress -> state.copy(
                    miiDeleteSlot = null,
                    miiDeleteError = null,
                )

                state.miiSlotsVisible -> state.copy(miiSlotsVisible = false)
                state.sortMenuOpen -> state.copy(sortMenuOpen = false)
                state.themePickerExpanded -> state.copy(themePickerExpanded = false)
                state.routes.size <= 1 -> state
                else -> state.copy(routes = state.routes.dropLast(1))
            }

            PocketPassEvent.ShuffleActivities -> state.copy(
                activityVariant = if (state.activityVariant == ActivityVariant.Default) {
                    ActivityVariant.Shuffled
                } else {
                    ActivityVariant.Default
                },
            )

            is PocketPassEvent.SetMessageBadgeText -> state.copy(
                messageBadgeOverride = event.text.take(12),
            )
            is PocketPassEvent.SetNearby -> state.copy(nearbyEnabled = event.enabled)
            is PocketPassEvent.SetSoundLevel -> state.copy(
                soundLevel = event.level.coerceIn(0f, 1f),
            )
            is PocketPassEvent.SetSfxLevel -> state.copy(
                sfxLevel = event.level.coerceIn(0f, 1f),
            )

            is PocketPassEvent.SetThemeMode -> state.copy(themeMode = event.mode)
            is PocketPassEvent.SetRecentInteractionsSort ->
                state.copy(recentInteractionsSort = event.sort)
            is PocketPassEvent.SetFriendsSort ->
                state.copy(friendsSort = event.sort)
            PocketPassEvent.OpenAccessibility ->
                if (state.routes.lastOrNull() == PocketPassRoute.Accessibility) {
                    state
                } else {
                    state.copy(routes = state.routes + PocketPassRoute.Accessibility)
                }
            PocketPassEvent.OpenSocial ->
                if (state.routes.lastOrNull() == PocketPassRoute.Social) {
                    state
                } else {
                    state.copy(routes = state.routes + PocketPassRoute.Social)
                }
            PocketPassEvent.OpenNotificationSettings ->
                if (state.routes.lastOrNull() == PocketPassRoute.NotificationSettings) {
                    state
                } else {
                    state.copy(
                        routes = state.routes + PocketPassRoute.NotificationSettings,
                    )
                }
            PocketPassEvent.OpenAppUpdate ->
                if (state.routes.lastOrNull() == PocketPassRoute.AppUpdate) {
                    state
                } else {
                    state.copy(routes = state.routes + PocketPassRoute.AppUpdate)
                }
            PocketPassEvent.CheckForAppUpdate,
            PocketPassEvent.DownloadAppUpdate,
            PocketPassEvent.InstallAppUpdate,
            -> state
            is PocketPassEvent.SetMoodEmojisEnabled -> state.copy(
                moodEmojisEnabled = event.enabled,
            )
            is PocketPassEvent.SetEncounterLedEnabled -> state.copy(
                encounterLedEnabled = event.enabled,
            )
            is PocketPassEvent.SetEncounterAlertsEnabled -> state.copy(
                encounterAlertsEnabled = event.enabled,
            )
            is PocketPassEvent.SetNearbyRepairAlertsEnabled -> state.copy(
                nearbyRepairAlertsEnabled = event.enabled,
            )
            is PocketPassEvent.SetUpdateAlertsEnabled -> state.copy(
                updateAlertsEnabled = event.enabled,
            )
            PocketPassEvent.ResetSettings -> state.copy(
                nearbyEnabled = true,
                soundLevel = 0.45f,
                sfxLevel = 0.6f,
                themeMode = ThemeMode.System,
                moodEmojisEnabled = true,
                encounterLedEnabled = true,
                encounterAlertsEnabled = true,
                nearbyRepairAlertsEnabled = true,
                updateAlertsEnabled = true,
            )
            PocketPassEvent.SignOut -> state

            PocketPassEvent.OpenDeleteAccount -> state.copy(
                deleteAccountVisible = true,
                deleteAccountError = null,
            )

            PocketPassEvent.CloseDeleteAccount -> state.copy(
                deleteAccountVisible = false,
                deleteAccountError = null,
            )

            PocketPassEvent.ConfirmDeleteAccount -> state.copy(
                deleteAccountInProgress = true,
                deleteAccountError = null,
            )

            is PocketPassEvent.StatusChanged -> state.copy(status = event.status)
        }
}
