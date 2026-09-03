package com.pocketpass.app.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShoulderTabsTest {
    private val settings = PocketPassUiState(
        routes = listOf(PocketPassRoute.Root(PocketPassDestination.Activities)),
    )

    @Test
    fun nothingOpenDoesNotBlock() {
        assertFalse(settings.blocksShoulderTabs())
    }

    @Test
    fun theActivitiesOverlaysLetTheShoulderButtonsSwitchTabs() {
        assertFalse(settings.copy(shop = ShopUiState(visible = true)).blocksShoulderTabs())
        assertFalse(settings.copy(games = GamesUiState(visible = true, activeGame = GameTarget.Bingo)).blocksShoulderTabs())
        assertFalse(settings.copy(leaderboard = LeaderboardUiState(visible = true)).blocksShoulderTabs())
    }

    @Test
    fun aDialogInsideAnOverlayStillBlocks() {
        assertTrue(settings.copy(shop = ShopUiState(visible = true, buyPromptItemId = "hat")).blocksShoulderTabs())
        assertTrue(settings.copy(games = GamesUiState(visible = true, activeGame = GameTarget.Bingo, bingoGoalIndex = 3)).blocksShoulderTabs())
        assertTrue(settings.copy(games = GamesUiState(visible = true, activeGame = GameTarget.WorldTour, worldTourRegionsVisible = true)).blocksShoulderTabs())
        assertTrue(settings.copy(leaderboard = LeaderboardUiState(visible = true, settingsVisible = true)).blocksShoulderTabs())
    }

    @Test
    fun anyOtherLayerStillBlocks() {
        assertTrue(settings.copy(shop = ShopUiState(visible = true), sortMenuOpen = true).blocksShoulderTabs())
        assertTrue(settings.copy(themePickerExpanded = true).blocksShoulderTabs())
        assertTrue(
            settings.copy(routes = settings.routes + PocketPassRoute.Contributors).blocksShoulderTabs(),
        )
    }
}
