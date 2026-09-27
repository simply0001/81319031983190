package com.pocketpass.app.model

import com.pocketpass.app.domain.model.ShopCategory
import com.pocketpass.app.domain.model.ShopItem
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.pocketpass.app.widget.WidgetSize
import com.pocketpass.app.widget.WidgetDesign
import com.pocketpass.app.widget.WidgetBlock
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class PocketPassReducerTest {
    @Test
    fun puzzleEventsLeaveTheReducedStateAlone() {
        val state = PocketPassUiState(
            routes = listOf(PocketPassRoute.Root(PocketPassDestination.Activities)),
            games = GamesUiState(visible = true, activeGame = GameTarget.PuzzleSwap),
        )
        listOf(
            PocketPassEvent.PreviousPuzzle,
            PocketPassEvent.NextPuzzle,
            PocketPassEvent.OpenBuyPuzzlePiece,
            PocketPassEvent.CloseBuyPuzzlePiece,
            PocketPassEvent.ConfirmBuyPuzzlePiece,
            PocketPassEvent.DismissPuzzleNotice,
            PocketPassEvent.OpenPuzzleInfo,
            PocketPassEvent.ClosePuzzleInfo,
        ).forEach { event ->
            assertEquals(state, PocketPassReducer.reduce(state, event))
        }
    }

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
    fun contributorsRoutePushesOnceAndPopsWithBack() {
        val settings = PocketPassReducer.reduce(
            PocketPassUiState(),
            PocketPassEvent.SelectDestination(PocketPassDestination.Settings),
        )
        val opened = PocketPassReducer.reduce(settings, PocketPassEvent.OpenContributors)
        val openedTwice = PocketPassReducer.reduce(opened, PocketPassEvent.OpenContributors)
        val back = PocketPassReducer.reduce(opened, PocketPassEvent.Back)

        assertEquals(PocketPassRoute.Contributors, opened.routes.last())
        assertEquals(opened.routes, openedTwice.routes)
        assertEquals(settings.routes, back.routes)
    }

    @Test
    fun appSettingsRoutePushesOnceAndPopsWithBack() {
        val settings = PocketPassReducer.reduce(
            PocketPassUiState(),
            PocketPassEvent.SelectDestination(PocketPassDestination.Settings),
        )
        val opened = PocketPassReducer.reduce(settings, PocketPassEvent.OpenAppSettings)
        val openedTwice = PocketPassReducer.reduce(opened, PocketPassEvent.OpenAppSettings)
        val back = PocketPassReducer.reduce(opened, PocketPassEvent.Back)

        assertEquals(PocketPassRoute.AppSettings, opened.routes.last())
        assertEquals(opened.routes, openedTwice.routes)
        assertEquals(settings.routes, back.routes)
    }

    @Test
    fun notificationSettingsOpenedFromAppSettingsReturnsThere() {
        val settings = PocketPassReducer.reduce(
            PocketPassUiState(),
            PocketPassEvent.SelectDestination(PocketPassDestination.Settings),
        )
        val appSettings = PocketPassReducer.reduce(settings, PocketPassEvent.OpenAppSettings)
        val notifications = PocketPassReducer.reduce(appSettings, PocketPassEvent.OpenNotificationSettings)
        val back = PocketPassReducer.reduce(notifications, PocketPassEvent.Back)

        assertEquals(PocketPassRoute.NotificationSettings, notifications.routes.last())
        assertEquals(appSettings.routes, back.routes)
    }

    @Test
    fun widgetMakerRoutePushesOnceAndPopsWithBack() {
        val settings = PocketPassReducer.reduce(
            PocketPassUiState(),
            PocketPassEvent.SelectDestination(PocketPassDestination.Settings),
        )
        val opened = PocketPassReducer.reduce(settings, PocketPassEvent.OpenWidgetMaker)
        val openedTwice = PocketPassReducer.reduce(opened, PocketPassEvent.OpenWidgetMaker)
        val back = PocketPassReducer.reduce(opened, PocketPassEvent.Back)

        assertEquals(PocketPassRoute.WidgetMaker, opened.routes.last())
        assertEquals(opened.routes, openedTwice.routes)
        assertEquals(settings.routes, back.routes)
    }

    @Test
    fun widgetEditorOverlaysCloseBeforeTheRoutePops() {
        val editing = widgetEditorState()
        val picker = PocketPassReducer.reduce(editing, PocketPassEvent.OpenWidgetBlockPicker(WidgetSlot.Tile(1)))
        val prompt = PocketPassReducer.reduce(editing, PocketPassEvent.OpenWidgetDeletePrompt)
        val rename = PocketPassReducer.reduce(editing, PocketPassEvent.OpenWidgetRename)

        assertEquals(WidgetSlot.Tile(1), picker.widgetMaker.blockPicker)
        assertTrue(picker.hasDismissableLayer())
        assertEquals(editing, PocketPassReducer.reduce(picker, PocketPassEvent.Back))
        assertEquals(editing, PocketPassReducer.reduce(prompt, PocketPassEvent.Back))
        assertEquals("Morning", rename.widgetMaker.renameDraft)
        assertEquals(editing, PocketPassReducer.reduce(rename, PocketPassEvent.Back))
        assertEquals(editing.routes.dropLast(1), PocketPassReducer.reduce(editing, PocketPassEvent.Back).routes)
    }

    @Test
    fun pickingABlockAndRenamingEditTheOpenDesign() {
        val editing = widgetEditorState()
        val picker = PocketPassReducer.reduce(editing, PocketPassEvent.OpenWidgetBlockPicker(WidgetSlot.Hero))
        val picked = PocketPassReducer.reduce(picker, PocketPassEvent.PickWidgetBlock(WidgetBlock.StepsToday))
        val renamed = PocketPassReducer.reduce(
            PocketPassReducer.reduce(
                PocketPassReducer.reduce(editing, PocketPassEvent.OpenWidgetRename),
                PocketPassEvent.UpdateWidgetNameDraft("  Evening "),
            ),
            PocketPassEvent.SaveWidgetName,
        )

        assertEquals(WidgetBlock.StepsToday, picked.editingWidgetDesign?.hero)
        assertNull(picked.widgetMaker.blockPicker)
        assertEquals("Evening", renamed.editingWidgetDesign?.name)
        assertNull(renamed.widgetMaker.renameDraft)
    }

    @Test
    fun deletingTheOpenDesignLeavesTheEditor() {
        val editing = widgetEditorState()

        val deleted = PocketPassReducer.reduce(editing, PocketPassEvent.DeleteWidgetDesign("d1"))

        assertEquals(PocketPassRoute.WidgetMaker, deleted.routes.last())
        assertTrue(deleted.widgetDesigns.isEmpty())
    }

    @Test
    fun aLauncherWidgetAskingForADesignOpensTheWidgetsPageUntilOneIsPickedOrAbandoned() {
        val home = PocketPassUiState(widgetDesigns = listOf(widgetDesign()))

        val assigning = PocketPassReducer.reduce(home, PocketPassEvent.BeginWidgetAssign(42))
        val assigned = PocketPassReducer.reduce(assigning, PocketPassEvent.AssignWidgetDesign("d1"))
        val abandoned = PocketPassReducer.reduce(assigning, PocketPassEvent.Back)

        assertEquals(
            listOf(PocketPassRoute.Root(PocketPassDestination.Settings), PocketPassRoute.WidgetMaker),
            assigning.routes,
        )
        assertEquals(42, assigning.widgetMaker.assigningAppWidgetId)
        assertNull(assigned.widgetMaker.assigningAppWidgetId)
        assertEquals("Your widget now shows Morning", assigned.widgetMaker.message)
        assertNull(abandoned.widgetMaker.assigningAppWidgetId)
        assertEquals(listOf(PocketPassRoute.Root(PocketPassDestination.Settings)), abandoned.routes)
    }

    private fun widgetDesign() = WidgetDesign(
        id = "d1",
        name = "Morning",
        size = WidgetSize.Wide,
        hero = WidgetBlock.Tokens,
        tiles = listOf(WidgetBlock.StepsToday, null, null),
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private fun widgetEditorState(): PocketPassUiState = PocketPassUiState(
        routes = listOf(
            PocketPassRoute.Root(PocketPassDestination.Settings),
            PocketPassRoute.WidgetMaker,
            PocketPassRoute.WidgetEditor("d1"),
        ),
        widgetDesigns = listOf(widgetDesign()),
    )

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
    fun settingsClampToTheirRanges() {
        var state = PocketPassUiState()
        state = PocketPassReducer.reduce(state, PocketPassEvent.SetNearby(false))
        state = PocketPassReducer.reduce(state, PocketPassEvent.SetSoundLevel(3f))
        state = PocketPassReducer.reduce(state, PocketPassEvent.SetSfxLevel(-1f))
        state = PocketPassReducer.reduce(state, PocketPassEvent.SetThemeMode(ThemeMode.Dark))
        state = PocketPassReducer.reduce(state, PocketPassEvent.SetStepRewardsEnabled(true))

        assertFalse(state.nearbyEnabled)
        assertTrue(state.stepRewardsEnabled)
        assertEquals(1f, state.soundLevel)
        assertEquals(0f, state.sfxLevel)
        assertEquals(ThemeMode.Dark, state.themeMode)
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
        val categoryOpen = PocketPassReducer.reduce(base, PocketPassEvent.OpenShopCategory("hats"))

        assertEquals("hats", categoryOpen.shop.selectedCategoryId)
        assertEquals(shop.categories.first(), categoryOpen.shop.selectedCategory)
        assertEquals(null, PocketPassReducer.reduce(categoryOpen, PocketPassEvent.Back).shop.selectedCategoryId)
        assertEquals(null, PocketPassReducer.reduce(categoryOpen, PocketPassEvent.CloseShopCategory).shop.selectedCategoryId)
        assertEquals(null, PocketPassReducer.reduce(categoryOpen, PocketPassEvent.CloseShop).shop.selectedCategoryId)
        assertEquals(null, PocketPassReducer.reduce(base, PocketPassEvent.OpenShopCategory("missing")).shop.selectedCategoryId)

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
        val categoryWithPrompt = PocketPassReducer.reduce(
            PocketPassReducer.reduce(funded, PocketPassEvent.OpenShopCategory("hats")),
            PocketPassEvent.OpenBuyShopItem("item-top-hat"),
        )

        assertEquals(null, tooPoor.shop.buyPromptItemId)
        assertEquals(null, owned.shop.buyPromptItemId)
        assertEquals("item-top-hat", opened.shop.buyPromptItemId)
        assertEquals("hats", PocketPassReducer.reduce(categoryWithPrompt, PocketPassEvent.Back).shop.selectedCategoryId)
        assertEquals(null, PocketPassReducer.reduce(categoryWithPrompt, PocketPassEvent.Back).shop.buyPromptItemId)
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

    @Test
    fun subscriptionAccessAllowsBuyingButOwnedPendingFreeAndUnaffordableItemsDoNot() {
        val hat = ShopItem("hat", "top_hat", "Top Hat", 120, "shop_item_top_hat", 2)
        val freeHat = hat.copy(id = "free", priceTokens = 0)
        val shop = ShopUiState(
            visible = true,
            categories = listOf(
                ShopCategory("hats", "hats", "Hats", "", "shop_category_hats", listOf(hat, freeHat)),
            ),
            tokenBalance = 120,
            unlockedItemIds = setOf(hat.id, freeHat.id),
        )
        val open = PocketPassEvent.OpenBuyShopItem(hat.id)

        assertEquals(ShopItemStatus.Unlocked, shop.statusOf(hat))
        assertTrue(shop.canBuy(hat))
        assertEquals(hat.id, PocketPassReducer.reduce(PocketPassUiState(shop = shop), open).shop.buyPromptItemId)
        assertEquals(
            "It costs 120 tokens. You have 120. Yours to keep after your subscription ends.",
            shop.purchasePromptBody(hat),
        )

        val pending = shop.copy(purchasingItemIds = setOf(hat.id))
        assertEquals(ShopItemStatus.Purchasing, pending.statusOf(hat))
        for (blocked in listOf(pending, shop.copy(ownedItemIds = setOf(hat.id)), shop.copy(tokenBalance = 119))) {
            assertFalse(blocked.canBuy(hat))
            assertEquals(null, PocketPassReducer.reduce(PocketPassUiState(shop = blocked), open).shop.buyPromptItemId)
        }
        assertFalse(shop.canBuy(freeHat))
        assertEquals(
            null,
            PocketPassReducer.reduce(PocketPassUiState(shop = shop), PocketPassEvent.OpenBuyShopItem(freeHat.id))
                .shop.buyPromptItemId,
        )
    }
}
