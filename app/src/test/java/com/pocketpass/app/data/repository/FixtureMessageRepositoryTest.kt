package com.pocketpass.app.data.repository

import com.pocketpass.app.domain.model.AddGroupMembersCommand
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.ConversationKind
import com.pocketpass.app.domain.model.CreateGroupConversationCommand
import com.pocketpass.app.domain.model.DeleteMessageCommand
import com.pocketpass.app.domain.model.EditMessageCommand
import com.pocketpass.app.domain.model.LeaveGroupConversationCommand
import com.pocketpass.app.domain.model.MAX_GROUP_MEMBERS
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.RemoveGroupMemberCommand
import com.pocketpass.app.domain.model.SendMessageCommand
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FixtureMessageRepositoryTest {
    @Test
    fun repeatedClientOperationReturnsTheSameMessageWithoutDuplicatingIt() = runBlocking {
        val sentAt = Instant.parse("2026-02-03T04:05:06Z")
        val repository = FixtureMessageRepository(clock = { sentAt })
        val command = SendMessageCommand(
            accountId = FixtureData.CurrentUserId,
            conversationId = FixtureData.SpobConversationId,
            messageId = MessageId("local-message-1"),
            clientOperationId = ClientOperationId("operation-1"),
            body = "Idempotent hello",
            clientCreatedAt = sentAt.minus((1).seconds),
        )

        val first = repository.sendMessage(command)
        val second = repository.sendMessage(command)

        assertTrue(first is RepositoryResult.Success)
        assertTrue(second is RepositoryResult.Success)
        assertSame(
            (first as RepositoryResult.Success).value,
            (second as RepositoryResult.Success).value,
        )
        val stored = repository.observeMessages(
            FixtureData.CurrentUserId,
            FixtureData.SpobConversationId,
        ).first()
        assertEquals(1, stored.count { it.clientOperationId == command.clientOperationId })
        assertEquals("Idempotent hello", stored.last().body)
    }

    @Test
    fun editRejectsMessagesFromOtherSenders() = runBlocking {
        val repository = FixtureMessageRepository()

        val result = repository.editMessage(
            EditMessageCommand(
                accountId = FixtureData.CurrentUserId,
                conversationId = FixtureData.SpobConversationId,
                messageId = MessageId("fixture-spob-incoming"),
                body = "Hijacked",
                editedAt = Instant.parse("2026-02-03T04:05:06Z"),
            ),
        )

        assertTrue(result is RepositoryResult.Failure)
        assertEquals(
            RepositoryFailureKind.Conflict,
            (result as RepositoryResult.Failure).error.kind,
        )
    }

    @Test
    fun editStampsEditedAtAndRefreshesThePreview() = runBlocking {
        val editedAt = Instant.parse("2026-02-03T04:05:06Z")
        val repository = FixtureMessageRepository(clock = { editedAt })

        val result = repository.editMessage(
            EditMessageCommand(
                accountId = FixtureData.CurrentUserId,
                conversationId = FixtureData.SpobConversationId,
                messageId = MessageId("fixture-spob-outgoing"),
                body = "yo (fixed)",
                editedAt = editedAt,
            ),
        )

        assertTrue(result is RepositoryResult.Success)
        val stored = repository.observeMessages(
            FixtureData.CurrentUserId,
            FixtureData.SpobConversationId,
        ).first()
        assertEquals("yo (fixed)", stored.last().body)
        assertEquals(editedAt, stored.last().editedAt)
        val conversation = repository.observeConversations(FixtureData.CurrentUserId)
            .first()
            .first { it.id == FixtureData.SpobConversationId }
        assertEquals("yo (fixed)", conversation.latestMessagePreview)
    }

    @Test
    fun deleteHidesTheMessageRecomputesThePreviewAndBlocksLaterEdits() = runBlocking {
        val deletedAt = Instant.parse("2026-02-03T04:05:06Z")
        val repository = FixtureMessageRepository(clock = { deletedAt })

        val result = repository.deleteMessage(
            DeleteMessageCommand(
                accountId = FixtureData.CurrentUserId,
                conversationId = FixtureData.SpobConversationId,
                messageId = MessageId("fixture-spob-outgoing"),
                deletedAt = deletedAt,
            ),
        )

        assertTrue(result is RepositoryResult.Success)
        assertEquals(deletedAt, (result as RepositoryResult.Success).value.deletedAt)
        val stored = repository.observeMessages(
            FixtureData.CurrentUserId,
            FixtureData.SpobConversationId,
        ).first()
        assertEquals(listOf("Hey bro"), stored.map { it.body })
        val conversation = repository.observeConversations(FixtureData.CurrentUserId)
            .first()
            .first { it.id == FixtureData.SpobConversationId }
        assertEquals("Hey bro", conversation.latestMessagePreview)

        val edit = repository.editMessage(
            EditMessageCommand(
                accountId = FixtureData.CurrentUserId,
                conversationId = FixtureData.SpobConversationId,
                messageId = MessageId("fixture-spob-outgoing"),
                body = "too late",
                editedAt = deletedAt,
            ),
        )
        assertTrue(edit is RepositoryResult.Failure)
    }

    @Test
    fun createGroupDedupesByClientOperationId() = runBlocking {
        val repository = FixtureMessageRepository(clock = { Instant.parse("2026-02-03T04:05:06Z") })
        val command = CreateGroupConversationCommand(
            accountId = FixtureData.CurrentUserId,
            title = " Trip ",
            memberIds = listOf(UserId("matt-1"), UserId("matt-2")),
            clientOperationId = ClientOperationId("group-op-1"),
        )

        val first = repository.createGroupConversation(command) as RepositoryResult.Success
        val second = repository.createGroupConversation(command) as RepositoryResult.Success

        assertEquals(first.value, second.value)
        val rows = repository.observeConversations(FixtureData.CurrentUserId).first()
        val group = rows.first { it.id == first.value }
        assertEquals(1, rows.count { it.id == first.value })
        assertEquals("Trip", group.title)
        assertEquals(ConversationKind.Group, group.kind)
        assertEquals(FixtureData.CurrentUserId, group.ownerId)
        assertEquals(listOf("Petah Griffin", "Matt", "Matt"), group.members.map { it.displayName })
    }

    @Test
    fun addGroupMembersEnforcesTheCap() = runBlocking {
        val repository = FixtureMessageRepository()
        val created = repository.createGroupConversation(
            CreateGroupConversationCommand(
                accountId = FixtureData.CurrentUserId,
                title = "Big",
                memberIds = (1..19).map { UserId("extra-$it") },
            ),
        ) as RepositoryResult.Success

        val result = repository.addGroupMembers(
            AddGroupMembersCommand(
                accountId = FixtureData.CurrentUserId,
                conversationId = created.value,
                memberIds = listOf(UserId("one-too-many")),
            ),
        )

        assertTrue(result is RepositoryResult.Failure)
        assertEquals(RepositoryFailureKind.Conflict, (result as RepositoryResult.Failure).error.kind)
        val group = repository.observeConversations(FixtureData.CurrentUserId).first().first { it.id == created.value }
        assertEquals(MAX_GROUP_MEMBERS, group.memberCount)
    }

    @Test
    fun removeGroupMemberRequiresTheOwner() = runBlocking {
        val repository = FixtureMessageRepository(accountId = FixtureData.SpobUserId)

        val result = repository.removeGroupMember(
            RemoveGroupMemberCommand(
                accountId = FixtureData.SpobUserId,
                conversationId = FixtureData.CrewConversationId,
                userId = FixtureData.SansUserId,
            ),
        )

        assertTrue(result is RepositoryResult.Failure)
        assertEquals(RepositoryFailureKind.Forbidden, (result as RepositoryResult.Failure).error.kind)
        val crew = repository.observeConversations(FixtureData.SpobUserId).first()
            .first { it.id == FixtureData.CrewConversationId }
        assertEquals(3, crew.memberCount)
    }

    @Test
    fun leaveGroupDeletesTheConversationAndItsMessages() = runBlocking {
        val repository = FixtureMessageRepository()

        val result = repository.leaveGroupConversation(
            LeaveGroupConversationCommand(
                accountId = FixtureData.CurrentUserId,
                conversationId = FixtureData.CrewConversationId,
            ),
        )

        assertTrue(result is RepositoryResult.Success)
        val rows = repository.observeConversations(FixtureData.CurrentUserId).first()
        assertTrue(rows.none { it.id == FixtureData.CrewConversationId })
        assertTrue(
            repository.observeMessages(FixtureData.CurrentUserId, FixtureData.CrewConversationId).first().isEmpty(),
        )
    }
}
