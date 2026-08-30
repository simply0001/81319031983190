package com.pocketpass.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pocketpass.app.data.local.PocketPassDatabase
import com.pocketpass.app.data.local.entity.LocalOutboxStates
import com.pocketpass.app.data.local.entity.NotificationEntity
import com.pocketpass.app.data.local.entity.ShopCategoryEntity
import com.pocketpass.app.data.local.entity.ShopItemEntity
import com.pocketpass.app.data.local.entity.TokenBalanceEntity
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.MarkNotificationReadCommand
import com.pocketpass.app.domain.model.NotificationId
import com.pocketpass.app.domain.model.PresenceStatus
import com.pocketpass.app.domain.model.PurchaseShopItemCommand
import com.pocketpass.app.domain.model.SendFriendRequestCommand
import com.pocketpass.app.domain.model.UpdateProfileCommand
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.UserProfile
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionMutationStoreTest {
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
    fun profileChangeAndOutboxRecordAreCommittedTogetherAndIdempotently() = runBlocking {
        val store = ProductionMutationStore(database)
        val command = UpdateProfileCommand(
            accountId = ACCOUNT,
            profile = profile(ACCOUNT, "Updated"),
            clientOperationId = OPERATION,
            changedAt = NOW,
        )

        val first = store.enqueueProfileUpdate(command)
        val duplicate = store.enqueueProfileUpdate(command)

        assertTrue(first is OptimisticMutationResult.Enqueued)
        assertTrue(duplicate is OptimisticMutationResult.AlreadyEnqueued)
        assertEquals("Updated", database.profileDao().get(ACCOUNT.value)?.displayName)
        assertEquals(
            LocalOutboxStates.PENDING,
            database.outboxDao().get(OPERATION.value)?.state,
        )
        assertEquals(1, database.outboxDao().pendingCount(ACCOUNT.value))
    }

    @Test
    fun remoteFriendSnapshotCannotEraseAnActiveOptimisticFriendRequest() = runBlocking {
        val friendId = UserId("friend-one")
        val store = ProductionMutationStore(database)
        store.enqueueFriendRequest(
            SendFriendRequestCommand(
                accountId = ACCOUNT,
                addressee = profile(friendId, "Nearby friend"),
                clientOperationId = OPERATION,
                requestedAt = NOW,
            ),
        )

        RoomRepositoryReconciler(database).reconcileFriends(
            accountId = ACCOUNT,
            remoteFriends = emptyList(),
        )

        val friends = database.friendDao().observeForOwner(ACCOUNT.value).first()
        assertEquals(listOf(friendId.value), friends.map { it.friendUserId })
    }

    @Test
    fun notificationReadAndOutboxAreAtomicAndSurviveAnOlderRemoteSnapshot() = runBlocking {
        val notification = NotificationEntity(
            accountId = ACCOUNT.value,
            notificationId = "notification-one",
            kind = "System",
            actorUserId = null,
            actorDisplayName = null,
            actorAvatarKind = null,
            actorAvatarValue = null,
            actorUpdatedAtEpochMillis = null,
            friendRequestId = null,
            friendRequestStatus = null,
            conversationId = null,
            title = "PocketPass",
            body = "Inbox ready",
            eventCount = 1,
            createdAtEpochMillis = NOW.minus((60).seconds).toEpochMilliseconds(),
            updatedAtEpochMillis = NOW.minus((60).seconds).toEpochMilliseconds(),
            readAtEpochMillis = null,
            deletedAtEpochMillis = null,
        )
        database.notificationDao().replaceFromRemote(
            accountId = ACCOUNT.value,
            remoteRows = listOf(notification),
        )
        val store = ProductionMutationStore(database)
        val command = MarkNotificationReadCommand(
            accountId = ACCOUNT,
            notificationId = NotificationId(notification.notificationId),
            clientOperationId = OPERATION,
            readAt = NOW,
        )

        assertTrue(store.enqueueMarkNotificationRead(command) is OptimisticMutationResult.Enqueued)
        assertEquals(
            NOW.toEpochMilliseconds(),
            database.notificationDao()
                .get(ACCOUNT.value, notification.notificationId)
                ?.readAtEpochMillis,
        )
        assertEquals(
            ProductionOperationKinds.MARK_NOTIFICATION_READ,
            database.outboxDao().get(OPERATION.value)?.kind,
        )

        database.notificationDao().replaceFromRemote(
            accountId = ACCOUNT.value,
            remoteRows = listOf(notification),
        )
        assertEquals(
            NOW.toEpochMilliseconds(),
            database.notificationDao()
                .get(ACCOUNT.value, notification.notificationId)
                ?.readAtEpochMillis,
        )
    }

    @Test
    fun purchaseReservesTokensAndInsertsPendingOwnershipWithTheOutboxRecord() = runBlocking {
        val store = ProductionMutationStore(database)
        seedShop(balance = 100)
        val command = purchaseCommand()

        val first = store.enqueuePurchase(command)
        val duplicate = store.enqueuePurchase(command)

        assertTrue(first is OptimisticMutationResult.Enqueued)
        assertTrue(duplicate is OptimisticMutationResult.AlreadyEnqueued)
        assertEquals(80, database.shopDao().observeAvailableBalance(ACCOUNT.value).first())
        assertEquals(100, database.shopDao().observeBalance(ACCOUNT.value).first())
        val owned = database.shopDao().observeOwnedItems(ACCOUNT.value).first().single()
        assertEquals(OPERATION.value, owned.pendingOperationId)
        assertEquals(
            LocalOutboxStates.PENDING,
            database.outboxDao().get(OPERATION.value)?.state,
        )
    }

    @Test
    fun purchaseIsRefusedWhenAlreadyOwnedOrUnaffordable() = runBlocking {
        val store = ProductionMutationStore(database)
        seedShop(balance = 30)

        val first = store.enqueuePurchase(purchaseCommand())
        val again = store.enqueuePurchase(
            purchaseCommand(operation = ClientOperationId("operation-two")),
        )
        val expensive = store.enqueuePurchase(
            purchaseCommand(
                itemId = "item-top-hat",
                price = 120,
                operation = ClientOperationId("operation-three"),
            ),
        )

        assertTrue(first is OptimisticMutationResult.Enqueued)
        assertTrue(again is OptimisticMutationResult.Conflict)
        assertTrue(expensive is OptimisticMutationResult.Conflict)
        assertEquals(1, database.outboxDao().pendingCount(ACCOUNT.value))
    }

    @Test
    fun remoteOwnedSnapshotCannotEraseAPendingPurchase() = runBlocking {
        val store = ProductionMutationStore(database)
        seedShop(balance = 100)
        store.enqueuePurchase(purchaseCommand())

        database.shopDao().replaceOwnedItemsFromRemote(ACCOUNT.value, emptyList())

        assertEquals(
            listOf("item-baseball-cap"),
            database.shopDao().observeOwnedItems(ACCOUNT.value).first().map { it.itemId },
        )
    }

    @Test
    fun rejectedPurchaseRollbackRestoresTheAvailableBalance() = runBlocking {
        val store = ProductionMutationStore(database)
        seedShop(balance = 100)
        store.enqueuePurchase(purchaseCommand())

        RoomRepositoryReconciler(database).reconcileRejectedPurchase(
            accountId = ACCOUNT,
            itemId = "item-baseball-cap",
            operationId = OPERATION.value,
        )

        assertTrue(database.shopDao().observeOwnedItems(ACCOUNT.value).first().isEmpty())
        assertEquals(100, database.shopDao().observeAvailableBalance(ACCOUNT.value).first())
    }

    @Test
    fun acknowledgedPurchaseMarksTheRowSyncedAndWritesTheReceiptBalance() = runBlocking {
        val store = ProductionMutationStore(database)
        seedShop(balance = 100)
        store.enqueuePurchase(purchaseCommand())

        RoomRepositoryReconciler(database).reconcileAcknowledgedPurchase(
            accountId = ACCOUNT,
            itemId = "item-baseball-cap",
            balance = 80,
            purchasedAt = NOW.plus((1).seconds),
        )

        val owned = database.shopDao().observeOwnedItems(ACCOUNT.value).first().single()
        assertEquals(null, owned.pendingOperationId)
        assertEquals(NOW.plus((1).seconds).toEpochMilliseconds(), owned.purchasedAtEpochMillis)
        assertEquals(80, database.shopDao().observeAvailableBalance(ACCOUNT.value).first())
        assertEquals(listOf(0), database.shopDao().observeConfirmedHatTypes(ACCOUNT.value).first())
    }

    private suspend fun seedShop(balance: Int) {
        database.shopDao().replaceCatalog(
            categories = listOf(
                ShopCategoryEntity(
                    categoryId = "category-hats",
                    slug = "hats",
                    title = "Hats",
                    subtitle = "Various headwear!",
                    iconKey = "shop_category_hats",
                    sortOrder = 0,
                ),
            ),
            items = listOf(
                ShopItemEntity(
                    itemId = "item-baseball-cap",
                    categoryId = "category-hats",
                    slug = "baseball_cap",
                    name = "Baseball Cap",
                    priceTokens = 20,
                    imageKey = "shop_item_baseball_cap",
                    sortOrder = 0,
                    miiHatType = 0,
                ),
                ShopItemEntity(
                    itemId = "item-top-hat",
                    categoryId = "category-hats",
                    slug = "top_hat",
                    name = "Top Hat",
                    priceTokens = 120,
                    imageKey = "shop_item_top_hat",
                    sortOrder = 2,
                    miiHatType = 2,
                ),
            ),
        )
        database.shopDao().upsertBalance(TokenBalanceEntity(ACCOUNT.value, balance))
    }

    private fun purchaseCommand(
        itemId: String = "item-baseball-cap",
        price: Int = 20,
        operation: ClientOperationId = OPERATION,
    ) = PurchaseShopItemCommand(
        accountId = ACCOUNT,
        itemId = itemId,
        priceTokens = price,
        clientOperationId = operation,
        requestedAt = NOW,
    )

    private fun profile(
        userId: UserId,
        displayName: String,
    ) = UserProfile(
        userId = userId,
        displayName = displayName,
        avatar = null,
        presence = PresenceStatus.Online,
        updatedAt = NOW,
    )

    private companion object {
        val ACCOUNT = UserId("account-one")
        val OPERATION = ClientOperationId("operation-one")
        val NOW: Instant = Instant.parse("2026-07-27T12:46:00Z")
    }
}
