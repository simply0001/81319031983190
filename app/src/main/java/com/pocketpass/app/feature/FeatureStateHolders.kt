package com.pocketpass.app.feature

import com.pocketpass.app.data.LocalSettings
import com.pocketpass.app.data.SettingsRepository
import com.pocketpass.app.domain.model.ActivitySnapshot
import com.pocketpass.app.domain.model.AchievementState
import com.pocketpass.app.domain.model.BingoCell
import com.pocketpass.app.domain.model.LeaderboardEntry
import com.pocketpass.app.domain.model.LeaderboardScope
import com.pocketpass.app.domain.model.WorldTourRegion
import com.pocketpass.app.domain.model.OwnedShopItem
import com.pocketpass.app.domain.model.PurchaseShopItemCommand
import com.pocketpass.app.domain.model.ShopCategory
import com.pocketpass.app.domain.model.ShopItem
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.ConversationMember
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.AddGroupMembersCommand
import com.pocketpass.app.domain.model.CreateGroupConversationCommand
import com.pocketpass.app.domain.model.GROUP_TITLE_MAX_LENGTH
import com.pocketpass.app.domain.model.LeaveGroupConversationCommand
import com.pocketpass.app.domain.model.MAX_GROUP_MEMBERS
import com.pocketpass.app.domain.model.RemoveGroupMemberCommand
import com.pocketpass.app.domain.model.RenameGroupConversationCommand
import com.pocketpass.app.model.GroupComposerState
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.FriendCode
import com.pocketpass.app.domain.model.FriendshipStatus
import com.pocketpass.app.domain.model.DeleteMessageCommand
import com.pocketpass.app.domain.model.EditMessageCommand
import com.pocketpass.app.domain.model.MarkConversationReadCommand
import com.pocketpass.app.domain.model.IMAGE_MESSAGE_PLACEHOLDER_BODY
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.MessageAttachment
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.domain.model.PresenceStatus
import com.pocketpass.app.domain.model.SendMessageCommand
import com.pocketpass.app.domain.model.SendFriendRequestCommand
import com.pocketpass.app.domain.model.UpdateProfileCommand
import com.pocketpass.app.domain.model.RenameProfileCommand
import com.pocketpass.app.domain.model.PROFILE_NAME_RULE_MESSAGE
import com.pocketpass.app.domain.model.PROFILE_NAME_TAKEN_MESSAGE
import com.pocketpass.app.domain.model.filterProfileNameInput
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.repository.FriendProfileStatsSource
import com.pocketpass.app.domain.repository.FriendsRepository
import com.pocketpass.app.domain.repository.EncounterRepository
import com.pocketpass.app.domain.repository.MessageRepository
import com.pocketpass.app.domain.repository.MutableFriendsRepository
import com.pocketpass.app.domain.repository.MutableProfileRepository
import com.pocketpass.app.domain.repository.ProfileRepository
import com.pocketpass.app.domain.repository.PresenceRepository
import com.pocketpass.app.domain.repository.AchievementsRepository
import com.pocketpass.app.domain.repository.BingoRepository
import com.pocketpass.app.domain.repository.LeaderboardRepository
import com.pocketpass.app.domain.repository.WorldTourRepository
import com.pocketpass.app.domain.repository.ShopRepository
import com.pocketpass.app.domain.state.LoadState
import com.pocketpass.app.domain.state.PendingState
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.model.ActivityVariant
import com.pocketpass.app.model.BIO_MAX_LENGTH
import com.pocketpass.app.model.BioEditorUiState
import com.pocketpass.app.model.NameEditorUiState
import com.pocketpass.app.model.FriendsOverlay
import com.pocketpass.app.model.GameTarget
import com.pocketpass.app.model.HomeMood
import com.pocketpass.app.model.ProfileFriendRequestState
import com.pocketpass.app.model.ProfileViewerSource
import com.pocketpass.app.model.RecentInteractionsSort
import com.pocketpass.app.model.ProfileViewerUiState
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val FeatureSharing = SharingStarted.Eagerly

data class HomeProfileFeatureState(
    val profile: LoadState<UserProfile?> = LoadState.Loading,
    val recentInteractions: LoadState<List<NearbyEncounter>> = LoadState.Loading,
    val selectedMood: HomeMood = HomeMood.Happy,
    val moodPickerExpanded: Boolean = false,
    val moodSelectionCount: Int = 0,
    val moodActive: Boolean = false,
    val bioEditor: BioEditorUiState = BioEditorUiState(),
    val nameEditor: NameEditorUiState = NameEditorUiState(),
)

private data class MoodSelectionState(
    val mood: HomeMood? = null,
    val selections: Int = 0,
)

private data class ResolvedMood(
    val mood: HomeMood,
    val selections: Int,
    val active: Boolean,
)

class HomeProfileStateHolder(
    accountId: Flow<UserId?>,
    private val profileRepository: ProfileRepository,
    encounterRepository: EncounterRepository,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val moodSelection = MutableStateFlow(MoodSelectionState())
    private val resolvedMood = combine(
        moodSelection,
        settingsRepository.settings.map { it.homeMood }.distinctUntilChanged(),
    ) { selection, persisted ->
        ResolvedMood(
            mood = selection.mood ?: persisted ?: HomeMood.Happy,
            selections = selection.selections,
            active = selection.mood != null || persisted != null,
        )
    }
    private val moodPickerExpanded = MutableStateFlow(false)
    private val bioEditor = MutableStateFlow(BioEditorUiState())
    private val nameEditor = MutableStateFlow(NameEditorUiState())
    private val activeAccountId = accountId.stateIn(
        scope = scope,
        started = FeatureSharing,
        initialValue = null,
    )

    val state: StateFlow<HomeProfileFeatureState> = combine(
        accountId.switchAccount<LoadState<UserProfile?>>(
            signedOut = LoadState.Data(null),
        ) { id ->
            profileRepository.observeProfile(id)
                .map<UserProfile?, LoadState<UserProfile?>> { LoadState.Data(it) }
        },
        accountId.switchAccount<LoadState<List<NearbyEncounter>>>(
            signedOut = LoadState.Data(emptyList()),
        ) { id ->
            encounterRepository.observeRecent(id)
                .map<List<NearbyEncounter>, LoadState<List<NearbyEncounter>>> {
                    LoadState.Data(it)
                }
        },
        resolvedMood,
        moodPickerExpanded,
        combine(bioEditor, nameEditor, ::Pair),
    ) { profile, recentInteractions, mood, expanded, (bio, name) ->
        HomeProfileFeatureState(
            profile = profile,
            recentInteractions = recentInteractions,
            selectedMood = mood.mood,
            moodPickerExpanded = expanded,
            moodSelectionCount = mood.selections,
            moodActive = mood.active,
            bioEditor = bio,
            nameEditor = name,
        )
    }.stateIn(
        scope = scope,
        started = FeatureSharing,
        initialValue = HomeProfileFeatureState(),
    )

    fun toggleMoodPicker() {
        moodPickerExpanded.update(Boolean::not)
    }

    fun selectMood(mood: HomeMood) {
        moodSelection.update {
            MoodSelectionState(mood = mood, selections = it.selections + 1)
        }
        moodPickerExpanded.value = false
        scope.launch { settingsRepository.setHomeMood(mood) }
    }

    fun closeMoodPicker(): Boolean {
        if (!moodPickerExpanded.value) return false
        moodPickerExpanded.value = false
        return true
    }

    fun openBioEditor() {
        moodPickerExpanded.value = false
        val bio = (state.value.profile as? LoadState.Data)?.value?.bio.orEmpty()
        bioEditor.value = BioEditorUiState(
            visible = true,
            draft = bio.take(BIO_MAX_LENGTH),
        )
    }

    fun setBioDraft(value: String) {
        bioEditor.update { editor ->
            if (!editor.visible || editor.saving) return@update editor
            editor.copy(draft = value.take(BIO_MAX_LENGTH), error = null)
        }
    }

    fun closeBioEditor(): Boolean {
        if (!bioEditor.value.visible) return false
        bioEditor.value = BioEditorUiState()
        return true
    }

    fun saveBio() {
        val editor = bioEditor.value
        if (!editor.visible || editor.saving) return
        val account = activeAccountId.value ?: return
        val repository = profileRepository as? MutableProfileRepository ?: return
        val profile = (state.value.profile as? LoadState.Data)?.value ?: return
        if (profile.userId != account) return
        bioEditor.update { it.copy(saving = true, error = null) }
        scope.launch {
            val now = Clock.System.now()
            val result = repository.updateProfile(
                UpdateProfileCommand(
                    accountId = account,
                    profile = profile.copy(
                        bio = editor.draft.trim().take(BIO_MAX_LENGTH),
                        updatedAt = now,
                    ),
                    changedAt = now,
                ),
            )
            when (result) {
                is RepositoryResult.Success -> bioEditor.value = BioEditorUiState()
                is RepositoryResult.Failure -> bioEditor.update {
                    it.copy(saving = false, error = "Your bio could not be saved.")
                }
            }
        }
    }

    fun openNameEditor() {
        moodPickerExpanded.value = false
        val profile = (state.value.profile as? LoadState.Data)?.value
        val current = profile?.username?.ifBlank { null } ?: profile?.displayName.orEmpty()
        nameEditor.value = NameEditorUiState(
            visible = true,
            draft = filterProfileNameInput(current),
        )
    }

    fun setNameDraft(value: String) {
        nameEditor.update { editor ->
            if (!editor.visible || editor.saving) return@update editor
            editor.copy(draft = filterProfileNameInput(value), error = null)
        }
    }

    fun closeNameEditor(): Boolean {
        if (!nameEditor.value.visible) return false
        nameEditor.value = NameEditorUiState()
        return true
    }

    fun saveName() {
        val editor = nameEditor.value
        if (!editor.visible || editor.saving) return
        if (!editor.valid) {
            nameEditor.value = editor.copy(
                error = PROFILE_NAME_RULE_MESSAGE,
                errorShakeNonce = editor.errorShakeNonce + 1,
            )
            return
        }
        val account = activeAccountId.value ?: return
        val repository = profileRepository as? MutableProfileRepository ?: return
        val profile = (state.value.profile as? LoadState.Data)?.value ?: return
        if (profile.userId != account) return
        if (editor.draft == profile.username) {
            nameEditor.value = NameEditorUiState()
            return
        }
        nameEditor.value = editor.copy(saving = true, error = null)
        scope.launch {
            val result = repository.renameProfile(
                RenameProfileCommand(
                    accountId = account,
                    name = editor.draft,
                    changedAt = Clock.System.now(),
                ),
            )
            when (result) {
                is RepositoryResult.Success -> nameEditor.value = NameEditorUiState()
                is RepositoryResult.Failure -> nameEditor.update {
                    it.copy(
                        saving = false,
                        error = when (result.error.kind) {
                            RepositoryFailureKind.Conflict -> PROFILE_NAME_TAKEN_MESSAGE
                            RepositoryFailureKind.Offline ->
                                "You're offline. Connect to change your name."
                            else -> "Your name could not be changed. Try again."
                        },
                        errorShakeNonce = it.errorShakeNonce + 1,
                    )
                }
            }
        }
    }

    fun resetSession() {
        moodSelection.value = MoodSelectionState()
        moodPickerExpanded.value = false
        bioEditor.value = BioEditorUiState()
        nameEditor.value = NameEditorUiState()
        scope.launch { settingsRepository.setHomeMood(null) }
    }
}

private const val STATS_PRELOAD_TIMEOUT_MS = 700L

class ProfileViewerStateHolder(
    accountId: Flow<UserId?>,
    private val profileRepository: ProfileRepository,
    private val friendsRepository: FriendsRepository,
    private val presenceRepository: PresenceRepository,
    private val scope: CoroutineScope,
    private val statsSource: FriendProfileStatsSource? = null,
    private val onOpenConversation: (ConversationId) -> Unit = {},
) {
    private val activeAccountId = accountId.stateIn(
        scope = scope,
        started = FeatureSharing,
        initialValue = null,
    )
    private val relationships = MutableStateFlow<List<Friend>>(emptyList())
    private var pendingOpen: Job? = null
    private var statsJob: Job? = null
    private val _state = MutableStateFlow(ProfileViewerUiState())
    val state: StateFlow<ProfileViewerUiState> = _state
    private var profileObservation: Job? = null
    private var profileRefresh: Job? = null
    private var presenceObservation: Job? = null
    private var presenceSnapshot: Map<UserId, PresenceStatus> = emptyMap()

    init {
        scope.launch {
            activeAccountId.collectLatest { account ->
                close()
                relationships.value = emptyList()
                if (account == null) return@collectLatest
                friendsRepository.observeFriends(account).collect { rows ->
                    relationships.value = rows
                    updateRelationshipState()
                }
            }
        }
    }

    fun open(profile: UserProfile, source: ProfileViewerSource) {
        cancelSelectionWork()
        presenceSnapshot = emptyMap()
        val stats = statsSource
        if (stats == null) {
            show(profile, source, stats = null, statsPending = false)
            return
        }
        pendingOpen = scope.launch {
            val result = withTimeoutOrNull(STATS_PRELOAD_TIMEOUT_MS) {
                stats.fetchFriendProfileStats(profile.userId)
            }
            pendingOpen = null
            show(
                profile = profile,
                source = source,
                stats = (result as? RepositoryResult.Success)?.value,
                statsPending = result == null,
            )
            if (result == null) loadStats(profile.userId)
        }
    }

    private fun show(
        profile: UserProfile,
        source: ProfileViewerSource,
        stats: com.pocketpass.app.domain.model.FriendProfileStats?,
        statsPending: Boolean,
    ) {
        _state.value = ProfileViewerUiState(
            selectedUserId = profile.userId.value,
            source = source,
            profile = profile,
            stats = stats,
            statsPending = statsPending,
        )
        updateRelationshipState()
        observeProfile(profile.userId)
        observePresence(profile.userId)
        refreshProfile(profile.userId)
    }

    private fun loadStats(userId: UserId) {
        val source = statsSource ?: return
        statsJob = scope.launch {
            val result = source.fetchFriendProfileStats(userId)
            if (_state.value.selectedUserId != userId.value) return@launch
            _state.update {
                it.copy(
                    stats = (result as? RepositoryResult.Success)?.value ?: it.stats,
                    statsPending = false,
                )
            }
        }
    }

    fun removeFriend() {
        val current = _state.value
        val account = activeAccountId.value ?: return
        val target = current.profile ?: return
        val repository = friendsRepository as? MutableFriendsRepository ?: return
        if (current.actionInProgress) return
        _state.update { it.copy(actionInProgress = true, actionError = null) }
        scope.launch {
            val result = repository.removeFriend(
                com.pocketpass.app.domain.model.RemoveFriendCommand(
                    accountId = account,
                    friendUserId = target.userId,
                    removedAt = Clock.System.now(),
                ),
            )
            when (result) {
                is RepositoryResult.Success -> close()
                is RepositoryResult.Failure -> _state.update {
                    it.copy(
                        actionInProgress = false,
                        actionError = "This friend couldn’t be removed.",
                    )
                }
            }
        }
    }

    fun openConversation() {
        val current = _state.value
        val target = current.profile ?: return
        val source = statsSource ?: return
        if (current.actionInProgress) return
        _state.update { it.copy(actionInProgress = true, actionError = null) }
        scope.launch {
            val result = source.openDirectConversation(
                friendUserId = target.userId,
                clientOperationId = com.pocketpass.app.domain.model.ClientOperationId.new(),
            )
            _state.update { it.copy(actionInProgress = false) }
            when (result) {
                is RepositoryResult.Success -> {
                    close()
                    onOpenConversation(result.value)
                }

                is RepositoryResult.Failure -> _state.update {
                    it.copy(actionError = "That conversation couldn’t be opened.")
                }
            }
        }
    }

    fun close(): Boolean {
        val opening = pendingOpen != null
        if (!opening && !_state.value.visible) return false
        cancelSelectionWork()
        presenceSnapshot = emptyMap()
        _state.value = ProfileViewerUiState()
        return true
    }

    fun sendFriendRequest() {
        val current = _state.value
        if (
            current.source != ProfileViewerSource.RecentInteraction ||
            current.friendRequestState !in setOf(
                ProfileFriendRequestState.Available,
                ProfileFriendRequestState.Failed,
            )
        ) {
            return
        }
        val account = activeAccountId.value ?: return
        val addressee = current.profile ?: return
        val repository = friendsRepository as? MutableFriendsRepository ?: run {
            _state.update {
                it.copy(
                    friendRequestState = ProfileFriendRequestState.Failed,
                    friendRequestError = "Friend requests are unavailable.",
                )
            }
            return
        }
        _state.update {
            it.copy(
                friendRequestState = ProfileFriendRequestState.Sending,
                friendRequestError = null,
            )
        }
        scope.launch {
            val result = repository.sendFriendRequest(
                SendFriendRequestCommand(
                    accountId = account,
                    addressee = addressee,
                    requestedAt = Clock.System.now(),
                ),
            )
            if (_state.value.selectedUserId != addressee.userId.value) return@launch
            when (result) {
                is RepositoryResult.Success -> {
                    _state.update {
                        it.copy(
                            friendRequestState = when (result.value.status) {
                                FriendshipStatus.Accepted ->
                                    ProfileFriendRequestState.Friends

                                FriendshipStatus.Blocked ->
                                    ProfileFriendRequestState.Unavailable

                                FriendshipStatus.PendingIncoming,
                                FriendshipStatus.PendingOutgoing,
                                -> ProfileFriendRequestState.Pending
                            },
                            friendRequestError = null,
                        )
                    }
                }

                is RepositoryResult.Failure -> {
                    _state.update {
                        when (result.error.kind) {
                            RepositoryFailureKind.Conflict ->
                                it.copy(
                                    friendRequestState =
                                        ProfileFriendRequestState.Pending,
                                    friendRequestError = null,
                                )

                            RepositoryFailureKind.Forbidden,
                            RepositoryFailureKind.NotFound,
                            ->
                                it.copy(
                                    friendRequestState =
                                        ProfileFriendRequestState.Unavailable,
                                    friendRequestError =
                                        "This profile can’t receive friend requests.",
                                )

                            else ->
                                it.copy(
                                    friendRequestState =
                                        ProfileFriendRequestState.Failed,
                                    friendRequestError =
                                        result.error.profileRequestMessage(),
                                )
                        }
                    }
                }
            }
        }
    }

    private fun observeProfile(userId: UserId) {
        profileObservation = scope.launch {
            var observedProfile = false
            profileRepository.observeProfile(userId).collect { profile ->
                if (_state.value.selectedUserId != userId.value) return@collect
                if (profile != null) {
                    observedProfile = true
                    _state.update {
                        it.copy(profile = profile, unavailable = false)
                    }
                } else if (observedProfile) {
                    _state.update {
                        it.copy(
                            profile = null,
                            unavailable = true,
                            isOnline = false,
                            friendRequestState =
                                ProfileFriendRequestState.Unavailable,
                        )
                    }
                }
            }
        }
    }

    private fun refreshProfile(userId: UserId) {
        profileRefresh = scope.launch {
            when (val result = profileRepository.refreshProfile(userId)) {
                is RepositoryResult.Success -> {
                    val refreshed = profileRepository.observeProfile(userId).first()
                    if (
                        refreshed == null &&
                        _state.value.selectedUserId == userId.value
                    ) {
                        _state.update {
                            it.copy(
                                profile = null,
                                unavailable = true,
                                isOnline = false,
                                friendRequestState =
                                    ProfileFriendRequestState.Unavailable,
                            )
                        }
                    }
                }

                is RepositoryResult.Failure -> {
                    if (
                        result.error.kind in setOf(
                            RepositoryFailureKind.Forbidden,
                            RepositoryFailureKind.NotFound,
                            RepositoryFailureKind.Unauthorized,
                        ) &&
                        _state.value.selectedUserId == userId.value
                    ) {
                        _state.update {
                            it.copy(
                                profile = null,
                                unavailable = true,
                                isOnline = false,
                                friendRequestState =
                                    ProfileFriendRequestState.Unavailable,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun observePresence(userId: UserId) {
        presenceObservation = scope.launch {
            presenceRepository.observePresence(setOf(userId)).collect { snapshot ->
                presenceSnapshot = snapshot
                updateRelationshipState()
            }
        }
    }

    private fun updateRelationshipState() {
        val current = _state.value
        val userId = current.selectedUserId?.let(::UserId) ?: return
        val relationship = relationships.value
            .firstOrNull { it.profile.userId == userId }
        val accepted = relationship?.status == FriendshipStatus.Accepted
        val requestState = when {
            current.source == ProfileViewerSource.Friend ->
                ProfileFriendRequestState.Hidden

            relationship?.status == FriendshipStatus.Accepted ->
                ProfileFriendRequestState.Friends

            relationship?.status == FriendshipStatus.PendingIncoming ||
                relationship?.status == FriendshipStatus.PendingOutgoing ->
                ProfileFriendRequestState.Pending

            relationship?.status == FriendshipStatus.Blocked ->
                ProfileFriendRequestState.Unavailable

            current.friendRequestState == ProfileFriendRequestState.Sending ||
                current.friendRequestState == ProfileFriendRequestState.Failed ->
                current.friendRequestState

            else -> ProfileFriendRequestState.Available
        }
        _state.update {
            it.copy(
                isOnline =
                    accepted &&
                        presenceSnapshot[userId] == PresenceStatus.Online,
                friendRequestState = requestState,
                friendRequestError = if (
                    requestState == ProfileFriendRequestState.Failed
                ) {
                    it.friendRequestError
                } else {
                    null
                },
            )
        }
    }

    private fun cancelSelectionWork() {
        pendingOpen?.cancel()
        pendingOpen = null
        profileObservation?.cancel()
        profileRefresh?.cancel()
        presenceObservation?.cancel()
        statsJob?.cancel()
        profileObservation = null
        profileRefresh = null
        presenceObservation = null
        statsJob = null
    }
}

data class FriendsFeatureState(
    val friends: LoadState<List<Friend>> = LoadState.Loading,
    val myFriendCode: LoadState<FriendCode?> = LoadState.Loading,
    val refreshing: Boolean = false,
    val refreshError: String? = null,
    val overlay: FriendsOverlay = FriendsOverlay.None,
    val entry: String = "",
    val submitting: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val onlineCount: Int
        get() = (friends as? LoadState.Data)?.value?.count(Friend::isOnline) ?: 0
}

class FriendsStateHolder(
    accountId: Flow<UserId?>,
    private val friendsRepository: FriendsRepository,
    private val presenceRepository: PresenceRepository,
    private val scope: CoroutineScope,
) {
    private val activeAccountId = accountId.stateIn(
        scope = scope,
        started = FeatureSharing,
        initialValue = null,
    )
    private val friends = MutableStateFlow<LoadState<List<Friend>>>(LoadState.Loading)
    private val livePresence = MutableStateFlow<Map<UserId, PresenceStatus>>(emptyMap())
    private val friendCode = MutableStateFlow<LoadState<FriendCode?>>(LoadState.Loading)
    private val refreshing = MutableStateFlow(false)
    private val refreshError = MutableStateFlow<String?>(null)
    private val overlay = MutableStateFlow(FriendsOverlay.None)
    private val entry = MutableStateFlow("")
    private val submitting = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)
    private var presenceObservation: Job? = null
    private var observedPresenceIds: Set<UserId> = emptySet()

    private val visibleFriends = combine(friends, livePresence) { friendState, presence ->
        fun List<Friend>.withLivePresence(): List<Friend> =
            asSequence()
                .filter { it.status == FriendshipStatus.Accepted }
                .map { friend ->
                    friend.copy(
                        isOnline = presence[friend.profile.userId] == PresenceStatus.Online,
                    )
                }
                .sortedWith(
                    compareByDescending<Friend>(Friend::isOnline)
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.profile.displayName }
                        .thenBy { it.profile.userId.value },
                )
                .toList()

        when (friendState) {
            LoadState.Loading -> LoadState.Loading
            is LoadState.Data -> LoadState.Data(
                value = friendState.value.withLivePresence(),
                isRefreshing = friendState.isRefreshing,
            )
            is LoadState.Error -> LoadState.Error(
                failure = friendState.failure,
                cachedValue = friendState.cachedValue?.withLivePresence(),
            )
        }
    }

    private val primaryState = combine(
        visibleFriends,
        friendCode,
        overlay,
        entry,
        submitting,
    ) { friendRows, ownCode, activeOverlay, codeEntry, isSubmitting ->
        FriendsFeatureState(
            friends = friendRows,
            myFriendCode = ownCode,
            overlay = activeOverlay,
            entry = codeEntry,
            submitting = isSubmitting,
        )
    }

    private val operationState = combine(
        primaryState,
        message,
        error,
    ) { primary, statusMessage, operationError ->
        primary.copy(message = statusMessage, error = operationError)
    }

    val state: StateFlow<FriendsFeatureState> = combine(
        operationState,
        refreshing,
        refreshError,
    ) { primary, isRefreshing, currentRefreshError ->
        primary.copy(
            refreshing = isRefreshing,
            refreshError = currentRefreshError,
        )
    }.stateIn(
        scope = scope,
        started = FeatureSharing,
        initialValue = FriendsFeatureState(),
    )

    init {
        scope.launch {
            activeAccountId.collectLatest { account ->
                overlay.value = FriendsOverlay.None
                entry.value = ""
                message.value = null
                error.value = null
                submitting.value = false
                refreshError.value = null
                refreshing.value = false
                presenceObservation?.cancel()
                presenceObservation = null
                observedPresenceIds = emptySet()
                livePresence.value = emptyMap()
                if (account == null) {
                    friends.value = LoadState.Data(emptyList())
                    friendCode.value = LoadState.Data(null)
                    return@collectLatest
                }
                launch {
                    friendsRepository.observeFriends(account).collect { rows ->
                        val accepted = rows.filter { it.status == FriendshipStatus.Accepted }
                        friends.value = LoadState.Data(rows)
                        observePresence(accepted.mapTo(linkedSetOf()) { it.profile.userId })
                    }
                }
                launch {
                    friendsRepository.observeMyFriendCode(account).collect { code ->
                        friendCode.value = LoadState.Data(code)
                    }
                }
                launch { refreshFriends(account) }
                launch { friendsRepository.refreshMyFriendCode(account) }
            }
        }
    }

    fun refreshFriends() {
        val account = activeAccountId.value ?: return
        if (refreshing.value) return
        scope.launch { refreshFriends(account) }
    }

    fun openAddFriend() {
        overlay.value = FriendsOverlay.AddFriend
        message.value = null
        error.value = null
    }

    fun toggleNotifications() {
        overlay.update {
            if (it == FriendsOverlay.Notifications) {
                FriendsOverlay.None
            } else {
                FriendsOverlay.Notifications
            }
        }
        message.value = null
        error.value = null
    }

    fun closeOverlay(): Boolean {
        if (overlay.value == FriendsOverlay.None) return false
        overlay.value = FriendsOverlay.None
        message.value = null
        error.value = null
        return true
    }

    fun setEntry(value: String) {
        entry.value = value.filter(Char::isDigit).take(FRIEND_CODE_LENGTH)
        message.value = null
        error.value = null
    }

    fun submitFriendCode() {
        if (submitting.value) return
        val account = activeAccountId.value ?: return
        val code = FriendCode.parseOrNull(entry.value) ?: run {
            error.value = "Enter all eight digits."
            return
        }
        val ownCode = (friendCode.value as? LoadState.Data)?.value
        if (ownCode == code) {
            error.value = "That is your friend code."
            return
        }
        val mutableRepository = friendsRepository as? MutableFriendsRepository ?: run {
            error.value = "Friend requests are unavailable."
            return
        }
        submitting.value = true
        error.value = null
        message.value = null
        scope.launch {
            val resolved = friendsRepository.resolveFriendCode(account, code)
            when (resolved) {
                is RepositoryResult.Failure -> {
                    error.value = resolved.error.friendCodeMessage()
                }

                is RepositoryResult.Success -> {
                    val duplicate = (friends.value as? LoadState.Data)
                        ?.value
                        ?.firstOrNull { it.profile.userId == resolved.value.userId }
                    if (duplicate != null) {
                        error.value = if (
                            duplicate.status ==
                            com.pocketpass.app.domain.model.FriendshipStatus.Accepted
                        ) {
                            "You are already friends."
                        } else {
                            "A friend request is already pending."
                        }
                    } else {
                        when (
                            val sent = mutableRepository.sendFriendRequest(
                                SendFriendRequestCommand(
                                    accountId = account,
                                    addressee = resolved.value,
                                    requestedAt = Clock.System.now(),
                                ),
                            )
                        ) {
                            is RepositoryResult.Success -> {
                                message.value = "Request sent."
                                entry.value = ""
                            }

                            is RepositoryResult.Failure -> {
                                error.value = sent.error.friendCodeMessage()
                            }
                        }
                    }
                }
            }
            submitting.value = false
        }
    }

    private suspend fun refreshFriends(account: UserId) {
        if (activeAccountId.value != account || refreshing.value) return
        refreshing.value = true
        refreshError.value = null
        when (val result = friendsRepository.refreshFriends(account)) {
            is RepositoryResult.Success -> refreshError.value = null
            is RepositoryResult.Failure -> {
                refreshError.value = result.error.friendsRefreshMessage()
            }
        }
        if (activeAccountId.value == account) {
            refreshing.value = false
        }
    }

    private fun observePresence(userIds: Set<UserId>) {
        if (observedPresenceIds == userIds) return
        observedPresenceIds = userIds
        presenceObservation?.cancel()
        livePresence.value = emptyMap()
        if (userIds.isEmpty()) {
            presenceObservation = null
            return
        }
        presenceObservation = scope.launch {
            presenceRepository.observePresence(userIds).collect { snapshot ->
                livePresence.value = snapshot
            }
        }
    }

    private companion object {
        const val FRIEND_CODE_LENGTH = 8
    }
}

private fun com.pocketpass.app.domain.state.RepositoryFailure.friendsRefreshMessage(): String =
    when (kind) {
        RepositoryFailureKind.Offline -> "You’re offline. Showing saved friends."
        RepositoryFailureKind.Unauthorized -> "Sign in again to refresh friends."
        RepositoryFailureKind.RateLimited -> "Friends are refreshing too often. Try again shortly."
        RepositoryFailureKind.Unavailable -> "Friends are temporarily unavailable."
        else -> "Friends couldn’t be refreshed."
    }

private fun com.pocketpass.app.domain.state.RepositoryFailure.profileRequestMessage(): String =
    when (kind) {
        RepositoryFailureKind.Offline -> "Connect to the internet and try again."
        RepositoryFailureKind.Unauthorized -> "Sign in again to send a request."
        RepositoryFailureKind.RateLimited -> "Too many requests. Try again shortly."
        RepositoryFailureKind.Unavailable -> "Friend requests are temporarily unavailable."
        else -> "The friend request couldn’t be sent."
    }

private fun com.pocketpass.app.domain.state.RepositoryFailure.friendCodeMessage(): String =
    when (kind) {
        RepositoryFailureKind.Offline -> "Connect to the internet to find this friend."
        RepositoryFailureKind.RateLimited -> "Too many attempts. Try again later."
        RepositoryFailureKind.NotFound,
        RepositoryFailureKind.Forbidden,
        -> "No PocketPass user is available with that code."
        RepositoryFailureKind.Conflict -> "A friend request is already pending."
        RepositoryFailureKind.Validation -> message ?: "That friend code is not valid."
        RepositoryFailureKind.Unauthorized -> "Sign in again to add friends."
        RepositoryFailureKind.Unavailable -> "Friend search is temporarily unavailable."
        RepositoryFailureKind.Misconfigured,
        RepositoryFailureKind.Unknown,
        -> "Friend search could not be completed."
    }

private fun com.pocketpass.app.domain.state.RepositoryFailure.groupMessage(): String =
    when (kind) {
        RepositoryFailureKind.Offline -> "Connect to the internet to manage groups."
        RepositoryFailureKind.Forbidden -> "That change isn't allowed for this group."
        RepositoryFailureKind.Conflict -> "That group is full."
        RepositoryFailureKind.NotFound -> "That group no longer exists."
        RepositoryFailureKind.Validation -> message ?: "That group change is not valid."
        RepositoryFailureKind.Unauthorized -> "Sign in again to manage groups."
        RepositoryFailureKind.RateLimited -> "Too many changes. Try again shortly."
        RepositoryFailureKind.Unavailable,
        RepositoryFailureKind.Misconfigured,
        RepositoryFailureKind.Unknown,
        -> "The group could not be updated."
    }

data class MessagesFeatureState(
    val conversations: LoadState<List<ConversationSummary>> = LoadState.Loading,
    val totalMessageCount: Int = 0,
    val selectedConversationId: ConversationId? = null,
    val selectedConversation: ConversationSummary? = null,
    val messages: LoadState<List<Message>> = LoadState.Data(emptyList()),
    val currentDraft: String = "",
    val actionRailExpanded: Boolean = false,
    val isSending: Boolean = false,
    val operationError: String? = null,
    val typingConversationIds: Set<ConversationId> = emptySet(),
    val typingUserIds: Set<UserId> = emptySet(),
    val actionMessageId: MessageId? = null,
    val editingMessageId: MessageId? = null,
    val groupComposer: GroupComposerState? = null,
    val groupInfoOpen: Boolean = false,
    val groupOperationInProgress: Boolean = false,
    val groupOperationError: String? = null,
    val conversationNotice: String? = null,
    val selectedMembersById: Map<UserId, ConversationMember> = emptyMap(),
    val isGroupOwner: Boolean = false,
    val canAddGroupMembers: Boolean = false,
) {
    val unreadConversationCount: Int
        get() = (conversations as? LoadState.Data)
            ?.value
            ?.count { it.unreadCount > 0 }
            ?: 0
}

private data class EditSession(
    val conversationId: ConversationId,
    val messageId: MessageId,
    val originalBody: String,
    val stashedDraft: String,
)

private data class MessageMutationUi(
    val sheetMessageId: MessageId? = null,
    val edit: EditSession? = null,
)

private data class GroupUi(
    val composer: GroupComposerState? = null,
    val infoOpen: Boolean = false,
    val inProgress: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

private fun Message.isEditableBy(account: UserId): Boolean =
    senderId == account && pendingState == PendingState.Synced && deletedAt == null

class MessagesStateHolder(
    accountId: Flow<UserId?>,
    private val conversationRepository: MessageRepository,
    private val scope: CoroutineScope,
    private val deterministicFixtureMessageCount: Int = 0,
    presenceRepository: PresenceRepository? = null,
    private val onMessageSent: () -> Unit = {},
    private val onGroupCreated: (ConversationId) -> Unit = {},
) {
    private val activeAccountId = accountId.stateIn(
        scope = scope,
        started = FeatureSharing,
        initialValue = null,
    )
    private val selectedConversationId = MutableStateFlow<ConversationId?>(null)
    private val conversations = MutableStateFlow<LoadState<List<ConversationSummary>>>(
        LoadState.Loading,
    )
    private val messages = MutableStateFlow<LoadState<List<Message>>>(
        LoadState.Data(emptyList()),
    )
    private val drafts = MutableStateFlow<Map<ConversationId, String>>(emptyMap())
    private val actionRailExpanded = MutableStateFlow(false)
    private val sending = MutableStateFlow(false)
    private val operationError = MutableStateFlow<String?>(null)
    private val mutation = MutableStateFlow(MessageMutationUi())
    private val groupUi = MutableStateFlow(GroupUi())
    private var selectedConversationObserved = false
    private var messageObservation: Job? = null
    private val typingIn = MutableStateFlow<ConversationId?>(null)
    private var typingTimeout: Job? = null
    private val partnerTyping = presenceRepository?.observeTypingConversations()
        ?: flowOf(emptyMap())
    private val imageAttachmentRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val imageAttachmentRequested: SharedFlow<Unit> =
        imageAttachmentRequests.asSharedFlow()

    private val primaryState = combine(
        conversations,
        messages,
        selectedConversationId,
        drafts,
        actionRailExpanded,
    ) { conversationState, messageState, selectedId, conversationDrafts, railExpanded ->
        val conversationRows = (conversationState as? LoadState.Data)?.value.orEmpty()
        MessagesFeatureState(
            conversations = conversationState,
            totalMessageCount = deterministicFixtureMessageCount.takeIf { it > 0 }
                ?: conversationRows.sumOf(ConversationSummary::unreadCount),
            selectedConversationId = selectedId,
            selectedConversation = conversationRows.firstOrNull { it.id == selectedId },
            messages = messageState,
            currentDraft = selectedId?.let { conversationDrafts[it] }.orEmpty(),
            actionRailExpanded = railExpanded,
        )
    }

    private val baseState = combine(
        primaryState,
        sending,
        operationError,
        partnerTyping,
        mutation,
    ) { primary, isSending, error, typing, mutationUi ->
        primary.copy(
            isSending = isSending,
            operationError = error,
            typingConversationIds = typing
                .filterValues(Set<UserId>::isNotEmpty)
                .keys,
            typingUserIds = primary.selectedConversationId?.let { typing[it] }.orEmpty(),
            actionMessageId = mutationUi.sheetMessageId,
            editingMessageId = mutationUi.edit?.messageId,
        )
    }

    val state: StateFlow<MessagesFeatureState> = combine(
        baseState,
        groupUi,
        activeAccountId,
    ) { base, group, account ->
        val selected = base.selectedConversation
        base.copy(
            groupComposer = group.composer,
            groupInfoOpen = group.infoOpen && selected?.isGroup == true,
            groupOperationInProgress = group.inProgress,
            groupOperationError = group.error,
            conversationNotice = group.notice,
            selectedMembersById = selected?.members.orEmpty().associateBy { it.userId },
            isGroupOwner = selected?.isGroup == true && selected.ownerId == account,
            canAddGroupMembers = selected?.isGroup == true &&
                selected.memberCount < MAX_GROUP_MEMBERS,
        )
    }.stateIn(
        scope = scope,
        started = FeatureSharing,
        initialValue = MessagesFeatureState(
            totalMessageCount = deterministicFixtureMessageCount,
        ),
    )

    init {
        scope.launch {
            activeAccountId.collectLatest { account ->
                selectedConversationId.value = null
                messageObservation?.cancel()
                messages.value = LoadState.Data(emptyList())
                drafts.value = emptyMap()
                actionRailExpanded.value = false
                sending.value = false
                operationError.value = null
                mutation.value = MessageMutationUi()
                groupUi.value = GroupUi()
                selectedConversationObserved = false
                if (account == null) {
                    conversations.value = LoadState.Data(emptyList())
                } else {
                    conversations.value = LoadState.Loading
                    conversationRepository.observeConversations(account).collect { rows ->
                        conversations.value = LoadState.Data(rows)
                        val selected = selectedConversationId.value ?: return@collect
                        if (rows.any { it.id == selected }) {
                            selectedConversationObserved = true
                        } else if (selectedConversationObserved) {
                            closeConversation()
                            groupUi.update {
                                it.copy(notice = "You're no longer in this conversation")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun conversationRows(): List<ConversationSummary> =
        (conversations.value as? LoadState.Data)?.value.orEmpty()

    private fun selectedGroup(): ConversationSummary? {
        val selected = selectedConversationId.value ?: return null
        return conversationRows().firstOrNull { it.id == selected && it.isGroup }
    }

    fun openGroupComposer() {
        if (activeAccountId.value == null) return
        groupUi.update { it.copy(composer = GroupComposerState(), error = null) }
    }

    fun closeGroupComposer(): Boolean {
        if (groupUi.value.composer == null) return false
        groupUi.update { it.copy(composer = null) }
        return true
    }

    fun toggleGroupMember(userId: UserId) {
        groupUi.update { ui ->
            val composer = ui.composer ?: return@update ui
            val selected = composer.selectedMemberIds
            val next = when {
                userId in selected -> selected - userId
                composer.remainingSlots <= 0 -> selected
                else -> selected + userId
            }
            ui.copy(composer = composer.copy(selectedMemberIds = next, error = null))
        }
    }

    fun setGroupTitle(value: String) {
        groupUi.update { ui ->
            val composer = ui.composer ?: return@update ui
            ui.copy(
                composer = composer.copy(
                    title = value.take(GROUP_TITLE_MAX_LENGTH),
                    error = null,
                ),
            )
        }
    }

    fun createGroup() {
        val account = activeAccountId.value ?: return
        val composer = groupUi.value.composer ?: return
        if (!composer.canSubmit) return
        groupUi.update { it.copy(composer = composer.copy(submitting = true, error = null)) }
        scope.launch {
            val result = conversationRepository.createGroupConversation(
                CreateGroupConversationCommand(
                    accountId = account,
                    title = composer.title.trim(),
                    memberIds = composer.selectedMemberIds.toList(),
                ),
            )
            when (result) {
                is RepositoryResult.Success -> {
                    groupUi.update { it.copy(composer = null) }
                    onGroupCreated(result.value)
                }

                is RepositoryResult.Failure -> groupUi.update { ui ->
                    ui.copy(
                        composer = ui.composer?.copy(
                            submitting = false,
                            error = result.error.groupMessage(),
                        ),
                    )
                }
            }
        }
    }

    fun openGroupInfo() {
        if (selectedGroup() == null) return
        actionRailExpanded.value = false
        groupUi.update { it.copy(infoOpen = true, error = null) }
    }

    fun closeGroupInfo(): Boolean {
        if (!groupUi.value.infoOpen) return false
        groupUi.update { it.copy(infoOpen = false, error = null) }
        return true
    }

    fun addMembersToGroup(userIds: List<UserId>) = runGroupOperation { account, group ->
        val additions = userIds
            .distinct()
            .filterNot { id -> id == account || group.member(id) != null }
            .take((MAX_GROUP_MEMBERS - group.memberCount).coerceAtLeast(0))
        if (additions.isEmpty()) {
            null
        } else {
            conversationRepository.addGroupMembers(
                AddGroupMembersCommand(
                    accountId = account,
                    conversationId = group.id,
                    memberIds = additions,
                ),
            )
        }
    }

    fun removeGroupMember(userId: UserId) = runGroupOperation(requireOwner = true) { account, group ->
        if (userId == account || group.member(userId) == null) {
            null
        } else {
            conversationRepository.removeGroupMember(
                RemoveGroupMemberCommand(
                    accountId = account,
                    conversationId = group.id,
                    userId = userId,
                ),
            )
        }
    }

    fun renameGroup(title: String) = runGroupOperation(requireOwner = true) { account, group ->
        val trimmed = title.trim().take(GROUP_TITLE_MAX_LENGTH)
        if (trimmed.isEmpty() || trimmed == group.title) {
            null
        } else {
            conversationRepository.renameGroupConversation(
                RenameGroupConversationCommand(
                    accountId = account,
                    conversationId = group.id,
                    title = trimmed,
                ),
            )
        }
    }

    fun leaveGroup() {
        val account = activeAccountId.value ?: return
        val group = selectedGroup() ?: return
        if (groupUi.value.inProgress) return
        groupUi.update { it.copy(inProgress = true, error = null) }
        scope.launch {
            val result = conversationRepository.leaveGroupConversation(
                LeaveGroupConversationCommand(
                    accountId = account,
                    conversationId = group.id,
                ),
            )
            when (result) {
                is RepositoryResult.Success -> {
                    closeConversation()
                    groupUi.update {
                        it.copy(inProgress = false, infoOpen = false, notice = "You left ${group.title}")
                    }
                }

                is RepositoryResult.Failure -> groupUi.update {
                    it.copy(inProgress = false, error = result.error.groupMessage())
                }
            }
        }
    }

    fun clearConversationNotice() {
        groupUi.update { it.copy(notice = null) }
    }

    private fun runGroupOperation(
        requireOwner: Boolean = false,
        operation: suspend (UserId, ConversationSummary) -> RepositoryResult<Unit>?,
    ) {
        val account = activeAccountId.value ?: return
        val group = selectedGroup() ?: return
        if (groupUi.value.inProgress) return
        if (requireOwner && group.ownerId != account) return
        groupUi.update { it.copy(inProgress = true, error = null) }
        scope.launch {
            val result = operation(account, group)
            groupUi.update { ui ->
                ui.copy(
                    inProgress = false,
                    error = (result as? RepositoryResult.Failure)?.error?.groupMessage(),
                )
            }
        }
    }

    suspend fun awaitConversation(conversationId: ConversationId) {
        val account = activeAccountId.value ?: return
        conversationRepository.refreshConversations(account)
        openConversation(conversationId)
    }

    fun openConversation(conversationId: ConversationId) {
        val account = activeAccountId.value ?: return
        selectedConversationId.value = conversationId
        selectedConversationObserved = conversationRows().any { it.id == conversationId }
        actionRailExpanded.value = false
        operationError.value = null
        mutation.value = MessageMutationUi()
        groupUi.update { it.copy(infoOpen = false, error = null, notice = null) }
        observeMessages(account, conversationId)
        scope.launch {
            conversationRepository.refreshConversations(account)
            conversationRepository.refreshMessages(account, conversationId)
            conversationRepository.markConversationRead(
                MarkConversationReadCommand(
                    accountId = account,
                    conversationId = conversationId,
                    readAt = Clock.System.now(),
                ),
            )
        }
    }

    fun closeConversation() {
        selectedConversationId.value = null
        selectedConversationObserved = false
        messageObservation?.cancel()
        messages.value = LoadState.Data(emptyList())
        actionRailExpanded.value = false
        sending.value = false
        operationError.value = null
        mutation.value = MessageMutationUi()
        groupUi.update { it.copy(infoOpen = false, error = null) }
        clearSelfTyping()
    }

    fun openMessageActions(messageId: MessageId) {
        val account = activeAccountId.value ?: return
        val target = visibleMessages().firstOrNull { it.id == messageId } ?: return
        if (!target.isEditableBy(account)) return
        actionRailExpanded.value = false
        mutation.update { it.copy(sheetMessageId = messageId) }
    }

    fun closeMessageActions(): Boolean {
        if (mutation.value.sheetMessageId == null) return false
        mutation.update { it.copy(sheetMessageId = null) }
        return true
    }

    fun editSelectedMessage() {
        val conversationId = selectedConversationId.value ?: return
        val current = mutation.value
        val messageId = current.sheetMessageId ?: return
        val target = visibleMessages().firstOrNull { it.id == messageId }
        if (target == null) {
            closeMessageActions()
            return
        }
        val stashedDraft = current.edit
            ?.takeIf { it.conversationId == conversationId }
            ?.stashedDraft
            ?: drafts.value[conversationId].orEmpty()
        drafts.update { it + (conversationId to target.body.take(MAX_MESSAGE_LENGTH)) }
        operationError.value = null
        mutation.value = MessageMutationUi(
            edit = EditSession(
                conversationId = conversationId,
                messageId = messageId,
                originalBody = target.body,
                stashedDraft = stashedDraft,
            ),
        )
    }

    fun cancelEdit(): Boolean {
        val session = mutation.value.edit ?: return false
        drafts.update { it + (session.conversationId to session.stashedDraft) }
        mutation.update { it.copy(edit = null) }
        operationError.value = null
        return true
    }

    fun deleteSelectedMessage() {
        val account = activeAccountId.value ?: return
        val conversationId = selectedConversationId.value ?: return
        val messageId = mutation.value.sheetMessageId ?: return
        closeMessageActions()
        if (mutation.value.edit?.messageId == messageId) cancelEdit()
        operationError.value = null
        scope.launch {
            val result = conversationRepository.deleteMessage(
                DeleteMessageCommand(
                    accountId = account,
                    conversationId = conversationId,
                    messageId = messageId,
                    deletedAt = Clock.System.now(),
                ),
            )
            if (result is RepositoryResult.Failure) {
                operationError.value = result.error.message ?: "Message could not be deleted."
            }
        }
    }

    private fun visibleMessages(): List<Message> =
        (messages.value as? LoadState.Data)?.value.orEmpty()

    fun setDraft(value: String) {
        val conversationId = selectedConversationId.value ?: return
        drafts.update { it + (conversationId to value.take(MAX_MESSAGE_LENGTH)) }
        operationError.value = null
        typingIn.value = conversationId
        typingTimeout?.cancel()
        typingTimeout = scope.launch {
            delay(TYPING_TIMEOUT_MILLIS)
            typingIn.value = null
        }
    }

    fun observeSelfTyping(conversationId: ConversationId): Flow<Boolean> =
        typingIn
            .map { it == conversationId }
            .distinctUntilChanged()

    private fun clearSelfTyping() {
        typingTimeout?.cancel()
        typingIn.value = null
    }

    fun toggleActionRail() {
        if (selectedConversationId.value == null) return
        actionRailExpanded.update(Boolean::not)
    }

    fun closeActionRail(): Boolean {
        if (!actionRailExpanded.value) return false
        actionRailExpanded.value = false
        return true
    }

    fun sendDraft() {
        if (sending.value) return
        val account = activeAccountId.value ?: return
        val conversationId = selectedConversationId.value ?: return
        val originalDraft = drafts.value[conversationId].orEmpty()
        val body = originalDraft.trim()
        if (body.isEmpty() || body.length > MAX_MESSAGE_LENGTH) return
        val session = mutation.value.edit?.takeIf { it.conversationId == conversationId }
        if (session != null && body == session.originalBody.trim()) {
            cancelEdit()
            return
        }

        sending.value = true
        operationError.value = null
        clearSelfTyping()
        if (session == null) onMessageSent()
        scope.launch {
            val result = if (session != null) {
                conversationRepository.editMessage(
                    EditMessageCommand(
                        accountId = account,
                        conversationId = conversationId,
                        messageId = session.messageId,
                        body = body,
                        editedAt = Clock.System.now(),
                    ),
                )
            } else {
                conversationRepository.sendMessage(
                    SendMessageCommand(
                        accountId = account,
                        conversationId = conversationId,
                        body = body,
                        clientCreatedAt = Clock.System.now(),
                    ),
                )
            }
            when (result) {
                is RepositoryResult.Success -> {
                    drafts.update { current ->
                        if (current[conversationId] == originalDraft) {
                            current + (conversationId to session?.stashedDraft.orEmpty())
                        } else {
                            current
                        }
                    }
                    if (session != null) mutation.update { it.copy(edit = null) }
                }

                is RepositoryResult.Failure -> {
                    operationError.value = result.error.message
                        ?: if (session != null) {
                            "Message could not be edited."
                        } else {
                            "Message could not be sent."
                        }
                }
            }
            sending.value = false
        }
    }

    fun requestImageAttachment() {
        if (selectedConversationId.value == null) return
        actionRailExpanded.value = false
        scope.launch { imageAttachmentRequests.emit(Unit) }
    }

    fun sendImageAttachment(localPath: String, mimeType: String) {
        if (sending.value || mutation.value.edit != null) return
        val account = activeAccountId.value ?: return
        val conversationId = selectedConversationId.value ?: return
        val originalDraft = drafts.value[conversationId].orEmpty()
        val body = originalDraft.trim().ifEmpty { IMAGE_MESSAGE_PLACEHOLDER_BODY }
        if (body.length > MAX_MESSAGE_LENGTH) return

        sending.value = true
        operationError.value = null
        onMessageSent()
        scope.launch {
            when (
                val result = conversationRepository.sendMessage(
                    SendMessageCommand(
                        accountId = account,
                        conversationId = conversationId,
                        body = body,
                        clientCreatedAt = Clock.System.now(),
                        attachment = MessageAttachment(
                            remotePath = null,
                            mimeType = mimeType,
                            localPath = localPath,
                        ),
                    ),
                )
            ) {
                is RepositoryResult.Success -> drafts.update { current ->
                    if (current[conversationId] == originalDraft) {
                        current + (conversationId to "")
                    } else {
                        current
                    }
                }

                is RepositoryResult.Failure -> {
                    operationError.value = result.error.message
                        ?: "Image could not be sent."
                }
            }
            sending.value = false
        }
    }

    fun retryMessage(messageId: MessageId) {
        val failed = (messages.value as? LoadState.Data)
            ?.value
            ?.firstOrNull { message ->
                message.id == messageId && message.pendingState is PendingState.Failed
            }
            ?: return
        actionRailExpanded.value = false
        val attachment = failed.attachment
        val localPath = attachment?.localPath
        if (attachment != null && localPath != null) {
            setDraft(failed.body.takeUnless { it == IMAGE_MESSAGE_PLACEHOLDER_BODY }.orEmpty())
            sendImageAttachment(
                localPath = localPath,
                mimeType = attachment.mimeType,
            )
            return
        }
        setDraft(failed.body)
    }

    private fun observeMessages(account: UserId, conversationId: ConversationId) {
        messageObservation?.cancel()
        messages.value = LoadState.Loading
        messageObservation = scope.launch {
            conversationRepository.observeMessages(account, conversationId).collect { rows ->
                messages.value = LoadState.Data(rows)
                val current = mutation.value
                if (current.sheetMessageId != null && rows.none { it.id == current.sheetMessageId }) {
                    closeMessageActions()
                }
                if (current.edit != null && rows.none { it.id == current.edit.messageId }) {
                    cancelEdit()
                }
            }
        }
    }

    private companion object {
        const val MAX_MESSAGE_LENGTH = 4_000
        const val TYPING_TIMEOUT_MILLIS = 3_500L
    }
}

data class ActivitiesFeatureState(
    val variant: ActivityVariant = ActivityVariant.Default,
    val snapshot: LoadState<ActivitySnapshot?> = LoadState.Loading,
)

data class ShopFeatureState(
    val visible: Boolean = false,
    val categories: List<ShopCategory> = emptyList(),
    val tokenBalance: Int = 0,
    val refreshError: String? = null,
    val ownedItemIds: Set<String> = emptySet(),
    val unlockedItemIds: Set<String> = emptySet(),
    val supporterUntil: Instant? = null,
    val purchasingItemIds: Set<String> = emptySet(),
    val purchaseError: String? = null,
)

private data class ShopEntitlements(
    val owned: List<OwnedShopItem> = emptyList(),
    val hatTypes: Set<Int> = emptySet(),
    val supporterUntil: Instant? = null,
)

private data class LocalShopState(
    val visible: Boolean,
    val refreshError: String?,
    val purchasing: Set<String>,
    val purchaseError: String?,
)

class ShopStateHolder(
    private val accountId: Flow<UserId?>,
    private val shopRepository: ShopRepository,
    private val scope: CoroutineScope,
    private val now: () -> Instant = Clock.System::now,
) {
    private val mutableVisible = MutableStateFlow(false)
    private val mutableRefreshError = MutableStateFlow<String?>(null)
    private val mutablePurchasing = MutableStateFlow<Set<String>>(emptySet())
    private val mutablePurchaseError = MutableStateFlow<String?>(null)

    private val local = combine(
        mutableVisible,
        mutableRefreshError,
        mutablePurchasing,
        mutablePurchaseError,
    ) { visible, refreshError, purchasing, purchaseError ->
        LocalShopState(
            visible = visible,
            refreshError = refreshError,
            purchasing = purchasing,
            purchaseError = purchaseError,
        )
    }

    private val entitlements = accountId.switchAccount<ShopEntitlements>(
        signedOut = ShopEntitlements(),
    ) { id ->
        combine(
            shopRepository.observeOwnedItems(id),
            shopRepository.observeOwnedHatTypes(id),
            shopRepository.observeSupporterUntil(id),
        ) { owned, hatTypes, supporterUntil ->
            ShopEntitlements(owned = owned, hatTypes = hatTypes, supporterUntil = supporterUntil)
        }
    }

    val state: StateFlow<ShopFeatureState> = combine(
        local,
        shopRepository.observeCatalog(),
        accountId.switchAccount<Int>(signedOut = 0) { id ->
            shopRepository.observeTokenBalance(id).map { it ?: 0 }
        },
        entitlements,
    ) { local, categories, balance, entitlements ->
        val owned = entitlements.owned
        val confirmed = owned.filterNot(OwnedShopItem::pending).map(OwnedShopItem::itemId).toSet()
        val pending = owned.filter(OwnedShopItem::pending).map(OwnedShopItem::itemId).toSet()
        val unlocked = categories.flatMap(ShopCategory::items)
            .filter { item -> item.miiHatType?.let { it in entitlements.hatTypes } == true }
            .map(ShopItem::id)
            .toSet() - confirmed
        ShopFeatureState(
            visible = local.visible,
            categories = categories,
            tokenBalance = balance,
            refreshError = local.refreshError,
            ownedItemIds = confirmed,
            unlockedItemIds = unlocked,
            supporterUntil = entitlements.supporterUntil,
            purchasingItemIds = (local.purchasing + pending) - confirmed,
            purchaseError = local.purchaseError,
        )
    }.stateIn(
        scope = scope,
        started = FeatureSharing,
        initialValue = ShopFeatureState(),
    )

    fun open() {
        mutableVisible.value = true
        refresh()
    }

    fun close(): Boolean {
        if (!mutableVisible.value) return false
        mutableVisible.value = false
        mutablePurchaseError.value = null
        return true
    }

    fun buy(itemId: String) {
        val current = state.value
        if (
            itemId in current.ownedItemIds ||
            itemId in current.unlockedItemIds ||
            itemId in current.purchasingItemIds
        ) {
            return
        }
        val item = current.categories.flatMap(ShopCategory::items).firstOrNull { it.id == itemId }
            ?: return
        mutablePurchasing.update { it + itemId }
        mutablePurchaseError.value = null
        scope.launch {
            val id = accountId.first()
            val result = if (id == null) {
                null
            } else {
                shopRepository.purchase(
                    PurchaseShopItemCommand(
                        accountId = id,
                        itemId = itemId,
                        priceTokens = item.priceTokens,
                        requestedAt = now(),
                    ),
                )
            }
            mutablePurchasing.update { it - itemId }
            if (result is RepositoryResult.Failure) {
                mutablePurchaseError.value = result.error.purchaseMessage()
            }
        }
    }

    private fun refresh() {
        scope.launch {
            val id = accountId.first() ?: return@launch
            mutableRefreshError.value =
                when (val result = shopRepository.refresh(id)) {
                    is RepositoryResult.Success -> null
                    is RepositoryResult.Failure -> result.error.message
                }
        }
    }

    private fun RepositoryFailure.purchaseMessage(): String = when (kind) {
        RepositoryFailureKind.Conflict -> message ?: PURCHASE_FAILED_MESSAGE
        else -> PURCHASE_FAILED_MESSAGE
    }

    private companion object {
        const val PURCHASE_FAILED_MESSAGE = "This item couldn\u2019t be bought."
    }
}

data class GamesFeatureState(
    val visible: Boolean = false,
    val activeGame: GameTarget? = null,
    val bingoGoalIndex: Int? = null,
    val worldTourRegionsVisible: Boolean = false,
)

class GamesStateHolder {
    private val mutableState = MutableStateFlow(GamesFeatureState())

    val state: StateFlow<GamesFeatureState> = mutableState.asStateFlow()

    fun open() {
        mutableState.value = GamesFeatureState(visible = true)
    }

    fun openGame(game: GameTarget) {
        mutableState.value = GamesFeatureState(visible = true, activeGame = game)
    }

    fun selectBingoGoal(index: Int) {
        mutableState.update { current ->
            if (current.activeGame == GameTarget.Bingo) {
                current.copy(bingoGoalIndex = index)
            } else {
                current
            }
        }
    }

    fun closeBingoGoal(): Boolean {
        if (mutableState.value.bingoGoalIndex == null) return false
        mutableState.update { it.copy(bingoGoalIndex = null) }
        return true
    }

    fun openWorldTourRegions() {
        mutableState.update { current ->
            if (current.activeGame == GameTarget.WorldTour) {
                current.copy(worldTourRegionsVisible = true)
            } else {
                current
            }
        }
    }

    fun closeWorldTourRegions(): Boolean {
        if (!mutableState.value.worldTourRegionsVisible) return false
        mutableState.update { it.copy(worldTourRegionsVisible = false) }
        return true
    }

    fun close(): Boolean {
        val current = mutableState.value
        return when {
            current.worldTourRegionsVisible -> {
                mutableState.value = current.copy(worldTourRegionsVisible = false)
                true
            }

            current.bingoGoalIndex != null -> {
                mutableState.value = current.copy(bingoGoalIndex = null)
                true
            }

            current.activeGame != null -> {
                mutableState.value = GamesFeatureState(visible = true)
                true
            }

            current.visible -> {
                mutableState.value = GamesFeatureState()
                true
            }

            else -> false
        }
    }

    fun reset() {
        mutableState.value = GamesFeatureState()
    }
}

data class LeaderboardFeatureState(
    val visible: Boolean = false,
    val settingsVisible: Boolean = false,
    val scope: LeaderboardScope = LeaderboardScope.Friends,
    val entries: List<LeaderboardEntry> = emptyList(),
    val refreshError: String? = null,
)

class LeaderboardStateHolder(
    private val accountId: Flow<UserId?>,
    private val leaderboardRepository: LeaderboardRepository,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val mutableVisible = MutableStateFlow(false)
    private val mutableSettingsVisible = MutableStateFlow(false)
    private val mutableRefreshError = MutableStateFlow<String?>(null)

    private val leaderboardScope: Flow<LeaderboardScope> = settingsRepository.settings
        .map { it.leaderboardScope }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val entries: Flow<List<LeaderboardEntry>> = combine(
        accountId,
        leaderboardScope,
    ) { id, boardScope -> id to boardScope }
        .distinctUntilChanged()
        .flatMapLatest { (id, boardScope) ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                leaderboardRepository.observeLeaderboard(id, boardScope)
            }
        }

    val state: StateFlow<LeaderboardFeatureState> = combine(
        mutableVisible,
        mutableSettingsVisible,
        leaderboardScope,
        entries,
        mutableRefreshError,
    ) { visible, settingsVisible, boardScope, boardEntries, error ->
        LeaderboardFeatureState(
            visible = visible,
            settingsVisible = settingsVisible,
            scope = boardScope,
            entries = boardEntries,
            refreshError = error,
        )
    }.stateIn(
        scope = scope,
        started = FeatureSharing,
        initialValue = LeaderboardFeatureState(),
    )

    fun open() {
        mutableVisible.value = true
        refresh()
    }

    fun openSettings() {
        if (!mutableVisible.value) return
        mutableSettingsVisible.value = true
    }

    fun closeSettings(): Boolean {
        if (!mutableSettingsVisible.value) return false
        mutableSettingsVisible.value = false
        return true
    }

    fun setScope(newScope: LeaderboardScope) {
        scope.launch {
            settingsRepository.setLeaderboardScope(newScope)
            refreshNow(newScope)
        }
    }

    fun close(): Boolean {
        if (closeSettings()) return true
        if (!mutableVisible.value) return false
        mutableVisible.value = false
        return true
    }

    private fun refresh() {
        scope.launch {
            refreshNow(settingsRepository.settings.first().leaderboardScope)
        }
    }

    private suspend fun refreshNow(boardScope: LeaderboardScope) {
        val id = accountId.first() ?: return
        mutableRefreshError.value =
            when (val result = leaderboardRepository.refresh(id, boardScope)) {
                is RepositoryResult.Success -> null
                is RepositoryResult.Failure -> result.error.message
            }
    }
}

data class AchievementsFeatureState(
    val visible: Boolean = false,
    val achievements: List<AchievementState> = emptyList(),
    val refreshError: String? = null,
)

class AchievementsStateHolder(
    private val accountId: Flow<UserId?>,
    private val achievementsRepository: AchievementsRepository,
    private val scope: CoroutineScope,
) {
    private val mutableVisible = MutableStateFlow(false)
    private val mutableRefreshError = MutableStateFlow<String?>(null)

    val state: StateFlow<AchievementsFeatureState> = combine(
        mutableVisible,
        accountId.switchAccount<List<AchievementState>>(signedOut = emptyList()) { id ->
            achievementsRepository.observeAchievements(id)
        },
        mutableRefreshError,
    ) { visible, achievements, error ->
        AchievementsFeatureState(
            visible = visible,
            achievements = achievements,
            refreshError = error,
        )
    }.stateIn(
        scope = scope,
        started = FeatureSharing,
        initialValue = AchievementsFeatureState(),
    )

    fun open() {
        mutableVisible.value = true
        refresh()
    }

    fun close(): Boolean {
        if (!mutableVisible.value) return false
        mutableVisible.value = false
        return true
    }

    private fun refresh() {
        scope.launch {
            val id = accountId.first() ?: return@launch
            mutableRefreshError.value =
                when (val result = achievementsRepository.refresh(id)) {
                    is RepositoryResult.Success -> null
                    is RepositoryResult.Failure -> result.error.message
                }
        }
    }
}

data class WorldTourFeatureState(
    val regions: List<WorldTourRegion> = emptyList(),
    val refreshError: String? = null,
)

class WorldTourStateHolder(
    private val accountId: Flow<UserId?>,
    private val worldTourRepository: WorldTourRepository,
    private val scope: CoroutineScope,
) {
    private val mutableRefreshError = MutableStateFlow<String?>(null)

    val state: StateFlow<WorldTourFeatureState> = combine(
        accountId.switchAccount<List<WorldTourRegion>>(signedOut = emptyList()) { id ->
            worldTourRepository.observeRegions(id)
        },
        mutableRefreshError,
    ) { regions, error ->
        WorldTourFeatureState(
            regions = regions,
            refreshError = error,
        )
    }.stateIn(
        scope = scope,
        started = FeatureSharing,
        initialValue = WorldTourFeatureState(),
    )

    fun refresh() {
        scope.launch {
            val id = accountId.first() ?: return@launch
            mutableRefreshError.value =
                when (val result = worldTourRepository.refresh(id)) {
                    is RepositoryResult.Success -> null
                    is RepositoryResult.Failure -> result.error.message
                }
        }
    }
}

data class BingoFeatureState(
    val cells: List<BingoCell> = emptyList(),
    val refreshError: String? = null,
)

class BingoStateHolder(
    private val accountId: Flow<UserId?>,
    private val bingoRepository: BingoRepository,
    private val scope: CoroutineScope,
) {
    private val mutableRefreshError = MutableStateFlow<String?>(null)

    val state: StateFlow<BingoFeatureState> = combine(
        accountId.switchAccount<List<BingoCell>>(signedOut = emptyList()) { id ->
            bingoRepository.observeBoard(id)
        },
        mutableRefreshError,
    ) { cells, error ->
        BingoFeatureState(
            cells = cells,
            refreshError = error,
        )
    }.stateIn(
        scope = scope,
        started = FeatureSharing,
        initialValue = BingoFeatureState(),
    )

    fun refresh() {
        scope.launch {
            val id = accountId.first() ?: return@launch
            mutableRefreshError.value =
                when (val result = bingoRepository.refresh(id)) {
                    is RepositoryResult.Success -> null
                    is RepositoryResult.Failure -> result.error.message
                }
        }
    }
}

class ActivitiesStateHolder(
    accountId: Flow<UserId?>,
    shopRepository: ShopRepository,
    leaderboardRepository: LeaderboardRepository,
    worldTourRepository: WorldTourRepository,
    scope: CoroutineScope,
    initialVariant: ActivityVariant = ActivityVariant.Default,
) {
    private val mutableVariant = MutableStateFlow(initialVariant)
    val variant: StateFlow<ActivityVariant> = mutableVariant
    val state: StateFlow<ActivitiesFeatureState> = combine(
        mutableVariant,
        accountId.switchAccount<LoadState<ActivitySnapshot?>>(
            signedOut = LoadState.Data(null),
        ) { id ->
            combine(
                shopRepository.observeTokenBalance(id),
                leaderboardRepository.observeLeaderboard(id, LeaderboardScope.Friends),
                worldTourRepository.observeRegions(id),
            ) { tokens, entries, regions ->
                LoadState.Data(
                    ActivitySnapshot(
                        coinCount = tokens ?: 0,
                        puzzleCount = 0,
                        nearbyCount = entries
                            .firstOrNull { entry -> entry.userId == id }
                            ?.encounterCount
                            ?: 0,
                        locationCount = regions.size,
                        updatedAt = Instant.fromEpochSeconds(0),
                    ),
                )
            }
        },
        ::ActivitiesFeatureState,
    ).stateIn(
        scope = scope,
        started = FeatureSharing,
        initialValue = ActivitiesFeatureState(initialVariant),
    )

    fun toggle() {
        mutableVariant.update {
            if (it == ActivityVariant.Default) {
                ActivityVariant.Shuffled
            } else {
                ActivityVariant.Default
            }
        }
    }
}

class SettingsStateHolder(
    private val repository: SettingsRepository,
    scope: CoroutineScope,
) {
    val settings: StateFlow<LocalSettings> = repository.settings.stateIn(
        scope = scope,
        started = FeatureSharing,
        initialValue = LocalSettings(),
    )

    suspend fun setNearby(enabled: Boolean) = repository.setNearby(enabled)

    suspend fun setNearbyOnboardingCompleted(completed: Boolean) =
        repository.setNearbyOnboardingCompleted(completed)

    suspend fun setSoundLevel(level: Float) = repository.setSoundLevel(level)

    suspend fun setSfxLevel(level: Float) = repository.setSfxLevel(level)

    suspend fun setThemeMode(mode: com.pocketpass.app.model.ThemeMode) =
        repository.setThemeMode(mode)

    suspend fun setMoodEmojisEnabled(enabled: Boolean) =
        repository.setMoodEmojisEnabled(enabled)

    suspend fun setEncounterLedEnabled(enabled: Boolean) =
        repository.setEncounterLedEnabled(enabled)

    suspend fun setEncounterAlertsEnabled(enabled: Boolean) =
        repository.setEncounterAlertsEnabled(enabled)

    suspend fun setNearbyRepairAlertsEnabled(enabled: Boolean) =
        repository.setNearbyRepairAlertsEnabled(enabled)

    suspend fun setUpdateAlertsEnabled(enabled: Boolean) =
        repository.setUpdateAlertsEnabled(enabled)

    suspend fun setRecentInteractionsSort(sort: RecentInteractionsSort) =
        repository.setRecentInteractionsSort(sort)

    suspend fun setFriendsSort(sort: RecentInteractionsSort) =
        repository.setFriendsSort(sort)

    suspend fun resetSettings() = repository.resetSettings()
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> Flow<UserId?>.switchAccount(
    signedOut: T,
    source: (UserId) -> Flow<T>,
): Flow<T> = distinctUntilChanged().flatMapLatest { accountId ->
    if (accountId == null) {
        flowOf(signedOut)
    } else {
        source(accountId)
    }
}
