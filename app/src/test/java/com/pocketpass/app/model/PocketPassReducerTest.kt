package com.pocketpass.app.model

import com.pocketpass.app.domain.model.ShopCategory
import com.pocketpass.app.domain.model.ShopItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketPassReducerTest {
    @Test
    fun selectingTabReplacesTheEntireRouteStack() {
        val detailState = PocketPassUiState(
            routes = listOf(
                PocketPassRoute.Root(PocketPassDestination.Messages),
                PocketPassRoute.MessageDetail("spob"),
            ),
        )

        val result = PocketPassReducer.reduce(
            detailState,
            PocketPassEvent.SelectDestination(PocketPassDestination.Settings),
        )

        assertEquals(
            listOf(PocketPassRoute.Root(PocketPassDestination.Settings)),
            result.routes,
        )
    }

    @Test
    fun messageDetailPushAndBackPopAreTypeSafe() {
        val messages = PocketPassReducer.reduce(
            PocketPassUiState(),
            PocketPassEvent.SelectDestination(PocketPassDestination.Messages),
        )
        val detail = PocketPassReducer.reduce(messages, PocketPassEvent.OpenMessage("sans"))
        val back = PocketPassReducer.reduce(detail, PocketPassEvent.Back)

        assertEquals(PocketPassRoute.MessageDetail("sans"), detail.routes.last())
        assertEquals(messages.routes, back.routes)
    }

    @Test
    fun messageCannotOpenOutsideMessagesRoot() {
        val home = PocketPassUiState()
        assertEquals(
            home,
            PocketPassReducer.reduce(home, PocketPassEvent.OpenMessage("spob")),
        )
    }

    @Test
    fun newGroupPushesOnlyFromTheMessagesRootAndPopsWithBack() {
        val home = PocketPassUiState()
        assertEquals(home, PocketPassReducer.reduce(home, PocketPassEvent.OpenNewGroup))

        val messages = PocketPassReducer.reduce(
            home,
            PocketPassEvent.SelectDestination(PocketPassDestination.Messages),
        )
        val composer = PocketPassReducer.reduce(messages, PocketPassEvent.OpenNewGroup)
        assertEquals(PocketPassRoute.NewGroup, composer.routes.last())
        assertEquals(composer, PocketPassReducer.reduce(composer, PocketPassEvent.OpenNewGroup))
        assertEquals(messages.routes, PocketPassReducer.reduce(composer, PocketPassEvent.Back).routes)

        val detail = PocketPassReducer.reduce(messages, PocketPassEvent.OpenMessage("crew"))
        assertEquals(detail, PocketPassReducer.reduce(detail, PocketPassEvent.OpenNewGroup))
    }

    @Test
    fun closeNewGroupPopsOnlyWhenTheComposerIsOnTop() {
        val messages = PocketPassReducer.reduce(
            PocketPassUiState(),
            PocketPassEvent.SelectDestination(PocketPassDestination.Messages),
        )
        val composer = PocketPassReducer.reduce(messages, PocketPassEvent.OpenNewGroup)
        assertEquals(messages.routes, PocketPassReducer.reduce(composer, PocketPassEvent.CloseNewGroup).routes)

        val detail = PocketPassReducer.reduce(messages, PocketPassEvent.OpenMessage("crew"))
        assertEquals(detail, PocketPassReducer.reduce(detail, PocketPassEvent.CloseNewGroup))
    }

    @Test
    fun groupEventsLeaveReducerStateUntouched() {
        val detail = PocketPassReducer.reduce(
            PocketPassReducer.reduce(
                PocketPassUiState(),
                PocketPassEvent.SelectDestination(PocketPassDestination.Messages),
            ),
            PocketPassEvent.OpenMessage("crew"),
        )
        listOf(
            PocketPassEvent.ToggleGroupMember("matt-1"),
            PocketPassEvent.UpdateGroupTitle("Trip"),
            PocketPassEvent.CreateGroup,
            PocketPassEvent.OpenGroupInfo,
            PocketPassEvent.CloseGroupInfo,
            PocketPassEvent.AddGroupMembers(listOf("matt-2")),
            PocketPassEvent.RemoveGroupMember("matt-2"),
            PocketPassEvent.LeaveGroup,
            PocketPassEvent.RenameGroup("Trip 2"),
            PocketPassEvent.DismissConversationNotice,
        ).forEach { event ->
            assertEquals(detail, PocketPassReducer.reduce(detail, event))
        }
    }

    @Test
    fun appUpdateRoutePushesOnceAndPopsWithBack() {
        val settings = PocketPassReducer.reduce(
            PocketPassUiState(),
            PocketPassEvent.SelectDestination(PocketPassDestination.Settings),
        )
        val opened = PocketPassReducer.reduce(settings, PocketPassEvent.OpenAppUpdate)
        val openedTwice = PocketPassReducer.reduce(opened, PocketPassEvent.OpenAppUpdate)
        val back = PocketPassReducer.reduce(opened, PocketPassEvent.Back)

        assertEquals(PocketPassRoute.AppUpdate, opened.routes.last())
        assertEquals(opened.routes, openedTwice.routes)
        assertEquals(settings.routes, back.routes)
    }

    @Test
    fun appUpdateWorkEventsLeaveStateUntouched() {
        val state = PocketPassUiState()

        assertEquals(
            state,
            PocketPassReducer.reduce(state, PocketPassEvent.CheckForAppUpdate),
        )
        assertEquals(
            state,
            PocketPassReducer.reduce(state, PocketPassEvent.DownloadAppUpdate),
        )
        assertEquals(
            state,
            PocketPassReducer.reduce(state, PocketPassEvent.InstallAppUpdate),
        )
    }

    @Test
    fun messageActionEventsLeaveReducerStateUntouched() {
        val messages = PocketPassReducer.reduce(
            PocketPassUiState(),
            PocketPassEvent.SelectDestination(PocketPassDestination.Messages),
        )
        val detail = PocketPassReducer.reduce(messages, PocketPassEvent.OpenMessage("sans"))

        listOf(
            PocketPassEvent.OpenMessageActions("message-1"),
            PocketPassEvent.CloseMessageActions,
            PocketPassEvent.EditSelectedMessage,
            PocketPassEvent.DeleteSelectedMessage,
            PocketPassEvent.CancelMessageEdit,
        ).forEach { event ->
            assertEquals(detail, PocketPassReducer.reduce(detail, event))
        }
    }

    @Test
    fun nameEditorEventsLeaveReducerStateUntouched() {
        val settings = PocketPassReducer.reduce(
            PocketPassUiState(),
            PocketPassEvent.SelectDestination(PocketPassDestination.Settings),
        )

        listOf(
            PocketPassEvent.OpenNameEditor,
            PocketPassEvent.UpdateNameDraft("newname"),
            PocketPassEvent.SaveName,
            PocketPassEvent.CloseNameEditor,
        ).forEach { event ->
            assertEquals(settings, PocketPassReducer.reduce(settings, event))
        }
    }

    @Test
    fun messageBadgeTextCanChangeAtRuntime() {
        val changed = PocketPassReducer.reduce(
            PocketPassUiState(),
            PocketPassEvent.SetMessageBadgeText("99+"),
        )

        assertEquals("99+", changed.messageBadgeText)
    }

    @Test
    fun shuffleAlternatesBetweenBothFigmaFixtures() {
        val shuffled = PocketPassReducer.reduce(
            PocketPassUiState(),
            PocketPassEvent.ShuffleActivities,
        )
        val original = PocketPassReducer.reduce(
            shuffled,
            PocketPassEvent.ShuffleActivities,
        )

        assertEquals(ActivityVariant.Shuffled, shuffled.activityVariant)
        assertEquals(ActivityVariant.Default, original.activityVariant)
    }

    @Test
    fun settingsClampAndResetToFigmaDefaults() {
        var state = PocketPassUiState()
        state = PocketPassReducer.reduce(state, PocketPassEvent.SetNearby(false))
        state = PocketPassReducer.reduce(state, PocketPassEvent.SetSoundLevel(3f))
        state = PocketPassReducer.reduce(state, PocketPassEvent.SetSfxLevel(-1f))
        state = PocketPassReducer.reduce(state, PocketPassEvent.SetThemeMode(ThemeMode.Dark))

        assertFalse(state.nearbyEnabled)
        assertEquals(1f, state.soundLevel)
        assertEquals(0f, state.sfxLevel)
        assertEquals(ThemeMode.Dark, state.themeMode)

        state = PocketPassReducer.reduce(state, PocketPassEvent.ResetSettings)
        assertTrue(state.nearbyEnabled)
        assertEquals(0.45f, state.soundLevel)
        assertEquals(0.6f, state.sfxLevel)
        assertEquals(ThemeMode.System, state.themeMode)
    }

    @Test
    fun themePickerOpensStaysOpenAcrossSelectionAndClosesOnBack() {
        val settings = PocketPassReducer.reduce(
            PocketPassUiState(),
            PocketPassEvent.SelectDestination(PocketPassDestination.Settings),
        )
        val opened = PocketPassReducer.reduce(settings, PocketPassEvent.OpenThemePicker)
        val picked = PocketPassReducer.reduce(opened, PocketPassEvent.SetThemeMode(ThemeMode.Dark))
        val closed = PocketPassReducer.reduce(picked, PocketPassEvent.Back)

        assertTrue(opened.themePickerExpanded)
        assertTrue(picked.themePickerExpanded)
        assertEquals(ThemeMode.Dark, picked.themeMode)
        assertFalse(closed.themePickerExpanded)
        assertEquals(settings.routes, closed.routes)
    }

    @Test
    fun sortMenuTogglesAndClosesOnBackBeforeRoutesPop() {
        val messages = PocketPassReducer.reduce(
            PocketPassUiState(),
            PocketPassEvent.SelectDestination(PocketPassDestination.Messages),
        )
        val detail = PocketPassReducer.reduce(messages, PocketPassEvent.OpenMessage("sans"))
        val opened = PocketPassReducer.reduce(detail, PocketPassEvent.ToggleSortMenu)
        val closedByBack = PocketPassReducer.reduce(opened, PocketPassEvent.Back)
        val toggledShut = PocketPassReducer.reduce(opened, PocketPassEvent.ToggleSortMenu)
        val switched = PocketPassReducer.reduce(
            opened,
            PocketPassEvent.SelectDestination(PocketPassDestination.Home),
        )

        assertTrue(opened.sortMenuOpen)
        assertFalse(closedByBack.sortMenuOpen)
        assertEquals(detail.routes, closedByBack.routes)
        assertFalse(toggledShut.sortMenuOpen)
        assertFalse(switched.sortMenuOpen)
    }

    @Test
    fun switchingTabsCollapsesTheThemePicker() {
        val opened = PocketPassReducer.reduce(PocketPassUiState(), PocketPassEvent.OpenThemePicker)
        val switched = PocketPassReducer.reduce(
            opened,
            PocketPassEvent.SelectDestination(PocketPassDestination.Home),
        )

        assertFalse(switched.themePickerExpanded)
    }

    @Test
    fun removeFriendPromptClosesOnBackConfirmCancelAndProfileChanges() {
        val opened = PocketPassReducer.reduce(PocketPassUiState(), PocketPassEvent.OpenRemoveFriend)
        val closedByBack = PocketPassReducer.reduce(opened, PocketPassEvent.Back)
        val confirmed = PocketPassReducer.reduce(opened, PocketPassEvent.RemoveProfileFriend)
        val cancelled = PocketPassReducer.reduce(opened, PocketPassEvent.CloseRemoveFriend)
        val profileClosed = PocketPassReducer.reduce(opened, PocketPassEvent.CloseUserProfile)
        val switched = PocketPassReducer.reduce(
            opened,
            PocketPassEvent.SelectDestination(PocketPassDestination.Home),
        )

        assertTrue(opened.removeFriendPromptVisible)
        assertFalse(closedByBack.removeFriendPromptVisible)
        assertEquals(opened.routes, closedByBack.routes)
        assertFalse(confirmed.removeFriendPromptVisible)
        assertFalse(cancelled.removeFriendPromptVisible)
        assertFalse(profileClosed.removeFriendPromptVisible)
        assertFalse(switched.removeFriendPromptVisible)
    }

    @Test
    fun buyPromptOpensOnlyForAffordableItemsAndClosesOnEveryExit() {
        val hat = ShopItem(
            id = "item-top-hat",
            slug = "top_hat",
            name = "Top Hat",
            priceTokens = 120,
            imageKey = "shop_item_top_hat",
            miiHatType = 2,
        )
        val cap = hat.copy(id = "item-cap", slug = "baseball_cap", priceTokens = 20, miiHatType = 0)
        val shop = ShopUiState(
            visible = true,
            categories = listOf(
                ShopCategory("hats", "hats", "Hats", "Various headwear!", "shop_category_hats", listOf(hat, cap)),
            ),
            tokenBalance = 50,
            ownedItemIds = setOf("item-cap"),
        )
        val base = PocketPassUiState(shop = shop)

        assertEquals(ShopItemStatus.Unaffordable, shop.statusOf(hat))
        assertEquals(ShopItemStatus.Owned, shop.statusOf(cap))
        assertEquals(
            ShopItemStatus.Available,
            shop.copy(tokenBalance = 120).statusOf(hat),
        )
        assertEquals(
            ShopItemStatus.Purchasing,
            shop.copy(purchasingItemIds = setOf("item-top-hat")).statusOf(hat),
        )

        val tooPoor = PocketPassReducer.reduce(base, PocketPassEvent.OpenBuyShopItem("item-top-hat"))
        val owned = PocketPassReducer.reduce(base, PocketPassEvent.OpenBuyShopItem("item-cap"))
        val funded = base.copy(shop = shop.copy(tokenBalance = 120))
        val opened = PocketPassReducer.reduce(funded, PocketPassEvent.OpenBuyShopItem("item-top-hat"))

        assertEquals(null, tooPoor.shop.buyPromptItemId)
        assertEquals(null, owned.shop.buyPromptItemId)
        assertEquals("item-top-hat", opened.shop.buyPromptItemId)
        assertEquals(hat, opened.shop.buyPromptItem)
        assertEquals(null, PocketPassReducer.reduce(opened, PocketPassEvent.Back).shop.buyPromptItemId)
        assertEquals(opened.routes, PocketPassReducer.reduce(opened, PocketPassEvent.Back).routes)
        assertEquals(null, PocketPassReducer.reduce(opened, PocketPassEvent.CloseBuyShopItem).shop.buyPromptItemId)
        assertEquals(null, PocketPassReducer.reduce(opened, PocketPassEvent.ConfirmBuyShopItem).shop.buyPromptItemId)
        assertEquals(null, PocketPassReducer.reduce(opened, PocketPassEvent.WearShopItem("item-cap")).shop.buyPromptItemId)
        assertEquals(null, PocketPassReducer.reduce(opened, PocketPassEvent.CloseShop).shop.buyPromptItemId)
        assertEquals(
            null,
            PocketPassReducer.reduce(
                opened,
                PocketPassEvent.SelectDestination(PocketPassDestination.Home),
            ).shop.buyPromptItemId,
        )
    }
}
