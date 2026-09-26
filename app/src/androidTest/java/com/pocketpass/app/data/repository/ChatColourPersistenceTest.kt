package com.pocketpass.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pocketpass.app.data.local.PocketPassDatabase
import com.pocketpass.app.data.local.toDomain
import com.pocketpass.app.data.local.toEntity
import com.pocketpass.app.domain.model.*
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChatColourPersistenceTest {
    private lateinit var db: PocketPassDatabase
    private val account = UserId("colour-owner")
    private val now = Instant.fromEpochMilliseconds(1000)
    private val original = UserProfile(account, "Name", null, bio = "Bio", updatedAt = now)
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PocketPassDatabase::class.java).build() }
    @After fun cleanup() { db.close() }
    private fun command(colour: ChatBubbleColour, id: String) = SetChatBubbleColourCommand(account, colour, now, ClientOperationId(id))

    @Test fun queuedColourSurvivesRefreshAndUnrelatedProfileEdits() = runBlocking {
        db.profileDao().upsert(original.toEntity())
        val store = ProductionMutationStore(db)
        val command = command(ChatBubbleColour.Pink, "colour-one")
        store.enqueueChatColour(command)
        store.enqueueChatColour(command)
        assertEquals(1, db.outboxDao().get(command.clientOperationId.value)?.let { 1 })
        val reconciler = RoomRepositoryReconciler(db)
        reconciler.reconcileProfile(account, original.copy(bio = "Remote bio"))
        assertEquals(ChatBubbleColour.Pink, db.profileDao().get(account.value)!!.toDomain().chatBubbleColour)
        store.enqueueProfileUpdate(UpdateProfileCommand(account, original.copy(bio = "New bio"), ClientOperationId("bio-change"), now))
        val cached = db.profileDao().get(account.value)!!
        assertEquals("pink", cached.chatBubbleColour)
        assertEquals("colour-one", cached.chatColourOperationId)
        assertEquals("New bio", cached.bio)
        reconciler.reconcileChatColour(account, "colour-one", "pink")
        assertFalse(db.profileDao().get(account.value)!!.toDomain().chatColourPending)
    }

    @Test fun staleAcknowledgementsDoNotReplaceTheLatestColourAndRetriesStayOrdered() = runBlocking {
        db.profileDao().upsert(original.toEntity())
        val store = ProductionMutationStore(db)
        store.enqueueChatColour(command(ChatBubbleColour.Pink, "z-first"))
        store.enqueueChatColour(command(ChatBubbleColour.Teal, "a-second"))
        val reconciler = RoomRepositoryReconciler(db)
        reconciler.reconcileChatColour(account, "z-first", "pink")
        assertEquals("teal", db.profileDao().get(account.value)!!.chatBubbleColour)
        val first = db.outboxDao().claimNext(account.value, 2000, 3000, "lease")!!
        assertEquals("z-first", first.operationId)
        db.outboxDao().markRetryable(first.operationId, "lease", 9000, "NETWORK", "Offline")
        assertNull(db.outboxDao().claimNext(account.value, 4000, 5000, "lease-two"))
        reconciler.reconcileChatColour(account, "a-second", "teal", "Try again")
        assertEquals("Try again", db.profileDao().get(account.value)!!.chatColourError)
        store.enqueueChatColour(command(ChatBubbleColour.Blue, "third"))
        assertNull(db.profileDao().get(account.value)!!.chatColourError)
    }

    @Test fun missingProfileDoesNotLeaveAQueuedOperation() = runBlocking {
        assertTrue(runCatching { ProductionMutationStore(db).enqueueChatColour(command(ChatBubbleColour.Red, "missing")) }.isFailure)
        assertNull(db.outboxDao().get("missing"))
    }

    @Test fun pendingColourPersistsAcrossDatabaseReopenAndStaysWithItsAccount() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val filename = "chat-colours-${java.util.UUID.randomUUID()}.db"
        val other = UserId("other-colour-owner")
        db.close()
        try {
            db = Room.databaseBuilder(context, PocketPassDatabase::class.java, filename).build()
            db.profileDao().upsert(original.toEntity())
            db.profileDao().upsert(original.copy(userId = other).toEntity())
            ProductionMutationStore(db).enqueueChatColour(command(ChatBubbleColour.Green, "persisted"))
            db.close()
            db = Room.databaseBuilder(context, PocketPassDatabase::class.java, filename).build()
            assertEquals("green", db.profileDao().get(account.value)!!.chatBubbleColour)
            assertEquals("persisted", db.profileDao().get(account.value)!!.chatColourOperationId)
            assertEquals("default", db.profileDao().get(other.value)!!.chatBubbleColour)
            assertNull(db.outboxDao().claimNext(other.value, 2000, 3000, "wrong-account"))
            assertEquals("persisted", db.outboxDao().claimNext(account.value, 2000, 3000, "right-account")!!.operationId)
        } finally {
            db.close()
            context.deleteDatabase(filename)
        }
    }

    @Test fun oldAndNewMessagesResolveByAuthorEvenWithoutCurrentMembership() = runBlocking {
        val conversation = ConversationId("colour-group")
        val former = UserId("former-member")
        val hidden = UserId("no-longer-visible")
        val reconciler = RoomRepositoryReconciler(db)
        db.profileDao().upsert(original.copy(userId = former, chatBubbleColour = ChatBubbleColour.Purple).toEntity())
        db.profileDao().upsert(original.copy(userId = hidden, chatBubbleColour = ChatBubbleColour.Red).toEntity())
        val messages = listOf("old", "new").map { Message(MessageId(it), conversation, former, null, it, now) } +
            Message(MessageId("hidden"), conversation, hidden, null, "Hidden", now)
        reconciler.upsertMessages(account, messages)
        assertEquals(setOf(former.value, hidden.value), db.profileDao().messageAuthorIds(account.value, conversation.value).toSet())
        assertEquals(2, db.profileDao().observeMessageAuthors(account.value, conversation.value).first().size)
        assertTrue(db.profileDao().observeMessageAuthors(UserId("other").value, conversation.value).first().isEmpty())
        reconciler.reconcileProfile(former, original.copy(userId = former, chatBubbleColour = ChatBubbleColour.Teal))
        reconciler.reconcileProfile(hidden, null)
        val authors = db.profileDao().observeMessageAuthors(account.value, conversation.value).first()
        assertEquals(listOf("teal"), authors.map { it.chatBubbleColour })
        assertEquals(3, db.messageDao().observeForConversation(account.value, conversation.value).first().size)
    }

    @Test fun typingMemberColourIsAvailableBeforeTheirFirstMessage() = runBlocking {
        val conversation = ConversationId("new-group")
        val member = UserId("new-member")
        val other = UserId("other-account-member")
        val reconciler = RoomRepositoryReconciler(db)
        db.profileDao().upsert(original.copy(userId = member, chatBubbleColour = ChatBubbleColour.Pink).toEntity())
        db.profileDao().upsert(original.copy(userId = other, chatBubbleColour = ChatBubbleColour.Red).toEntity())
        val summary = ConversationSummary(conversation, "Group", null, "", null, 0,
            kind = ConversationKind.Group,
            members = listOf(ConversationMember(member, "New member", null, joinedAt = now)))
        reconciler.upsertConversations(account, listOf(summary))
        reconciler.upsertConversations(UserId("other-account"), listOf(summary.copy(
            members = listOf(ConversationMember(other, "Other", null, joinedAt = now)))))
        assertTrue(db.messageDao().observeForConversation(account.value, conversation.value).first().isEmpty())
        assertEquals(listOf(member.value), db.profileDao().messageAuthorIds(account.value, conversation.value))
        assertEquals(listOf("pink"), db.profileDao().observeMessageAuthors(account.value, conversation.value).first().map { it.chatBubbleColour })
    }
}
