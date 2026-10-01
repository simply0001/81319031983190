package com.pocketpass.app.model

import com.pocketpass.app.steps.StepRewardsState
import com.pocketpass.app.auth.AuthUiState
import com.pocketpass.app.domain.model.ActivitySnapshot
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.ConversationMember
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.domain.state.SyncState
import com.pocketpass.app.feature.AccountSecurityUiState
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.mii.MiiEditorUiState
import com.pocketpass.app.nearby.NearbyPermissionUiState
import com.pocketpass.app.nearby.NearbyRuntimeState
import com.pocketpass.app.update.AppUpdateUiState
import com.pocketpass.app.widget.WidgetBlock
import com.pocketpass.app.widget.WidgetDesign
import com.pocketpass.app.widget.upserted
import kotlinx.serialization.Serializable

@Serializable
sealed interface PocketPassRoute : NavKeyMarker {
    @Serializable
    data class Root(val destination: PocketPassDestination) : PocketPassRoute

    @Serializable
    data class MessageDetail(val conversationId: String) : PocketPassRoute

    @Serializable
    data object NewGroup : PocketPassRoute

    @Serializable
    data object Accessibility : PocketPassRoute

    @Serializable
    data object AppSettings : PocketPassRoute

    @Serializable
    data object ChatColours : PocketPassRoute

    @Serializable
    data object Social : PocketPassRoute

    @Serializable
    data object EditInfo : PocketPassRoute

    @Serializable
    data object AccountSecurity : PocketPassRoute

    @Serializable
    data object Contributors : PocketPassRoute

    @Serializable
    data object NotificationSettings : PocketPassRoute

    @Serializable
    data object AppUpdate : PocketPassRoute

    @Serializable
    data object WidgetMaker : PocketPassRoute

    @Serializable
    data class WidgetEditor(val designId: String) : PocketPassRoute
}

data class PocketPassUiState(
    val boardsVisible: Boolean = true,
    val boards: com.pocketpass.app.boards.BoardsUiState = com.pocketpass.app.boards.BoardsUiState(),
    val routes: List<PocketPassRoute> =
        listOf(PocketPassRoute.Root(PocketPassDestination.Home)),
    val activityVariant: ActivityVariant = ActivityVariant.Default,
    val nearbyEnabled: Boolean = true,
    val nearbyRuntime: NearbyRuntimeState = NearbyRuntimeState(),
    val nearbyPermissionUi: NearbyPermissionUiState = NearbyPermissionUiState(),
    val soundLevel: Float = 0.45f,
    val sfxLevel: Float = 0.6f,
    val themeMode: ThemeMode = ThemeMode.System,
    val messagePrivacySaving: Boolean = false,
    val messagePrivacyError: String? = null,
    val invitesPrivacySaving: Boolean = false,
    val invitesPrivacyError: String? = null,
    val chatColourSaving: Boolean = false,
    val chatColourSaveError: String? = null,
    val messageAuthorColours: Map<UserId, com.pocketpass.app.domain.model.ChatBubbleColour> = emptyMap(),
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
    val messageAlertsEnabled: Boolean = true,
    val messagePushSupported: Boolean = false,
    val stepRewardsEnabled: Boolean = false,
    val stepRewards: StepRewardsState = StepRewardsState(),
    val accountSetup: AccountSetupUiState = AccountSetupUiState(),
    val profile: UserProfile? = null,
    val homeMood: HomeMood = HomeMood.Happy,
    val homeMoodPickerExpanded: Boolean = false,
    val homeMoodSelectionCount: Int = 0,
    val homeMoodActive: Boolean = false,
    val bioEditor: BioEditorUiState = BioEditorUiState(),
    val nameEditor: NameEditorUiState = NameEditorUiState(),
    val ageEditor: AgeEditorUiState = AgeEditorUiState(),
    val countryEditor: CountryEditorUiState = CountryEditorUiState(),
    val activitySnapshot: ActivitySnapshot? = null,
    val recentInteractions: List<NearbyEncounter> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val conversations: List<ConversationSummary> = emptyList(),
    val selectedConversationId: ConversationId? = null,
    val selectedConversation: ConversationSummary? = null,
    val selectedMessages: List<Message> = emptyList(),
    val previewConversationId: ConversationId? = null,
    val previewMessages: List<Message> = emptyList(),
    val messageDraft: String = "",
    val typingConversationIds: Set<String> = emptySet(),
    val messageSendInProgress: Boolean = false,
    val messageOperationError: String? = null,
    val messageActionMessageId: String? = null,
    val editingMessageId: String? = null,
    val groupComposer: GroupComposerState? = null,
    val groupInfoOpen: Boolean = false,
    val groupOperationInProgress: Boolean = false,
    val groupOperationError: String? = null,
    val groupMemberFriendStates: Map<UserId, GroupMemberFriendState> = emptyMap(),
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
    val auth: AuthUiState = AuthUiState(),
    val accountSecurity: AccountSecurityUiState = AccountSecurityUiState(),
    val sessionState: SessionState = SessionState.Initializing,
    val accountBan: com.pocketpass.app.domain.model.AccountBanNotice? = null,
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
    val puzzle: PuzzleUiState = PuzzleUiState(),
    val deleteAccountVisible: Boolean = false,
    val deleteAccountInProgress: Boolean = false,
    val deleteAccountError: String? = null,
    val removeFriendPromptVisible: Boolean = false,
    val appUpdate: AppUpdateUiState = AppUpdateUiState(),
    val widgetDesigns: List<WidgetDesign> = emptyList(),
    val widgetMaker: WidgetMakerUiState = WidgetMakerUiState(),
    val status: StatusInfo = StatusInfo(),
) {
    val rootDestination: PocketPassDestination
        get() = routes.firstOrNull()
            .let { it as? PocketPassRoute.Root }
            ?.destination
            ?: PocketPassDestination.Home

    val messageBadgeText: String
        get() = unreadConversationCount.coerceAtLeast(0).toString()

    val unreadNotificationCount: Int
        get() = notifications.count { it.isUnread }
}

object PocketPassReducer {
    fun reduce(state: PocketPassUiState, event: PocketPassEvent): PocketPassUiState {
        if (event is PocketPassEvent.StatusChanged) return state.copy(status = event.status)
        return reduceRoutes(state, event)
            ?: reduceWidgetMaker(state, event)
            ?: reduceSettings(state, event)
            ?: reduceShop(state, event)
            ?: reduceDialogs(state, event)
            ?: state
    }

    private fun reduceRoutes(state: PocketPassUiState, event: PocketPassEvent): PocketPassUiState? =
        when (event) {
            PocketPassEvent.Back -> reduceBack(state)

            is PocketPassEvent.SelectDestination -> state.copy(
                routes = listOf(PocketPassRoute.Root(event.destination)),
                shop = state.shop.copy(buyPromptItemId = null),
                miiSlotsVisible = false,
                removeFriendPromptVisible = false,
                miiDeleteSlot = null,
                miiDeleteError = null,
                themePickerExpanded = false,
                sortMenuOpen = false,
                widgetMaker = state.widgetMaker.closed(),
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

            PocketPassEvent.OpenAccessibility -> state.pushRoute(PocketPassRoute.Accessibility)
            PocketPassEvent.OpenAppSettings -> state.pushRoute(PocketPassRoute.AppSettings)
            PocketPassEvent.OpenChatColours -> if (state.routes.lastOrNull() == PocketPassRoute.ChatColours) state
                else state.copy(routes = state.routes + PocketPassRoute.ChatColours, chatColourSaveError = null)
            PocketPassEvent.OpenSocial -> state.pushRoute(PocketPassRoute.Social)
            PocketPassEvent.OpenEditInfo -> state.pushRoute(PocketPassRoute.EditInfo)
            PocketPassEvent.OpenAccountSecurity -> state.pushRoute(PocketPassRoute.AccountSecurity)
            PocketPassEvent.OpenContributors -> state.pushRoute(PocketPassRoute.Contributors)
            PocketPassEvent.OpenNotificationSettings -> state.pushRoute(PocketPassRoute.NotificationSettings)
            PocketPassEvent.OpenAppUpdate -> state.pushRoute(PocketPassRoute.AppUpdate)
            else -> null
        }

    private fun reduceBack(state: PocketPassUiState): PocketPassUiState = when {
        state.shop.buyPromptItemId != null ->
            state.copy(shop = state.shop.copy(buyPromptItemId = null))
        state.shop.selectedCategoryId != null ->
            state.copy(shop = state.shop.copy(selectedCategoryId = null))
        state.removeFriendPromptVisible -> state.copy(removeFriendPromptVisible = false)
        state.deleteAccountVisible && !state.deleteAccountInProgress -> state.copy(
            deleteAccountVisible = false,
            deleteAccountError = null,
        )
        state.miiDeleteSlot != null && !state.miiDeleteInProgress -> state.copy(
            miiDeleteSlot = null,
            miiDeleteError = null,
        )

        state.miiSlotsVisible -> state.copy(miiSlotsVisible = false)
        state.sortMenuOpen -> state.copy(sortMenuOpen = false)
        state.themePickerExpanded -> state.copy(themePickerExpanded = false)
        state.widgetMaker.blockPicker != null ->
            state.copy(widgetMaker = state.widgetMaker.copy(blockPicker = null))
        state.widgetMaker.deletePromptVisible ->
            state.copy(widgetMaker = state.widgetMaker.copy(deletePromptVisible = false))
        state.widgetMaker.renameDraft != null ->
            state.copy(widgetMaker = state.widgetMaker.copy(renameDraft = null))
        state.routes.size <= 1 && state.rootDestination == PocketPassDestination.Messages ->
            state.copy(routes = listOf(PocketPassRoute.Root(PocketPassDestination.Home)))
        state.routes.size <= 1 -> state
        else -> state.copy(
            routes = state.routes.dropLast(1),
            widgetMaker = if (state.routes.last() == PocketPassRoute.WidgetMaker) {
                state.widgetMaker.copy(assigningAppWidgetId = null)
            } else {
                state.widgetMaker
            },
        )
    }

    private fun reduceWidgetMaker(state: PocketPassUiState, event: PocketPassEvent): PocketPassUiState? =
        when (event) {
            PocketPassEvent.OpenWidgetMaker ->
                if (state.routes.lastOrNull() == PocketPassRoute.WidgetMaker) {
                    state
                } else {
                    state.copy(
                        routes = state.routes + PocketPassRoute.WidgetMaker,
                        widgetMaker = state.widgetMaker.copy(message = null),
                    )
                }
            is PocketPassEvent.OpenWidgetEditor -> {
                val route = PocketPassRoute.WidgetEditor(event.designId)
                if (state.routes.lastOrNull() == route) {
                    state
                } else {
                    state.copy(
                        routes = state.routes + route,
                        widgetMaker = state.widgetMaker.copy(
                            blockPicker = null,
                            deletePromptVisible = false,
                            renameDraft = null,
                            message = null,
                        ),
                    )
                }
            }
            is PocketPassEvent.UpdateWidgetDesign ->
                state.copy(widgetDesigns = state.widgetDesigns.upserted(event.design))
            is PocketPassEvent.DeleteWidgetDesign -> state.copy(
                widgetDesigns = state.widgetDesigns.filterNot { it.id == event.designId },
                routes = state.routes.filterNot { it == PocketPassRoute.WidgetEditor(event.designId) },
                widgetMaker = state.widgetMaker.copy(
                    blockPicker = null,
                    deletePromptVisible = false,
                    renameDraft = null,
                ),
            )
            PocketPassEvent.OpenWidgetDeletePrompt ->
                state.copy(widgetMaker = state.widgetMaker.copy(deletePromptVisible = true))
            PocketPassEvent.CloseWidgetDeletePrompt ->
                state.copy(widgetMaker = state.widgetMaker.copy(deletePromptVisible = false))
            is PocketPassEvent.OpenWidgetBlockPicker ->
                state.copy(widgetMaker = state.widgetMaker.copy(blockPicker = event.slot))
            PocketPassEvent.CloseWidgetBlockPicker ->
                state.copy(widgetMaker = state.widgetMaker.copy(blockPicker = null))
            is PocketPassEvent.PickWidgetBlock -> {
                val picked = state.widgetDesignAfterPick(event.block)
                state.copy(
                    widgetDesigns = picked?.let { state.widgetDesigns.upserted(it) } ?: state.widgetDesigns,
                    widgetMaker = state.widgetMaker.copy(blockPicker = null),
                )
            }
            PocketPassEvent.OpenWidgetRename -> state.copy(
                widgetMaker = state.widgetMaker.copy(
                    renameDraft = state.editingWidgetDesign?.name.orEmpty(),
                ),
            )
            is PocketPassEvent.UpdateWidgetNameDraft -> state.copy(
                widgetMaker = state.widgetMaker.copy(
                    renameDraft = event.value.take(WidgetDesign.MAX_NAME_LENGTH),
                ),
            )
            PocketPassEvent.SaveWidgetName -> {
                val renamed = state.widgetDesignAfterRename()
                state.copy(
                    widgetDesigns = renamed?.let { state.widgetDesigns.upserted(it) } ?: state.widgetDesigns,
                    widgetMaker = state.widgetMaker.copy(renameDraft = null),
                )
            }
            PocketPassEvent.CloseWidgetRename ->
                state.copy(widgetMaker = state.widgetMaker.copy(renameDraft = null))
            is PocketPassEvent.BeginWidgetAssign -> state.copy(
                routes = listOf(
                    PocketPassRoute.Root(PocketPassDestination.Settings),
                    PocketPassRoute.WidgetMaker,
                ),
                widgetMaker = state.widgetMaker.closed().copy(
                    assigningAppWidgetId = event.appWidgetId,
                    message = null,
                ),
            )
            is PocketPassEvent.AssignWidgetDesign -> state.copy(
                widgetMaker = state.widgetMaker.copy(
                    assigningAppWidgetId = null,
                    message = state.widgetDesigns.firstOrNull { it.id == event.designId }
                        ?.let { "Your widget now shows ${it.name}" }
                        ?: "Widget updated",
                ),
            )
            PocketPassEvent.DismissWidgetMessage ->
                state.copy(widgetMaker = state.widgetMaker.copy(message = null))
            else -> null
        }

    private fun reduceSettings(state: PocketPassUiState, event: PocketPassEvent): PocketPassUiState? =
        when (event) {
            PocketPassEvent.ShuffleActivities -> state.copy(
                activityVariant = if (state.activityVariant == ActivityVariant.Default) {
                    ActivityVariant.Shuffled
                } else {
                    ActivityVariant.Default
                },
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
            is PocketPassEvent.SetMessageAlertsEnabled -> state.copy(
                messageAlertsEnabled = event.enabled,
            )
            is PocketPassEvent.SetStepRewardsEnabled -> state.copy(
                stepRewardsEnabled = event.enabled,
            )
            else -> null
        }

    private fun reduceShop(state: PocketPassUiState, event: PocketPassEvent): PocketPassUiState? =
        when (event) {
            is PocketPassEvent.OpenBuyShopItem -> {
                val item = state.shop.item(event.itemId)
                if (item != null && state.shop.canBuy(item)) {
                    state.copy(shop = state.shop.copy(buyPromptItemId = event.itemId))
                } else {
                    state
                }
            }

            is PocketPassEvent.OpenShopCategory -> {
                if (state.shop.visible && state.shop.categories.any { it.id == event.categoryId }) {
                    state.copy(shop = state.shop.copy(selectedCategoryId = event.categoryId))
                } else {
                    state
                }
            }

            PocketPassEvent.CloseShopCategory ->
                state.copy(shop = state.shop.copy(selectedCategoryId = null))

            PocketPassEvent.CloseBuyShopItem,
            PocketPassEvent.ConfirmBuyShopItem,
            is PocketPassEvent.WearShopItem,
            -> state.copy(shop = state.shop.copy(buyPromptItemId = null))

            PocketPassEvent.CloseShop ->
                state.copy(shop = state.shop.copy(buyPromptItemId = null, selectedCategoryId = null))

            else -> null
        }

    private fun reduceDialogs(state: PocketPassUiState, event: PocketPassEvent): PocketPassUiState? =
        when (event) {
            PocketPassEvent.OpenMiiSlots -> state.copy(miiSlotsVisible = true)
            PocketPassEvent.CloseMiiSlots -> state.copy(
                miiSlotsVisible = false,
                miiDeleteSlot = null,
                miiDeleteError = null,
            )

            PocketPassEvent.OpenThemePicker -> state.copy(themePickerExpanded = true)
            PocketPassEvent.ToggleSortMenu -> state.copy(sortMenuOpen = !state.sortMenuOpen)
            PocketPassEvent.CloseSortMenu -> state.copy(sortMenuOpen = false)

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

            is PocketPassEvent.OpenUserProfile,
            PocketPassEvent.CloseUserProfile,
            PocketPassEvent.RemoveProfileFriend,
            PocketPassEvent.CloseRemoveFriend,
            -> state.copy(removeFriendPromptVisible = false)

            PocketPassEvent.OpenRemoveFriend -> state.copy(removeFriendPromptVisible = true)

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

            else -> null
        }
}

private fun PocketPassUiState.pushRoute(route: PocketPassRoute): PocketPassUiState =
    if (routes.lastOrNull() == route) this else copy(routes = routes + route)

fun PocketPassUiState.blocksShoulderTabs(): Boolean {
    if (!hasDismissableLayer()) return false
    val activitiesOverlayOpen = shop.visible || games.visible || leaderboard.visible
    if (!activitiesOverlayOpen) return true
    val nestedDialogOpen = shop.buyPromptItemId != null ||
        games.bingoGoalIndex != null ||
        games.worldTourRegionsVisible ||
        games.puzzleBuyPromptVisible ||
        games.puzzleInfoVisible ||
        leaderboard.settingsVisible
    if (nestedDialogOpen) return true
    return copy(
        shop = shop.copy(visible = false),
        games = games.copy(visible = false),
        leaderboard = leaderboard.copy(visible = false),
    ).hasDismissableLayer()
}

fun PocketPassUiState.hasDismissableLayer(): Boolean =
    accountBan != null ||
    (rootDestination == PocketPassDestination.Messages) ||
    (accountSetup.resolved && accountSetup.required) ||
        profileViewer.visible ||
        shop.visible ||
        games.visible ||
        achievements.visible ||
        leaderboard.visible ||
        miiSlotsVisible ||
        connectedApps.visible ||
        connectedApps.revokeClientId != null ||
        oauthConsent.visible ||
        miiEditor.activeAdjustment != null ||
        miiEditor.colorPaletteOpen ||
        miiEditor.discardPromptVisible ||
        (miiEditor.isEditorVisible && miiEditor.mode == com.pocketpass.app.mii.MiiEditorMode.EditExisting) ||
        themePickerExpanded ||
        sortMenuOpen ||
        widgetMaker.hasOverlay ||
        deleteAccountVisible ||
        homeMoodPickerExpanded ||
        bioEditor.visible ||
        nameEditor.visible ||
        ageEditor.visible ||
        countryEditor.visible ||
        friendsOverlay != FriendsOverlay.None ||
        messageActionMessageId != null ||
        editingMessageId != null ||
        groupInfoOpen ||
        routes.size > 1

fun WidgetMakerUiState.closed(): WidgetMakerUiState = copy(
    assigningAppWidgetId = null,
    blockPicker = null,
    deletePromptVisible = false,
    renameDraft = null,
)

val PocketPassUiState.editingWidgetDesign: WidgetDesign?
    get() {
        val route = routes.lastOrNull { it is PocketPassRoute.WidgetEditor } as? PocketPassRoute.WidgetEditor
            ?: return null
        return widgetDesigns.firstOrNull { it.id == route.designId }
    }

fun PocketPassUiState.widgetDesignAfterPick(block: WidgetBlock?): WidgetDesign? {
    val design = editingWidgetDesign ?: return null
    return when (val slot = widgetMaker.blockPicker) {
        null -> null
        WidgetSlot.Hero -> design.withHero(block, design.updatedAtEpochMillis)
        is WidgetSlot.Tile -> design.withTile(slot.index, block, design.updatedAtEpochMillis)
    }
}

fun PocketPassUiState.widgetDesignAfterRename(): WidgetDesign? {
    val design = editingWidgetDesign ?: return null
    val draft = widgetMaker.renameDraft?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (draft == design.name) return null
    return design.withName(draft, design.updatedAtEpochMillis)
}
