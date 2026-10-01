package com.pocketpass.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.pocketpass.app.ui.phone.PhoneRoot
import com.pocketpass.app.ui.phone.PhoneSurface
import com.pocketpass.app.ui.PocketPassTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.pocketpass.app.boards.Board
import com.pocketpass.app.boards.BoardAlertLevel
import com.pocketpass.app.boards.BoardsUiState
import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassReducer
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.BottomDisplayContent
import com.pocketpass.app.ui.controller.ControllerFocus
import com.pocketpass.app.ui.controller.FocusDirection
import com.pocketpass.app.ui.controller.LocalControllerFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MessageNotificationUiTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(PocketPassUiState())
    private val events = mutableListOf<PocketPassEvent>()
    private val focus = ControllerFocus()

    private fun show(
        supported: Boolean,
        boardsEnabled: Boolean = false,
        boardsVisible: Boolean = true,
        boards: BoardsUiState = BoardsUiState(enabled = boardsEnabled),
        route: PocketPassRoute = PocketPassRoute.NotificationSettings,
        phone: Boolean = false,
    ) {
        state = PocketPassUiState(
            routes = listOf(PocketPassRoute.Root(PocketPassDestination.Settings), route),
            sessionState = SessionState.Authenticated(FixtureData.CurrentUserId),
            accountSetup = AccountSetupUiState(resolved = true),
            messagePushSupported = supported,
            boardPushSupported = supported,
            boardsVisible = boardsVisible,
            boards = boards,
        )
        compose.setContent {
            CompositionLocalProvider(LocalControllerFocus provides focus) {
                val dispatch: (PocketPassEvent) -> Unit = { events += it; state = PocketPassReducer.reduce(state, it) }
                if (phone) PocketPassTheme(state.themeMode) {
                    Box(Modifier.fillMaxSize()) { PhoneSurface { PhoneRoot(it, state, dispatch, null) } }
                } else BottomDisplayContent(state = state, dispatch = dispatch)
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(400)
        val directory = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "board-alerts").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(directory, "$name.png")).use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun boardAlertScreens(layout: String, phone: Boolean) {
        val art = Board("99290000-0000-4000-8000-000000000021", "owner", "Pixel Art", role = "member")
        val runs = Board("99290000-0000-4000-8000-000000000022", "owner", "Speedrunning", role = "member", pushEnabled = false)
        show(true, boardsEnabled = true, phone = phone)
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithTag("board_alerts_level", useUnmergedTree = true).performScrollTo()
        for (level in BoardAlertLevel.entries) {
            compose.runOnIdle { state = state.copy(boards = state.boards.copy(alertLevel = level)) }
            capture("$layout-${level.wire}")
        }
        compose.runOnIdle { state = state.copy(boards = state.boards.copy(pushEnabled = false)) }
        capture("$layout-off")
        compose.runOnIdle {
            state = state.copy(routes = listOf(PocketPassRoute.Root(PocketPassDestination.Settings), PocketPassRoute.BoardNotificationSettings),
                boards = state.boards.copy(pushEnabled = true, notificationBoards = listOf(art, runs)))
        }
        compose.mainClock.advanceTimeBy(2000)
        capture("$layout-board-list")
    }

    @Test fun boardAlertScreensOnTheThor() { boardAlertScreens("thor", phone = false) }

    @Test fun boardAlertScreensOnPhones() { boardAlertScreens("phone", phone = true) }

    @Test fun messageToggleWorksAndFourthRowIsReachable() {
        show(true)
        compose.onNodeWithTag("message_alerts_toggle").assertIsDisplayed().performClick()
        compose.runOnIdle { assertFalse(state.messageAlertsEnabled) }
        compose.onNodeWithTag("update_alerts_toggle").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertFalse(state.updateAlertsEnabled) }
    }

    @Test fun boardAlertsLiveInNotificationsAndRepairAlertsAreGone() {
        show(true, boardsEnabled = true)
        compose.onNodeWithTag("repair_alerts_toggle").assertDoesNotExist()
        compose.onNodeWithTag("board_alerts_toggle").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(PocketPassEvent.SetBoardAlertsEnabled(false), events.last()) }
    }

    @Test fun theSliderPicksHowMuchBoardsAlertsYou() {
        show(true, boardsEnabled = true)
        compose.onNodeWithTag("board_alerts_level").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            focus.focus("board_alerts_panel")
            assertTrue(focus.move(FocusDirection.Down))
            assertEquals("board_alerts_level", focus.focusId)
            assertTrue(focus.adjust(-1))
        }
        compose.runOnIdle { assertEquals(PocketPassEvent.SetBoardAlertLevel(BoardAlertLevel.Personal), events.last()) }
        compose.onNodeWithTag("board_alerts_level_mentions").performClick()
        compose.runOnIdle { assertEquals(PocketPassEvent.SetBoardAlertLevel(BoardAlertLevel.Mentions), events.last()) }
        compose.runOnIdle {
            focus.focus("board_alerts_panel")
            assertTrue(focus.activate())
        }
        compose.runOnIdle { assertEquals(PocketPassEvent.SetBoardAlertsEnabled(false), events.last()) }
    }

    @Test fun theSliderRestsWhileBoardAlertsAreOff() {
        show(true, boards = BoardsUiState(enabled = true, pushEnabled = false))
        compose.onNodeWithTag("board_alerts_level").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            focus.focus("board_alerts_level")
            assertFalse(focus.adjust(-1))
            focus.focus("board_alerts_panel")
            assertTrue(focus.move(FocusDirection.Down))
            assertEquals("board_notifications_row", focus.focusId)
        }
    }

    @Test fun boardNotificationsOpenTheListOfBoards() {
        show(true, boardsEnabled = true)
        compose.onNodeWithTag("board_notifications_row").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(PocketPassRoute.BoardNotificationSettings, state.routes.last()) }
    }

    @Test fun eachBoardHasItsOwnAlertSwitch() {
        val art = Board("99290000-0000-4000-8000-000000000021", "owner", "Pixel Art", role = "member")
        val runs = Board("99290000-0000-4000-8000-000000000022", "owner", "Speedrunning", role = "member", pushEnabled = false)
        show(true, boards = BoardsUiState(enabled = true, notificationBoards = listOf(art, runs)),
            route = PocketPassRoute.BoardNotificationSettings)
        compose.onNodeWithTag("board_alerts_board_${runs.id}").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(PocketPassEvent.SetBoardPushEnabled(runs.id, true), events.last()) }
        compose.onNodeWithTag("board_alerts_board_${art.id}").performClick()
        compose.runOnIdle { assertEquals(PocketPassEvent.SetBoardPushEnabled(art.id, false), events.last()) }
    }

    @Test fun theBoardListSaysWhenThereIsNothingToShow() {
        show(true, boards = BoardsUiState(enabled = true, notificationBoards = emptyList()),
            route = PocketPassRoute.BoardNotificationSettings)
        compose.onNodeWithTag("board_notifications_message").assertIsDisplayed()
    }

    @Test fun boardAlertsStayHiddenWhileBoardsIsOff() {
        show(true)
        compose.onNodeWithTag("board_alerts_toggle").assertDoesNotExist()
        compose.onNodeWithTag("board_notifications_row").assertDoesNotExist()
    }

    @Test fun hidingBoardsHidesItsAlerts() {
        show(true, boardsEnabled = true, boardsVisible = false)
        compose.onNodeWithTag("board_alerts_toggle").assertDoesNotExist()
        compose.onNodeWithTag("board_notifications_row").assertDoesNotExist()
        compose.onNodeWithTag("encounter_alerts_toggle").assertIsDisplayed()
    }

    @Test fun unconfiguredBuildDoesNotOfferAnUnavailablePushToggle() {
        show(false, boardsEnabled = true)
        compose.onNodeWithTag("message_alerts_toggle").assertDoesNotExist()
        compose.onNodeWithTag("board_alerts_toggle").assertDoesNotExist()
        compose.onNodeWithTag("encounter_alerts_toggle").assertIsDisplayed()
    }
}
