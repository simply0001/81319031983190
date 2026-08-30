package com.pocketpass.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pocketpass.app.data.local.dao.OutboxEnqueueResult
import com.pocketpass.app.data.local.entity.LocalDeliveryStates
import com.pocketpass.app.data.local.entity.LocalOutboxStates
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.SendMessageCommand
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.sync.MessageOutboxStore
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutboxDaoTest {
    private lateinit var database: PocketPassDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketPassDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun enqueueIsAtomicIdempotentAndRequiresTheActiveLeaseToComplete() = runBlocking {
        val store = MessageOutboxStore(database.outboxDao())
        val command = command()

        val first = store.enqueue(command)
        val duplicate = store.enqueue(command)

        assertTrue(first is OutboxEnqueueResult.Enqueued)
        assertTrue(duplicate is OutboxEnqueueResult.AlreadyEnqueued)
        assertEquals(1, database.outboxDao().pendingCount(ACCOUNT.value))

        val queuedMessage = database.messageDao().get(ACCOUNT.value, MESSAGE.value)
        assertEquals(LocalDeliveryStates.QUEUED, queuedMessage?.deliveryState)

        val claimed = database.outboxDao().claimNext(
            accountId = ACCOUNT.value,
            nowEpochMillis = NOW.toEpochMilliseconds(),
            leaseUntilEpochMillis = NOW.plus((120).seconds).toEpochMilliseconds(),
            leaseToken = "active-lease",
        )
        requireNotNull(claimed)
        assertEquals(1, claimed.attemptCount)
        assertEquals(LocalOutboxStates.IN_FLIGHT, claimed.state)
        assertEquals(
            LocalDeliveryStates.SENDING,
            database.messageDao().get(ACCOUNT.value, MESSAGE.value)?.deliveryState,
        )

        assertFalse(
            database.outboxDao().markSucceeded(
                operationId = OPERATION.value,
                leaseToken = "stale-lease",
                completedAtEpochMillis = NOW.plus((1).seconds).toEpochMilliseconds(),
            ),
        )
        assertTrue(
            database.outboxDao().markSucceeded(
                operationId = OPERATION.value,
                leaseToken = "active-lease",
                completedAtEpochMillis = NOW.plus((1).seconds).toEpochMilliseconds(),
            ),
        )
        assertEquals(
            LocalOutboxStates.SUCCEEDED,
            database.outboxDao().get(OPERATION.value)?.state,
        )
        assertEquals(
            LocalDeliveryStates.SYNCED,
            database.messageDao().get(ACCOUNT.value, MESSAGE.value)?.deliveryState,
        )
        assertEquals(0, database.outboxDao().pendingCount(ACCOUNT.value))
    }

    @Test
    fun retryIsNotClaimableEarlyAndCanBePermanentlyFailedAfterReclaim() = runBlocking {
        val store = MessageOutboxStore(database.outboxDao())
        store.enqueue(command())
        database.outboxDao().claimNext(
            accountId = ACCOUNT.value,
            nowEpochMillis = NOW.toEpochMilliseconds(),
            leaseUntilEpochMillis = NOW.plus((120).seconds).toEpochMilliseconds(),
            leaseToken = "lease-1",
        )
        val retryAt = NOW.plus((30).seconds)

        assertTrue(
            database.outboxDao().markRetryable(
                operationId = OPERATION.value,
                leaseToken = "lease-1",
                nextAttemptAtEpochMillis = retryAt.toEpochMilliseconds(),
                errorCode = "OFFLINE",
                errorMessage = "No network",
            ),
        )
        assertNull(
            database.outboxDao().claimNext(
                accountId = ACCOUNT.value,
                nowEpochMillis = NOW.plus((29).seconds).toEpochMilliseconds(),
                leaseUntilEpochMillis = NOW.plus((149).seconds).toEpochMilliseconds(),
                leaseToken = "too-early",
            ),
        )

        val reclaimed = database.outboxDao().claimNext(
            accountId = ACCOUNT.value,
            nowEpochMillis = retryAt.toEpochMilliseconds(),
            leaseUntilEpochMillis = retryAt.plus((120).seconds).toEpochMilliseconds(),
            leaseToken = "lease-2",
        )
        requireNotNull(reclaimed)
        assertEquals(2, reclaimed.attemptCount)
        assertTrue(
            database.outboxDao().markPermanentlyFailed(
                operationId = OPERATION.value,
                leaseToken = "lease-2",
                completedAtEpochMillis = retryAt.plus((1).seconds).toEpochMilliseconds(),
                errorCode = "VALIDATION",
                errorMessage = "Rejected",
            ),
        )
        assertEquals(
            LocalDeliveryStates.FAILED_PERMANENT,
            database.messageDao().get(ACCOUNT.value, MESSAGE.value)?.deliveryState,
        )
    }

    @Test
    fun reusingAnIdempotencyKeyForDifferentContentIsRejected() = runBlocking {
        val store = MessageOutboxStore(database.outboxDao())
        assertTrue(store.enqueue(command()) is OutboxEnqueueResult.Enqueued)

        val conflicting = store.enqueue(
            command().copy(body = "Different payload under the same key"),
        )

        assertTrue(conflicting is OutboxEnqueueResult.Conflict)
        assertEquals(
            "Queued exactly once",
            database.messageDao().get(ACCOUNT.value, MESSAGE.value)?.body,
        )
        assertEquals(1, database.outboxDao().pendingCount(ACCOUNT.value))
    }

    private fun command() = SendMessageCommand(
        accountId = ACCOUNT,
        conversationId = CONVERSATION,
        messageId = MESSAGE,
        clientOperationId = OPERATION,
        body = "Queued exactly once",
        clientCreatedAt = NOW,
    )

    private companion object {
        val ACCOUNT = UserId("account-one")
        val CONVERSATION = ConversationId("conversation-one")
        val MESSAGE = MessageId("message-one")
        val OPERATION = ClientOperationId("operation-one")
        val NOW: Instant = Instant.parse("2026-04-05T06:07:08Z")
    }
}
