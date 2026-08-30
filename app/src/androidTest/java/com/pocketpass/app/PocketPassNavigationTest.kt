package com.pocketpass.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.model.ActivityVariant
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassReducer
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.ThemeMode
import com.pocketpass.app.ui.BottomDisplayContent
import com.pocketpass.app.ui.TopDisplayContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PocketPassNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(signedIn(PocketPassDestination.Home))

    @Test
    fun everyBottomTabReplacesTheSharedDestination() {
        showBottom(signedIn(PocketPassDestination.Home))

        listOf(
            PocketPassDestination.Messages,
            PocketPassDestination.Friends,
            PocketPassDestination.Home,
            PocketPassDestination.Activities,
            PocketPassDestination.Settings,
        ).forEach { destination ->
            compose.onNodeWithTag("tab_${destination.name.lowercase()}").performClick()
            compose.runOnIdle {
                assertEquals(destination, state.rootDestination)
            }
        }
    }

    @Test
    fun messageDetailPushesAndVisibleHeaderPops() {
        showBottom(signedIn(PocketPassDestination.Home))

        compose.onNodeWithTag("tab_messages").performClick()
        compose.onNodeWithTag("message_spob").performClick()
        compose.runOnIdle {
            assertEquals(PocketPassRoute.MessageDetail("spob"), state.routes.last())
        }
        compose.onNodeWithTag("message_back").performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(PocketPassRoute.Root(PocketPassDestination.Messages)),
                state.routes,
            )
        }
    }

    @Test
    fun newGroupHeaderPushesAndBackPops() {
        showBottom(signedIn(PocketPassDestination.Messages))

        compose.onNodeWithTag("messages_new_group").performClick()
        compose.runOnIdle {
            assertEquals(PocketPassRoute.NewGroup, state.routes.last())
        }
        compose.onNodeWithTag("group_title_field").assertIsDisplayed()
        compose.onNodeWithTag("new_group_back").performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(PocketPassRoute.Root(PocketPassDestination.Messages)),
                state.routes,
            )
        }
    }

    @Test
    fun groupThreadOpensInfoAndCloses() {
        showBottomWithGroupInfo(signedIn(PocketPassDestination.Messages))

        compose.onNodeWithTag("message_crew").performClick()
        compose.runOnIdle {
            assertEquals(PocketPassRoute.MessageDetail("crew"), state.routes.last())
        }
        compose.onNodeWithTag("message_group_info").performClick()
        compose.runOnIdle { assertTrue(state.groupInfoOpen) }
        compose.onNodeWithTag("group_info_panel").assertIsDisplayed()
        compose.onNodeWithTag("close_group_info").performClick()
        compose.runOnIdle { assertFalse(state.groupInfoOpen) }
        compose.onNodeWithTag("message_back").performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(PocketPassRoute.Root(PocketPassDestination.Messages)),
                state.routes,
            )
        }
    }

    @Test
    fun groupThreadAttributesIncomingSendersOnce() {
        val crew = FixtureData.conversations.first { it.id == FixtureData.CrewConversationId }
        showTop(
            signedIn(PocketPassDestination.Messages).copy(
                routes = listOf(
                    PocketPassRoute.Root(PocketPassDestination.Messages),
                    PocketPassRoute.MessageDetail("crew"),
                ),
                selectedConversationId = crew.id,
                selectedConversation = crew,
                selectedMessages = FixtureData.messages.getValue(crew.id),
            ),
        )

        compose.onNodeWithTag("message_sender_fixture-crew-1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("message_sender_fixture-crew-2", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("message_sender_fixture-crew-3", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("message_sender_fixture-crew-4", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun groupRowShowsACollageOfTheOtherMembers() {
        showBottom(signedIn(PocketPassDestination.Messages))

        compose.onNodeWithTag("avatar_collage_crew", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("collage_tile_1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("collage_tile_2", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun topShuffleUsesTheSecondExactFigmaVariant() {
        showTop(signedIn(PocketPassDestination.Activities))

        compose.onNodeWithTag("shuffle_activities").performClick()
        compose.runOnIdle {
            assertEquals(ActivityVariant.Shuffled, state.activityVariant)
        }
    }

    @Test
    fun settingsControlsToggleSlideSelectAndPrompt() {
        showBottom(signedIn(PocketPassDestination.Settings))

        scrollSettingsTo("nearby_toggle").performClick()
        compose.runOnIdle { assertFalse(state.nearbyEnabled) }
        scrollSettingsTo("sound_slider").performTouchInput { click() }
        compose.runOnIdle { assertTrue(state.soundLevel > 0.48f) }

        scrollSettingsTo("theme_dark").performClick()
        compose.runOnIdle { assertEquals(ThemeMode.Dark, state.themeMode) }

        scrollSettingsTo("delete_account").performClick()
        compose.runOnIdle { assertTrue(state.deleteAccountVisible) }
        compose.onNodeWithTag("delete_account_cancel").performClick()
        compose.runOnIdle { assertFalse(state.deleteAccountVisible) }
    }

    private fun showBottom(initial: PocketPassUiState) {
        state = initial
        compose.setContent {
            BottomDisplayContent(state = state, dispatch = {
                state = PocketPassReducer.reduce(state, it)
            })
        }
    }

    private fun showTop(initial: PocketPassUiState) {
        state = initial
        compose.setContent {
            TopDisplayContent(state = state, dispatch = {
                state = PocketPassReducer.reduce(state, it)
            })
        }
    }

    private fun showBottomWithGroupInfo(initial: PocketPassUiState) {
        state = initial
        compose.setContent {
            BottomDisplayContent(state = state, dispatch = { event ->
                val reduced = PocketPassReducer.reduce(state, event)
                state = when (event) {
                    is PocketPassEvent.OpenMessage -> reduced.copy(
                        selectedConversationId = ConversationId(event.conversationId),
                        selectedConversation = reduced.conversations.first { it.id.value == event.conversationId },
                        selectedMessages = FixtureData.messages[ConversationId(event.conversationId)].orEmpty(),
                    )
                    PocketPassEvent.OpenGroupInfo -> reduced.copy(groupInfoOpen = true)
                    PocketPassEvent.CloseGroupInfo -> reduced.copy(groupInfoOpen = false)
                    PocketPassEvent.Back -> reduced.copy(
                        groupInfoOpen = false,
                        selectedConversationId = null,
                        selectedConversation = null,
                        selectedMessages = emptyList(),
                    )
                    else -> reduced
                }
            })
        }
    }

    private fun scrollSettingsTo(tag: String): SemanticsNodeInteraction {
        val scroll = compose.onNodeWithTag("settings_scroll").fetchSemanticsNode()
        val viewportBottom = scroll.boundsInRoot.bottom
        val tabBarBottom = compose.onNodeWithTag("tab_settings").fetchSemanticsNode().boundsInRoot.bottom
        val layoutPerRootPx = scroll.size.height / scroll.boundsInRoot.height
        repeat(3) {
            val target = compose.onNodeWithTag(tag).fetchSemanticsNode().unclippedBoundsInRoot()
            val delta = when {
                target.top < tabBarBottom -> target.top - tabBarBottom
                target.bottom > viewportBottom -> target.bottom - viewportBottom
                else -> return compose.onNodeWithTag(tag)
            }
            compose.onNodeWithTag("settings_scroll")
                .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, delta * layoutPerRootPx) }
        }
        return compose.onNodeWithTag(tag).assertIsDisplayed()
    }
}

private fun SemanticsNode.unclippedBoundsInRoot(): Rect {
    val coordinates = layoutInfo.coordinates
    return coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
}

private fun signedIn(destination: PocketPassDestination) = PocketPassUiState(
    routes = listOf(PocketPassRoute.Root(destination)),
    sessionState = SessionState.Authenticated(FixtureData.CurrentUserId),
    accountSetup = AccountSetupUiState(resolved = true),
    profile = FixtureData.currentProfile,
    friends = FixtureData.friends,
    conversations = FixtureData.conversations,
)
