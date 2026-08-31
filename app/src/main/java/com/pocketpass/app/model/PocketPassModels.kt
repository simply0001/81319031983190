package com.pocketpass.app.model

import androidx.navigation3.runtime.NavKey
import com.pocketpass.app.auth.AuthUiState
import com.pocketpass.app.domain.model.ActivitySnapshot
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.ConversationMember
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.FORMER_MEMBER_LABEL
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.domain.state.SyncState
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.mii.MiiEditorUiState
import com.pocketpass.app.nearby.NearbyPermissionUiState
import com.pocketpass.app.nearby.NearbyRuntimeState
import com.pocketpass.app.update.AppUpdateUiState
import kotlinx.serialization.Serializable

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
