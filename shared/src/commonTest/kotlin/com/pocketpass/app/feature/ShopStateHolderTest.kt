package com.pocketpass.app.feature

import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.data.repository.FixtureShopRepository
import com.pocketpass.app.domain.model.OwnedShopItem
import com.pocketpass.app.domain.model.UserId
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShopStateHolderTest {
    @Test
    fun buyingAnAffordableHatDebitsTokensAndMarksItOwned() = runTest {
        val holder = holder(balance = 100)
        runCurrent()

        holder.buy("fixture-item-top_hat")
        runCurrent()
        assertEquals("Not enough tokens", holder.state.value.purchaseError)

        holder.buy("fixture-item-ribbons")
        runCurrent()

        val state = holder.state.first { "fixture-item-ribbons" in it.ownedItemIds }
        assertEquals(60, state.tokenBalance)
        assertNull(state.purchaseError)
        assertTrue("fixture-item-top_hat" !in state.ownedItemIds)
        assertTrue(state.purchasingItemIds.isEmpty())
    }

    @Test
    fun ownedAndPendingItemsAreNeverBoughtTwice() = runTest {
        val repository = FixtureShopRepository(
            balance = 500,
            owned = FixtureData.ownedShopItems + OwnedShopItem(
                itemId = "fixture-item-bow",
                purchasedAt = Instant.fromEpochSeconds(0),
                pricePaid = 40,
                pending = true,
            ),
        )
        val holder = holder(repository)
        runCurrent()

        holder.buy("fixture-item-baseball_cap")
        holder.buy("fixture-item-bow")
        runCurrent()

        val state = holder.state.value
        assertEquals(500, state.tokenBalance)
        assertEquals(setOf("fixture-item-bow"), state.purchasingItemIds)
        assertNull(state.purchaseError)
    }

    @Test
    fun closingTheShopClearsThePurchaseError() = runTest {
        val holder = holder(balance = 0)
        runCurrent()
        holder.open()
        runCurrent()

        holder.buy("fixture-item-top_hat")
        runCurrent()
        assertEquals("Not enough tokens", holder.state.value.purchaseError)

        assertTrue(holder.close())
        runCurrent()
        assertNull(holder.state.value.purchaseError)
    }

    @Test
    fun activeSupporterUnlocksEveryHatWithoutOwningIt() = runTest {
        val repository = FixtureShopRepository(
            balance = 500,
            supporterUntil = Instant.fromEpochSeconds(0).plus((3_600).seconds),
            now = { Instant.fromEpochSeconds(0) },
        )
        val holder = holder(repository)
        runCurrent()

        val state = holder.state.value
        val hatIds = FixtureData.shopCatalog.flatMap { it.items }
            .filter { it.miiHatType != null }
            .map { it.id }
            .toSet()
        assertEquals(setOf("fixture-item-baseball_cap"), state.ownedItemIds)
        assertEquals(hatIds - "fixture-item-baseball_cap", state.unlockedItemIds)
        assertEquals((0..10).toSet(), repository.observeOwnedHatTypes(FixtureData.CurrentUserId).first())
    }

    @Test
    fun supporterCanBuyAnIncludedHatAndKeepItAfterExpiry() = runTest {
        var currentTime = Instant.fromEpochSeconds(0)
        val repository = FixtureShopRepository(
            balance = 500,
            supporterUntil = currentTime.plus((3_600).seconds),
            now = { currentTime },
        )
        val holder = holder(repository)
        runCurrent()

        holder.buy("fixture-item-top_hat")
        runCurrent()

        assertEquals(380, holder.state.value.tokenBalance)
        assertEquals(setOf("fixture-item-baseball_cap", "fixture-item-top_hat"), holder.state.value.ownedItemIds)
        assertTrue("fixture-item-top_hat" !in holder.state.value.unlockedItemIds)
        assertNull(holder.state.value.purchaseError)
        val purchase = repository.observeOwnedItems(FixtureData.CurrentUserId).first()
            .single { it.itemId == "fixture-item-top_hat" }
        assertEquals(120, purchase.pricePaid)
        assertTrue(!purchase.pending)

        holder.buy("fixture-item-top_hat")
        runCurrent()
        assertEquals(380, holder.state.value.tokenBalance)
        assertNull(holder.state.value.purchaseError)

        currentTime = currentTime.plus((3_601).seconds)
        val reopened = holder(repository)
        runCurrent()
        assertEquals(setOf("fixture-item-baseball_cap", "fixture-item-top_hat"), reopened.state.value.ownedItemIds)
        assertEquals(setOf("fixture-item-hijab"), reopened.state.value.unlockedItemIds)
        assertEquals(setOf(0, 2, 10), repository.observeOwnedHatTypes(FixtureData.CurrentUserId).first())
    }

    @Test
    fun supporterWithoutEnoughTokensKeepsAccessWithoutGainingOwnership() = runTest {
        val repository = FixtureShopRepository(
            balance = 100,
            supporterUntil = Instant.fromEpochSeconds(3_600),
            now = { Instant.fromEpochSeconds(0) },
        )
        val holder = holder(repository)
        runCurrent()

        holder.buy("fixture-item-top_hat")
        runCurrent()

        assertEquals(100, holder.state.value.tokenBalance)
        assertEquals("Not enough tokens", holder.state.value.purchaseError)
        assertTrue("fixture-item-top_hat" in holder.state.value.unlockedItemIds)
        assertTrue("fixture-item-top_hat" !in holder.state.value.ownedItemIds)
    }

    @Test
    fun lapsedSupporterKeepsOnlyFreeHats() = runTest {
        val repository = FixtureShopRepository(
            balance = 0,
            supporterUntil = Instant.fromEpochSeconds(0).minus((1).seconds),
            now = { Instant.fromEpochSeconds(0) },
        )
        val holder = holder(repository)
        runCurrent()

        assertEquals(setOf("fixture-item-hijab"), holder.state.value.unlockedItemIds)
        assertEquals(setOf(0, 10), repository.observeOwnedHatTypes(FixtureData.CurrentUserId).first())
    }

    @Test
    fun freeHatIsWearableWithoutBuying() = runTest {
        val repository = FixtureShopRepository(balance = 0, owned = emptyList())
        val holder = holder(repository)
        runCurrent()

        val hijab = FixtureData.shopCatalog.flatMap { it.items }.single { it.slug == "hijab" }
        assertEquals(0, hijab.priceTokens)
        assertEquals(setOf(hijab.id), holder.state.value.unlockedItemIds)
        assertEquals(setOf(10), repository.observeOwnedHatTypes(FixtureData.CurrentUserId).first())

        holder.buy(hijab.id)
        runCurrent()
        assertTrue(holder.state.value.ownedItemIds.isEmpty())
        assertNull(holder.state.value.purchaseError)
    }

    @Test
    fun ownedHatTypesFollowConfirmedPurchasesOnly() = runTest {
        val repository = FixtureShopRepository(
            balance = 0,
            owned = listOf(
                OwnedShopItem("fixture-item-cat_ears", Instant.fromEpochSeconds(0), 100, pending = false),
                OwnedShopItem("fixture-item-bow", Instant.fromEpochSeconds(0), 40, pending = true),
            ),
        )

        assertEquals(setOf(5, 10), repository.observeOwnedHatTypes(FixtureData.CurrentUserId).first())
    }

    private fun kotlinx.coroutines.test.TestScope.holder(
        repository: FixtureShopRepository,
    ): ShopStateHolder = ShopStateHolder(
        accountId = MutableStateFlow<UserId?>(FixtureData.CurrentUserId),
        shopRepository = repository,
        scope = backgroundScope,
        now = { Instant.fromEpochSeconds(0) },
    ).also { holder ->
        backgroundScope.launch { holder.state.collect {} }
    }

    private fun kotlinx.coroutines.test.TestScope.holder(balance: Int): ShopStateHolder =
        holder(FixtureShopRepository(balance = balance))
}
