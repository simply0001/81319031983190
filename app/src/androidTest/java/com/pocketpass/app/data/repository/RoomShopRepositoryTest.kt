package com.pocketpass.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pocketpass.app.data.local.PocketPassDatabase
import com.pocketpass.app.data.local.entity.OwnedShopItemEntity
import com.pocketpass.app.data.local.entity.ShopCategoryEntity
import com.pocketpass.app.data.local.entity.ShopItemEntity
import com.pocketpass.app.data.local.entity.TokenBalanceEntity
import com.pocketpass.app.data.repository.remote.ShopRemoteDataSource
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.OwnedShopItem
import com.pocketpass.app.domain.model.PurchaseShopItemCommand
import com.pocketpass.app.domain.model.ShopCategory
import com.pocketpass.app.domain.model.ShopPurchaseOutcome
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
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
class RoomShopRepositoryTest {
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
    fun ownedSnapshotReplacesConfirmedRowsButKeepsPendingPurchases() = runBlocking {
        seedShop(balance = 100)
        val repository = repository(
            remoteOwned = listOf(
                OwnedShopItem(
                    itemId = "item-top-hat",
                    purchasedAt = NOW,
                    pricePaid = 120,
                    pending = false,
                ),
            ),
        )
        database.shopDao().upsertOwnedItems(
            listOf(
                OwnedShopItemEntity(
                    accountId = ACCOUNT.value,
                    itemId = "item-bow",
                    pricePaid = 40,
                    purchasedAtEpochMillis = NOW.toEpochMilliseconds(),
                    pendingOperationId = null,
                ),
            ),
        )
        val queued = repository.purchase(purchaseCommand())

        val refreshed = repository.refreshOwnedItems(ACCOUNT)

        assertTrue(queued is RepositoryResult.Success)
        assertTrue(refreshed is RepositoryResult.Success)
        assertEquals(
            setOf("item-baseball-cap", "item-top-hat"),
            repository.observeOwnedItems(ACCOUNT).first().map { it.itemId }.toSet(),
        )
        assertEquals(setOf(2), repository.observeOwnedHatTypes(ACCOUNT).first())
        assertEquals(80, repository.observeTokenBalance(ACCOUNT).first())
    }

    @Test
    fun purchaseBeyondTheAvailableBalanceIsAConflict() = runBlocking {
        seedShop(balance = 30)
        val repository = repository(remoteOwned = emptyList())

        val first = repository.purchase(purchaseCommand())
        val second = repository.purchase(
            purchaseCommand(
                itemId = "item-top-hat",
                price = 120,
                operation = ClientOperationId("operation-two"),
            ),
        )

        assertTrue(first is RepositoryResult.Success)
        assertEquals(
            RepositoryFailureKind.Conflict,
            (second as RepositoryResult.Failure).error.kind,
        )
        assertEquals(10, repository.observeTokenBalance(ACCOUNT).first())
    }

    @Test
    fun activeSupporterUnlocksEveryCatalogueHatUntilItLapses() = runBlocking {
        seedShop(balance = 0)
        val repository = repository(
            remoteOwned = emptyList(),
            supporterUntil = NOW.plus((3_600).seconds),
        )

        val refreshed = repository.refreshSupporterStatus(ACCOUNT)

        assertTrue(refreshed is RepositoryResult.Success)
        assertEquals(NOW.plus((3_600).seconds), repository.observeSupporterUntil(ACCOUNT).first())
        assertEquals(setOf(0, 2, 4), repository.observeOwnedHatTypes(ACCOUNT).first())

        val lapsed = repository(
            remoteOwned = emptyList(),
            supporterUntil = NOW.minus((1).seconds),
        )
        assertTrue(lapsed.refreshSupporterStatus(ACCOUNT) is RepositoryResult.Success)
        assertEquals(emptySet<Int>(), lapsed.observeOwnedHatTypes(ACCOUNT).first())

        val none = repository(remoteOwned = emptyList(), supporterUntil = null)
        assertTrue(none.refreshSupporterStatus(ACCOUNT) is RepositoryResult.Success)
        assertEquals(null, none.observeSupporterUntil(ACCOUNT).first())
    }

    private fun repository(
        remoteOwned: List<OwnedShopItem>,
        supporterUntil: Instant? = null,
    ) = RoomShopRepository(
        shopDao = database.shopDao(),
        remote = StubShopRemote(remoteOwned, supporterUntil),
        mutationStore = ProductionMutationStore(database),
        now = { NOW },
    )

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
                hat("item-baseball-cap", "baseball_cap", "Baseball Cap", 20, 0),
                hat("item-top-hat", "top_hat", "Top Hat", 120, 2),
                hat("item-bow", "bow", "Bow", 40, 4),
            ),
        )
        database.shopDao().upsertBalance(TokenBalanceEntity(ACCOUNT.value, balance))
    }

    private fun hat(id: String, slug: String, name: String, price: Int, hatType: Int) =
        ShopItemEntity(
            itemId = id,
            categoryId = "category-hats",
            slug = slug,
            name = name,
            priceTokens = price,
            imageKey = "shop_item_$slug",
            sortOrder = hatType,
            miiHatType = hatType,
        )

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

    private class StubShopRemote(
        private val owned: List<OwnedShopItem>,
        private val supporterUntil: Instant? = null,
    ) : ShopRemoteDataSource {
        override suspend fun fetchCatalog(): RepositoryResult<List<ShopCategory>> =
            RepositoryResult.Success(emptyList())

        override suspend fun fetchTokenBalance(accountId: UserId): RepositoryResult<Int> =
            RepositoryResult.Success(0)

        override suspend fun fetchOwnedItems(
            accountId: UserId,
        ): RepositoryResult<List<OwnedShopItem>> = RepositoryResult.Success(owned)

        override suspend fun fetchSupporterStatus(
            accountId: UserId,
        ): RepositoryResult<Instant?> = RepositoryResult.Success(supporterUntil)

        override suspend fun purchaseItem(
            command: PurchaseShopItemCommand,
        ): RepositoryResult<ShopPurchaseOutcome> = RepositoryResult.Failure(
            RepositoryFailure(kind = RepositoryFailureKind.Unavailable, retryable = false),
        )
    }

    private companion object {
        val ACCOUNT = UserId("account-one")
        val OPERATION = ClientOperationId("operation-one")
        val NOW: Instant = Instant.parse("2026-08-23T12:00:00Z")
    }
}
