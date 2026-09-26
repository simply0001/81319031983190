package com.pocketpass.app

import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.mii.MiiAppearance
import com.pocketpass.app.mii.MiiEditorUiState
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassReducer
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.ShopUiState
import com.pocketpass.app.ui.BottomDisplayContent
import com.pocketpass.app.ui.controller.ControllerFocus
import com.pocketpass.app.ui.controller.FocusDirection
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.input.handleBackGamepadKeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShopSubscriptionUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val hat = FixtureData.shopCatalog.flatMap { it.items }.single { it.slug == "top_hat" }
    private var state by mutableStateOf(PocketPassUiState())
    private var lastEvent: PocketPassEvent? = null
    private val focus = ControllerFocus()

    @Test
    fun categoryOpensFullWidthItemsAndBackReturnsToCategories() {
        showShop(balance = 500, twoItems = true, detail = false)
        capture("shop-categories-review")
        compose.onNodeWithTag("shop_category_hats").assertIsDisplayed().performClick()
        compose.onNodeWithTag("shop_item_top_hat").assertIsDisplayed()
        compose.onNodeWithTag("shop_item_second_hat").assertIsDisplayed()
        capture("shop-hats-review")
        compose.runOnIdle {
            assertEquals(FixtureData.shopCatalog.first().id, state.shop.selectedCategoryId)
            focus.focus("shop_category_back")
            pressBack()
        }
        compose.onNodeWithTag("shop_category_hats").assertIsDisplayed()
        compose.onNodeWithTag("shop_item_top_hat").assertDoesNotExist()
        compose.runOnIdle { assertNull(state.shop.selectedCategoryId) }
    }

    @Test
    fun controllerCanReachTheLastHatInTheFullCategory() {
        showShop(balance = 500)
        val category = FixtureData.shopCatalog.first()
        compose.runOnIdle {
            state = state.copy(shop = state.shop.copy(categories = listOf(category)))
        }
        val last = category.items.last()
        compose.runOnIdle { focus.focus("shop_item_${last.slug}") }
        compose.onNodeWithTag("shop_item_${last.slug}").assertIsDisplayed()
        capture("shop-hats-last-review")
    }

    @Test
    fun controllerBrowsesWholeCardsThenEntersBuyAndWear() {
        showShop(balance = 500, twoItems = true)
        compose.runOnIdle {
            focus.focus("shop_category_back")
            focus.move(FocusDirection.Down)
            assertEquals("shop_item_top_hat", focus.focusId)
            focus.move(FocusDirection.Down)
            assertEquals("shop_item_second_hat", focus.focusId)
            focus.move(FocusDirection.Up)
            focus.activate()
            assertEquals("shop_item_top_hat_buy", focus.focusId)
            assertNull(lastEvent)
            focus.move(FocusDirection.Right)
            assertEquals("shop_item_top_hat_wear", focus.focusId)
            focus.move(FocusDirection.Right)
            assertEquals("shop_item_top_hat_wear", focus.focusId)
            pressBack()
            assertEquals("shop_item_top_hat", focus.focusId)
            assertNull(lastEvent)
            assertTrue(state.shop.visible)
        }
    }

    @Test
    fun controllerPurchaseNeedsActionActivationAndCancelReturnsToBuy() {
        showShop(balance = 500)
        compose.runOnIdle {
            focus.focus("shop_item_top_hat")
            focus.activate()
            assertNull(state.shop.buyPromptItemId)
            focus.activate()
        }
        compose.onNodeWithTag("shop_buy_panel").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("shop_buy_cancel", focus.focusId)
            pressBack()
        }
        compose.onNodeWithTag("shop_buy_panel").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("shop_item_top_hat_buy", focus.focusId)
            assertTrue(state.shop.visible)
            pressBack()
            assertEquals("shop_item_top_hat", focus.focusId)
            assertTrue(state.shop.visible)
            lastEvent = null
            pressBack()
            assertEquals(PocketPassEvent.Back, lastEvent)
        }
    }

    @Test
    fun ownedHatSelectsWearOnlyAfterEnteringTheCard() {
        showShop(balance = 500, owned = true)
        compose.runOnIdle {
            focus.focus("shop_item_top_hat")
            focus.activate()
            assertEquals("shop_item_top_hat_wear", focus.focusId)
            assertNull(lastEvent)
            focus.activate()
            assertEquals(PocketPassEvent.WearShopItem(hat.id), lastEvent)
        }
    }

    @Test
    fun activeHatShowsWearingAndCannotBeSelectedAgain() {
        showShop(balance = 500, owned = true)
        compose.runOnIdle {
            state = state.copy(
                miiEditor = state.miiEditor.copy(
                    saved = MiiAppearance(extHatType = hat.miiHatType!!),
                    draft = MiiAppearance(extHatType = hat.miiHatType!!),
                ),
            )
        }

        compose.onNodeWithText("Wearing", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("shop_item_top_hat_wear").assertIsNotEnabled()
        compose.runOnIdle {
            focus.focus("shop_item_top_hat")
            focus.activate()
            assertEquals("shop_item_top_hat", focus.focusId)
            assertNull(lastEvent)
        }
    }

    @Test
    fun hatBeingAppliedShowsSavingThenWearing() {
        showShop(balance = 500, owned = true)
        compose.runOnIdle {
            state = state.copy(
                miiEditor = state.miiEditor.copy(
                    draft = MiiAppearance(extHatType = hat.miiHatType!!),
                    wearHatInProgress = true,
                ),
            )
        }
        compose.onNodeWithText("Saving", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("shop_item_top_hat_wear").assertIsNotEnabled()

        compose.runOnIdle {
            state = state.copy(miiEditor = state.miiEditor.copy(wearHatInProgress = false))
        }
        compose.onNodeWithText("Wearing", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("shop_item_top_hat_wear").assertIsNotEnabled()
    }

    @Test
    fun availableHatSelectsBuyOnlyAfterEnteringTheCard() {
        showShop(balance = 500, unlocked = false)
        compose.onNodeWithTag("shop_item_top_hat_buy").assertIsDisplayed()
        compose.runOnIdle {
            focus.focus("shop_item_top_hat")
            focus.activate()
            assertEquals("shop_item_top_hat_buy", focus.focusId)
            assertNull(lastEvent)
            focus.activate()
        }
        compose.onNodeWithTag("shop_buy_panel").assertIsDisplayed()
    }

    @Test
    fun subscriptionWithoutEnoughTokensEntersWearInsteadOfDisabledBuy() {
        showShop(balance = 0)
        compose.runOnIdle {
            focus.focus("shop_item_top_hat")
            focus.activate()
            assertEquals("shop_item_top_hat_wear", focus.focusId)
            focus.move(FocusDirection.Left)
            assertEquals("shop_item_top_hat_wear", focus.focusId)
            assertNull(lastEvent)
        }
    }

    @Test
    fun unaffordableHatStaysSelectedWithoutActivatingAnUnavailableAction() {
        showShop(balance = 0, unlocked = false)
        compose.runOnIdle {
            focus.focus("shop_item_top_hat")
            focus.activate()
            assertEquals("shop_item_top_hat", focus.focusId)
            assertNull(lastEvent)
            assertTrue(!focus.canExitToParent())
        }
    }

    @Test
    fun completedPurchaseReturnsFocusToTheCardWhenBuyIsRemoved() {
        showShop(balance = 500)
        compose.runOnIdle {
            focus.focus("shop_item_top_hat")
            focus.activate()
            state = state.copy(shop = state.shop.copy(ownedItemIds = setOf(hat.id)))
        }
        compose.onNodeWithTag("shop_item_top_hat_buy").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("shop_item_top_hat", focus.focusId)
            focus.activate()
            assertEquals("shop_item_top_hat_wear", focus.focusId)
        }
    }

    @Test
    fun subscriptionItemOffersBuyingAndWearingSeparately() {
        showShop(balance = 500)

        compose.onNodeWithText("Included with Ko-fi").assertIsDisplayed()
        compose.onNodeWithTag("shop_item_top_hat_buy").assertIsDisplayed().performClick()
        compose.onNodeWithTag("shop_buy_panel").assertIsDisplayed()
        compose.onNodeWithText(state.shop.purchasePromptBody(hat)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(hat.id, state.shop.buyPromptItemId) }

        compose.onNodeWithTag("shop_buy_cancel").performClick()
        compose.onNodeWithTag("shop_item_top_hat_wear").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(PocketPassEvent.WearShopItem(hat.id), lastEvent) }
    }

    @Test
    fun subscriptionItemCanStillBeWornWithoutEnoughTokensToBuy() {
        showShop(balance = 0)

        compose.onNodeWithTag("shop_item_top_hat_buy").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("shop_item_top_hat_wear").performClick()
        compose.runOnIdle { assertEquals(PocketPassEvent.WearShopItem(hat.id), lastEvent) }
    }

    @Test
    fun confirmedPurchaseReplacesSubscriptionControlsWithOwnedStatus() {
        showShop(balance = 500)
        compose.onNodeWithTag("shop_item_top_hat_buy").performClick()
        compose.onNodeWithTag("shop_buy_confirm").performClick()
        compose.runOnIdle {
            assertEquals(PocketPassEvent.ConfirmBuyShopItem, lastEvent)
            state = state.copy(shop = state.shop.copy(purchasingItemIds = setOf(hat.id)))
        }
        compose.onNodeWithText("Buying…").assertIsDisplayed()
        compose.onNodeWithTag("shop_item_top_hat_buy").assertDoesNotExist()

        compose.runOnIdle {
            state = state.copy(
                shop = state.shop.copy(
                    ownedItemIds = setOf(hat.id),
                    unlockedItemIds = emptySet(),
                    purchasingItemIds = emptySet(),
                    tokenBalance = 380,
                ),
            )
        }
        compose.onNodeWithText("Owned", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("shop_item_top_hat_buy").assertDoesNotExist()
        compose.onNodeWithTag("shop_item_top_hat_wear").performClick()
        compose.runOnIdle { assertEquals(PocketPassEvent.WearShopItem(hat.id), lastEvent) }
    }

    private fun pressBack() {
        handleBackGamepadKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B), state, ::dispatch, focus)
        handleBackGamepadKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B), state, ::dispatch, focus)
    }

    private fun dispatch(event: PocketPassEvent) {
        lastEvent = event
        state = PocketPassReducer.reduce(state, event)
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.getExternalFilesDir(null), "$name.png")
        java.io.FileOutputStream(file).use {
            compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun showShop(balance: Int, unlocked: Boolean = true, owned: Boolean = false, twoItems: Boolean = false, detail: Boolean = true) {
        val items = if (twoItems) listOf(hat, hat.copy(id = "second-hat", slug = "second_hat", name = "Second Hat")) else listOf(hat)
        state = PocketPassUiState(
            routes = listOf(PocketPassRoute.Root(PocketPassDestination.Activities)),
            sessionState = SessionState.Authenticated(FixtureData.CurrentUserId),
            accountSetup = AccountSetupUiState(resolved = true),
            profile = FixtureData.currentProfile,
            miiEditorEnabled = true,
            miiEditor = MiiEditorUiState(
                activeAccountKey = FixtureData.CurrentUserId.value,
                isInitialized = true,
            ),
            shop = ShopUiState(
                visible = true,
                categories = listOf(FixtureData.shopCatalog.first().copy(items = items)),
                selectedCategoryId = if (detail) FixtureData.shopCatalog.first().id else null,
                tokenBalance = balance,
                unlockedItemIds = if (unlocked) items.map { it.id }.toSet() else emptySet(),
                ownedItemIds = if (owned) setOf(hat.id) else emptySet(),
            ),
        )
        compose.setContent {
            CompositionLocalProvider(LocalControllerFocus provides focus) {
                BottomDisplayContent(state = state, dispatch = ::dispatch)
            }
        }
    }
}
