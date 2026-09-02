package com.pocketpass.app.feature

import com.pocketpass.app.data.LocalSettings
import com.pocketpass.app.data.SettingsRepository
import com.pocketpass.app.data.repository.FixtureAchievementsRepository
import com.pocketpass.app.data.repository.FixtureBingoRepository
import com.pocketpass.app.data.repository.FixtureLeaderboardRepository
import com.pocketpass.app.data.repository.FixtureWorldTourRepository
import com.pocketpass.app.data.repository.FixtureShopRepository
import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.data.repository.FixtureFriendsRepository
import com.pocketpass.app.data.repository.FixtureEncounterRepository
import com.pocketpass.app.data.repository.FixtureMessageRepository
import com.pocketpass.app.data.repository.FixtureNotificationRepository
import com.pocketpass.app.data.repository.FixtureProfileRepository
import com.pocketpass.app.data.repository.FixturePresenceRepository
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.ConversationKind
import com.pocketpass.app.domain.model.ConversationMember
import com.pocketpass.app.domain.model.ConversationMemberRole
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.AchievementState
import com.pocketpass.app.domain.model.BingoCell
import com.pocketpass.app.domain.model.LeaderboardEntry
import com.pocketpass.app.domain.model.LeaderboardScope
import com.pocketpass.app.domain.model.WorldTourRegion
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.domain.model.FriendRequestNotificationStatus
import com.pocketpass.app.domain.model.NotificationId
import com.pocketpass.app.domain.model.SendMessageCommand
import com.pocketpass.app.domain.model.DeleteMessageCommand
import com.pocketpass.app.domain.model.EditMessageCommand
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.PROFILE_NAME_RULE_MESSAGE
import com.pocketpass.app.domain.model.PROFILE_NAME_TAKEN_MESSAGE
import com.pocketpass.app.domain.repository.FriendsRepository
import com.pocketpass.app.domain.repository.EncounterRepository
import com.pocketpass.app.domain.repository.MessageRepository
import com.pocketpass.app.domain.state.LoadState
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.model.ActivityVariant
import com.pocketpass.app.model.BIO_MAX_LENGTH
import com.pocketpass.app.model.FriendsOverlay
import com.pocketpass.app.model.GameTarget
import com.pocketpass.app.model.HomeMood
import com.pocketpass.app.model.ProfileFriendRequestState
import com.pocketpass.app.model.ProfileViewerSource
import com.pocketpass.app.model.ThemeMode
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import com.pocketpass.app.domain.model.FriendProfileStats
import com.pocketpass.app.domain.repository.FriendProfileStatsSource
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureStateHoldersTest {
    @Test
    fun oneFriendsHolderSharesItsUpstreamAndDerivesOnlineCount() = runTest {
        val accountId = MutableStateFlow<UserId?>(FixtureData.CurrentUserId)
        val repository = CountingFriendsRepository(FixtureData.friends)
        val presence = FixturePresenceRepository()
        val holder = FriendsStateHolder(
            accountId = accountId,
            friendsRepository = repository,
            presenceRepository = presence,
            scope = backgroundScope,
        )

        runCurrent()

        val topDisplayState = holder.state
        val bottomDisplayState = holder.state
        assertSame(topDisplayState, bottomDisplayState)
        assertEquals(1, repository.observeCalls)
        assertEquals(1, holder.state.value.onlineCount)
        assertEquals(
            FixtureData.friends,
            (holder.state.value.friends as LoadState.Data).value,
        )

        FixtureData.friends.forEach {
            presence.setLocalPresence(it.profile.userId, com.pocketpass.app.domain.model.PresenceStatus.Online)
        }
        runCurrent()

        assertEquals(3, topDisplayState.value.onlineCount)
        assertEquals(3, bottomDisplayState.value.onlineCount)
        assertEquals(1, repository.observeCalls)
    }

    @Test
    fun friendsOverlayFiltersPastedDigitsAndSendsOneResolvedRequest() = runTest {
        val repository = FixtureFriendsRepository()
        val holder = FriendsStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            friendsRepository = repository,
            presenceRepository = FixturePresenceRepository(),
            scope = backgroundScope,
        )
        runCurrent()

        holder.openAddFriend()
        holder.setEntry("87a65 43-21")
        runCurrent()
        assertEquals(FriendsOverlay.AddFriend, holder.state.value.overlay)
        assertEquals("87654321", holder.state.value.entry)
        assertEquals(
            FixtureData.CurrentFriendCode,
            (holder.state.value.myFriendCode as LoadState.Data).value,
        )

        holder.toggleNotifications()
        runCurrent()
        assertEquals(FriendsOverlay.Notifications, holder.state.value.overlay)
        holder.openAddFriend()
        holder.submitFriendCode()
        runCurrent()

        assertEquals("Request sent.", holder.state.value.message)
        assertEquals("", holder.state.value.entry)
        assertTrue(
            (holder.state.value.friends as LoadState.Data)
                .value
                .none { it.profile.userId == FixtureData.SpobUserId },
        )
    }

    @Test
    fun friendsHolderFiltersPendingAndSortsOnlineBeforeDisplayName() = runTest {
        val ada = FixtureData.friends[0].copy(
            profile = FixtureData.friends[0].profile.copy(displayName = "Ada"),
            isOnline = false,
        )
        val zed = FixtureData.friends[1].copy(
            profile = FixtureData.friends[1].profile.copy(displayName = "zed"),
            isOnline = false,
        )
        val pending = FixtureData.friends[2].copy(
            status = com.pocketpass.app.domain.model.FriendshipStatus.PendingIncoming,
        )
        val presence = FixturePresenceRepository(emptyMap())
        val holder = FriendsStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            friendsRepository = CountingFriendsRepository(listOf(zed, pending, ada)),
            presenceRepository = presence,
            scope = backgroundScope,
        )
        runCurrent()

        assertEquals(
            listOf("Ada", "zed"),
            (holder.state.value.friends as LoadState.Data)
                .value
                .map { it.profile.displayName },
        )

        presence.setLocalPresence(zed.profile.userId, com.pocketpass.app.domain.model.PresenceStatus.Online)
        runCurrent()

        assertEquals(
            listOf("zed", "Ada"),
            (holder.state.value.friends as LoadState.Data)
                .value
                .map { it.profile.displayName },
        )
        assertEquals(1, holder.state.value.onlineCount)
    }

    @Test
    fun notificationInboxDerivesUnreadAndPersistsFriendResponseBeforeDelete() = runTest {
        val notifications = FixtureNotificationRepository()
        val holder = NotificationStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            notificationRepository = notifications,
            friendsRepository = FixtureFriendsRepository(),
            scope = backgroundScope,
        )
        runCurrent()

        assertEquals(2, holder.state.value.unreadCount)
        val requestId = NotificationId("fixture-friend-request")
        holder.respondToFriendRequest(requestId, accept = true)
        runCurrent()

        val accepted = (holder.state.value.notifications as LoadState.Data)
            .value
            .single { it.id == requestId }
        assertEquals(FriendRequestNotificationStatus.Accepted, accepted.friendRequestStatus)
        assertEquals(1, holder.state.value.unreadCount)

        holder.delete(requestId)
        runCurrent()
        assertTrue(
            (holder.state.value.notifications as LoadState.Data)
                .value
                .none { it.id == requestId },
        )

        holder.markAllRead()
        runCurrent()
        assertEquals(0, holder.state.value.unreadCount)

        holder.clearAll()
        runCurrent()
        assertTrue(
            (holder.state.value.notifications as LoadState.Data)
                .value
                .isEmpty(),
        )
    }

    @Test
    fun homeHolderCombinesOneProfileAndRecentInteractionStream() = runTest {
        val repository = CountingEncounterRepository(FixtureData.encounters)
        val holder = HomeProfileStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            profileRepository = FixtureProfileRepository(),
            encounterRepository = repository,
            settingsRepository = InMemorySettingsRepository(),
            scope = backgroundScope,
        )

        runCurrent()

        val state = holder.state.value
        assertEquals(
            FixtureData.currentProfile,
            (state.profile as LoadState.Data).value,
        )
        assertEquals(
            FixtureData.encounters,
            (state.recentInteractions as LoadState.Data).value,
        )
        assertEquals(1, repository.observeCalls)
    }

    @Test
    fun homeMoodPickerSelectsAndRetainsItsSessionMood() = runTest {
        val holder = HomeProfileStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            profileRepository = FixtureProfileRepository(),
            encounterRepository = FixtureEncounterRepository(),
            settingsRepository = InMemorySettingsRepository(),
            scope = backgroundScope,
        )
        runCurrent()

        assertEquals(HomeMood.Happy, holder.state.value.selectedMood)
        assertFalse(holder.state.value.moodPickerExpanded)
        assertEquals(0, holder.state.value.moodSelectionCount)

        holder.toggleMoodPicker()
        runCurrent()
        assertTrue(holder.state.value.moodPickerExpanded)

        holder.selectMood(HomeMood.Cool)
        runCurrent()
        assertEquals(HomeMood.Cool, holder.state.value.selectedMood)
        assertFalse(holder.state.value.moodPickerExpanded)
        assertEquals(1, holder.state.value.moodSelectionCount)

        holder.selectMood(HomeMood.Cool)
        runCurrent()
        assertEquals(2, holder.state.value.moodSelectionCount)

        holder.toggleMoodPicker()
        runCurrent()
        assertTrue(holder.closeMoodPicker())
        runCurrent()
        assertEquals(HomeMood.Cool, holder.state.value.selectedMood)
        assertFalse(holder.state.value.moodPickerExpanded)

        holder.resetSession()
        runCurrent()
        assertEquals(HomeMood.Happy, holder.state.value.selectedMood)
        assertFalse(holder.state.value.moodPickerExpanded)
        assertEquals(0, holder.state.value.moodSelectionCount)
    }

    @Test
    fun bioEditorSeedsFromProfileCapsDraftAndSavesThroughRepository() = runTest {
        val profiles = FixtureProfileRepository()
        val holder = HomeProfileStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            profileRepository = profiles,
            encounterRepository = FixtureEncounterRepository(),
            settingsRepository = InMemorySettingsRepository(),
            scope = backgroundScope,
        )
        runCurrent()

        assertFalse(holder.closeBioEditor())
        holder.setBioDraft("ignored while closed")
        holder.toggleMoodPicker()
        runCurrent()
        holder.openBioEditor()
        runCurrent()

        assertTrue(holder.state.value.bioEditor.visible)
        assertFalse(holder.state.value.moodPickerExpanded)
        assertEquals(
            FixtureData.currentProfile.bio,
            holder.state.value.bioEditor.draft,
        )

        holder.setBioDraft("a".repeat(BIO_MAX_LENGTH + 40))
        runCurrent()
        assertEquals(BIO_MAX_LENGTH, holder.state.value.bioEditor.draft.length)

        holder.setBioDraft("  Ready for the next encounter!  ")
        holder.saveBio()
        runCurrent()

        assertFalse(holder.state.value.bioEditor.visible)
        assertEquals(
            "Ready for the next encounter!",
            (holder.state.value.profile as LoadState.Data).value?.bio,
        )

        holder.openBioEditor()
        runCurrent()
        assertEquals(
            "Ready for the next encounter!",
            holder.state.value.bioEditor.draft,
        )
        assertTrue(holder.closeBioEditor())
        runCurrent()
        assertFalse(holder.state.value.bioEditor.visible)
    }

    @Test
    fun nameEditorSeedsFromUsernameFiltersAndSaves() = runTest {
        val holder = HomeProfileStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            profileRepository = FixtureProfileRepository(),
            encounterRepository = FixtureEncounterRepository(),
            settingsRepository = InMemorySettingsRepository(),
            scope = backgroundScope,
        )
        runCurrent()

        assertFalse(holder.closeNameEditor())
        holder.setNameDraft("ignored while closed")
        holder.toggleMoodPicker()
        runCurrent()
        holder.openNameEditor()
        runCurrent()

        assertTrue(holder.state.value.nameEditor.visible)
        assertFalse(holder.state.value.moodPickerExpanded)
        assertEquals(FixtureData.currentProfile.username, holder.state.value.nameEditor.draft)

        holder.setNameDraft("New Name!!")
        runCurrent()
        assertEquals("newname", holder.state.value.nameEditor.draft)

        holder.saveName()
        runCurrent()

        assertFalse(holder.state.value.nameEditor.visible)
        val profile = (holder.state.value.profile as LoadState.Data).value
        assertEquals("newname", profile?.username)
        assertEquals("newname", profile?.displayName)
    }

    @Test
    fun nameEditorKeepsTheDraftAndShowsTakenWhenTheNameIsInUse() = runTest {
        val holder = HomeProfileStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            profileRepository = FixtureProfileRepository(),
            encounterRepository = FixtureEncounterRepository(),
            settingsRepository = InMemorySettingsRepository(),
            scope = backgroundScope,
        )
        runCurrent()

        holder.openNameEditor()
        holder.setNameDraft(FixtureData.spobProfile.displayName)
        holder.saveName()
        runCurrent()

        val editor = holder.state.value.nameEditor
        assertTrue(editor.visible)
        assertFalse(editor.saving)
        assertEquals(FixtureData.spobProfile.displayName, editor.draft)
        assertEquals(PROFILE_NAME_TAKEN_MESSAGE, editor.error)
        assertEquals(1, editor.errorShakeNonce)
        assertEquals(
            FixtureData.currentProfile.username,
            (holder.state.value.profile as LoadState.Data).value?.username,
        )
    }

    @Test
    fun nameEditorRejectsInvalidDraftsAndClosesOnAnUnchangedName() = runTest {
        val holder = HomeProfileStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            profileRepository = FixtureProfileRepository(),
            encounterRepository = FixtureEncounterRepository(),
            settingsRepository = InMemorySettingsRepository(),
            scope = backgroundScope,
        )
        runCurrent()

        holder.openNameEditor()
        holder.setNameDraft("ab")
        holder.saveName()
        runCurrent()

        assertTrue(holder.state.value.nameEditor.visible)
        assertEquals(PROFILE_NAME_RULE_MESSAGE, holder.state.value.nameEditor.error)
        assertEquals(1, holder.state.value.nameEditor.errorShakeNonce)

        holder.setNameDraft(FixtureData.currentProfile.username)
        holder.saveName()
        runCurrent()

        assertFalse(holder.state.value.nameEditor.visible)
        assertEquals(
            FixtureData.currentProfile.updatedAt,
            (holder.state.value.profile as LoadState.Data).value?.updatedAt,
        )
    }

    @Test
    fun profileViewerOpensFromEncounterAndQueuesOneFriendRequest() = runTest {
        val friends = FixtureFriendsRepository()
        val holder = ProfileViewerStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            profileRepository = FixtureProfileRepository(),
            friendsRepository = friends,
            presenceRepository = FixturePresenceRepository(),
            scope = backgroundScope,
        )
        runCurrent()

        holder.open(
            profile = FixtureData.spobProfile,
            source = ProfileViewerSource.RecentInteraction,
        )
        runCurrent()

        assertTrue(holder.state.value.visible)
        assertEquals(FixtureData.spobProfile, holder.state.value.profile)
        assertEquals(
            ProfileFriendRequestState.Available,
            holder.state.value.friendRequestState,
        )

        holder.sendFriendRequest()
        runCurrent()

        assertEquals(
            ProfileFriendRequestState.Pending,
            holder.state.value.friendRequestState,
        )
        holder.sendFriendRequest()
        runCurrent()
        assertEquals(
            ProfileFriendRequestState.Pending,
            holder.state.value.friendRequestState,
        )
        assertTrue(holder.close())
        runCurrent()
        assertFalse(holder.state.value.visible)
    }

    @Test
    fun profileViewerUsesFriendPresenceAndHidesFriendRequestAction() = runTest {
        val friend = FixtureData.friends.first()
        val presence = FixturePresenceRepository(
            mapOf(friend.profile.userId to com.pocketpass.app.domain.model.PresenceStatus.Online),
        )
        val holder = ProfileViewerStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            profileRepository = FixtureProfileRepository(),
            friendsRepository = FixtureFriendsRepository(),
            presenceRepository = presence,
            scope = backgroundScope,
        )
        runCurrent()

        holder.open(friend.profile, ProfileViewerSource.Friend)
        runCurrent()

        assertTrue(holder.state.value.isOnline)
        assertEquals(
            ProfileFriendRequestState.Hidden,
            holder.state.value.friendRequestState,
        )
    }

    @Test
    fun profileViewerRemovesSeedWhenAuthoritativeProfileIsUnavailable() = runTest {
        val holder = ProfileViewerStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            profileRepository = FixtureProfileRepository(initialProfiles = emptyList()),
            friendsRepository = FixtureFriendsRepository(initialFriends = emptyList()),
            presenceRepository = FixturePresenceRepository(emptyMap()),
            scope = backgroundScope,
        )
        runCurrent()

        holder.open(
            profile = FixtureData.spobProfile,
            source = ProfileViewerSource.RecentInteraction,
        )
        runCurrent()

        assertTrue(holder.state.value.visible)
        assertTrue(holder.state.value.unavailable)
        assertEquals(null, holder.state.value.profile)
        assertEquals(
            ProfileFriendRequestState.Unavailable,
            holder.state.value.friendRequestState,
        )
    }

    @Test
    fun profileViewerWaitsForStatsBeforeShowingAndFallsBackAfterTimeout() = runTest {
        val stats = FriendProfileStats(encounterCount = 7, trophyCount = 3)
        val fastHolder = ProfileViewerStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            profileRepository = FixtureProfileRepository(),
            friendsRepository = FixtureFriendsRepository(),
            presenceRepository = FixturePresenceRepository(),
            scope = backgroundScope,
            statsSource = DelayedStatsSource(delayMillis = 120L, stats = stats),
        )
        runCurrent()

        fastHolder.open(FixtureData.spobProfile, ProfileViewerSource.RecentInteraction)
        runCurrent()
        assertFalse(fastHolder.state.value.visible)

        advanceTimeBy(121L)
        runCurrent()
        assertTrue(fastHolder.state.value.visible)
        assertEquals(stats, fastHolder.state.value.stats)
        assertFalse(fastHolder.state.value.statsPending)

        val slowHolder = ProfileViewerStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            profileRepository = FixtureProfileRepository(),
            friendsRepository = FixtureFriendsRepository(),
            presenceRepository = FixturePresenceRepository(),
            scope = backgroundScope,
            statsSource = DelayedStatsSource(delayMillis = 2_000L, stats = stats),
        )
        runCurrent()

        slowHolder.open(FixtureData.spobProfile, ProfileViewerSource.RecentInteraction)
        advanceTimeBy(701L)
        runCurrent()
        assertTrue(slowHolder.state.value.visible)
        assertEquals(null, slowHolder.state.value.stats)
        assertTrue(slowHolder.state.value.statsPending)

        advanceTimeBy(2_001L)
        runCurrent()
        assertEquals(stats, slowHolder.state.value.stats)
        assertFalse(slowHolder.state.value.statsPending)

        val cancelledHolder = ProfileViewerStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            profileRepository = FixtureProfileRepository(),
            friendsRepository = FixtureFriendsRepository(),
            presenceRepository = FixturePresenceRepository(),
            scope = backgroundScope,
            statsSource = DelayedStatsSource(delayMillis = 120L, stats = stats),
        )
        runCurrent()
        cancelledHolder.open(FixtureData.spobProfile, ProfileViewerSource.RecentInteraction)
        runCurrent()
        assertTrue(cancelledHolder.close())
        advanceTimeBy(200L)
        runCurrent()
        assertFalse(cancelledHolder.state.value.visible)
    }

    private class DelayedStatsSource(
        private val delayMillis: Long,
        private val stats: FriendProfileStats,
    ) : FriendProfileStatsSource {
        override suspend fun fetchFriendProfileStats(
            friendUserId: UserId,
        ): RepositoryResult<FriendProfileStats> {
            delay(delayMillis)
            return RepositoryResult.Success(stats)
        }

        override suspend fun openDirectConversation(
            friendUserId: UserId,
            clientOperationId: ClientOperationId,
        ): RepositoryResult<ConversationId> =
            RepositoryResult.Failure(RepositoryFailure(RepositoryFailureKind.Offline))
    }

    @Test
    fun messagesDeriveUnreadConversationBadgeFromConversationRows() = runTest {
        val repository = MutableMessageRepository(
            listOf(
                conversation("read", unreadCount = 0),
                conversation("one", unreadCount = 7),
                conversation("two", unreadCount = 1),
            ),
        )
        val holder = MessagesStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            conversationRepository = repository,
            scope = backgroundScope,
        )

        runCurrent()

        assertEquals(2, holder.state.value.unreadConversationCount)
        assertEquals(8, holder.state.value.totalMessageCount)

        repository.conversations.value = listOf(
            conversation("read", unreadCount = 0),
            conversation("one", unreadCount = 0),
            conversation("two", unreadCount = 4),
        )
        runCurrent()

        assertEquals(1, holder.state.value.unreadConversationCount)
        assertEquals(4, holder.state.value.totalMessageCount)
    }

    @Test
    fun messagesKeepPerConversationDraftsMarkReadAndSendOptimistically() = runTest {
        val repository = FixtureMessageRepository(
            clock = { Instant.parse("2026-01-01T12:47:00Z") },
        )
        val holder = MessagesStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            conversationRepository = repository,
            scope = backgroundScope,
        )
        runCurrent()

        holder.openConversation(FixtureData.SpobConversationId)
        runCurrent()
        assertEquals(FixtureData.SpobConversationId, holder.state.value.selectedConversationId)
        assertEquals(0, holder.state.value.selectedConversation?.unreadCount)
        assertEquals(listOf("Hey bro", "yo"), holder.state.value.messages.data().map { it.body })
        holder.toggleActionRail()
        runCurrent()
        assertTrue(holder.state.value.actionRailExpanded)
        assertTrue(holder.closeActionRail())
        runCurrent()

        holder.setDraft("spob draft")
        runCurrent()
        holder.closeConversation()
        holder.openConversation(FixtureData.SansConversationId)
        holder.setDraft("sans draft")
        runCurrent()
        holder.closeConversation()
        holder.openConversation(FixtureData.SpobConversationId)
        runCurrent()
        assertEquals("spob draft", holder.state.value.currentDraft)

        holder.sendDraft()
        runCurrent()
        assertEquals("", holder.state.value.currentDraft)
        assertEquals("spob draft", holder.state.value.messages.data().last().body)
    }

    @Test
    fun messagesOpenTheActionSheetOnlyForOwnSyncedMessages() = runTest {
        val holder = MessagesStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            conversationRepository = FixtureMessageRepository(),
            scope = backgroundScope,
        )
        runCurrent()
        holder.openConversation(FixtureData.SpobConversationId)
        runCurrent()
        val (incoming, outgoing) = holder.state.value.messages.data()

        holder.openMessageActions(incoming.id)
        runCurrent()
        assertEquals(null, holder.state.value.actionMessageId)

        holder.openMessageActions(outgoing.id)
        runCurrent()
        assertEquals(outgoing.id, holder.state.value.actionMessageId)
        assertTrue(holder.closeMessageActions())
        runCurrent()
        assertEquals(null, holder.state.value.actionMessageId)
        assertEquals(false, holder.closeMessageActions())
    }

    @Test
    fun messagesEditPrefillsSavesAndRestoresTheStashedDraft() = runTest {
        val editedAt = Instant.parse("2026-01-01T12:47:00Z")
        val holder = MessagesStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            conversationRepository = FixtureMessageRepository(clock = { editedAt }),
            scope = backgroundScope,
        )
        runCurrent()
        holder.openConversation(FixtureData.SpobConversationId)
        runCurrent()
        val outgoing = holder.state.value.messages.data().last()
        holder.setDraft("half typed")
        runCurrent()

        holder.openMessageActions(outgoing.id)
        holder.editSelectedMessage()
        runCurrent()
        assertEquals(null, holder.state.value.actionMessageId)
        assertEquals(outgoing.id, holder.state.value.editingMessageId)
        assertEquals("yo", holder.state.value.currentDraft)

        holder.setDraft("yo!")
        holder.sendDraft()
        runCurrent()
        assertEquals(null, holder.state.value.editingMessageId)
        assertEquals("half typed", holder.state.value.currentDraft)
        val messages = holder.state.value.messages.data()
        assertEquals(2, messages.size)
        assertEquals("yo!", messages.last().body)
        assertEquals(editedAt, messages.last().editedAt)
    }

    @Test
    fun messagesCancelledOrUnchangedEditRestoresThePreviousDraft() = runTest {
        val holder = MessagesStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            conversationRepository = FixtureMessageRepository(),
            scope = backgroundScope,
        )
        runCurrent()
        holder.openConversation(FixtureData.SpobConversationId)
        runCurrent()
        val outgoing = holder.state.value.messages.data().last()
        holder.setDraft("keep me")
        runCurrent()

        holder.openMessageActions(outgoing.id)
        holder.editSelectedMessage()
        runCurrent()
        assertTrue(holder.cancelEdit())
        runCurrent()
        assertEquals(null, holder.state.value.editingMessageId)
        assertEquals("keep me", holder.state.value.currentDraft)
        assertEquals(false, holder.cancelEdit())

        holder.openMessageActions(outgoing.id)
        holder.editSelectedMessage()
        runCurrent()
        holder.sendDraft()
        runCurrent()
        assertEquals(null, holder.state.value.editingMessageId)
        assertEquals("keep me", holder.state.value.currentDraft)
        assertEquals(null, holder.state.value.messages.data().last().editedAt)
    }

    @Test
    fun messagesDeleteHidesTheRowAndRecomputesThePreview() = runTest {
        val holder = MessagesStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            conversationRepository = FixtureMessageRepository(),
            scope = backgroundScope,
        )
        runCurrent()
        holder.openConversation(FixtureData.SpobConversationId)
        runCurrent()
        val outgoing = holder.state.value.messages.data().last()

        holder.openMessageActions(outgoing.id)
        holder.deleteSelectedMessage()
        runCurrent()

        assertEquals(null, holder.state.value.actionMessageId)
        assertEquals(listOf("Hey bro"), holder.state.value.messages.data().map { it.body })
        assertEquals("Hey bro", holder.state.value.selectedConversation?.latestMessagePreview)
    }

    @Test
    fun activitiesVariantSurvivesAccountAndTabLikeObservationChanges() = runTest {
        val accountId = MutableStateFlow<UserId?>(FixtureData.CurrentUserId)
        val holder = ActivitiesStateHolder(
            accountId = accountId,
            shopRepository = FixtureShopRepository(),
            leaderboardRepository = FixtureLeaderboardRepository(),
            worldTourRepository = FixtureWorldTourRepository(),
            scope = backgroundScope,
        )
        runCurrent()

        holder.toggle()
        runCurrent()
        assertEquals(ActivityVariant.Shuffled, holder.variant.value)
        assertEquals(ActivityVariant.Shuffled, holder.state.value.variant)

        val stateObservedAfterReturningToActivities = holder.state
        assertSame(holder.state, stateObservedAfterReturningToActivities)
        assertEquals(ActivityVariant.Shuffled, stateObservedAfterReturningToActivities.value.variant)

        accountId.value = null
        runCurrent()
        accountId.value = UserId("another-account")
        runCurrent()

        assertEquals(ActivityVariant.Shuffled, holder.state.value.variant)
        holder.toggle()
        runCurrent()
        assertEquals(ActivityVariant.Default, holder.state.value.variant)
    }

    @Test
    fun leaderboardOpensWithServerEntriesAndClosesOnlyWhenVisible() = runTest {
        val accountId = MutableStateFlow<UserId?>(FixtureData.CurrentUserId)
        val holder = LeaderboardStateHolder(
            accountId = accountId,
            leaderboardRepository = FixtureLeaderboardRepository(),
            settingsRepository = InMemorySettingsRepository(),
            scope = backgroundScope,
        )
        runCurrent()

        assertEquals(false, holder.state.value.visible)
        assertEquals(false, holder.close())

        holder.open()
        runCurrent()
        assertEquals(true, holder.state.value.visible)
        assertEquals(FixtureData.leaderboard, holder.state.value.entries)
        assertEquals(null, holder.state.value.refreshError)

        assertEquals(true, holder.close())
        runCurrent()
        assertEquals(false, holder.state.value.visible)

        accountId.value = null
        runCurrent()
        assertEquals(emptyList<LeaderboardEntry>(), holder.state.value.entries)
    }

    @Test
    fun leaderboardScopePersistsAndSettingsLayerClosesFirst() = runTest {
        val accountId = MutableStateFlow<UserId?>(FixtureData.CurrentUserId)
        val settingsRepository = InMemorySettingsRepository()
        val holder = LeaderboardStateHolder(
            accountId = accountId,
            leaderboardRepository = FixtureLeaderboardRepository(),
            settingsRepository = settingsRepository,
            scope = backgroundScope,
        )
        runCurrent()

        assertEquals(LeaderboardScope.Friends, holder.state.value.scope)

        holder.open()
        holder.openSettings()
        runCurrent()
        assertEquals(true, holder.state.value.settingsVisible)

        holder.setScope(LeaderboardScope.Global)
        runCurrent()
        assertEquals(LeaderboardScope.Global, holder.state.value.scope)

        assertEquals(true, holder.close())
        runCurrent()
        assertEquals(false, holder.state.value.settingsVisible)
        assertEquals(true, holder.state.value.visible)

        assertEquals(true, holder.close())
        runCurrent()
        assertEquals(false, holder.state.value.visible)
    }

    @Test
    fun achievementsOpenWithServerStatesAndCloseOnlyWhenVisible() = runTest {
        val accountId = MutableStateFlow<UserId?>(FixtureData.CurrentUserId)
        val holder = AchievementsStateHolder(
            accountId = accountId,
            achievementsRepository = FixtureAchievementsRepository(),
            scope = backgroundScope,
        )
        runCurrent()

        assertEquals(false, holder.state.value.visible)
        assertEquals(false, holder.close())

        holder.open()
        runCurrent()
        assertEquals(true, holder.state.value.visible)
        assertEquals(FixtureData.achievements, holder.state.value.achievements)
        assertEquals(null, holder.state.value.refreshError)

        assertEquals(true, holder.close())
        runCurrent()
        assertEquals(false, holder.state.value.visible)

        accountId.value = null
        runCurrent()
        assertEquals(emptyList<AchievementState>(), holder.state.value.achievements)
    }

    @Test
    fun worldTourExposesServerRegionsAndClearsOnSignOut() = runTest {
        val accountId = MutableStateFlow<UserId?>(FixtureData.CurrentUserId)
        val holder = WorldTourStateHolder(
            accountId = accountId,
            worldTourRepository = FixtureWorldTourRepository(),
            scope = backgroundScope,
        )
        runCurrent()

        assertEquals(FixtureData.worldTourRegions, holder.state.value.regions)
        assertEquals(null, holder.state.value.refreshError)

        holder.refresh()
        runCurrent()
        assertEquals(null, holder.state.value.refreshError)

        accountId.value = null
        runCurrent()
        assertEquals(emptyList<WorldTourRegion>(), holder.state.value.regions)
    }

    @Test
    fun bingoExposesServerBoardAndClearsOnSignOut() = runTest {
        val accountId = MutableStateFlow<UserId?>(FixtureData.CurrentUserId)
        val holder = BingoStateHolder(
            accountId = accountId,
            bingoRepository = FixtureBingoRepository(),
            scope = backgroundScope,
        )
        runCurrent()

        assertEquals(FixtureData.bingoBoard, holder.state.value.cells)
        assertEquals(null, holder.state.value.refreshError)

        holder.refresh()
        runCurrent()
        assertEquals(null, holder.state.value.refreshError)

        accountId.value = null
        runCurrent()
        assertEquals(emptyList<BingoCell>(), holder.state.value.cells)
    }

    @Test
    fun gamesHolderClosesLayersInStagesAndResetClearsEverything() = runTest {
        val holder = GamesStateHolder()

        assertEquals(false, holder.close())

        holder.openGame(GameTarget.Bingo)
        holder.selectBingoGoal(7)
        assertEquals(true, holder.state.value.visible)
        assertEquals(GameTarget.Bingo, holder.state.value.activeGame)
        assertEquals(7, holder.state.value.bingoGoalIndex)

        assertEquals(true, holder.close())
        assertEquals(GameTarget.Bingo, holder.state.value.activeGame)
        assertEquals(null, holder.state.value.bingoGoalIndex)

        assertEquals(true, holder.close())
        assertEquals(null, holder.state.value.activeGame)
        assertEquals(true, holder.state.value.visible)

        assertEquals(true, holder.close())
        assertEquals(false, holder.state.value.visible)
        assertEquals(false, holder.close())

        holder.openGame(GameTarget.WorldTour)
        holder.selectBingoGoal(3)
        assertEquals(null, holder.state.value.bingoGoalIndex)

        holder.openWorldTourRegions()
        assertEquals(true, holder.state.value.worldTourRegionsVisible)

        assertEquals(true, holder.close())
        assertEquals(false, holder.state.value.worldTourRegionsVisible)
        assertEquals(GameTarget.WorldTour, holder.state.value.activeGame)

        holder.openGame(GameTarget.Bingo)
        holder.openWorldTourRegions()
        assertEquals(false, holder.state.value.worldTourRegionsVisible)

        holder.reset()
        assertEquals(GamesFeatureState(), holder.state.value)
    }

    @Test
    fun settingsHolderUsesRepositoryBoundaryAndResetRestoresDefaults() = runTest {
        val repository: SettingsRepository = InMemorySettingsRepository()
        val holder = SettingsStateHolder(repository, backgroundScope)
        runCurrent()

        holder.setNearby(false)
        holder.setSoundLevel(0.8f)
        holder.setThemeMode(ThemeMode.Dark)
        holder.setMoodEmojisEnabled(false)
        holder.setEncounterLedEnabled(false)
        holder.setEncounterAlertsEnabled(false)
        holder.setNearbyRepairAlertsEnabled(false)
        holder.setStepRewardsEnabled(true)
        runCurrent()

        assertEquals(
            LocalSettings(
                nearbyEnabled = false,
                soundLevel = 0.8f,
                themeMode = ThemeMode.Dark,
                moodEmojisEnabled = false,
                encounterLedEnabled = false,
                encounterAlertsEnabled = false,
                nearbyRepairAlertsEnabled = false,
                stepRewardsEnabled = true,
            ),
            holder.settings.value,
        )

        holder.resetSettings()
        runCurrent()

        assertEquals(LocalSettings(), holder.settings.value)
        assertTrue(holder.settings.value.nearbyEnabled)
    }

    @Test
    fun groupComposerTogglesMembersCapsSelectionAndCreatesTheGroup() = runTest {
        val repository = FixtureMessageRepository(
            clock = { Instant.parse("2026-01-01T12:47:00Z") },
        )
        var created: ConversationId? = null
        val holder = MessagesStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            conversationRepository = repository,
            scope = backgroundScope,
            onGroupCreated = { created = it },
        )
        runCurrent()

        holder.openGroupComposer()
        holder.toggleGroupMember(UserId("matt-1"))
        holder.toggleGroupMember(UserId("matt-2"))
        holder.toggleGroupMember(UserId("matt-1"))
        holder.toggleGroupMember(UserId("matt-1"))
        holder.setGroupTitle("  Trip  ")
        runCurrent()
        val composer = requireNotNull(holder.state.value.groupComposer)
        assertEquals(setOf(UserId("matt-1"), UserId("matt-2")), composer.selectedMemberIds)
        assertTrue(composer.canSubmit)

        holder.createGroup()
        runCurrent()

        val group = holder.state.value.conversations.data().first { it.id == created }
        assertEquals(null, holder.state.value.groupComposer)
        assertEquals("Trip", group.title)
        assertEquals(ConversationKind.Group, group.kind)
        assertEquals(3, group.memberCount)
        assertEquals(FixtureData.CurrentUserId, group.ownerId)
    }

    @Test
    fun groupComposerRefusesToSubmitWithoutTitleOrMembers() = runTest {
        var created: ConversationId? = null
        val holder = MessagesStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            conversationRepository = FixtureMessageRepository(),
            scope = backgroundScope,
            onGroupCreated = { created = it },
        )
        runCurrent()

        holder.openGroupComposer()
        holder.setGroupTitle("Only a title")
        runCurrent()
        assertFalse(requireNotNull(holder.state.value.groupComposer).canSubmit)
        holder.createGroup()
        runCurrent()
        assertEquals(null, created)

        holder.setGroupTitle("   ")
        holder.toggleGroupMember(UserId("matt-1"))
        runCurrent()
        assertFalse(requireNotNull(holder.state.value.groupComposer).canSubmit)
        assertTrue(holder.closeGroupComposer())
        assertFalse(holder.closeGroupComposer())
    }

    @Test
    fun groupInfoGatesOwnerActionsAndSurfacesServerErrors() = runTest {
        val holder = MessagesStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            conversationRepository = FixtureMessageRepository(),
            scope = backgroundScope,
        )
        runCurrent()
        holder.openConversation(FixtureData.CrewConversationId)
        runCurrent()
        assertTrue(holder.state.value.isGroupOwner)
        assertTrue(holder.state.value.canAddGroupMembers)
        assertEquals("spob", holder.state.value.selectedMembersById[FixtureData.SpobUserId]?.displayName)

        holder.openGroupInfo()
        runCurrent()
        assertTrue(holder.state.value.groupInfoOpen)
        holder.removeGroupMember(FixtureData.SansUserId)
        runCurrent()
        assertEquals(2, holder.state.value.selectedConversation?.memberCount)
        assertEquals(null, holder.state.value.groupOperationError)

        val memberHolder = MessagesStateHolder(
            accountId = flowOf(FixtureData.SpobUserId),
            conversationRepository = FixtureMessageRepository(accountId = FixtureData.SpobUserId),
            scope = backgroundScope,
        )
        runCurrent()
        memberHolder.openConversation(FixtureData.CrewConversationId)
        runCurrent()
        assertFalse(memberHolder.state.value.isGroupOwner)
        memberHolder.renameGroup("Nope")
        runCurrent()
        assertEquals("crew", memberHolder.state.value.selectedConversation?.title)

        val unavailable = MutableMessageRepository(listOf(groupConversation("crew")))
        val failingHolder = MessagesStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            conversationRepository = unavailable,
            scope = backgroundScope,
        )
        runCurrent()
        failingHolder.openConversation(ConversationId("crew"))
        runCurrent()
        failingHolder.addMembersToGroup(listOf(UserId("matt-3")))
        runCurrent()
        assertEquals("The group could not be updated.", failingHolder.state.value.groupOperationError)
        assertFalse(failingHolder.state.value.groupOperationInProgress)
    }

    @Test
    fun leavingAGroupClosesTheThreadAndRaisesANotice() = runTest {
        val holder = MessagesStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            conversationRepository = FixtureMessageRepository(),
            scope = backgroundScope,
        )
        runCurrent()
        holder.openConversation(FixtureData.CrewConversationId)
        holder.openGroupInfo()
        runCurrent()

        holder.leaveGroup()
        runCurrent()

        assertEquals(null, holder.state.value.selectedConversationId)
        assertFalse(holder.state.value.groupInfoOpen)
        assertEquals("You left crew", holder.state.value.conversationNotice)
        assertTrue(holder.state.value.conversations.data().none { it.id == FixtureData.CrewConversationId })
        holder.clearConversationNotice()
        runCurrent()
        assertEquals(null, holder.state.value.conversationNotice)
    }

    @Test
    fun removedConversationAutoClosesAnObservedSelection() = runTest {
        val repository = MutableMessageRepository(
            listOf(conversation("one", unreadCount = 0), conversation("two", unreadCount = 0)),
        )
        val holder = MessagesStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            conversationRepository = repository,
            scope = backgroundScope,
        )
        runCurrent()
        holder.openConversation(ConversationId("one"))
        runCurrent()

        repository.conversations.value = listOf(conversation("two", unreadCount = 0))
        runCurrent()
        assertEquals(null, holder.state.value.selectedConversationId)
        assertEquals("You're no longer in this conversation", holder.state.value.conversationNotice)

        holder.openConversation(ConversationId("ghost"))
        runCurrent()
        repository.conversations.value = listOf(
            conversation("two", unreadCount = 0),
            conversation("three", unreadCount = 0),
        )
        runCurrent()
        assertEquals(ConversationId("ghost"), holder.state.value.selectedConversationId)
    }

    private fun groupConversation(id: String) = ConversationSummary(
        id = ConversationId(id),
        title = id,
        avatar = null,
        latestMessagePreview = "",
        latestMessageAt = Instant.parse("2026-01-01T00:00:00Z"),
        unreadCount = 0,
        kind = ConversationKind.Group,
        members = listOf(
            ConversationMember(
                userId = FixtureData.CurrentUserId,
                displayName = "Me",
                avatar = null,
                role = ConversationMemberRole.Owner,
                joinedAt = Instant.fromEpochSeconds(0),
            ),
            ConversationMember(
                userId = FixtureData.SpobUserId,
                displayName = "spob",
                avatar = null,
                role = ConversationMemberRole.Member,
                joinedAt = Instant.fromEpochSeconds(0),
            ),
        ),
    )

    private fun conversation(
        id: String,
        unreadCount: Int,
    ) = ConversationSummary(
        id = ConversationId(id),
        title = id,
        avatar = null,
        latestMessagePreview = "preview",
        latestMessageAt = Instant.parse("2026-01-01T00:00:00Z"),
        unreadCount = unreadCount,
    )

    private class CountingFriendsRepository(
        initialFriends: List<Friend>,
    ) : FriendsRepository {
        val values = MutableStateFlow(initialFriends)
        var observeCalls = 0
            private set

        override fun observeFriends(accountId: UserId): Flow<List<Friend>> {
            observeCalls += 1
            return values
        }

        override suspend fun refreshFriends(accountId: UserId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }

    private class CountingEncounterRepository(
        initialEncounters: List<NearbyEncounter>,
    ) : EncounterRepository {
        val values = MutableStateFlow(initialEncounters)
        var observeCalls = 0
            private set

        override fun observeRecent(accountId: UserId): Flow<List<NearbyEncounter>> {
            observeCalls += 1
            return values
        }

        override suspend fun refresh(accountId: UserId): RepositoryResult<Unit> =
            RepositoryResult.Success(Unit)
    }

    private class MutableMessageRepository(
        initialConversations: List<ConversationSummary>,
    ) : MessageRepository {
        val conversations = MutableStateFlow(initialConversations)

        override fun observeConversations(
            accountId: UserId,
        ): Flow<List<ConversationSummary>> = conversations

        override fun observeMessages(
            accountId: UserId,
            conversationId: ConversationId,
        ): Flow<List<Message>> = flowOf(emptyList())

        override suspend fun refreshConversations(
            accountId: UserId,
        ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun refreshMessages(
            accountId: UserId,
            conversationId: ConversationId,
        ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

        override suspend fun sendMessage(
            command: SendMessageCommand,
        ): RepositoryResult<Message> = error("Not used by this state-holder test")

        override suspend fun editMessage(
            command: EditMessageCommand,
        ): RepositoryResult<Message> = error("Not used by this state-holder test")

        override suspend fun deleteMessage(
            command: DeleteMessageCommand,
        ): RepositoryResult<Message> = error("Not used by this state-holder test")
    }

    private class InMemorySettingsRepository : SettingsRepository {
        private val mutableSettings = MutableStateFlow(LocalSettings())
        override val settings: Flow<LocalSettings> = mutableSettings

        override suspend fun setNearby(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(nearbyEnabled = enabled)
        }

        override suspend fun setNearbyOnboardingCompleted(completed: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(
                nearbyOnboardingCompleted = completed,
            )
        }

        override suspend fun setSoundLevel(level: Float) {
            mutableSettings.value = mutableSettings.value.copy(
                soundLevel = level.coerceIn(0f, 1f),
            )
        }

        override suspend fun setSfxLevel(level: Float) {
            mutableSettings.value = mutableSettings.value.copy(
                sfxLevel = level.coerceIn(0f, 1f),
            )
        }

        override suspend fun setThemeMode(mode: ThemeMode) {
            mutableSettings.value = mutableSettings.value.copy(themeMode = mode)
        }

        override suspend fun setMoodEmojisEnabled(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(
                moodEmojisEnabled = enabled,
            )
        }

        override suspend fun setHomeMood(mood: com.pocketpass.app.model.HomeMood?) {
            mutableSettings.value = mutableSettings.value.copy(homeMood = mood)
        }

        override suspend fun setEncounterLedEnabled(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(
                encounterLedEnabled = enabled,
            )
        }

        override suspend fun setEncounterAlertsEnabled(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(
                encounterAlertsEnabled = enabled,
            )
        }

        override suspend fun setNearbyRepairAlertsEnabled(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(
                nearbyRepairAlertsEnabled = enabled,
            )
        }

        override suspend fun setUpdateAlertsEnabled(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(
                updateAlertsEnabled = enabled,
            )
        }

        override suspend fun setStepRewardsEnabled(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(
                stepRewardsEnabled = enabled,
            )
        }

        override suspend fun setLastNotifiedUpdateVersionCode(versionCode: Int) {
            mutableSettings.value = mutableSettings.value.copy(
                lastNotifiedUpdateVersionCode = versionCode,
            )
        }

        override suspend fun setLeaderboardScope(scope: LeaderboardScope) {
            mutableSettings.value = mutableSettings.value.copy(
                leaderboardScope = scope,
            )
        }

        override suspend fun setRecentInteractionsSort(
            sort: com.pocketpass.app.model.RecentInteractionsSort,
        ) {
            mutableSettings.value = mutableSettings.value.copy(
                recentInteractionsSort = sort,
            )
        }

        override suspend fun setFriendsSort(
            sort: com.pocketpass.app.model.RecentInteractionsSort,
        ) {
            mutableSettings.value = mutableSettings.value.copy(friendsSort = sort)
        }

        override suspend fun setLastSeenMinSupportedVersionCode(versionCode: Int) {
            mutableSettings.value = mutableSettings.value.copy(
                lastSeenMinSupportedVersionCode = versionCode,
            )
        }

        override suspend fun setNearbyAlertsSeenThrough(epochMillis: Long) {
            mutableSettings.value = mutableSettings.value.copy(
                nearbyAlertsSeenThroughEpochMillis = epochMillis,
            )
        }

        override suspend fun resetSettings() {
            mutableSettings.value = LocalSettings()
        }
    }
}

private fun <T> LoadState<T>.data(): T =
    (this as LoadState.Data).value
