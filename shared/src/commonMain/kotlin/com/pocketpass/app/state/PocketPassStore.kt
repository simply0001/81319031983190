package com.pocketpass.app.state

import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.NotificationAction
import com.pocketpass.app.domain.model.NotificationId
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.LoadState
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.showsPocketPassApp
import com.pocketpass.app.mii.MiiEditorEvent
import com.pocketpass.app.mii.MiiEditorMode
import com.pocketpass.app.model.AchievementsUiState
import com.pocketpass.app.model.BingoUiState
import com.pocketpass.app.model.ConnectedAppsUiState
import com.pocketpass.app.model.GameTarget
import com.pocketpass.app.model.GamesUiState
import com.pocketpass.app.model.LeaderboardUiState
import com.pocketpass.app.model.MessageComposerAction
import com.pocketpass.app.model.OAuthConsentUiState
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassReducer
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.ProfileViewerSource
import com.pocketpass.app.model.WorldTourUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The platform-neutral heart of the app: it owns PocketPassUiState, folds every feature
 * holder's state into it, and routes events to the reducer and the feature holders.
 * Each platform wraps it — Android in a ViewModel, iOS in the app's entry point.
 */
class PocketPassStore(
    private val container: PocketPassStoreContainer,
    private val statusFeed: StatusFeed,
    private val routeStore: RouteStateStore = NoRouteStateStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(
        PocketPassUiState(
            routes = routeStore.restore()
                ?.takeIf { it.firstOrNull() is PocketPassRoute.Root }
                ?: listOf(PocketPassRoute.Root(PocketPassDestination.Home)),
            integrityCompromised = container.integrityCompromised,
            miiEditorEnabled = container.miiEditorEnabled,
            pretendoImportEnabled = container.pretendoImportEnabled,
        ),
    )
    val state: StateFlow<PocketPassUiState> = _state.asStateFlow()

    init {
        collectFeatureState()
        persistRoutes()
        reopenRestoredConversation()
    }

    private fun persistRoutes() {
        scope.launch {
            _state.map { it.routes }.distinctUntilChanged().collect { routes ->
                routeStore.persist(routes)
            }
        }
    }

    private fun reopenRestoredConversation() {
        val routes = _state.value.routes
        val conversationId = routes
            .filterIsInstance<PocketPassRoute.MessageDetail>()
            .lastOrNull()
            ?.conversationId
        val composerOpen = routes.lastOrNull() is PocketPassRoute.NewGroup
        if (conversationId == null && !composerOpen) return
        scope.launch {
            container.activeAccountId.filterNotNull().first()
            if (
                conversationId != null &&
                container.messages.state.value.selectedConversationId?.value != conversationId
            ) {
                container.messages.openConversation(ConversationId(conversationId))
            }
            if (composerOpen) container.messages.openGroupComposer()
        }
    }

    fun dispatch(event: PocketPassEvent) {
        soundEffectFor(event, _state.value.rootDestination)?.let(container.soundEffects::play)
        when (event) {
            PocketPassEvent.Back -> {
                if (_state.value.shop.buyPromptItemId != null) {
                    _state.update { current ->
                        PocketPassReducer.reduce(current, PocketPassEvent.CloseBuyShopItem)
                    }
                    return
                }
                if (_state.value.removeFriendPromptVisible) {
                    _state.update { current ->
                        PocketPassReducer.reduce(current, PocketPassEvent.CloseRemoveFriend)
                    }
                    return
                }
                if (_state.value.sortMenuOpen) {
                    _state.update { current ->
                        PocketPassReducer.reduce(current, PocketPassEvent.CloseSortMenu)
                    }
                    return
                }
                val setupState = container.accountSetup.state.value
                if (setupState.required && setupState.resolved) {
                    container.accountSetup.backStep()
                    return
                }
                val miiState = container.miiEditor.state.value
                if (miiState.pretendoImport != null) {
                    container.miiEditor.dispatch(MiiEditorEvent.ClosePretendoImport)
                    return
                }
                if (miiState.isEditorVisible) {
                    when {
                        miiState.colorPaletteOpen ->
                            container.miiEditor.dispatch(MiiEditorEvent.CloseColorPalette)

                        miiState.activeAdjustment != null ->
                            container.miiEditor.dispatch(MiiEditorEvent.CloseAdjustment)

                        miiState.discardPromptVisible ->
                            container.miiEditor.dispatch(MiiEditorEvent.DismissDiscardPrompt)

                        miiState.mode == MiiEditorMode.EditExisting ->
                            container.miiEditor.dispatch(MiiEditorEvent.RequestCancel)

                        else -> Unit
                    }
                    return
                }
                if (container.connectedApps.dismissConsent()) return
                if (container.connectedApps.closeRevoke()) return
                if (container.connectedApps.close()) return
                if (container.profileViewer.close()) return
                if (container.shop.close()) return
                if (container.games.close()) return
                if (container.achievements.close()) return
                if (container.leaderboard.close()) return
                if (container.homeProfile.closeBioEditor()) return
                if (container.homeProfile.closeNameEditor()) return
                if (container.homeProfile.closeMoodPicker()) return
                if (container.friends.closeOverlay()) return
                if (container.messages.closeGroupInfo()) return
                if (container.messages.closeMessageActions()) return
                if (container.messages.closeActionRail()) return
                if (container.messages.cancelEdit()) return
                if (_state.value.routes.lastOrNull() is PocketPassRoute.MessageDetail) {
                    container.messages.closeConversation()
                }
                if (_state.value.routes.lastOrNull() is PocketPassRoute.NewGroup) {
                    container.messages.closeGroupComposer()
                }
            }

            PocketPassEvent.OpenConnectedApps -> container.connectedApps.open()
            PocketPassEvent.CloseConnectedApps -> container.connectedApps.close()
            is PocketPassEvent.OpenRevokeConnectedApp ->
                container.connectedApps.openRevoke(event.clientId)
            PocketPassEvent.CloseRevokeConnectedApp -> container.connectedApps.closeRevoke()
            PocketPassEvent.ConfirmRevokeConnectedApp -> container.connectedApps.confirmRevoke()
            PocketPassEvent.DismissOAuthConsent -> container.connectedApps.dismissConsent()
            PocketPassEvent.ApproveOAuthConsent -> container.connectedApps.decideConsent(true)
            PocketPassEvent.DenyOAuthConsent -> container.connectedApps.decideConsent(false)

            PocketPassEvent.OpenShop -> container.shop.open()
            PocketPassEvent.CloseShop -> container.shop.close()
            PocketPassEvent.ConfirmBuyShopItem ->
                _state.value.shop.buyPromptItemId?.let(container.shop::buy)

            PocketPassEvent.OpenGames -> container.games.open()
            PocketPassEvent.CloseGames -> container.games.close()
            is PocketPassEvent.OpenGame -> {
                container.games.openGame(event.game)
                when (event.game) {
                    GameTarget.WorldTour -> container.worldTour.refresh()
                    GameTarget.Bingo -> container.bingo.refresh()
                    GameTarget.PuzzleSwap -> Unit
                }
            }
            is PocketPassEvent.SelectBingoSquare ->
                container.games.selectBingoGoal(event.index)
            PocketPassEvent.CloseBingoSquare -> container.games.closeBingoGoal()
            PocketPassEvent.OpenWorldTourRegions -> container.games.openWorldTourRegions()
            PocketPassEvent.CloseWorldTourRegions -> container.games.closeWorldTourRegions()

            PocketPassEvent.OpenLeaderboard -> container.leaderboard.open()
            PocketPassEvent.CloseLeaderboard -> container.leaderboard.close()
            PocketPassEvent.OpenLeaderboardSettings ->
                container.leaderboard.openSettings()
            PocketPassEvent.CloseLeaderboardSettings ->
                container.leaderboard.closeSettings()
            is PocketPassEvent.SetLeaderboardScope ->
                container.leaderboard.setScope(event.scope)

            PocketPassEvent.OpenAchievements -> container.achievements.open()
            PocketPassEvent.CloseAchievements -> container.achievements.close()

            is PocketPassEvent.SelectDestination -> {
                container.miiEditor.dispatch(MiiEditorEvent.ClosePretendoImport)
                container.profileViewer.close()
                container.shop.close()
                container.games.reset()
                container.achievements.close()
                container.leaderboard.close()
                container.homeProfile.closeBioEditor()
                container.homeProfile.closeNameEditor()
                container.homeProfile.closeMoodPicker()
                container.friends.closeOverlay()
                container.messages.closeConversation()
                container.messages.closeGroupComposer()
                container.connectedApps.close()
            }

            PocketPassEvent.OpenMiiEditor -> {
                container.profileViewer.close()
                container.homeProfile.closeBioEditor()
                container.homeProfile.closeNameEditor()
                container.homeProfile.closeMoodPicker()
                container.friends.closeOverlay()
                container.messages.closeConversation()
                container.messages.closeGroupComposer()
            }

            PocketPassEvent.OpenBioEditor -> {
                container.profileViewer.close()
                container.friends.closeOverlay()
                container.homeProfile.openBioEditor()
            }

            PocketPassEvent.OpenNameEditor -> {
                container.profileViewer.close()
                container.homeProfile.openNameEditor()
            }

            is PocketPassEvent.OpenMessage -> {
                val conversationId = runCatching {
                    ConversationId(event.conversationId)
                }.getOrNull() ?: return
                if (_state.value.conversations.none { it.id == conversationId }) return
                container.messages.openConversation(conversationId)
            }

            is PocketPassEvent.OpenUserProfile -> {
                val profile = when (event.source) {
                    ProfileViewerSource.RecentInteraction ->
                        _state.value.recentInteractions
                            .firstOrNull {
                                it.profile.userId.value == event.userId
                            }
                            ?.profile

                    ProfileViewerSource.Friend ->
                        _state.value.friends
                            .firstOrNull {
                                it.profile.userId.value == event.userId
                            }
                            ?.profile
                } ?: return
                container.homeProfile.closeMoodPicker()
                container.friends.closeOverlay()
                container.profileViewer.open(profile, event.source)
            }

            PocketPassEvent.CloseUserProfile -> container.profileViewer.close()

            PocketPassEvent.SendProfileFriendRequest -> Unit

            is PocketPassEvent.OpenNotification -> {
                container.profileViewer.close()
                container.homeProfile.closeMoodPicker()
                val notificationId = runCatching {
                    NotificationId(event.notificationId)
                }.getOrNull() ?: return
                val notification = _state.value.notifications
                    .firstOrNull { it.id == notificationId }
                    ?: return
                container.notifications.markRead(notificationId)
                when (val action = notification.action) {
                    is NotificationAction.OpenConversation -> {
                        container.friends.closeOverlay()
                        container.messages.openConversation(action.conversationId)
                        _state.update {
                            it.copy(
                                routes = listOf(
                                    PocketPassRoute.Root(PocketPassDestination.Messages),
                                    PocketPassRoute.MessageDetail(action.conversationId.value),
                                ),
                            )
                        }
                    }

                    NotificationAction.OpenFriends -> {
                        container.friends.closeOverlay()
                        _state.update {
                            it.copy(
                                routes = listOf(
                                    PocketPassRoute.Root(PocketPassDestination.Friends),
                                ),
                            )
                        }
                    }

                    NotificationAction.OpenHome -> {
                        container.friends.closeOverlay()
                        _state.update {
                            it.copy(
                                routes = listOf(
                                    PocketPassRoute.Root(PocketPassDestination.Home),
                                ),
                            )
                        }
                    }

                    is NotificationAction.RespondToFriendRequest,
                    NotificationAction.None,
                    -> Unit
                }
            }

            else -> Unit
        }
        _state.update { current -> PocketPassReducer.reduce(current, event) }

        when (event) {
            is PocketPassEvent.Auth -> container.auth.dispatch(event.event)
            is PocketPassEvent.AccountSetup ->
                container.accountSetup.dispatch(event.event)
            is PocketPassEvent.Mii -> container.miiEditor.dispatch(event.event)
            PocketPassEvent.CloseMiiSlots ->
                container.miiEditor.dispatch(MiiEditorEvent.ClosePretendoImport)
            PocketPassEvent.OpenMiiEditor ->
                container.miiEditor.beginEdit(
                    container.miiEditor.state.value.activeSlot,
                )
            is PocketPassEvent.EditMiiSlot -> container.miiEditor.beginEdit(event.slot)
            is PocketPassEvent.WearShopItem -> {
                val shop = _state.value.shop
                val item = shop.item(event.itemId)
                val hat = item?.miiHatType
                if (
                    container.miiEditorEnabled &&
                    hat != null &&
                    (item.id in shop.ownedItemIds || item.id in shop.unlockedItemIds) &&
                    container.miiEditor.state.value.mode == MiiEditorMode.Inactive
                ) {
                    container.miiEditor.beginEdit(
                        slot = container.miiEditor.state.value.activeSlot,
                        wearHat = hat,
                    )
                }
            }
            is PocketPassEvent.SetActiveMiiSlot ->
                container.miiEditor.setActiveSlot(event.slot)
            PocketPassEvent.ConfirmDeleteMiiSlot -> scope.launch {
                val slot = _state.value.miiDeleteSlot ?: return@launch
                val result = container.deleteMiiSlot(slot)
                _state.update {
                    it.copy(
                        miiDeleteInProgress = false,
                        miiDeleteSlot = if (result is RepositoryResult.Failure) {
                            it.miiDeleteSlot
                        } else {
                            null
                        },
                        miiDeleteError = if (result is RepositoryResult.Failure) {
                            "Your Mii could not be deleted."
                        } else {
                            null
                        },
                    )
                }
            }
            PocketPassEvent.ShuffleActivities -> container.activities.toggle()
            PocketPassEvent.ToggleHomeMoodPicker ->
                container.homeProfile.toggleMoodPicker()
            is PocketPassEvent.SelectHomeMood ->
                container.homeProfile.selectMood(event.mood)
            PocketPassEvent.CloseHomeMoodPicker ->
                container.homeProfile.closeMoodPicker()
            is PocketPassEvent.UpdateBioDraft ->
                container.homeProfile.setBioDraft(event.value)
            PocketPassEvent.SaveBio -> container.homeProfile.saveBio()
            PocketPassEvent.CloseBioEditor -> container.homeProfile.closeBioEditor()
            is PocketPassEvent.UpdateNameDraft ->
                container.homeProfile.setNameDraft(event.value)
            PocketPassEvent.SaveName -> container.homeProfile.saveName()
            PocketPassEvent.CloseNameEditor -> container.homeProfile.closeNameEditor()
            is PocketPassEvent.UpdateMessageDraft ->
                container.messages.setDraft(event.value)

            PocketPassEvent.SendMessage -> container.messages.sendDraft()
            PocketPassEvent.ToggleMessageActions -> container.messages.toggleActionRail()
            is PocketPassEvent.RetryMessage -> runCatching {
                container.messages.retryMessage(MessageId(event.messageId))
            }

            is PocketPassEvent.SelectMessageAction -> when (event.action) {
                MessageComposerAction.Image -> container.messages.requestImageAttachment()
                MessageComposerAction.Emoji,
                MessageComposerAction.File,
                -> container.messages.closeActionRail()
            }

            is PocketPassEvent.OpenMessageActions -> runCatching {
                container.messages.openMessageActions(MessageId(event.messageId))
            }

            PocketPassEvent.CloseMessageActions -> container.messages.closeMessageActions()
            PocketPassEvent.EditSelectedMessage -> container.messages.editSelectedMessage()
            PocketPassEvent.DeleteSelectedMessage -> container.messages.deleteSelectedMessage()
            PocketPassEvent.CancelMessageEdit -> container.messages.cancelEdit()

            PocketPassEvent.OpenAddFriend -> {
                container.profileViewer.close()
                container.friends.openAddFriend()
            }
            PocketPassEvent.RefreshFriends -> container.friends.refreshFriends()
            is PocketPassEvent.OpenUserProfile -> Unit
            PocketPassEvent.CloseUserProfile -> Unit
            PocketPassEvent.SendProfileFriendRequest ->
                container.profileViewer.sendFriendRequest()
            PocketPassEvent.RemoveProfileFriend ->
                container.profileViewer.removeFriend()
            PocketPassEvent.MessageProfileFriend ->
                container.profileViewer.openConversation()
            PocketPassEvent.ToggleNotifications -> {
                container.profileViewer.close()
                container.homeProfile.closeMoodPicker()
                container.friends.toggleNotifications()
                container.notifications.refresh()
            }
            PocketPassEvent.CloseFriendsOverlay -> container.friends.closeOverlay()
            is PocketPassEvent.UpdateFriendCode ->
                container.friends.setEntry(event.value)
            PocketPassEvent.SubmitFriendCode -> container.friends.submitFriendCode()
            is PocketPassEvent.RespondToNotificationFriendRequest -> runCatching {
                container.notifications.respondToFriendRequest(
                    NotificationId(event.notificationId),
                    event.accept,
                )
            }
            is PocketPassEvent.DeleteNotification -> runCatching {
                container.notifications.delete(NotificationId(event.notificationId))
            }
            PocketPassEvent.MarkAllNotificationsRead ->
                container.notifications.markAllRead()
            PocketPassEvent.ClearAllNotifications ->
                container.notifications.clearAll()

            is PocketPassEvent.OpenNotification -> Unit

            is PocketPassEvent.SetNearby ->
                container.nearby.onNearbyPreferenceChanged(event.enabled)

            PocketPassEvent.RequestNearbyPermissions ->
                container.nearby.requestPermissions()

            PocketPassEvent.SkipNearbyPermissions ->
                container.nearby.skipOnboarding()

            is PocketPassEvent.SetSoundLevel -> scope.launch {
                container.settings.setSoundLevel(event.level)
            }

            is PocketPassEvent.SetSfxLevel -> scope.launch {
                container.settings.setSfxLevel(event.level)
            }

            is PocketPassEvent.SetThemeMode -> scope.launch {
                container.settings.setThemeMode(event.mode)
            }

            is PocketPassEvent.SetRecentInteractionsSort -> scope.launch {
                container.settings.setRecentInteractionsSort(event.sort)
            }

            is PocketPassEvent.SetFriendsSort -> scope.launch {
                container.settings.setFriendsSort(event.sort)
            }

            is PocketPassEvent.SetMoodEmojisEnabled -> scope.launch {
                container.settings.setMoodEmojisEnabled(event.enabled)
            }

            is PocketPassEvent.SetEncounterLedEnabled -> scope.launch {
                container.settings.setEncounterLedEnabled(event.enabled)
            }

            is PocketPassEvent.SetEncounterAlertsEnabled -> scope.launch {
                container.settings.setEncounterAlertsEnabled(event.enabled)
            }

            is PocketPassEvent.SetNearbyRepairAlertsEnabled -> scope.launch {
                container.settings.setNearbyRepairAlertsEnabled(event.enabled)
            }

            is PocketPassEvent.SetUpdateAlertsEnabled -> scope.launch {
                container.setUpdateAlertsEnabled(event.enabled)
            }

            is PocketPassEvent.SetStepRewardsEnabled ->
                container.stepRewards.onPreferenceChanged(event.enabled)

            PocketPassEvent.RequestStepRewardsPermission ->
                container.stepRewards.requestPermission()

            PocketPassEvent.ResetSettings -> scope.launch {
                container.resetSettings()
            }

            PocketPassEvent.CheckForAppUpdate -> container.appUpdate.check()

            PocketPassEvent.DownloadAppUpdate -> container.appUpdate.download()

            PocketPassEvent.InstallAppUpdate -> container.appUpdate.install()

            PocketPassEvent.ConfirmDeleteAccount -> scope.launch {
                container.profileViewer.close()
                container.homeProfile.resetSession()
                val result = container.deleteAccount()
                _state.update { current ->
                    current.copy(
                        deleteAccountInProgress = false,
                        deleteAccountVisible = result is RepositoryResult.Failure,
                        deleteAccountError = (result as? RepositoryResult.Failure)
                            ?.error
                            ?.deleteAccountMessage(),
                    )
                }
            }

            PocketPassEvent.SignOut -> scope.launch {
                container.profileViewer.close()
                container.homeProfile.resetSession()
                container.signOut()
            }

            PocketPassEvent.OpenNewGroup -> {
                if (_state.value.routes.lastOrNull() is PocketPassRoute.NewGroup) {
                    container.profileViewer.close()
                    container.friends.closeOverlay()
                    container.messages.openGroupComposer()
                }
            }
            PocketPassEvent.CloseNewGroup -> container.messages.closeGroupComposer()
            is PocketPassEvent.ToggleGroupMember -> runCatching {
                container.messages.toggleGroupMember(UserId(event.userId))
            }
            is PocketPassEvent.UpdateGroupTitle -> container.messages.setGroupTitle(event.value)
            PocketPassEvent.CreateGroup -> container.messages.createGroup()
            PocketPassEvent.OpenGroupInfo -> container.messages.openGroupInfo()
            PocketPassEvent.CloseGroupInfo -> container.messages.closeGroupInfo()
            is PocketPassEvent.AddGroupMembers -> runCatching {
                container.messages.addMembersToGroup(event.userIds.map(::UserId))
            }
            is PocketPassEvent.RemoveGroupMember -> runCatching {
                container.messages.removeGroupMember(UserId(event.userId))
            }
            PocketPassEvent.LeaveGroup -> container.messages.leaveGroup()
            is PocketPassEvent.RenameGroup -> container.messages.renameGroup(event.title)
            PocketPassEvent.DismissConversationNotice ->
                container.messages.clearConversationNotice()

            else -> Unit
        }
    }

    fun handleAuthCallback(callbackUri: String) {
        scope.launch {
            val result = container.handleAuthCallback(callbackUri)
            if (result is RepositoryResult.Success) {
                container.auth.clearTemporaryStateAfterAuthentication()
            }
        }
    }

    fun handleConsentLink(authorizationId: String) {
        container.connectedApps.handleConsentLink(authorizationId)
    }

    fun onAppOpened(openNearbyRepair: Boolean) {
        container.nearby.onAppOpened(openNearbyRepair)
    }

    fun onNearbyPermissionResult() {
        container.nearby.onPermissionResult()
    }

    fun onStepRewardsPermissionResult() {
        container.stepRewards.onPermissionResult()
    }

    private fun collectFeatureState() {
        scope.launch {
            container.miiEditor.state.collect { mii ->
                _state.update {
                    it.copy(
                        miiEditorEnabled = container.miiEditorEnabled,
                        pretendoImportEnabled = container.pretendoImportEnabled,
                        miiEditor = mii,
                        miiSlotsVisible = it.miiSlotsVisible && !mii.isEditorPresented,
                    )
                }
            }
        }
        scope.launch {
            container.homeProfile.state.collect { feature ->
                _state.update { current ->
                    current.copy(
                        profile = feature.profile.dataOr(current.profile),
                        homeMood = feature.selectedMood,
                        homeMoodPickerExpanded = feature.moodPickerExpanded,
                        homeMoodSelectionCount = feature.moodSelectionCount,
                        homeMoodActive = feature.moodActive,
                        bioEditor = feature.bioEditor,
                        nameEditor = feature.nameEditor,
                        recentInteractions = feature.recentInteractions.dataOr(
                            current.recentInteractions,
                        ),
                    )
                }
            }
        }
        scope.launch {
            container.profileViewer.state.collect { viewer ->
                _state.update { current ->
                    current.copy(profileViewer = viewer)
                }
            }
        }
        scope.launch {
            container.friends.state.collect { feature ->
                _state.update { current ->
                    val friends = feature.friends.dataOr(current.friends)
                    current.copy(
                        friends = friends,
                        onlineFriendCount = friends.count { it.isOnline },
                        friendsLoading = feature.friends is LoadState.Loading,
                        friendsRefreshing = feature.refreshing,
                        friendsRefreshError = feature.refreshError,
                        friendsOverlay = feature.overlay,
                        myFriendCode = feature.myFriendCode.dataOr(current.myFriendCode),
                        friendCodeEntry = feature.entry,
                        friendCodeSubmitting = feature.submitting,
                        friendCodeMessage = feature.message,
                        friendCodeError = feature.error,
                    )
                }
            }
        }
        scope.launch {
            container.notifications.state.collect { feature ->
                _state.update { current ->
                    current.copy(
                        notifications = feature.notifications.dataOr(current.notifications),
                        notificationOperationError = feature.error,
                    )
                }
            }
        }
        scope.launch {
            container.messages.state.collect { feature ->
                _state.update { current ->
                    val conversations = feature.conversations.dataOr(current.conversations)
                    val routes = if (
                        feature.conversationNotice != null &&
                        feature.selectedConversationId == null &&
                        current.routes.lastOrNull() is PocketPassRoute.MessageDetail
                    ) {
                        current.routes.dropLast(1)
                    } else {
                        current.routes
                    }
                    current.copy(
                        routes = routes,
                        conversations = conversations,
                        selectedConversationId = feature.selectedConversationId,
                        selectedConversation = feature.selectedConversation,
                        selectedMessages = feature.messages.dataOr(emptyList()),
                        messageDraft = feature.currentDraft,
                        messageActionRailExpanded = feature.actionRailExpanded,
                        messageSendInProgress = feature.isSending,
                        messageOperationError = feature.operationError,
                        messageActionMessageId = feature.actionMessageId?.value,
                        editingMessageId = feature.editingMessageId?.value,
                        groupComposer = feature.groupComposer,
                        groupInfoOpen = feature.groupInfoOpen,
                        groupOperationInProgress = feature.groupOperationInProgress,
                        groupOperationError = feature.groupOperationError,
                        conversationNotice = feature.conversationNotice,
                        selectedMembersById = feature.selectedMembersById,
                        typingUserIds = feature.typingUserIds,
                        isGroupOwner = feature.isGroupOwner,
                        canAddGroupMembers = feature.canAddGroupMembers,
                        messageTotalCount = feature.totalMessageCount,
                        typingConversationIds = feature.typingConversationIds
                            .mapTo(mutableSetOf()) { it.value },
                        unreadConversationCount = conversations.count { it.unreadCount > 0 },
                    )
                }
            }
        }
        scope.launch {
            container.activities.state.collect { feature ->
                _state.update { current ->
                    current.copy(
                        activityVariant = feature.variant,
                        activitySnapshot = feature.snapshot.dataOr(current.activitySnapshot),
                    )
                }
            }
        }
        scope.launch {
            container.shop.state.collect { feature ->
                _state.update { current ->
                    current.copy(
                        shop = current.shop.copy(
                            visible = feature.visible,
                            categories = feature.categories,
                            tokenBalance = feature.tokenBalance,
                            refreshError = feature.refreshError,
                            ownedItemIds = feature.ownedItemIds,
                            unlockedItemIds = feature.unlockedItemIds,
                            purchasingItemIds = feature.purchasingItemIds,
                            purchaseError = feature.purchaseError,
                            buyPromptItemId = current.shop.buyPromptItemId.takeIf { feature.visible },
                        ),
                    )
                }
            }
        }
        scope.launch {
            container.games.state.collect { feature ->
                _state.update { current ->
                    current.copy(
                        games = GamesUiState(
                            visible = feature.visible,
                            activeGame = feature.activeGame,
                            bingoGoalIndex = feature.bingoGoalIndex,
                            worldTourRegionsVisible = feature.worldTourRegionsVisible,
                        ),
                    )
                }
            }
        }
        scope.launch {
            container.leaderboard.state.collect { feature ->
                _state.update { current ->
                    current.copy(
                        leaderboard = LeaderboardUiState(
                            visible = feature.visible,
                            settingsVisible = feature.settingsVisible,
                            scope = feature.scope,
                            entries = feature.entries,
                            refreshError = feature.refreshError,
                        ),
                    )
                }
            }
        }
        scope.launch {
            container.connectedApps.state.collect { feature ->
                _state.update { current ->
                    current.copy(
                        connectedApps = ConnectedAppsUiState(
                            enabled = container.connectedApps.enabled,
                            visible = feature.visible,
                            loading = feature.loading,
                            apps = feature.apps,
                            error = feature.error,
                            revokeClientId = feature.revokeClientId,
                            revokeInProgress = feature.revokeInProgress,
                            revokeError = feature.revokeError,
                        ),
                        oauthConsent = OAuthConsentUiState(
                            visible = feature.consent.visible,
                            loading = feature.consent.loading,
                            request = feature.consent.request,
                            error = feature.consent.error,
                            deciding = feature.consent.deciding,
                        ),
                    )
                }
            }
        }
        scope.launch {
            container.achievements.state.collect { feature ->
                _state.update { current ->
                    current.copy(
                        achievements = AchievementsUiState(
                            visible = feature.visible,
                            achievements = feature.achievements,
                            refreshError = feature.refreshError,
                        ),
                    )
                }
            }
        }
        scope.launch {
            container.worldTour.state.collect { feature ->
                _state.update { current ->
                    current.copy(
                        worldTour = WorldTourUiState(
                            regions = feature.regions,
                            refreshError = feature.refreshError,
                        ),
                    )
                }
            }
        }
        scope.launch {
            container.bingo.state.collect { feature ->
                _state.update { current ->
                    current.copy(
                        bingo = BingoUiState(
                            cells = feature.cells,
                            refreshError = feature.refreshError,
                        ),
                    )
                }
            }
        }
        _state.update {
            it.copy(encounterLedSupported = container.encounterLedSupported)
        }
        scope.launch {
            container.settings.settings.collect { settings ->
                _state.update {
                    it.copy(
                        nearbyEnabled = settings.nearbyEnabled,
                        soundLevel = settings.soundLevel,
                        sfxLevel = settings.sfxLevel,
                        themeMode = settings.themeMode,
                        recentInteractionsSort = settings.recentInteractionsSort,
                        friendsSort = settings.friendsSort,
                        moodEmojisEnabled = settings.moodEmojisEnabled,
                        encounterLedEnabled = settings.encounterLedEnabled,
                        encounterAlertsEnabled = settings.encounterAlertsEnabled,
                        nearbyRepairAlertsEnabled = settings.nearbyRepairAlertsEnabled,
                        updateAlertsEnabled = settings.updateAlertsEnabled,
                        stepRewardsEnabled = settings.stepRewardsEnabled,
                    )
                }
            }
        }
        scope.launch {
            container.nearby.state.collect { nearby ->
                _state.update {
                    it.copy(
                        nearbyRuntime = nearby.runtime,
                        nearbyPermissionUi = nearby.permissionUi,
                    )
                }
            }
        }
        scope.launch {
            container.stepRewards.state.collect { steps ->
                _state.update { it.copy(stepRewards = steps) }
            }
        }
        scope.launch {
            container.appUpdate.state.collect { update ->
                _state.update { it.copy(appUpdate = update) }
            }
        }
        scope.launch {
            container.repositories.session.sessionState.collect { session ->
                _state.update { it.copy(sessionState = session) }
            }
        }
        scope.launch {
            container.auth.state.collect { auth ->
                _state.update { it.copy(auth = auth) }
            }
        }
        scope.launch {
            container.accountSetup.state.collect { setup ->
                _state.update { it.copy(accountSetup = setup) }
            }
        }
        scope.launch {
            container.repositories.sync.syncState.collect { sync ->
                _state.update { it.copy(syncState = sync) }
            }
        }
        scope.launch {
            statusFeed.status().collect { status ->
                dispatch(PocketPassEvent.StatusChanged(status))
            }
        }
        scope.launch {
            container.requestedAppUpdate.collect { requested ->
                if (!requested) return@collect
                _state.first { it.sessionState.showsPocketPassApp() && it.accountSetup.resolved }
                container.consumeRequestedAppUpdate()
                dispatch(PocketPassEvent.SelectDestination(PocketPassDestination.Settings))
                dispatch(PocketPassEvent.OpenAppUpdate)
            }
        }
        scope.launch {
            container.requestedConversation.collect { conversationId ->
                if (conversationId == null) return@collect
                container.consumeRequestedConversation()
                container.messages.awaitConversation(conversationId)
                _state.update {
                    it.copy(
                        routes = listOf(
                            PocketPassRoute.Root(PocketPassDestination.Messages),
                            PocketPassRoute.MessageDetail(conversationId.value),
                        ),
                    )
                }
            }
        }
    }
}

private fun RepositoryFailure.deleteAccountMessage(): String =
    when (kind) {
        RepositoryFailureKind.Offline ->
            "Connect to the internet to delete your account."

        RepositoryFailureKind.Unauthorized ->
            "Sign in again to delete your account."

        RepositoryFailureKind.Misconfigured ->
            "Account deletion is unavailable."

        else -> "Your account could not be deleted."
    }

private fun <T> LoadState<T>.dataOr(previous: T): T =
    when (this) {
        is LoadState.Data -> value
        is LoadState.Error -> cachedValue ?: previous
        LoadState.Loading -> previous
    }
