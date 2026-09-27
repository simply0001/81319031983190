package com.pocketpass.app

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.model.*
import com.pocketpass.app.ui.DesignSurface
import com.pocketpass.app.ui.PocketPassTheme
import com.pocketpass.app.ui.components.*
import com.pocketpass.app.ui.controller.ControllerFocus
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.phone.PhoneSurface
import com.pocketpass.app.ui.phone.PhoneThread
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class KeyboardInputUiTest {
    @get:Rule val compose = createComposeRule()
    private val focus = ControllerFocus()
    private var typed = ""
    private var layout by mutableStateOf(PocketKeyboardLayout.Text)

    private fun showKeyboard() {
        compose.setContent {
            PocketPassTheme(ThemeMode.Light) {
                CompositionLocalProvider(LocalControllerFocus provides focus) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1240f / 1080f)) {
                        DesignSurface(1240f, 1080f, modifier = Modifier.fillMaxSize()) { metrics ->
                            PocketKeyboard(metrics, layout, "Send", true, emojiKey = true, onKey = { key ->
                                when (key) {
                                    is PocketKey.Character -> typed += key.value
                                    PocketKey.Space -> typed += " "
                                    PocketKey.Backspace -> typed = typed.dropLast(1)
                                    PocketKey.Emoji -> layout = PocketKeyboardLayout.Emoji
                                    PocketKey.Alphabet -> layout = PocketKeyboardLayout.Text
                                    PocketKey.Submit -> Unit
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    private fun assertShift(description: String) {
        compose.onNodeWithTag("key_shift").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description))
    }

    @Test fun shiftAndNumberKeysUseTheirSwappedRows() {
        showKeyboard()
        val shift = compose.onNodeWithTag("key_shift").fetchSemanticsNode().boundsInRoot
        val numbers = compose.onNodeWithTag("key_symbols").fetchSemanticsNode().boundsInRoot
        val letters = compose.onNodeWithTag("key_z").fetchSemanticsNode().boundsInRoot
        val emoji = compose.onNodeWithTag("key_emoji").fetchSemanticsNode().boundsInRoot
        assertEquals(letters.top, shift.top, 1f)
        assertTrue(shift.bottom < numbers.top)
        assertEquals(shift.left, numbers.left, 1f)
        assertTrue(numbers.right < emoji.left)

        compose.onNodeWithTag("key_symbols").performClick()
        compose.onNodeWithTag("key_shift").assertDoesNotExist()
        compose.onNodeWithTag("key_symbols").assertTextEquals("ABC")
        compose.onNodeWithTag("key_symbols").performClick()
        compose.onNodeWithTag("key_shift").assertExists()
    }

    @Test fun doubleTapLocksAcrossTypingSpacesBackspaceSymbolsAndEmoji() {
        showKeyboard()
        compose.onNodeWithTag("key_shift").performTouchInput { doubleClick() }
        assertShift("Caps lock on")
        compose.onNodeWithTag("key_a").performClick()
        compose.onNodeWithTag("key_space").performClick()
        compose.onNodeWithTag("key_b").performClick()
        compose.onNodeWithTag("key_backspace").performClick()
        compose.onNodeWithTag("key_symbols").performClick()
        compose.onNodeWithTag("key_1").performClick()
        compose.onNodeWithTag("key_symbols").performClick()
        assertShift("Caps lock on")
        compose.onNodeWithTag("key_emoji").performClick()
        compose.onNodeWithTag("key_alphabet").performClick()
        assertShift("Caps lock on")
        compose.onNodeWithTag("key_c").performClick()
        compose.onNodeWithTag("key_shift").performClick()
        assertShift("Lowercase")
        compose.onNodeWithTag("key_d").performClick()
        compose.runOnIdle { assertEquals("A 1Cd", typed) }
    }

    @Test fun singleTapShiftsOneLetterAndControllerCanLockAndUnlock() {
        showKeyboard()
        compose.onNodeWithTag("key_shift").performClick()
        assertShift("Next letter uppercase")
        compose.onNodeWithTag("key_a").performClick()
        assertShift("Lowercase")
        compose.onNodeWithTag("key_b").performClick()
        compose.runOnIdle {
            focus.focus("key_shift", reveal = true)
            assertTrue(focus.activate())
            assertTrue(focus.activate())
        }
        assertShift("Caps lock on")
        compose.onNodeWithTag("key_c").performClick()
        compose.runOnIdle { assertEquals("AbC", typed) }
        compose.onNodeWithTag("key_shift").performClick()
        assertShift("Lowercase")
    }

    @Test fun phoneComposingInputSurvivesDelayedDraftUpdatesWithoutRestartingTheKeyboard() {
        val conversation = FixtureData.conversations.first { it.id == FixtureData.SpobConversationId }
        var state by mutableStateOf(PocketPassUiState(selectedConversationId = conversation.id,
            selectedConversation = conversation, profile = FixtureData.currentProfile))
        var connection: InputConnection? = null
        var sessions = 0
        val drafts = mutableListOf<String>()
        val interceptor = PlatformTextInputInterceptor { request, _ ->
            sessions++
            connection = request.createInputConnection(EditorInfo())
            awaitCancellation()
        }
        compose.setContent {
            InterceptPlatformTextInput(interceptor) {
                PocketPassTheme(state.themeMode) {
                    PhoneSurface { metrics ->
                        PhoneThread(metrics, state, { event ->
                            if (event is PocketPassEvent.UpdateMessageDraft) drafts.add(event.value)
                        })
                    }
                }
            }
        }
        val composer = compose.onNodeWithTag("message_composer")
        composer.performClick()
        compose.waitUntil(5_000) { connection != null }
        compose.runOnIdle { connection!!.setComposingText("H", 1) }
        composer.assertTextEquals("H")
        compose.runOnIdle { connection!!.setComposingText("HELLO", 1) }
        composer.assertTextEquals("HELLO")
        compose.runOnIdle { state = state.copy(messageOperationError = "Fixture update") }
        composer.assertTextEquals("HELLO")
        compose.runOnIdle { state = state.copy(messageDraft = "H") }
        composer.assertTextEquals("HELLO")
        compose.runOnIdle { state = state.copy(messageDraft = "HELLO") }
        compose.runOnIdle {
            connection!!.finishComposingText()
            connection!!.commitText(" ", 1)
            connection!!.setComposingText("WORLD", 1)
        }
        composer.assertTextEquals("HELLO WORLD")
        compose.runOnIdle {
            assertEquals(1, sessions)
            assertEquals("HELLO WORLD", drafts.last())
            state = state.copy(messageDraft = "HELLO WORLD")
        }
        compose.runOnIdle { state = state.copy(messageDraft = "") }
        composer.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
    }
}
