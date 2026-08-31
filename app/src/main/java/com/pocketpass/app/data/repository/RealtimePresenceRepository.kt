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

class RealtimePresenceRepository : PresenceRepository {
    private val lock = Any()
    private val conversationPresence =
        mutableMapOf<ConversationId, Map<UserId, PresenceStatus>>()
    private val conversationTyping =
        mutableMapOf<ConversationId, Set<UserId>>()
    private val typingConversations =
        MutableStateFlow<Map<ConversationId, Set<UserId>>>(emptyMap())
    private val friendPresence =
        mutableMapOf<String, Map<UserId, PresenceStatus>>()
    private val localPresence = mutableMapOf<UserId, PresenceStatus>()
    private val aggregatePresence =
        MutableStateFlow<Map<UserId, PresenceStatus>>(emptyMap())

    override fun observePresence(
        userIds: Set<UserId>,
    ): Flow<Map<UserId, PresenceStatus>> = aggregatePresence
        .map { current -> current.filterKeys(userIds::contains) }
        .distinctUntilChanged()

    override suspend fun setLocalPresence(
        accountId: UserId,
        status: PresenceStatus,
    ): RepositoryResult<Unit> {
        synchronized(lock) {
            if (status == PresenceStatus.Unknown) {
                localPresence.remove(accountId)
            } else {
                localPresence[accountId] = status
            }
            publishLocked()
        }
        return RepositoryResult.Success(Unit)
    }

    fun replaceConversationPresence(
        conversationId: ConversationId,
        snapshot: Map<UserId, PresenceStatus>,
    ) {
        synchronized(lock) {
            if (snapshot.isEmpty()) {
                conversationPresence.remove(conversationId)
            } else {
                conversationPresence[conversationId] = snapshot.toMap()
            }
            publishLocked()
        }
    }

    override fun observeTypingConversations(): Flow<Map<ConversationId, Set<UserId>>> =
        typingConversations

    fun replaceConversationTyping(
        conversationId: ConversationId,
        typingUserIds: Set<UserId>,
    ) {
        synchronized(lock) {
            if (typingUserIds.isEmpty()) {
                conversationTyping.remove(conversationId)
            } else {
                conversationTyping[conversationId] = typingUserIds.toSet()
            }
            typingConversations.value = conversationTyping.toMap()
        }
    }

    fun clearConversation(conversationId: ConversationId) {
        synchronized(lock) {
            conversationPresence.remove(conversationId)
            conversationTyping.remove(conversationId)
            typingConversations.value = conversationTyping.toMap()
            publishLocked()
        }
    }

    fun replaceFriendPresence(
        pairKey: String,
        snapshot: Map<UserId, PresenceStatus>,
    ) {
        synchronized(lock) {
            if (snapshot.isEmpty()) {
                friendPresence.remove(pairKey)
            } else {
                friendPresence[pairKey] = snapshot.toMap()
            }
            publishLocked()
        }
    }

    fun clearFriendPresence(pairKey: String) {
        synchronized(lock) {
            friendPresence.remove(pairKey)
            publishLocked()
        }
    }

    fun clearAll() {
        synchronized(lock) {
            conversationPresence.clear()
            friendPresence.clear()
            localPresence.clear()
            publishLocked()
        }
    }

    private fun publishLocked() {
        val merged = buildMap {
            conversationPresence.values.forEach(::putAll)
            friendPresence.values.forEach(::putAll)
            putAll(localPresence)
        }
        aggregatePresence.value = merged
    }
}
