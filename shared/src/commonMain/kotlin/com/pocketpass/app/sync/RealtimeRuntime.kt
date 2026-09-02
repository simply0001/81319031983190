package com.pocketpass.app.sync

import kotlin.time.Clock
import com.pocketpass.app.data.local.PocketPassDatabase
import com.pocketpass.app.data.repository.ProductionRepositoryBundle
import com.pocketpass.app.data.repository.RealtimePresenceRepository
import com.pocketpass.app.data.repository.remote.ProfileRemoteDataSource
import com.pocketpass.app.data.supabase.realtime.ConversationRealtimeEvent
import com.pocketpass.app.data.supabase.realtime.MessageChangeOperation
import com.pocketpass.app.data.supabase.realtime.PresenceStateDto
import com.pocketpass.app.data.supabase.realtime.NotificationChange
import com.pocketpass.app.data.supabase.realtime.SupabaseRealtimeGateway
import com.pocketpass.app.data.supabase.realtime.TokenChannelEvent
import com.pocketpass.app.audio.SoundEffect
import com.pocketpass.app.audio.SoundEffectSink
import com.pocketpass.app.PocketPassRepositoryGraph
import com.pocketpass.app.data.SettingsRepository
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.FriendshipStatus
import com.pocketpass.app.domain.model.LeaderboardScope
import com.pocketpass.app.domain.model.PresenceStatus
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.state.accountIdOrNull
import com.pocketpass.app.logPlatformDebug
import com.pocketpass.app.logPlatformInfo
import com.pocketpass.app.logPlatformWarning
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RealtimeRuntimeGate(
    val accountId: UserId?,
    val appForeground: Boolean,
    val networkAvailable: Boolean,
    val networkGeneration: Long,
) {
    val shouldRun: Boolean
        get() = accountId != null && appForeground && networkAvailable
}

data class RealtimeNetworkState(
    val available: Boolean,
    val networkHandle: Long?,
    val generation: Long,
)

// The whole "keep server state fresh while signed in, foregrounded and online"
// loop, shared verbatim between the Android and iOS containers. Platform
// concerns (system notifications, update checks, typing state) arrive as
// callbacks.
class RealtimeRuntime(
    private val scope: CoroutineScope,
    private val database: PocketPassDatabase,
    private val repositories: PocketPassRepositoryGraph,
    private val bundle: ProductionRepositoryBundle,
    private val presence: RealtimePresenceRepository,
    private val realtime: SupabaseRealtimeGateway,
    private val profileRemote: ProfileRemoteDataSource,
    private val soundEffects: SoundEffectSink,
    private val settingsRepository: SettingsRepository,
    private val appForeground: StateFlow<Boolean>,
    private val networkState: StateFlow<RealtimeNetworkState>,
    private val observeSelfTyping: (ConversationId) -> Flow<Boolean>,
    private val onAppUpdateSignal: () -> Unit,
    private val onNearbyEncounterNotification: (displayName: String, notificationKey: String) -> Unit,
) {
    private var lastSeenRefreshJob: Job? = null
    private val postedNearbyNotificationIds = mutableSetOf<String>()

    fun start() {
        scope.launch {
            combine(
                repositories.session.sessionState
                    .map { it.accountIdOrNull() }
                    .distinctUntilChanged(),
                appForeground,
                networkState,
            ) { accountId, foreground, network ->
                RealtimeRuntimeGate(
                    accountId = accountId,
                    appForeground = foreground,
                    networkAvailable = network.available,
                    networkGeneration = network.generation,
                )
            }
                .distinctUntilChanged()
                .collectLatest { gate ->
                    presence.clearAll()
                    if (!gate.shouldRun) {
                        logPlatformInfo(
                            TAG,
                            "Realtime stopped: foreground=${gate.appForeground}, " +
                                "network=${gate.networkAvailable}, " +
                                "authenticated=${gate.accountId != null}",
                        )
                        if (gate.accountId != null && gate.networkAvailable) {
                            touchLastSeen()
                        }
                        return@collectLatest
                    }

                    val accountId = requireNotNull(gate.accountId)
                    logPlatformInfo(
                        TAG,
                        "Realtime starting for network generation ${gate.networkGeneration}",
                    )
                    try {
                        repositories.sync.synchronize(accountId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        logPlatformWarning(
                            TAG,
                            "Realtime reconciliation failed before reconnect: $error",
                        )
                    }
                    coroutineScope {
                        launch { touchLastSeenPeriodically() }
                        launch { collectRealtimeConversations(accountId) }
                        launch { collectRealtimeNotifications(accountId) }
                        launch { collectRealtimeFriends(accountId) }
                        launch { collectRealtimeTokenBalance(accountId) }
                        launch { collectRealtimeEncounterStats(accountId) }
                        launch { collectRealtimeAppUpdates() }
                    }
                }
        }
    }

    fun clearPostedNearbyNotifications() {
        postedNearbyNotificationIds.clear()
    }

    private suspend fun collectRealtimeFriends(accountId: UserId) = coroutineScope {
        bundle.friends.refreshFriends(accountId)
        launch {
            var retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
            while (currentCoroutineContext().isActive) {
                try {
                    realtime
                        .friendInvalidations(accountId.value)
                        .collect {
                            bundle.friends.refreshFriends(accountId)
                            bundle.notifications.refreshNotifications(accountId)
                        }
                    retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    logPlatformWarning(TAG, "Friend invalidation channel failed; retrying: $error")
                }
                delay(retryDelayMillis)
                bundle.friends.refreshFriends(accountId)
                retryDelayMillis = (retryDelayMillis * 2)
                    .coerceAtMost(MAX_REALTIME_RETRY_MILLIS)
            }
        }
        launch {
            val channelJobs = mutableMapOf<UserId, Job>()
            try {
                database.friendDao()
                    .observeForOwner(accountId.value)
                    .map { rows ->
                        rows.asSequence()
                            .filter {
                                it.friendshipStatus == FriendshipStatus.Accepted.name
                            }
                            .mapTo(linkedSetOf()) { UserId(it.friendUserId) }
                    }
                    .distinctUntilChanged()
                    .collect { friendIds ->
                        val removed = channelJobs.keys - friendIds
                        removed.forEach { friendId ->
                            channelJobs.remove(friendId)?.cancel()
                            presence.clearFriendPresence(
                                friendPresencePairKey(accountId, friendId),
                            )
                        }

                        (friendIds - channelJobs.keys).forEach { friendId ->
                            channelJobs[friendId] = launch {
                                collectFriendPresence(
                                    accountId = accountId,
                                    friendId = friendId,
                                )
                            }
                        }
                    }
            } finally {
                channelJobs.values.forEach(Job::cancel)
                channelJobs.keys.forEach { friendId ->
                    presence.clearFriendPresence(
                        friendPresencePairKey(accountId, friendId),
                    )
                }
            }
        }
    }

    private suspend fun touchLastSeen() {
        when (val result = profileRemote.touchLastSeen()) {
            is RepositoryResult.Success -> Unit
            is RepositoryResult.Failure ->
                logPlatformDebug(TAG, "Last seen touch failed: ${result.error.message}")
        }
    }

    private suspend fun touchLastSeenPeriodically() {
        while (currentCoroutineContext().isActive) {
            touchLastSeen()
            delay(LAST_SEEN_TOUCH_INTERVAL_MILLIS)
        }
    }

    private fun scheduleLastSeenRefresh(accountId: UserId) {
        lastSeenRefreshJob?.cancel()
        lastSeenRefreshJob = scope.launch {
            delay(LAST_SEEN_REFRESH_DELAY_MILLIS)
            bundle.friends.refreshFriends(accountId)
        }
    }

    private suspend fun collectFriendPresence(
        accountId: UserId,
        friendId: UserId,
    ) {
        val pairKey = friendPresencePairKey(accountId, friendId)
        var friendOnline = false
        var retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
        while (currentCoroutineContext().isActive) {
            try {
                realtime
                    .friendPresence(
                        accountId = accountId.value,
                        friendUserId = friendId.value,
                    )
                    .collect { presences ->
                        logPlatformInfo(
                            TAG,
                            "Friend presence changed: onlineMembers=${presences.size}",
                        )
                        val online = presences.any { it.userId == friendId.value }
                        if (friendOnline && !online) {
                            scheduleLastSeenRefresh(accountId)
                        }
                        friendOnline = online
                        presence.replaceFriendPresence(
                            pairKey = pairKey,
                            snapshot = presences
                                .mapNotNull { presenceState ->
                                    runCatching {
                                        UserId(presenceState.userId) to PresenceStatus.Online
                                    }.getOrNull()
                                }
                                .toMap(),
                        )
                    }
                retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                presence.clearFriendPresence(pairKey)
                logPlatformWarning(
                    TAG,
                    "Friend presence channel failed; reconciling and retrying: $error",
                )
                try {
                    bundle.friends.refreshFriends(accountId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (refreshError: Throwable) {
                    logPlatformWarning(
                        TAG,
                        "Friend reconciliation failed after Presence error: $refreshError",
                    )
                }
            }
            delay(retryDelayMillis)
            retryDelayMillis = (retryDelayMillis * 2)
                .coerceAtMost(MAX_REALTIME_RETRY_MILLIS)
        }
    }

    private fun friendPresencePairKey(first: UserId, second: UserId): String =
        listOf(first.value, second.value)
            .sorted()
            .joinToString(separator = ":")

    private suspend fun collectRealtimeAppUpdates() {
        var retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
        while (currentCoroutineContext().isActive) {
            try {
                realtime
                    .appUpdateSignals()
                    .collect { onAppUpdateSignal() }
                retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                logPlatformWarning(TAG, "App update channel failed; retrying: $error")
            }
            delay(retryDelayMillis)
            retryDelayMillis = (retryDelayMillis * 2)
                .coerceAtMost(MAX_REALTIME_RETRY_MILLIS)
        }
    }

    private suspend fun collectRealtimeTokenBalance(accountId: UserId) {
        var retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
        while (currentCoroutineContext().isActive) {
            repositories.shop.refresh(accountId)
            try {
                realtime
                    .tokenBalanceInvalidations(accountId.value)
                    .collect { event ->
                        when (event) {
                            TokenChannelEvent.Balance ->
                                repositories.shop.refreshTokenBalance(accountId)
                            TokenChannelEvent.Supporter ->
                                repositories.shop.refreshSupporterStatus(accountId)
                        }
                    }
                retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                logPlatformWarning(TAG, "Token balance channel failed; retrying: $error")
            }
            delay(retryDelayMillis)
            retryDelayMillis = (retryDelayMillis * 2)
                .coerceAtMost(MAX_REALTIME_RETRY_MILLIS)
        }
    }

    private suspend fun collectRealtimeEncounterStats(accountId: UserId) {
        suspend fun refreshStats() {
            repositories.leaderboard.refresh(accountId, LeaderboardScope.Friends)
            repositories.leaderboard.refresh(accountId, LeaderboardScope.Global)
            repositories.worldTour.refresh(accountId)
        }
        var retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
        while (currentCoroutineContext().isActive) {
            refreshStats()
            try {
                realtime
                    .encounterInvalidations(accountId.value)
                    .collect { refreshStats() }
                retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                logPlatformWarning(TAG, "Encounter stats channel failed; retrying: $error")
            }
            delay(retryDelayMillis)
            retryDelayMillis = (retryDelayMillis * 2)
                .coerceAtMost(MAX_REALTIME_RETRY_MILLIS)
        }
    }

    private suspend fun collectRealtimeNotifications(accountId: UserId) {
        var retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
        while (currentCoroutineContext().isActive) {
            bundle.notifications.refreshNotifications(accountId)
            postUnreadNearbyNotifications(accountId)
            try {
                realtime
                    .notificationInvalidations(accountId.value)
                    .collect { change ->
                        if (change == NotificationChange.Inserted) {
                            soundEffects.play(SoundEffect.Notification)
                        }
                        bundle.notifications.refreshNotifications(accountId)
                        postUnreadNearbyNotifications(accountId)
                        refreshConversationsForNotifications(accountId)
                    }
                retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                logPlatformWarning(
                    TAG,
                    "Notification invalidation channel failed; retrying: $error",
                )
            }
            delay(retryDelayMillis)
            retryDelayMillis = (retryDelayMillis * 2)
                .coerceAtMost(MAX_REALTIME_RETRY_MILLIS)
        }
    }

    private suspend fun postUnreadNearbyNotifications(accountId: UserId) {
        val settings = settingsRepository.settings.first()
        // Passes are announced once, when they arrive; a reinstall or cold start
        // records the unread backlog as seen instead of replaying it, just as
        // the encounter list never resurfaces old passes.
        val plan = planNearbyAlerts(
            unread = database.notificationDao().getUnreadNearbyForAccount(accountId.value),
            seenThroughEpochMillis = settings.nearbyAlertsSeenThroughEpochMillis,
            nowEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )
        if (plan.seenThroughEpochMillis != settings.nearbyAlertsSeenThroughEpochMillis) {
            settingsRepository.setNearbyAlertsSeenThrough(plan.seenThroughEpochMillis)
        }
        plan.announce.forEach { notification ->
            if (
                postedNearbyNotificationIds.add(notification.notificationId) &&
                settings.encounterAlertsEnabled
            ) {
                onNearbyEncounterNotification(
                    notification.actorDisplayName
                        ?.takeIf(String::isNotBlank)
                        ?: "Someone",
                    notification.notificationId,
                )
            }
        }
    }

    private suspend fun refreshConversationsForNotifications(accountId: UserId) {
        val pending = database.notificationDao().conversationIdsNeedingRefresh(accountId.value)
        if (pending.isNotEmpty()) {
            bundle.messages.refreshConversations(accountId)
        }
    }

    private suspend fun collectRealtimeConversations(accountId: UserId) = coroutineScope {
        val channelJobs = mutableMapOf<ConversationId, Job>()
        try {
            database.conversationDao()
                .observeForAccount(accountId.value)
                .map { rows ->
                    rows.mapTo(linkedSetOf()) { row ->
                        ConversationId(row.conversationId)
                    }
                }
                .distinctUntilChanged()
                .collect { conversationIds ->
                    val removed = channelJobs.keys - conversationIds
                    removed.forEach { conversationId ->
                        channelJobs.remove(conversationId)?.cancel()
                        presence.clearConversation(conversationId)
                    }

                    (conversationIds - channelJobs.keys).forEach { conversationId ->
                        channelJobs[conversationId] = launch {
                            collectConversationRealtime(
                                accountId = accountId,
                                conversationId = conversationId,
                            )
                        }
                    }
                }
        } finally {
            channelJobs.values.forEach(Job::cancel)
            channelJobs.keys.forEach(presence::clearConversation)
        }
    }

    private suspend fun collectConversationRealtime(
        accountId: UserId,
        conversationId: ConversationId,
    ) {
        var retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
        while (currentCoroutineContext().isActive) {
            bundle.messages.refreshConversations(accountId)
            bundle.messages.refreshMessages(accountId, conversationId)

            try {
                realtime
                    .conversationEvents(
                        conversationId = conversationId.value,
                        userId = accountId.value,
                        typingUpdates = observeSelfTyping(conversationId),
                    )
                    .collect { event ->
                        when (event) {
                            is ConversationRealtimeEvent.MessageInvalidated -> {
                                val invalidation = event.invalidation
                                if (
                                    invalidation.operation == MessageChangeOperation.Insert &&
                                    invalidation.senderId != null &&
                                    invalidation.senderId != accountId.value
                                ) {
                                    soundEffects.play(SoundEffect.MessageReceived)
                                }
                                bundle.messages.refreshMessages(
                                    accountId,
                                    conversationId,
                                )
                                bundle.messages.refreshConversations(accountId)
                            }

                            is ConversationRealtimeEvent.ConversationInvalidated -> {
                                bundle.messages.refreshConversations(accountId)
                            }

                            is ConversationRealtimeEvent.PresenceChanged -> {
                                presence.replaceConversationPresence(
                                    conversationId = conversationId,
                                    snapshot = event.presences
                                        .mapNotNull { presenceState ->
                                            runCatching {
                                                UserId(presenceState.userId) to
                                                    PresenceStatus.Online
                                            }.getOrNull()
                                        }
                                        .toMap(),
                                )
                                presence.replaceConversationTyping(
                                    conversationId = conversationId,
                                    typingUserIds = event.presences
                                        .filter(PresenceStateDto::isTyping)
                                        .mapNotNull { presenceState ->
                                            runCatching {
                                                UserId(presenceState.userId)
                                            }.getOrNull()
                                        }
                                        .filterNot { it == accountId }
                                        .toSet(),
                                )
                            }
                        }
                    }
                retryDelayMillis = INITIAL_REALTIME_RETRY_MILLIS
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                presence.clearConversation(conversationId)
                logPlatformWarning(TAG, "Conversation Realtime channel failed; retrying: $error")
            }

            delay(retryDelayMillis)
            retryDelayMillis = (retryDelayMillis * 2)
                .coerceAtMost(MAX_REALTIME_RETRY_MILLIS)
        }
    }

    private companion object {
        const val TAG = "PocketPassRealtime"
        const val INITIAL_REALTIME_RETRY_MILLIS = 1_000L
        const val LAST_SEEN_TOUCH_INTERVAL_MILLIS = 5 * 60_000L
        const val LAST_SEEN_REFRESH_DELAY_MILLIS = 4_000L
        const val MAX_REALTIME_RETRY_MILLIS = 30_000L
    }
}
