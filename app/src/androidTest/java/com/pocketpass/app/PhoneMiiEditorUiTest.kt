package com.pocketpass.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.mii.MiiAdjustmentField
import com.pocketpass.app.mii.MiiAppearance
import com.pocketpass.app.mii.MiiCategory
import com.pocketpass.app.mii.MiiColorField
import com.pocketpass.app.mii.MiiEditorController
import com.pocketpass.app.mii.MiiEditorEvent
import com.pocketpass.app.mii.MiiEditorMode
import com.pocketpass.app.mii.MiiEditorUiState
import com.pocketpass.app.mii.MiiRendererCommand
import com.pocketpass.app.mii.MiiRendererStatus
import com.pocketpass.app.mii.MiiTraitField
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.ThemeMode
import com.pocketpass.app.ui.MiiRenderSurfaceFillingHeight
import com.pocketpass.app.ui.PocketPassTheme
import com.pocketpass.app.ui.controller.ControllerFocus
import com.pocketpass.app.ui.controller.ControllerFocusHighlight
import com.pocketpass.app.ui.controller.FocusDirection
import com.pocketpass.app.ui.controller.FocusDisplay
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.LocalFocusDisplay
import com.pocketpass.app.ui.mii.LocalMiiRenderSurface
import com.pocketpass.app.ui.phone.PhoneRoot
import com.pocketpass.app.ui.phone.PhoneSurface
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PhoneMiiEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var state by mutableStateOf(fixture())
    private var focusEnabled by mutableStateOf(false)
    private val focus = ControllerFocus()
    private val events = mutableListOf<MiiEditorEvent>()
    private val controller = object : MiiEditorController {
        override val state = MutableStateFlow(editor())
        override val rendererCommands = MutableSharedFlow<MiiRendererCommand>()
        override fun activateAccount(accountKey: String?) = Unit
        override fun beginEdit(slot: Int, wearHat: Int?) = Unit
        override fun wearHat(hatType: Int) = Unit
        override fun setActiveSlot(slot: Int) = Unit
        override fun deleteSlot(slot: Int) = Unit
        override fun dispatch(event: MiiEditorEvent) = Unit
    }

    private fun editor() = MiiEditorUiState(
        activeAccountKey = FixtureData.CurrentUserId.value,
        mode = MiiEditorMode.EditExisting,
        isInitialized = true,
        draft = MiiAppearance(),
        saved = MiiAppearance(),
        rendererStatus = MiiRendererStatus.Ready("test"),
        presented = true,
    )

    private fun fixture() = PocketPassUiState(
        sessionState = SessionState.Authenticated(FixtureData.CurrentUserId),
        accountSetup = AccountSetupUiState(resolved = true),
        profile = FixtureData.currentProfile,
        miiEditorEnabled = true,
        miiEditor = editor(),
        themeMode = ThemeMode.Light,
    )

    private fun show() {
        compose.setContent {
            CompositionLocalProvider(
                LocalMiiRenderSurface provides MiiRenderSurfaceFillingHeight,
                LocalControllerFocus provides focus.takeIf { focusEnabled },
                LocalFocusDisplay provides FocusDisplay.Bottom,
            ) {
                PocketPassTheme(state.themeMode) {
                    Box(Modifier.fillMaxSize()) {
                        PhoneSurface { metrics ->
                            PhoneRoot(metrics, state, { event -> if (event is PocketPassEvent.Mii) events += event.event }, controller)
                        }
                        if (focusEnabled) ControllerFocusHighlight(focus, FocusDisplay.Bottom)
                    }
                }
            }
        }
        android.os.SystemClock.sleep(RENDERER_BOOT_MILLIS)
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        android.os.SystemClock.sleep(600)
    }

    private fun edit(change: MiiEditorUiState.() -> MiiEditorUiState) {
        compose.runOnIdle { state = state.copy(miiEditor = state.miiEditor.change()) }
        settle()
    }

    private fun capture(name: String) {
        settle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = context.resources.configuration
        val directory = java.io.File(
            context.getExternalFilesDir(null),
            "mii-editor-${config.screenWidthDp}x${config.screenHeightDp}",
        ).apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(directory, "$name.png")).use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun theSingleScreenEditorRendersEveryPanel() {
        show()
        compose.onNodeWithTag("mii_editor_save").assertIsDisplayed()
        compose.onNodeWithTag("mii_editor_back").assertIsDisplayed()
        compose.onNodeWithTag("mii_trait_grid").assertIsDisplayed()
        capture("01-face")

        compose.onNodeWithTag("mii_editor_save").performClick()
        compose.onNodeWithTag("mii_editor_back").performClick()
        compose.onNodeWithTag("mii_category_Hair").performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(MiiEditorEvent.Save, MiiEditorEvent.RequestCancel, MiiEditorEvent.SelectCategory(MiiCategory.Hair)),
                events.filterNot { it is MiiEditorEvent.SetTraitPage },
            )
        }

        edit { copy(selectedCategory = MiiCategory.Hair, activeTraitField = MiiTraitField.HairType, activeColorField = MiiColorField.Hair) }
        capture("02-hair")
        edit { copy(colorPaletteField = MiiColorField.Hair) }
        capture("03-hair-palette")
        edit { copy(colorPaletteField = null, selectedCategory = MiiCategory.Eyes, activeTraitField = MiiTraitField.EyeType, activeColorField = MiiColorField.Eyes) }
        capture("04-eyes")
        edit { copy(activeAdjustment = MiiAdjustmentField.EyeScale) }
        capture("05-eyes-scale-slider")
        edit { copy(activeAdjustment = MiiAdjustmentField.EyeYPosition) }
        capture("06-eyes-height-slider")
        edit { copy(activeAdjustment = null, selectedCategory = MiiCategory.Body, activeTraitField = MiiTraitField.Gender, activeColorField = MiiColorField.Favorite) }
        capture("07-body")
        edit { copy(discardPromptVisible = true) }
        capture("08-discard")
        edit { copy(discardPromptVisible = false, saveState = com.pocketpass.app.mii.MiiSaveState.Error("Your Piip could not be saved. Try again.", false)) }
        capture("09-notice")
    }

    @Test fun theControllerHighlightMovesAroundTheEditor() {
        focusEnabled = true
        show()
        compose.runOnIdle { focus.focus("mii_category_Face") }
        capture("10-focus-category")
        compose.runOnIdle { focus.move(FocusDirection.Down) }
        capture("11-focus-down")
        compose.runOnIdle { focus.move(FocusDirection.Down) }
        capture("12-focus-down-again")
        compose.runOnIdle {
            focus.focus("mii_editor_save")
        }
        capture("13-focus-save")
        edit { copy(selectedCategory = MiiCategory.Eyes, activeTraitField = MiiTraitField.EyeType, activeColorField = MiiColorField.Eyes, activeAdjustment = MiiAdjustmentField.EyeScale) }
        capture("14-focus-slider")
        compose.runOnIdle { assertEquals("mii_adjustment_slider", focus.focusId) }
    }

    private companion object {
        const val RENDERER_BOOT_MILLIS = 9_000L
    }
}
