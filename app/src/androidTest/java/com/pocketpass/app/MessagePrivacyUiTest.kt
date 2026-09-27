@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.pocketpass.app

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.domain.model.*
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.model.*
import com.pocketpass.app.ui.*
import com.pocketpass.app.ui.phone.*
import com.pocketpass.app.ui.screens.GroupInfoBottomOverlay
import com.pocketpass.app.ui.controller.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MessagePrivacyUiTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(PocketPassUiState())
    private val events = mutableListOf<PocketPassEvent>()
    private val focus = ControllerFocus()
    private fun show(phone: Boolean, screen: String = "settings") {
        val group = FixtureData.conversations.first().copy(kind = ConversationKind.Group,
            title = "Existing group")
        state = PocketPassUiState(profile = FixtureData.currentProfile,
            sessionState = SessionState.Authenticated(FixtureData.CurrentUserId),
            accountSetup = AccountSetupUiState(resolved = true),
            routes = listOf(PocketPassRoute.Root(PocketPassDestination.Settings), PocketPassRoute.Social),
            selectedConversationId = group.id, selectedConversation = group,
            groupInfoOpen = screen == "add",
            groupOperationError = if (screen == "add") GROUP_MESSAGES_BLOCKED else null,
            groupComposer = if (screen == "new") GroupComposerState(error = GROUP_MESSAGES_BLOCKED) else null)
        if (screen == "new") state = state.copy(routes = listOf(PocketPassRoute.Root(PocketPassDestination.Messages), PocketPassRoute.NewGroup))
        if (screen == "add") state = state.copy(routes = listOf(PocketPassRoute.Root(PocketPassDestination.Messages), PocketPassRoute.MessageDetail(group.id.value)))
        compose.setContent {
            CompositionLocalProvider(LocalControllerFocus provides focus) {
                val dispatch: (PocketPassEvent) -> Unit = { events.add(it) }
                if (phone) PocketPassTheme(state.themeMode) {
                    PhoneSurface { metrics ->
                        when(screen) {
                            "new" -> PhoneNewGroupPage(metrics, state, dispatch)
                            "add" -> PhoneGroupInfoSheet(metrics, state, dispatch)
                            else -> PhoneRoot(metrics, state, dispatch, null)
                        }
                    }
                } else Box(Modifier.aspectRatio(1240f / 1080f, matchHeightConstraintsFirst = true)) {
                    if (screen == "add") PocketPassTheme(state.themeMode) {
                        DesignSurface(1240f, 1080f, Modifier.fillMaxSize()) { GroupInfoBottomOverlay(it, state, dispatch) }
                    } else BottomDisplayContent(state, dispatch)
                }
            }
        }
    }
    @Test fun phoneSettingsOnlyChangeAfterAcknowledgment() = checkSettings(true)
    @Test fun dualScreenSettingsOnlyChangeAfterAcknowledgment() = checkSettings(false)
    @Test fun phoneSocialSettingsContainsSplitControls() {
        show(true)
        compose.onNodeWithTag("block_invites_toggle", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("boards_visibility_toggle", useUnmergedTree = true).assertExists()
    }
    @Test fun dualScreenSocialSettingsContainsSplitControls() {
        show(false)
        compose.onNodeWithTag("block_invites_toggle", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("boards_visibility_toggle", useUnmergedTree = true).assertExists()
    }
    @Test fun splitControlsDispatchIndependently() {
        state = PocketPassUiState(profile = FixtureData.currentProfile)
        compose.setContent {
            PocketPassTheme(state.themeMode) {
                DesignSurface(1240f, 1080f, Modifier.fillMaxSize()) { metrics ->
                    com.pocketpass.app.ui.screens.InvitesPrivacyPanel(metrics, 0f, state) { events.add(it) }
                    com.pocketpass.app.ui.screens.BoardsVisibilityPanel(metrics, 400f, state) { events.add(it) }
                }
            }
        }
        val invites = compose.onNodeWithTag("block_invites_toggle", useUnmergedTree = true)
        invites.assertIsOff().performClick()
        compose.runOnIdle {
            assertEquals(PocketPassEvent.SetInvitesPrivacy(true), events.last())
            state = state.copy(profile = state.profile!!.copy(blockInvites = true))
        }
        invites.assertIsOn()
        val boards = compose.onNodeWithTag("boards_visibility_toggle", useUnmergedTree = true)
        boards.assertIsOn().performClick()
        compose.runOnIdle {
            assertEquals(PocketPassEvent.SetBoardsVisible(false), events.last())
            state = state.copy(boardsVisible = false)
        }
        boards.assertIsOff()
    }
    private fun checkSettings(phone: Boolean) {
        show(phone)
        fun toggle() = compose.onNodeWithTag("block_messages_toggle", useUnmergedTree = true)
        toggle().performScrollTo()
        toggle().assertIsDisplayed().assertIsOff().performClick()
        compose.runOnIdle { assertEquals(PocketPassEvent.SetMessagePrivacy(true), events.last()); state = state.copy(messagePrivacySaving = true) }
        toggle().assertIsOff()
        compose.runOnIdle { state = state.copy(messagePrivacySaving = false, messagePrivacyError = "Couldn't save this setting. Check your connection and try again.") }
        toggle().assertIsOff()
        compose.onNodeWithTag("message_privacy_status", useUnmergedTree = true).assertTextContains("Couldn't save", substring = true)
        toggle().performClick()
        compose.runOnIdle { state = state.copy(profile = state.profile!!.copy(blockMessages = true), messagePrivacyError = null) }
        toggle().assertIsOn()
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            compose.runOnIdle { state = state.copy(themeMode = mode) }
            toggle().assertIsDisplayed()
            capture("privacy-${if(phone) "phone" else "dual"}-${mode.name}")
        }
        toggle().performClick()
        compose.runOnIdle { assertEquals(PocketPassEvent.SetMessagePrivacy(false), events.last()) }
    }
    @Test fun phoneNewGroupShowsBlockingPopup() = checkPopup(true, "new")
    @Test fun dualScreenNewGroupShowsBlockingPopup() = checkPopup(false, "new")
    @Test fun phoneExistingGroupAddShowsBlockingPopup() = checkPopup(true, "add")
    @Test fun dualScreenExistingGroupAddShowsBlockingPopup() = checkPopup(false, "add")
    private fun checkPopup(phone: Boolean, screen: String) {
        show(phone, screen)
        compose.onNodeWithTag("group_messages_blocked_dialog").assertIsDisplayed()
        compose.onNodeWithTag("group_messages_blocked_ok").performClick()
        compose.onNodeWithTag("group_messages_blocked_dialog").assertDoesNotExist()
        compose.runOnIdle { assertTrue(events.isEmpty()); state = state.copy(groupOperationError = null, groupComposer = state.groupComposer?.copy(error = null)) }
        compose.runOnIdle { state = state.copy(groupOperationError = GROUP_MESSAGES_BLOCKED, groupComposer = state.groupComposer?.copy(error = GROUP_MESSAGES_BLOCKED)) }
        compose.onNodeWithTag("group_messages_blocked_dialog").assertIsDisplayed()
    }
    private fun capture(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.FileOutputStream(java.io.File(context.getExternalFilesDir(null), "$name.png")).use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
