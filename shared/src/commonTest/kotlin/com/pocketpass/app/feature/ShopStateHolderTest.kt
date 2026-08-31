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
        assertEquals(Instant.fromEpochSeconds(0).plus((3_600).seconds), state.supporterUntil)
        assertEquals((0..9).toSet(), repository.observeOwnedHatTypes(FixtureData.CurrentUserId).first())

        holder.buy("fixture-item-top_hat")
        runCurrent()
        assertEquals(500, holder.state.value.tokenBalance)
        assertEquals(setOf("fixture-item-baseball_cap"), holder.state.value.ownedItemIds)
    }

    @Test
    fun lapsedSupporterUnlocksNothing() = runTest {
        val repository = FixtureShopRepository(
            balance = 0,
            supporterUntil = Instant.fromEpochSeconds(0).minus((1).seconds),
            now = { Instant.fromEpochSeconds(0) },
        )
        val holder = holder(repository)
        runCurrent()

        assertTrue(holder.state.value.unlockedItemIds.isEmpty())
        assertEquals(setOf(0), repository.observeOwnedHatTypes(FixtureData.CurrentUserId).first())
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

        assertEquals(setOf(5), repository.observeOwnedHatTypes(FixtureData.CurrentUserId).first())
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
