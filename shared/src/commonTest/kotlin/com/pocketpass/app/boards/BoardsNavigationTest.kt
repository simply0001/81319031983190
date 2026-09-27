package com.pocketpass.app.boards

import com.pocketpass.app.model.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent

class BoardsNavigationTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun messagesReturnToTheSharedBoardDirectoryWithoutAChooser() = runTest {
        val holder = BoardsStateHolder(null, MutableStateFlow(null), backgroundScope, MutableStateFlow(true))
        runCurrent()
        assertEquals(BoardsScreen.Directory, holder.state.value.screen)
        assertFalse(holder.back())
        holder.dispatch(BoardAction.OpenChats)
        assertEquals(BoardsScreen.Chats, holder.state.value.screen)
        assertTrue(holder.back())
        assertEquals(BoardsScreen.Directory, holder.state.value.screen)
        holder.dispatch(BoardAction.Directory(explore = true))
        assertTrue(holder.back())
        assertFalse(holder.state.value.explore)
        assertFalse(holder.back())
    }
    @Test fun boardPagesParticipateInSystemAndControllerBackNavigation() {
        for(screen in BoardsScreen.entries) {
            val state = PocketPassUiState(routes = listOf(PocketPassRoute.Root(PocketPassDestination.Messages)),
                boards = BoardsUiState(screen = screen))
            assertTrue(state.hasDismissableLayer(), screen.name)
            assertTrue(state.blocksShoulderTabs(), screen.name)
        }
    }

    @Test fun leavingTheBoardsRootReturnsToPocketPass() {
        val state = PocketPassUiState(routes = listOf(PocketPassRoute.Root(PocketPassDestination.Messages)))
        assertEquals(PocketPassDestination.Home, PocketPassReducer.reduce(state, PocketPassEvent.Back).rootDestination)
    }

    @Test fun exploreReturnsToJoinedBoards() {
        val state = PocketPassUiState(routes = listOf(PocketPassRoute.Root(PocketPassDestination.Messages)),
            boards = BoardsUiState(screen = BoardsScreen.Directory, explore = true))
        assertTrue(state.hasDismissableLayer())
    }

    @Test fun aRememberedBoardPageDoesNotCaptureBackOnOtherTabs() {
        val state = PocketPassUiState(routes = listOf(PocketPassRoute.Root(PocketPassDestination.Home)),
            boards = BoardsUiState(screen = BoardsScreen.Thread))
        assertFalse(state.hasDismissableLayer())
    }
}
