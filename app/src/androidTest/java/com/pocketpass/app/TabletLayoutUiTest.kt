package com.pocketpass.app

import androidx.activity.ComponentActivity
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.model.*
import com.pocketpass.app.ui.PocketPassTheme
import com.pocketpass.app.ui.phone.PhoneRoot
import com.pocketpass.app.ui.phone.PhoneSurface
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TabletLayoutUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var state by mutableStateOf(fixture())
    private var lastEvent: PocketPassEvent? = null
    @Volatile private var imeBottom = 0

    private fun fixture() = PocketPassUiState(
        sessionState = SessionState.Authenticated(FixtureData.CurrentUserId),
        accountSetup = AccountSetupUiState(resolved = true),
        profile = FixtureData.currentProfile,
        friends = FixtureData.friends,
        friendsLoading = false,
        recentInteractions = FixtureData.encounters,
        conversations = FixtureData.conversations,
        notifications = FixtureData.notifications,
        myFriendCode = FixtureData.CurrentFriendCode,
        messagePushSupported = true,
        messageTotalCount = 102,
        shop = ShopUiState(categories = FixtureData.shopCatalog, tokenBalance = 500),
        leaderboard = LeaderboardUiState(entries = FixtureData.leaderboard),
        achievements = AchievementsUiState(achievements = FixtureData.achievements),
        themeMode = ThemeMode.Light,
        boardsVisible = false,
    )

    private fun show() {
        compose.activityRule.scenario.onActivity {
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        compose.setContent {
            val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
            SideEffect { imeBottom = keyboardHeight }
            PocketPassTheme(state.themeMode) {
                PhoneSurface { metrics ->
                    PhoneRoot(metrics, state, { lastEvent = it; state = PocketPassReducer.reduce(state, it) }, null)
                }
            }
        }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(2500)
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        settle()
        android.os.SystemClock.sleep(300)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = context.resources.configuration
        val directory = java.io.File(context.getExternalFilesDir(null), "tablet-layout-${config.screenWidthDp}x${config.screenHeightDp}").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(directory, "$name.png")).use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun swipeContentUp() {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val usableHeight = root.height - imeBottom
        compose.onRoot().performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(root.width * 0.78f, usableHeight * 0.9f),
                androidx.compose.ui.geometry.Offset(root.width * 0.78f, usableHeight * 0.3f), 500)
        }
        settle()
    }

    private fun withinScreen(tag: String) {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
        node.assertIsDisplayed()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("$tag extends outside the viewport: $bounds / $root", bounds.left >= -1f && bounds.top >= -1f && bounds.right <= root.width + 1f && bounds.bottom <= root.height + 1f)
    }

    @Test fun allTabsAndTheirActivityPagesRenderInBothThemes() {
        show()
        for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            compose.runOnIdle { state = fixture().copy(themeMode = theme) }
            for (tab in PocketPassDestination.entries) {
                compose.onNodeWithTag("tab_${tab.name.lowercase()}").performClick()
                settle()
                PocketPassDestination.entries.forEach { withinScreen("tab_${it.name.lowercase()}") }
                capture("${theme.name}-${tab.name}")
            }
            compose.onNodeWithTag("tab_activities").performClick()
            for ((event, back, name) in listOf(
                Triple(PocketPassEvent.OpenShop, "shop_back", "shop"),
                Triple(PocketPassEvent.OpenGames, "games_back", "games"),
                Triple(PocketPassEvent.OpenLeaderboard, "leaderboard_back", "leaderboard"),
                Triple(PocketPassEvent.OpenAchievements, "achievements_back", "achievements"),
            )) {
                compose.runOnIdle {
                    val base = fixture().copy(themeMode = theme, routes = listOf(PocketPassRoute.Root(PocketPassDestination.Activities)))
                    state = when (event) {
                        PocketPassEvent.OpenShop -> base.copy(shop = base.shop.copy(visible = true))
                        PocketPassEvent.OpenGames -> base.copy(games = base.games.copy(visible = true))
                        PocketPassEvent.OpenLeaderboard -> base.copy(leaderboard = base.leaderboard.copy(visible = true))
                        else -> base.copy(achievements = base.achievements.copy(visible = true))
                    }
                }
                settle()
                withinScreen(back)
                capture("${theme.name}-$name")
                compose.onNodeWithTag(back).performClick()
                settle()
            }
        }
    }

    @Test fun settingsStayAlignedAndTheirLastControlsCanBeReached() {
        show()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithTag("settings_app").performScrollTo().performClick()
        settle()
        capture("app-settings")
        repeat(3) { swipeContentUp() }
        compose.onNodeWithTag("settings_chat_colours").assertIsDisplayed().performClick()
        settle()
        capture("chat-colours-open")
        assertEquals(PocketPassRoute.ChatColours, state.routes.last())
        compose.onNodeWithTag("chat_colour_teal").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("chat_colour_save").performScrollTo()
        withinScreen("chat_colour_save")
        if (compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            val preview = compose.onNodeWithTag("chat_colour_preview", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertEquals(root.width / 2f, preview.center.x, 3f)
        }
        capture("chat-colours")
        compose.onNodeWithTag("chat_colours_back").performClick()
        settle()
        for (route in listOf(PocketPassRoute.Accessibility, PocketPassRoute.NotificationSettings,
            PocketPassRoute.Social, PocketPassRoute.AccountSecurity, PocketPassRoute.Contributors,
            PocketPassRoute.AppUpdate, PocketPassRoute.WidgetMaker)) {
            compose.runOnIdle { state = fixture().copy(routes = listOf(PocketPassRoute.Root(PocketPassDestination.Settings), route)) }
            capture("settings-${route::class.simpleName}")
        }
    }

    @Test fun directAndGroupChatsComposerAndDialogsFit() {
        show()
        compose.mainClock.autoAdvance = false
        for (conversation in FixtureData.conversations) {
            compose.runOnIdle {
                state = fixture().copy(
                    routes = listOf(PocketPassRoute.Root(PocketPassDestination.Messages), PocketPassRoute.MessageDetail(conversation.id.value)),
                    selectedConversationId = conversation.id,
                    selectedConversation = conversation,
                    selectedMessages = FixtureData.messages[conversation.id].orEmpty(),
                    selectedMembersById = FixtureData.crewMembers.associateBy { it.userId },
                )
            }
            capture("chat-${conversation.id.value}")
            withinScreen("message_composer")
            withinScreen("message_send")
        }
        compose.runOnIdle { state = fixture().copy(friendsOverlay = FriendsOverlay.Notifications) }
        capture("notifications")
        compose.runOnIdle { state = fixture().copy(routes = listOf(PocketPassRoute.Root(PocketPassDestination.Friends)), friendsOverlay = FriendsOverlay.AddFriend) }
        capture("add-friend")
        compose.runOnIdle { state = fixture().copy(routes = listOf(PocketPassRoute.Root(PocketPassDestination.Activities)), shop = fixture().shop.copy(visible = true, buyPromptItemId = FixtureData.shopCatalog.first().items.first().id)) }
        capture("purchase-confirmation")
        compose.runOnIdle { state = fixture().copy(routes = listOf(PocketPassRoute.Root(PocketPassDestination.Messages))) }
        compose.runOnIdle { state = state.copy(boardsVisible = true, boards = state.boards.copy(screen = com.pocketpass.app.boards.BoardsScreen.Chats)) }
        compose.mainClock.autoAdvance = false
        settle()
        compose.onNodeWithTag("messages_new_group").performClick()
        capture("new-group")
        withinScreen("group_create")
        val members = compose.onNodeWithTag("group_members_viewport", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val create = compose.onNodeWithTag("group_create", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Friend cards overlap Create Group", members.bottom <= create.top)
        compose.runOnIdle { state = fixture().copy(profileViewer = ProfileViewerUiState(selectedUserId = FixtureData.SpobUserId.value, source = ProfileViewerSource.RecentInteraction, profile = FixtureData.spobProfile)) }
        capture("profile")
        compose.runOnIdle { state = fixture().copy(recentInteractions = emptyList(), friends = emptyList(), conversations = emptyList(), notifications = emptyList()) }
        capture("empty-home")
    }

    @Test fun newGroupListStopsAboveCreate() {
        state = fixture().copy(
            routes = listOf(PocketPassRoute.Root(PocketPassDestination.Messages), PocketPassRoute.NewGroup),
            groupComposer = GroupComposerState(),
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PocketPassTheme(state.themeMode) {
                PhoneSurface { metrics ->
                    com.pocketpass.app.ui.phone.PhoneNewGroupPage(metrics, state) {}
                }
            }
        }
        settle()
        withinScreen("group_create")
        val members = compose.onNodeWithTag("group_members_viewport").fetchSemanticsNode().boundsInRoot
        val create = compose.onNodeWithTag("group_create").fetchSemanticsNode().boundsInRoot
        assertTrue("Friend cards overlap Create Group", members.bottom <= create.top)
        capture("new-group-final")
    }

    @Test fun keyboardLeavesTheComposerAndSendButtonVisible() {
        state = fixture().copy(
            routes = listOf(PocketPassRoute.Root(PocketPassDestination.Messages), PocketPassRoute.MessageDetail(FixtureData.SpobConversationId.value)),
            selectedConversationId = FixtureData.SpobConversationId,
            selectedConversation = FixtureData.conversations.first { it.id == FixtureData.SpobConversationId },
            selectedMessages = FixtureData.messages.getValue(FixtureData.SpobConversationId),
        )
        show()
        compose.onNodeWithTag("message_composer").performClick()
        compose.waitUntil(8000) { imeBottom > 0 }
        settle()
        withinScreen("message_composer")
        withinScreen("message_send")
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val send = compose.onNodeWithTag("message_send").fetchSemanticsNode().boundsInRoot
        assertTrue("Keyboard obscures Send", send.bottom <= root.height - imeBottom + 2f)
        capture("chat-keyboard")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
    }

    @Test fun boardFormStaysReachableAboveKeyboard() {
        state = fixture().copy(routes = listOf(PocketPassRoute.Root(PocketPassDestination.Messages)), boardsVisible = true,
            boards = com.pocketpass.app.boards.BoardsUiState(enabled = true, screen = com.pocketpass.app.boards.BoardsScreen.Propose))
        show()
        compose.onAllNodes(hasSetTextAction()).onFirst().performClick().performTextInput("Sketch club")
        compose.waitUntil(8000) { imeBottom > 0 }
        settle()
        capture("boards-keyboard-before-scroll")
        repeat(3) { swipeContentUp() }
        capture("boards-keyboard-after-scroll")
        compose.onNodeWithTag("proposal_submit").assertIsDisplayed()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val submit = compose.onNodeWithTag("proposal_submit").fetchSemanticsNode().boundsInRoot
        assertTrue("Keyboard obscures the board form", submit.bottom <= root.height - imeBottom + 2f)
        capture("boards-keyboard")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
    }

    @Test fun allGameBoardsFitTheAvailableViewport() {
        show()
        compose.mainClock.autoAdvance = false
        for (theme in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            for (game in GameTarget.entries) {
                compose.runOnIdle {
                    state = fixture().copy(
                        themeMode = theme,
                        routes = listOf(PocketPassRoute.Root(PocketPassDestination.Activities)),
                        games = GamesUiState(visible = true, activeGame = game),
                        worldTour = WorldTourUiState(regions = FixtureData.worldTourRegions),
                        bingo = BingoUiState(cells = FixtureData.bingoBoard),
                        puzzle = PuzzleUiState(collection = FixtureData.puzzleCollection, viewedIndex = 1, tokenBalance = 500),
                    )
                }
                settle()
                capture("game-${game.name}-${theme.name}")
                listOf("game_back", "game_hero", "game_board").forEach(::withinScreen)
                val scene = compose.onNodeWithTag("single_screen_game", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val background = compose.onNodeWithTag("game_scene_background", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                assertEquals(scene, background)
                when (game) {
                    GameTarget.WorldTour -> {
                        withinScreen("world_tour_regions_button")
                        compose.onNodeWithTag("world_tour_regions_button").performClick()
                        assertEquals(PocketPassEvent.OpenWorldTourRegions, lastEvent)
                        compose.runOnIdle { state = state.copy(games = state.games.copy(worldTourRegionsVisible = true)) }
                        capture("game-regions-${theme.name}")
                        withinScreen("world_tour_region_JP")
                        withinScreen("game_dialog_back")
                        compose.onNodeWithTag("game_dialog_back").performClick()
                        assertEquals(PocketPassEvent.CloseWorldTourRegions, lastEvent)
                    }
                    GameTarget.Bingo -> {
                        withinScreen("bingo_cell_0")
                        withinScreen("bingo_cell_24")
                        compose.onNodeWithTag("bingo_cell_0").performClick()
                        assertEquals(PocketPassEvent.SelectBingoSquare(0), lastEvent)
                        compose.runOnIdle { state = state.copy(games = state.games.copy(bingoGoalIndex = 0)) }
                        capture("game-bingo-goal-${theme.name}")
                        withinScreen("bingo_goal_scrim")
                        compose.onNodeWithTag("game_dialog_back").performClick()
                        assertEquals(PocketPassEvent.CloseBingoSquare, lastEvent)
                    }
                    GameTarget.PuzzleSwap -> {
                        listOf("puzzle_board", "puzzle_prev", "puzzle_next", "puzzle_buy", "puzzle_info").forEach(::withinScreen)
                        compose.onNodeWithTag("puzzle_info").performClick()
                        assertEquals(PocketPassEvent.OpenPuzzleInfo, lastEvent)
                        compose.runOnIdle { state = state.copy(games = state.games.copy(puzzleInfoVisible = true)) }
                        capture("game-puzzle-info-${theme.name}")
                        withinScreen("puzzle_info_close")
                        compose.onNodeWithTag("puzzle_info_close").performClick()
                        assertEquals(PocketPassEvent.ClosePuzzleInfo, lastEvent)
                        compose.runOnIdle { state = state.copy(games = state.games.copy(puzzleInfoVisible = false, puzzleBuyPromptVisible = true)) }
                        capture("game-puzzle-buy-${theme.name}")
                        withinScreen("puzzle_buy_cancel")
                        withinScreen("puzzle_buy_confirm")
                        compose.onNodeWithTag("puzzle_buy_cancel").performClick()
                        assertEquals(PocketPassEvent.CloseBuyPuzzlePiece, lastEvent)
                    }
                }
            }
        }
    }
}
