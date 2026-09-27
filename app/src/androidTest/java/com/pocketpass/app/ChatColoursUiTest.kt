package com.pocketpass.app

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.domain.model.ChatBubbleColour
import com.pocketpass.app.domain.model.ConversationKind
import com.pocketpass.app.domain.model.ConversationMember
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.model.*
import com.pocketpass.app.ui.BottomDisplayContent
import com.pocketpass.app.ui.TopDisplayContent
import com.pocketpass.app.ui.PocketPassTheme
import com.pocketpass.app.ui.phone.PhoneRoot
import com.pocketpass.app.ui.phone.PhoneThread
import com.pocketpass.app.ui.phone.PhoneSurface
import com.pocketpass.app.ui.controller.ControllerFocus
import com.pocketpass.app.ui.controller.LocalControllerFocus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ChatColoursUiTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(PocketPassUiState())
    private val saves = mutableListOf<ChatBubbleColour>()
    private val focus = ControllerFocus()
    private fun show(route: PocketPassRoute = PocketPassRoute.ChatColours, phone: Boolean = false, top: Boolean = false, threadOnly: Boolean = false,
        initialState: (PocketPassUiState) -> PocketPassUiState = { it }) {
        val routes = if (route is PocketPassRoute.MessageDetail) listOf(PocketPassRoute.Root(PocketPassDestination.Messages), route)
            else listOf(PocketPassRoute.Root(PocketPassDestination.Settings), PocketPassRoute.AppSettings, route)
        state = initialState(PocketPassUiState(routes = routes,
            profile = FixtureData.currentProfile, sessionState = SessionState.Authenticated(FixtureData.CurrentUserId),
            accountSetup = AccountSetupUiState(resolved = true)))
        compose.setContent {
            CompositionLocalProvider(LocalControllerFocus provides focus) {
                val dispatch: (PocketPassEvent) -> Unit = { event ->
                    if (event is PocketPassEvent.SaveChatColour) {
                        saves.add(event.colour)
                        state = state.copy(profile = state.profile!!.copy(chatBubbleColour = event.colour, chatColourPending = true))
                    } else state = PocketPassReducer.reduce(state, event)
                }
                if (phone) {
                    PocketPassTheme(state.themeMode) {
                        PhoneSurface { metrics ->
                            if (threadOnly) PhoneThread(metrics, state, dispatch)
                            else PhoneRoot(metrics, state, dispatch, null)
                        }
                    }
                } else if (top) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1920f / 1080f)) {
                        TopDisplayContent(state, dispatch)
                    }
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(1240f / 1080f)) {
                        BottomDisplayContent(state, dispatch)
                    }
                }
            }
        }
    }

    @Test fun previewDoesNotSaveUntilRequestedAndResetIsAlsoAPreview() {
        show()
        compose.onNodeWithTag("chat_colour_pink").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertTrue(saves.isEmpty()) }
        compose.onNodeWithTag("chat_colour_save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(ChatBubbleColour.Pink), saves) }
        compose.onNodeWithTag("chat_colour_status").performScrollTo().assertTextContains("Waiting to sync", substring = true)
        compose.onNodeWithTag("chat_colour_reset").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, saves.size) }
        compose.onNodeWithTag("chat_colour_save").performClick()
        compose.runOnIdle { assertEquals(ChatBubbleColour.Default, saves.last()) }
    }

    @Test fun backDiscardsDraftAndAppSettingsEntryReopensSavedColour() {
        show()
        compose.onNodeWithTag("chat_colour_teal").performScrollTo().performClick()
        compose.onNodeWithTag("chat_colours_back").performClick()
        compose.runOnIdle { assertEquals(PocketPassRoute.AppSettings, state.routes.last()) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("settings_chat_colours").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings_chat_colours").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("chat_colour_default").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("chat_colour_default").performScrollTo().assertIsSelected()
        compose.runOnIdle { assertTrue(saves.isEmpty()) }
    }

    @Test fun controllerCanFocusColourAndSaveControls() {
        show()
        compose.runOnIdle { focus.focus("chat_colour_purple", reveal = true) }
        compose.onNodeWithTag("chat_colour_purple").assertIsDisplayed()
        compose.runOnIdle { assertTrue(focus.activate()) }
        compose.onNodeWithTag("chat_colour_purple").assertIsSelected()
        compose.runOnIdle { focus.focus("chat_colour_save", reveal = true) }
        compose.onNodeWithTag("chat_colour_save").assertIsDisplayed()
        compose.runOnIdle { assertTrue(focus.activate()) }
        compose.runOnIdle { assertEquals(listOf(ChatBubbleColour.Purple), saves) }
    }

    @Test fun captureLightAndDarkColourSettings() {
        show()
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            compose.runOnIdle { state = state.copy(themeMode = mode) }
            compose.onNodeWithTag("chat_colour_preview").performScrollTo().assertIsDisplayed()
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            capture("chat-colours-${mode.name}")
        }
    }

    @Test fun phoneSettingsPreviewSaveAndBack() {
        show(phone = true)
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            compose.runOnIdle { state = state.copy(themeMode = mode) }
            compose.onNodeWithTag("chat_colour_teal").performScrollTo().performClick()
            compose.onNodeWithTag("chat_colour_save").performScrollTo().performClick()
            compose.onNodeWithTag("chat_colour_status", useUnmergedTree = true).performScrollTo().assertTextContains("Waiting to sync", substring = true)
            capture("chat-colours-phone-${mode.name}")
            compose.onNodeWithTag("chat_colour_reset").performClick()
            compose.onNodeWithTag("chat_colours_back").performClick()
            compose.runOnIdle { assertEquals(PocketPassRoute.AppSettings, state.routes.last()) }
            compose.onNodeWithTag("settings_chat_colours").performScrollTo().performClick()
            compose.onNodeWithTag("chat_colour_teal").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("chat_colour_pink").performClick()
        }
    }

    private fun capture(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        java.io.FileOutputStream(java.io.File(context.getExternalFilesDir(null), "$name.png")).use {
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun allPresetsRenderOldMessagesAndAttachmentCaptionsInBothThemes() {
        show(route = PocketPassRoute.MessageDetail(FixtureData.SpobConversationId.value), phone = true)
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val attachment = java.io.File(context.cacheDir, "chat-colour-test-image.png")
        val bitmap = android.graphics.Bitmap.createBitmap(320, 200, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(190, 220, 230))
        java.io.FileOutputStream(attachment).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val history = FixtureData.messages.getValue(FixtureData.SpobConversationId).mapIndexed { index, message ->
            if (index == 0) message.copy(body = "Photo caption", attachment = com.pocketpass.app.domain.model.MessageAttachment(null, "image/png", attachment.absolutePath))
            else message.copy(body = "My chosen chat colour")
        }
        compose.runOnIdle { state = state.copy(selectedConversationId = FixtureData.SpobConversationId,
            selectedConversation = FixtureData.conversations.first { it.id == FixtureData.SpobConversationId }, selectedMessages = history) }
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            for (colour in ChatBubbleColour.entries) {
                compose.runOnIdle { state = state.copy(themeMode = mode,
                    profile = state.profile!!.copy(chatBubbleColour = colour),
                    messageAuthorColours = mapOf(FixtureData.SpobUserId to colour)) }
                compose.onNodeWithText("Photo caption").assertExists()
                compose.onNodeWithText("My chosen chat colour").assertExists()
                compose.mainClock.advanceTimeBy(1000)
                capture("chat-bubbles-${mode.name}-${colour.key}")
                compose.runOnIdle { assertEquals(history, state.selectedMessages) }
            }
        }
    }

    @Test fun topScreenTypingMatchesEachMembersColour() = captureTypingColours(phone = false)

    @Test fun phoneTypingMatchesEachMembersColour() = captureTypingColours(phone = true)

    @Test fun topScreenGifOpensFullScreenAndCloses() = checkImageViewer(phone = false)

    @Test fun phoneGifOpensFullScreenAndCloses() = checkImageViewer(phone = true)

    private fun checkImageViewer(phone: Boolean) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val gif = java.io.File(instrumentation.targetContext.cacheDir, "viewer-fixture.gif")
        instrumentation.context.assets.open("animated-message.gif").use { input -> gif.outputStream().use { input.copyTo(it) } }
        val conversation = FixtureData.conversations.first { it.id == FixtureData.SpobConversationId }
        val message = FixtureData.messages.getValue(conversation.id).first().copy(
            body = "Animated image",
            attachment = com.pocketpass.app.domain.model.MessageAttachment(null, "image/gif", gif.absolutePath),
        )
        show(route = PocketPassRoute.MessageDetail(conversation.id.value), phone = phone, top = !phone, threadOnly = phone) {
            it.copy(selectedConversationId = conversation.id, selectedConversation = conversation, selectedMessages = listOf(message))
        }
        compose.mainClock.advanceTimeBy(1000)
        val thumbnail = compose.onNodeWithTag("message_attachment").assertIsDisplayed()
        assertGifAnimates(thumbnail)
        val thumbnailBounds = thumbnail.fetchSemanticsNode().boundsInRoot
        thumbnail.performClick()
        val viewer = compose.onNodeWithTag("message_image_viewer").assertIsDisplayed()
        assertTrue(viewer.fetchSemanticsNode().boundsInRoot.width > thumbnailBounds.width * 2)
        assertGifAnimates(viewer)
        viewer.performTouchInput { doubleClick(center) }
        val bitmap = viewer.captureToImage().asAndroidBitmap()
        java.io.FileOutputStream(java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "message-gif-fullscreen-${if (phone) "phone" else "top"}.png")).use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithTag("close_message_image").performClick()
        compose.onNodeWithTag("message_image_viewer").assertDoesNotExist()
        thumbnail.assertIsDisplayed().performClick()
        compose.onNodeWithTag("message_image_viewer").assertIsDisplayed()
        assertTrue(instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
        compose.waitUntil(5000) { compose.onAllNodesWithTag("message_image_viewer").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("message_image_viewer").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(message), state.selectedMessages) }
    }

    private fun assertGifAnimates(node: SemanticsNodeInteraction) {
        val frames = mutableSetOf<Int>()
        repeat(8) {
            val bitmap = node.captureToImage().asAndroidBitmap()
            frames.add(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
            if (frames.size > 1) return
            android.os.SystemClock.sleep(150)
        }
        assertTrue("GIF should display more than one animation frame", frames.size > 1)
    }

    private fun captureTypingColours(phone: Boolean) {
        val second = UserId("second-typist")
        val conversation = FixtureData.conversations.first { it.id == FixtureData.SpobConversationId }.copy(
            kind = ConversationKind.Group,
            members = listOf(
                ConversationMember(FixtureData.SpobUserId, "Pink typist", null, joinedAt = kotlin.time.Instant.fromEpochMilliseconds(0)),
                ConversationMember(second, "Green typist", null, joinedAt = kotlin.time.Instant.fromEpochMilliseconds(0)),
            ),
        )
        show(route = PocketPassRoute.MessageDetail(conversation.id.value), phone = phone, top = !phone, threadOnly = phone) { initial ->
            initial.copy(
                selectedConversationId = conversation.id,
                selectedConversation = conversation,
                selectedMessages = FixtureData.messages.getValue(conversation.id).takeLast(2).map { it.copy(body = "Matching chat colours") },
                profile = initial.profile!!.copy(chatBubbleColour = ChatBubbleColour.Teal),
                messageAuthorColours = mapOf(FixtureData.SpobUserId to ChatBubbleColour.Pink, second to ChatBubbleColour.Green),
                typingConversationIds = setOf(conversation.id.value),
                typingUserIds = setOf(FixtureData.SpobUserId, second),
            )
        }
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            compose.runOnIdle { state = state.copy(themeMode = mode) }
            compose.mainClock.advanceTimeBy(1000)
            for (colour in listOf(ChatBubbleColour.Pink, ChatBubbleColour.Green)) {
                val node = compose.onNodeWithTag("typing_indicator_${colour.key}").assertIsDisplayed()
                val pixels = node.captureToImage().toPixelMap()
                val fill = pixels[pixels.width / 2, pixels.height / 4]
                if (colour == ChatBubbleColour.Pink) assertTrue("Typing bubble should be pink", fill.red > fill.green && fill.blue > fill.green)
                else assertTrue("Typing bubble should be green", fill.green > fill.red && fill.green > fill.blue)
            }
            capture("typing-colours-${if (phone) "phone" else "top"}-${mode.name}")
        }
    }
}
