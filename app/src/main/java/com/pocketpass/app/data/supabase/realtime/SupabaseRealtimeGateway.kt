package com.pocketpass.app.data.supabase.realtime

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.currentPresences
import io.github.jan.supabase.realtime.presenceDataFlow
import io.github.jan.supabase.realtime.track
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.util.UUID

class SupabaseRealtimeGateway(
    private val client: SupabaseClient,
) {
    fun conversationEvents(
        conversationId: String,
        userId: String,
        typingUpdates: Flow<Boolean> = emptyFlow(),
    ): Flow<ConversationRealtimeEvent> {
        val safeConversationId = requireUuidChannelSegment(conversationId, "conversationId")
        val safeUserId = requireUuidChannelSegment(userId, "userId")
        return callbackFlow {
            val channel = conversationChannel(
                conversationId = safeConversationId,
                presenceKey = safeUserId,
            )
            val collections = buildList {
                CONVERSATION_CHANGE_EVENTS.forEach { event ->
                    add(
                        launch(start = CoroutineStart.UNDISPATCHED) {
                            channel
                                .broadcastFlow<MessageChangeBroadcastDto>(event)
                                .collect { change ->
                                    change
                                        .toConversationRealtimeEvent(
                                            expectedConversationId = safeConversationId,
                                        )
                                        ?.let(::trySend)
                                }
                        },
                    )
                }
                add(
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        channel.presenceDataFlow<PresenceStateDto>().collect { presences ->
                            trySend(
                                ConversationRealtimeEvent.PresenceChanged(
                                    presences.distinctBy(PresenceStateDto::userId),
                                ),
                            )
                        }
                    },
                )
            }

            var typingJob: Job? = null
            try {
                channel.subscribe(blockUntilSubscribed = true)
                channel.track(
                    PresenceStateDto(
                        userId = safeUserId,
                        activeConversationId = safeConversationId,
                        isTyping = false,
                    ),
                )
                trySend(
                    ConversationRealtimeEvent.PresenceChanged(
                        channel.currentPresences<PresenceStateDto>(),
                    ),
                )
                typingJob = launch {
                    typingUpdates.distinctUntilChanged().collect { typing ->
                        runCatching {
                            channel.track(
                                PresenceStateDto(
                                    userId = safeUserId,
                                    activeConversationId = safeConversationId,
                                    isTyping = typing,
                                ),
                            )
                        }
                    }
                }
            } catch (error: Throwable) {
                collections.forEach(Job::cancel)
                close(error)
            }

            awaitClose {
                typingJob?.cancel()
                collections.forEach(Job::cancel)
                launch(NonCancellable) {
                    runCatching { channel.untrack() }
                    runCatching { channel.unsubscribe() }
                }
            }
        }
    }

    fun notificationInvalidations(userId: String): Flow<NotificationChange> {
        val safeUserId = requireUuidChannelSegment(userId, "userId")
        return callbackFlow {
            val channel = client.channel("$NOTIFICATION_CHANNEL_PREFIX$safeUserId") {
                isPrivate = true
                broadcast {
                    acknowledgeBroadcasts = true
                    receiveOwnBroadcasts = false
                }
            }
            val collectors = NOTIFICATION_CHANGE_EVENTS.map { event ->
                launch(start = CoroutineStart.UNDISPATCHED) {
                    channel
                        .broadcastFlow<NotificationChangeBroadcastDto>(event)
                        .collect { change ->
                            if (change.isFor(safeUserId)) {
                                trySend(
                                    if (change.operation.equals(INSERT_EVENT, ignoreCase = true)) {
                                        NotificationChange.Inserted
                                    } else {
                                        NotificationChange.Changed
                                    },
                                )
                            }
                        }
                }
            }
            try {
                channel.subscribe(blockUntilSubscribed = true)
            } catch (error: Throwable) {
                collectors.forEach(Job::cancel)
                close(error)
            }
            awaitClose {
                collectors.forEach(Job::cancel)
                launch(NonCancellable) {
                    runCatching { channel.unsubscribe() }
                }
            }
        }
    }

    fun friendInvalidations(userId: String): Flow<Unit> {
        val safeUserId = requireUuidChannelSegment(userId, "userId")
        return callbackFlow {
            val channel = client.channel("$FRIENDS_CHANNEL_PREFIX$safeUserId") {
                isPrivate = true
                broadcast {
                    acknowledgeBroadcasts = true
                    receiveOwnBroadcasts = false
                }
            }
            val collectors = FRIEND_CHANGE_EVENTS.map { event ->
                launch(start = CoroutineStart.UNDISPATCHED) {
                    channel.broadcastFlow<JsonObject>(event).collect {
                        trySend(Unit)
                    }
                }
            }
            try {
                channel.subscribe(blockUntilSubscribed = true)
            } catch (error: Throwable) {
                collectors.forEach(Job::cancel)
                close(error)
            }
            awaitClose {
                collectors.forEach(Job::cancel)
                launch(NonCancellable) {
                    runCatching { channel.unsubscribe() }
                }
            }
        }
    }

    fun tokenBalanceInvalidations(userId: String): Flow<TokenChannelEvent> {
        val safeUserId = requireUuidChannelSegment(userId, "userId")
        return callbackFlow {
            val channel = client.channel("$TOKENS_CHANNEL_PREFIX$safeUserId") {
                isPrivate = true
                broadcast {
                    acknowledgeBroadcasts = true
                    receiveOwnBroadcasts = false
                }
            }
            val collectors = TOKEN_CHANGE_EVENTS.map { event ->
                launch(start = CoroutineStart.UNDISPATCHED) {
                    channel.broadcastFlow<JsonObject>(event).collect {
                        trySend(TokenChannelEvent.Balance)
                    }
                }
            } + launch(start = CoroutineStart.UNDISPATCHED) {
                channel.broadcastFlow<JsonObject>(SUPPORTER_STATUS_EVENT).collect {
                    trySend(TokenChannelEvent.Supporter)
                }
            }
            try {
                channel.subscribe(blockUntilSubscribed = true)
            } catch (error: Throwable) {
                collectors.forEach(Job::cancel)
                close(error)
            }
            awaitClose {
                collectors.forEach(Job::cancel)
                launch(NonCancellable) {
                    runCatching { channel.unsubscribe() }
                }
            }
        }
    }

    fun appUpdateSignals(): Flow<Unit> = callbackFlow {
        val channel = client.channel(APP_UPDATES_CHANNEL) {
            isPrivate = true
            broadcast {
                acknowledgeBroadcasts = true
                receiveOwnBroadcasts = false
            }
        }
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            channel.broadcastFlow<JsonObject>(APP_UPDATE_EVENT).collect {
                trySend(Unit)
            }
        }
        try {
            channel.subscribe(blockUntilSubscribed = true)
        } catch (error: Throwable) {
            collector.cancel()
            close(error)
        }
        awaitClose {
            collector.cancel()
            launch(NonCancellable) {
                runCatching { channel.unsubscribe() }
            }
        }
    }

    fun encounterInvalidations(userId: String): Flow<Unit> {
        val safeUserId = requireUuidChannelSegment(userId, "userId")
        return callbackFlow {
            val channel = client.channel("$ENCOUNTERS_CHANNEL_PREFIX$safeUserId") {
                isPrivate = true
                broadcast {
                    acknowledgeBroadcasts = true
                    receiveOwnBroadcasts = false
                }
            }
            val collectors = ENCOUNTER_CHANGE_EVENTS.map { event ->
                launch(start = CoroutineStart.UNDISPATCHED) {
                    channel.broadcastFlow<JsonObject>(event).collect {
                        trySend(Unit)
                    }
                }
            }
            try {
                channel.subscribe(blockUntilSubscribed = true)
            } catch (error: Throwable) {
                collectors.forEach(Job::cancel)
                close(error)
            }
            awaitClose {
                collectors.forEach(Job::cancel)
                launch(NonCancellable) {
                    runCatching { channel.unsubscribe() }
                }
            }
        }
    }

    fun friendPresence(
        accountId: String,
        friendUserId: String,
    ): Flow<List<PresenceStateDto>> {
        val safeAccountId = requireUuidChannelSegment(accountId, "accountId")
        val safeFriendUserId = requireUuidChannelSegment(friendUserId, "friendUserId")
        require(safeAccountId != safeFriendUserId) {
            "Friend presence requires two different users"
        }
        val pair = listOf(safeAccountId, safeFriendUserId).sorted()
        return callbackFlow {
            val channel = client.channel(
                "$FRIEND_PRESENCE_CHANNEL_PREFIX${pair[0]}:${pair[1]}",
            ) {
                isPrivate = true
                presence {
                    key = safeAccountId
                }
            }
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                channel.presenceDataFlow<PresenceStateDto>().collect { presences ->
                    trySend(presences.distinctBy(PresenceStateDto::userId))
                }
            }
            try {
                channel.subscribe(blockUntilSubscribed = true)
                channel.track(PresenceStateDto(userId = safeAccountId))
                trySend(
                    channel.currentPresences<PresenceStateDto>()
                        .distinctBy(PresenceStateDto::userId),
                )
            } catch (error: Throwable) {
                collector.cancel()
                close(error)
            }
            awaitClose {
                collector.cancel()
                launch(NonCancellable) {
                    runCatching { channel.untrack() }
                    runCatching { channel.unsubscribe() }
                }
            }
        }
    }

    private fun conversationChannel(
        conversationId: String,
        presenceKey: String,
    ): RealtimeChannel =
        client.channel("$CONVERSATION_CHANNEL_PREFIX$conversationId") {
            isPrivate = true
            broadcast {
                acknowledgeBroadcasts = true
                receiveOwnBroadcasts = false
            }
            presence {
                key = presenceKey
            }
        }

    private fun requireUuidChannelSegment(value: String, name: String): String {
        val normalized = value.trim().lowercase()
        val parsed = runCatching { UUID.fromString(normalized) }.getOrNull()
        require(parsed != null && parsed.toString() == normalized) {
            "$name must be a canonical UUID"
        }
        return normalized
    }

    companion object {
        private const val CONVERSATION_CHANNEL_PREFIX = "conversation:"
        private const val NOTIFICATION_CHANNEL_PREFIX = "notifications:"
        private const val FRIENDS_CHANNEL_PREFIX = "friends:"
        private const val FRIEND_PRESENCE_CHANNEL_PREFIX = "friend-presence:"
        private const val TOKENS_CHANNEL_PREFIX = "tokens:"
        private const val ENCOUNTERS_CHANNEL_PREFIX = "encounters:"
        private const val APP_UPDATES_CHANNEL = "app_updates"
        private const val APP_UPDATE_EVENT = "app_update"
        private const val SUPPORTER_STATUS_EVENT = "supporter_status"
        private val CONVERSATION_CHANGE_EVENTS = listOf(
            INSERT_EVENT,
            UPDATE_EVENT,
            DELETE_EVENT,
            MEMBERSHIP_EVENT,
            CONVERSATION_EVENT,
        )
        private val FRIEND_CHANGE_EVENTS = listOf(INSERT_EVENT, UPDATE_EVENT, DELETE_EVENT)
        private val TOKEN_CHANGE_EVENTS = listOf(INSERT_EVENT, UPDATE_EVENT)
        private val ENCOUNTER_CHANGE_EVENTS = listOf(INSERT_EVENT, UPDATE_EVENT)
        private val NOTIFICATION_CHANGE_EVENTS = listOf(
            INSERT_EVENT,
            UPDATE_EVENT,
            DELETE_EVENT,
        )
    }
}

enum class TokenChannelEvent {
    Balance,
    Supporter,
}

enum class NotificationChange {
    Inserted,
    Changed,
}
