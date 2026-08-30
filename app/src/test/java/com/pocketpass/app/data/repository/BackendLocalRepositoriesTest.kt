package com.pocketpass.app.data.repository

import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.PresenceStatus
import com.pocketpass.app.domain.model.UserId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BackendLocalRepositoriesTest {
    @Test
    fun realtimePresenceIsEphemeralAndMergedAcrossConversationChannels() = runTest {
        val repository = RealtimePresenceRepository()
        val alex = UserId("alex")
        val broco = UserId("broco")
        val k0o1 = UserId("k0o1")
        val firstConversation = ConversationId("first")
        val secondConversation = ConversationId("second")

        repository.replaceConversationPresence(
            firstConversation,
            mapOf(
                alex to PresenceStatus.Online,
                broco to PresenceStatus.Away,
            ),
        )
        repository.replaceConversationPresence(
            secondConversation,
            mapOf(
                alex to PresenceStatus.Online,
                k0o1 to PresenceStatus.Online,
            ),
        )

        assertEquals(
            mapOf(
                alex to PresenceStatus.Online,
                broco to PresenceStatus.Away,
                k0o1 to PresenceStatus.Online,
            ),
            repository.observePresence(setOf(alex, broco, k0o1)).first(),
        )

        repository.clearConversation(firstConversation)
        assertEquals(
            mapOf(
                alex to PresenceStatus.Online,
                k0o1 to PresenceStatus.Online,
            ),
            repository.observePresence(setOf(alex, broco, k0o1)).first(),
        )

        repository.replaceFriendPresence(
            "alex:broco",
            mapOf(broco to PresenceStatus.Online),
        )
        assertEquals(
            PresenceStatus.Online,
            repository.observePresence(setOf(broco)).first()[broco],
        )
        repository.clearFriendPresence("alex:broco")
        assertEquals(
            emptyMap<UserId, PresenceStatus>(),
            repository.observePresence(setOf(broco)).first(),
        )

        repository.setLocalPresence(broco, PresenceStatus.Offline)
        assertEquals(
            PresenceStatus.Offline,
            repository.observePresence(setOf(broco)).first()[broco],
        )

        repository.clearAll()
        assertEquals(
            emptyMap<UserId, PresenceStatus>(),
            repository.observePresence(setOf(alex, broco, k0o1)).first(),
        )
    }
}
