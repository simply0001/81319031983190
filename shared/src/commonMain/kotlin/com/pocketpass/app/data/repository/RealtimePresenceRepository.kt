package com.pocketpass.app.data.repository

import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.PresenceStatus
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.repository.PresenceRepository
import com.pocketpass.app.domain.state.RepositoryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

// Kotlin/Native has no monitor locks, so every mutation replaces one immutable
// snapshot through a compare-and-set update instead of guarding mutable maps.
class RealtimePresenceRepository : PresenceRepository {
    private class Snapshot(
        val conversationPresence: Map<ConversationId, Map<UserId, PresenceStatus>>,
        val conversationTyping: Map<ConversationId, Set<UserId>>,
        val friendPresence: Map<String, Map<UserId, PresenceStatus>>,
        val localPresence: Map<UserId, PresenceStatus>,
    ) {
        val aggregate: Map<UserId, PresenceStatus> by lazy {
            buildMap {
                conversationPresence.values.forEach(::putAll)
                friendPresence.values.forEach(::putAll)
                putAll(localPresence)
            }
        }

        fun with(
            conversationPresence: Map<ConversationId, Map<UserId, PresenceStatus>> =
                this.conversationPresence,
            conversationTyping: Map<ConversationId, Set<UserId>> = this.conversationTyping,
            friendPresence: Map<String, Map<UserId, PresenceStatus>> = this.friendPresence,
            localPresence: Map<UserId, PresenceStatus> = this.localPresence,
        ): Snapshot = Snapshot(
            conversationPresence = conversationPresence,
            conversationTyping = conversationTyping,
            friendPresence = friendPresence,
            localPresence = localPresence,
        )
    }

    private val snapshots = MutableStateFlow(
        Snapshot(
            conversationPresence = emptyMap(),
            conversationTyping = emptyMap(),
            friendPresence = emptyMap(),
            localPresence = emptyMap(),
        ),
    )

    override fun observePresence(
        userIds: Set<UserId>,
    ): Flow<Map<UserId, PresenceStatus>> = snapshots
        .map { current -> current.aggregate.filterKeys(userIds::contains) }
        .distinctUntilChanged()

    override suspend fun setLocalPresence(
        accountId: UserId,
        status: PresenceStatus,
    ): RepositoryResult<Unit> {
        snapshots.update { current ->
            current.with(
                localPresence = if (status == PresenceStatus.Unknown) {
                    current.localPresence - accountId
                } else {
                    current.localPresence + (accountId to status)
                },
            )
        }
        return RepositoryResult.Success(Unit)
    }

    fun replaceConversationPresence(
        conversationId: ConversationId,
        snapshot: Map<UserId, PresenceStatus>,
    ) {
        snapshots.update { current ->
            current.with(
                conversationPresence = if (snapshot.isEmpty()) {
                    current.conversationPresence - conversationId
                } else {
                    current.conversationPresence + (conversationId to snapshot.toMap())
                },
            )
        }
    }

    override fun observeTypingConversations(): Flow<Map<ConversationId, Set<UserId>>> =
        snapshots
            .map { it.conversationTyping }
            .distinctUntilChanged()

    fun replaceConversationTyping(
        conversationId: ConversationId,
        typingUserIds: Set<UserId>,
    ) {
        snapshots.update { current ->
            current.with(
                conversationTyping = if (typingUserIds.isEmpty()) {
                    current.conversationTyping - conversationId
                } else {
                    current.conversationTyping + (conversationId to typingUserIds.toSet())
                },
            )
        }
    }

    fun clearConversation(conversationId: ConversationId) {
        snapshots.update { current ->
            current.with(
                conversationPresence = current.conversationPresence - conversationId,
                conversationTyping = current.conversationTyping - conversationId,
            )
        }
    }

    fun replaceFriendPresence(
        pairKey: String,
        snapshot: Map<UserId, PresenceStatus>,
    ) {
        snapshots.update { current ->
            current.with(
                friendPresence = if (snapshot.isEmpty()) {
                    current.friendPresence - pairKey
                } else {
                    current.friendPresence + (pairKey to snapshot.toMap())
                },
            )
        }
    }

    fun clearFriendPresence(pairKey: String) {
        snapshots.update { current ->
            current.with(friendPresence = current.friendPresence - pairKey)
        }
    }

    fun clearAll() {
        snapshots.update { current ->
            current.with(
                conversationPresence = emptyMap(),
                friendPresence = emptyMap(),
                localPresence = emptyMap(),
            )
        }
    }
}
