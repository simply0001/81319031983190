package com.pocketpass.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.pocketpass.app.data.repository.FixtureData
import com.pocketpass.app.domain.state.SessionState
import com.pocketpass.app.feature.AccountSetupUiState
import com.pocketpass.app.mii.MiiAppearance
import com.pocketpass.app.mii.MiiEditorController
import com.pocketpass.app.mii.MiiEditorEvent
import com.pocketpass.app.mii.MiiEditorMode
import com.pocketpass.app.mii.MiiEditorUiState
import com.pocketpass.app.mii.MiiRendererCommand
import com.pocketpass.app.mii.MiiRendererStatus
import com.pocketpass.app.model.PocketPassDestination
import com.pocketpass.app.model.PocketPassRoute
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.ThemeMode
import com.pocketpass.app.ui.MiiRenderSurfaceFillingHeight
import com.pocketpass.app.ui.PocketPassTheme
import com.pocketpass.app.ui.mii.LocalMiiRenderSurface
import com.pocketpass.app.ui.phone.PhoneRoot
import com.pocketpass.app.ui.phone.PhoneSurface
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

class RightToLeftLayoutUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var state by mutableStateOf(fixture())
    private var direction by mutableStateOf(LayoutDirection.Ltr)
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
                LocalLayoutDirection provides direction,
                LocalMiiRenderSurface provides MiiRenderSurfaceFillingHeight,
            ) {
                PocketPassTheme(state.themeMode) {
                    Box(Modifier.fillMaxSize()) {
                        PhoneSurface { metrics -> PhoneRoot(metrics, state, {}, controller) }
                    }
                }
            }
        }
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        android.os.SystemClock.sleep(600)
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = java.io.File(context.getExternalFilesDir(null), "rtl-layout").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(directory, "$name.png")).use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun Rect.rounded(): List<Int> = listOf(left, top, right, bottom).map { it.roundToInt() }

    private fun taggedBounds(): Map<String, List<List<Int>>> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.TestTag), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .groupBy({ it.config[SemanticsProperties.TestTag] }, { it.boundsInRoot.rounded() })
            .mapValues { (_, bounds) -> bounds.sortedWith(compareBy({ it[1] }, { it[0] })) }

    private fun assertSameInBothDirections(name: String) {
        compose.runOnIdle { direction = LayoutDirection.Ltr }
        settle()
        val leftToRight = taggedBounds()
        capture("$name-ltr")
        compose.runOnIdle { direction = LayoutDirection.Rtl }
        settle()
        capture("$name-rtl")
        val rightToLeft = taggedBounds()
        assertTrue(leftToRight.isNotEmpty())
        val moved = leftToRight.keys.filter { tag -> leftToRight[tag] != rightToLeft[tag] }
        assertEquals("$name: elements that moved in right-to-left: $moved", emptyList<String>(), moved)
    }

    @Test fun thePiipEditorKeepsItsLayoutInRightToLeftLanguages() {
        show()
        android.os.SystemClock.sleep(RENDERER_BOOT_MILLIS)
        settle()
        assertSameInBothDirections("piip-editor")
    }

    @Test fun theMainScreensKeepTheirLayoutInRightToLeftLanguages() {
        state = fixture().copy(miiEditorEnabled = false)
        show()
        for (destination in PocketPassDestination.entries) {
            compose.runOnIdle { state = state.copy(routes = listOf(PocketPassRoute.Root(destination))) }
            settle()
            assertSameInBothDirections(destination.name.lowercase())
        }
    }

    private companion object {
        const val RENDERER_BOOT_MILLIS = 9_000L
    }
}
