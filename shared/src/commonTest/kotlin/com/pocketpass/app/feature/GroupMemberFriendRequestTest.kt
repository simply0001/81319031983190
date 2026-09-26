package com.pocketpass.app.feature

import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.data.repository.FixtureFriendsRepository
import com.pocketpass.app.data.repository.FixtureMessageRepository
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.FriendshipStatus
import com.pocketpass.app.domain.model.SendFriendRequestCommand
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.repository.MutableFriendsRepository
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.model.GroupMemberFriendState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class GroupMemberFriendRequestTest {
    @Test
    fun regularGroupMembersCanSendOneRequestWithoutLeavingTheGroup() = runTest {
        val repository = RequestRepository()
        repository.completion = CompletableDeferred()
        val holder = MessagesStateHolder(
            accountId = flowOf(FixtureData.SpobUserId),
            conversationRepository = FixtureMessageRepository(accountId = FixtureData.SpobUserId),
            friendsRepository = repository,
            scope = backgroundScope,
        )
        runCurrent()
        holder.openConversation(FixtureData.CrewConversationId)
        holder.openGroupInfo()
        runCurrent()
        assertFalse(holder.state.value.isGroupOwner)
        assertEquals(GroupMemberFriendState.Available, holder.state.value.groupMemberFriendStates[FixtureData.SansUserId])

        holder.addGroupMemberFriend(FixtureData.SpobUserId)
        holder.addGroupMemberFriend(UserId("not-in-the-group"))
        holder.addGroupMemberFriend(FixtureData.SansUserId)
        holder.addGroupMemberFriend(FixtureData.SansUserId)
        runCurrent()
        assertEquals(1, repository.commands.size)
        assertEquals(FixtureData.SpobUserId, repository.commands.single().accountId)
        assertEquals(FixtureData.SansUserId, repository.commands.single().addressee.userId)
        assertEquals(GroupMemberFriendState.Sending, holder.state.value.groupMemberFriendStates[FixtureData.SansUserId])
        repository.completion!!.complete(Unit)
        runCurrent()
        assertEquals(GroupMemberFriendState.Pending, holder.state.value.groupMemberFriendStates[FixtureData.SansUserId])
        assertTrue(holder.state.value.groupInfoOpen)
        holder.addGroupMemberFriend(FixtureData.SansUserId)
        runCurrent()
        assertEquals(1, repository.commands.size)
        repository.rows.value = listOf(friend(FriendshipStatus.Accepted))
        runCurrent()
        assertEquals(GroupMemberFriendState.Friends, holder.state.value.groupMemberFriendStates[FixtureData.SansUserId])
        repository.rows.value = emptyList()
        runCurrent()
        assertEquals(GroupMemberFriendState.Available, holder.state.value.groupMemberFriendStates[FixtureData.SansUserId])
    }

    @Test
    fun existingFriendsIncomingOutgoingRequestsAndBlockedUsersCannotBeRequestedAgain() = runTest {
        val repository = RequestRepository()
        val holder = MessagesStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            conversationRepository = FixtureMessageRepository(),
            friendsRepository = repository,
            scope = backgroundScope,
        )
        runCurrent()
        holder.openConversation(FixtureData.CrewConversationId)
        holder.openGroupInfo()
        runCurrent()
        for ((status, expected) in listOf(
            FriendshipStatus.Accepted to GroupMemberFriendState.Friends,
            FriendshipStatus.PendingIncoming to GroupMemberFriendState.Pending,
            FriendshipStatus.PendingOutgoing to GroupMemberFriendState.Pending,
            FriendshipStatus.Blocked to GroupMemberFriendState.Unavailable,
        )) {
            repository.rows.value = listOf(friend(status))
            runCurrent()
            assertEquals(expected, holder.state.value.groupMemberFriendStates[FixtureData.SansUserId])
            holder.addGroupMemberFriend(FixtureData.SansUserId)
            runCurrent()
            assertTrue(repository.commands.isEmpty())
        }
        repository.rows.value = emptyList()
        runCurrent()
        holder.closeGroupInfo()
        holder.addGroupMemberFriend(FixtureData.SansUserId)
        runCurrent()
        assertTrue(repository.commands.isEmpty())
    }

    @Test
    fun failedRequestsCanBeRetriedAndConflictsBecomePending() = runTest {
        val repository = RequestRepository()
        repository.failure = RepositoryFailure(RepositoryFailureKind.Offline, "Offline")
        val holder = MessagesStateHolder(
            accountId = flowOf(FixtureData.CurrentUserId),
            conversationRepository = FixtureMessageRepository(),
            friendsRepository = repository,
            scope = backgroundScope,
        )
        runCurrent()
        holder.openConversation(FixtureData.CrewConversationId)
        holder.openGroupInfo()
        holder.addGroupMemberFriend(FixtureData.SansUserId)
        runCurrent()
        assertEquals(GroupMemberFriendState.Failed, holder.state.value.groupMemberFriendStates[FixtureData.SansUserId])
        assertEquals("Connect to the internet and try again.", holder.state.value.groupOperationError)
        repository.failure = RepositoryFailure(RepositoryFailureKind.Conflict, "Already pending")
        holder.addGroupMemberFriend(FixtureData.SansUserId)
        runCurrent()
        assertEquals(2, repository.commands.size)
        assertEquals(GroupMemberFriendState.Pending, holder.state.value.groupMemberFriendStates[FixtureData.SansUserId])
        assertEquals(null, holder.state.value.groupOperationError)
    }

    @Test
    fun lateRequestResultsDoNotLeakAcrossAccounts() = runTest {
        val account = MutableStateFlow<UserId?>(FixtureData.CurrentUserId)
        val repository = RequestRepository()
        repository.completion = CompletableDeferred()
        val holder = MessagesStateHolder(
            accountId = account,
            conversationRepository = FixtureMessageRepository(),
            friendsRepository = repository,
            scope = backgroundScope,
        )
        runCurrent()
        holder.openConversation(FixtureData.CrewConversationId)
        holder.openGroupInfo()
        holder.addGroupMemberFriend(FixtureData.SansUserId)
        runCurrent()
        account.value = null
        runCurrent()
        assertEquals(0, repository.rows.subscriptionCount.value)
        repository.completion!!.complete(Unit)
        runCurrent()
        assertTrue(holder.state.value.groupMemberFriendStates.isEmpty())
        assertEquals(null, holder.state.value.groupOperationError)
        assertFalse(holder.state.value.groupInfoOpen)
    }

    private fun friend(status: FriendshipStatus) = Friend(
        ownerId = FixtureData.CurrentUserId,
        profile = UserProfile(FixtureData.SansUserId, "sans", null, updatedAt = Instant.parse("2026-01-01T00:00:00Z")),
        status = status,
        lastInteractionAt = null,
    )

    private class RequestRepository : MutableFriendsRepository by FixtureFriendsRepository() {
        val rows = MutableStateFlow<List<Friend>>(emptyList())
        val commands = mutableListOf<SendFriendRequestCommand>()
        var completion: CompletableDeferred<Unit>? = null
        var failure: RepositoryFailure? = null

        override fun observeFriends(accountId: UserId) = rows

        override suspend fun sendFriendRequest(command: SendFriendRequestCommand): RepositoryResult<Friend> {
            commands += command
            completion?.await()
            failure?.let { return RepositoryResult.Failure(it) }
            return RepositoryResult.Success(
                Friend(command.accountId, command.addressee, FriendshipStatus.PendingOutgoing, command.requestedAt),
            )
        }
    }
}
