package com.pocketpass.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.model.GameTarget
import com.pocketpass.app.model.GamesUiState
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.PuzzleUiState
import com.pocketpass.app.ui.BottomDisplayContent
import com.pocketpass.app.ui.controller.ControllerFocus
import com.pocketpass.app.ui.controller.FocusDisplay
import com.pocketpass.app.ui.controller.LocalControllerFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PuzzleSwapFocusUiTest {
    @get:Rule val compose = createComposeRule()

    private val focus = ControllerFocus()
    private var lastEvent: PocketPassEvent? = null
    private var state by mutableStateOf(
        PocketPassUiState(
            routes = listOf(PocketPassRoute.Root(PocketPassDestination.Activities)),
            sessionState = SessionState.Authenticated(FixtureData.CurrentUserId),
            accountSetup = AccountSetupUiState(resolved = true),
            profile = FixtureData.currentProfile,
            games = GamesUiState(visible = true, activeGame = GameTarget.PuzzleSwap),
            puzzle = PuzzleUiState(
                collection = FixtureData.puzzleCollection,
                viewedIndex = 1,
                tokenBalance = 500,
            ),
        ),
    )

    private fun dispatch(event: PocketPassEvent) {
        lastEvent = event
        when (event) {
            PocketPassEvent.OpenBuyPuzzlePiece ->
                state = state.copy(games = state.games.copy(puzzleBuyPromptVisible = true))
            PocketPassEvent.CloseBuyPuzzlePiece ->
                state = state.copy(games = state.games.copy(puzzleBuyPromptVisible = false))
            PocketPassEvent.ConfirmBuyPuzzlePiece -> state = state.copy(
                games = state.games.copy(puzzleBuyPromptVisible = false),
                puzzle = state.puzzle.copy(buying = true),
            )
            else -> Unit
        }
    }

    @Test fun purchaseReturnsSelectorToBuyEvenWhileButtonIsTemporarilyDisabled() {
        compose.setContent {
            CompositionLocalProvider(LocalControllerFocus provides focus) {
                BottomDisplayContent(state, ::dispatch)
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            focus.focus("puzzle_buy")
            assertTrue(focus.activate())
        }
        compose.onNodeWithTag("puzzle_buy_confirm").assertIsDisplayed()
        compose.runOnIdle {
            focus.focus("puzzle_buy_confirm")
            assertTrue(focus.activate())
        }
        compose.onNodeWithTag("puzzle_buy_confirm").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("puzzle_buy", focus.focusedTarget(FocusDisplay.Bottom)?.id)
            lastEvent = null
            assertTrue(focus.activate())
            assertEquals(null, lastEvent)
            state = state.copy(puzzle = state.puzzle.copy(buying = false, tokenBalance = 485))
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("puzzle_buy", focus.focusedTarget(FocusDisplay.Bottom)?.id)
            assertTrue(focus.activate())
            assertEquals(PocketPassEvent.OpenBuyPuzzlePiece, lastEvent)
        }
    }
}
