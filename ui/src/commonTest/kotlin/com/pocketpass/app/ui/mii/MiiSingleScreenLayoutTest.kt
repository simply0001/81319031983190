package com.pocketpass.app.ui.mii

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiiSingleScreenLayoutTest {
    private fun portraitPhone() = miiSingleScreenLayout(1240f, 2754f, insetTop = 135f, insetBottom = 72f, insetStart = 0f, insetEnd = 0f)

    private fun odinThree() = miiSingleScreenLayout(2205f, 1240f, insetTop = 72f, insetBottom = 0f, insetStart = 0f, insetEnd = 0f)

    private fun landscapePhone() = miiSingleScreenLayout(2755f, 1240f, insetTop = 72f, insetBottom = 50f, insetStart = 110f, insetEnd = 0f)

    private fun portraitTablet() = miiSingleScreenLayout(1667f, 2667f, insetTop = 50f, insetBottom = 50f, insetStart = 0f, insetEnd = 0f)

    private fun landscapeTablet() = miiSingleScreenLayout(2667f, 1667f, insetTop = 50f, insetBottom = 50f, insetStart = 0f, insetEnd = 0f)

    private fun Rect.inside(other: Rect) =
        left >= other.left - 0.01f && top >= other.top - 0.01f && right <= other.right + 0.01f && bottom <= other.bottom + 0.01f

    @Test
    fun uprightScreensPutThePreviewAboveTheEditor() {
        for (layout in listOf(portraitPhone(), portraitTablet())) {
            assertFalse(layout.landscape)
            assertEquals(layout.insetTop, layout.preview.top)
            assertTrue(layout.preview.bottom <= layout.categories.top + 0.01f)
            assertTrue(layout.categories.bottom <= layout.colors.top)
            assertTrue(layout.colors.bottom <= layout.traits.top)
            assertTrue(layout.traits.bottom <= layout.adjustments.top)
            assertTrue(layout.adjustments.bottom <= layout.height - layout.insetBottom)
        }
    }

    @Test
    fun wideScreensPutThePreviewBesideTheEditor() {
        for (layout in listOf(odinThree(), landscapePhone(), landscapeTablet())) {
            assertTrue(layout.landscape)
            assertEquals(layout.panel.left, layout.preview.right)
            assertEquals(layout.insetStart, layout.preview.left)
            assertTrue(layout.categories.right <= layout.traits.left)
            assertTrue(layout.traits.right <= layout.colors.left)
            assertTrue(layout.colors.right <= layout.adjustments.left)
            assertEquals(layout.width - layout.insetEnd, layout.adjustments.right)
        }
    }

    @Test
    fun theOdinKeepsAGenerousPreviewAndWidePhonesGainATraitColumn() {
        val odin = odinThree()
        assertEquals(2, odin.traitColumns)
        assertTrue(odin.preview.width >= LANDSCAPE_MIN_PREVIEW)
        val phone = landscapePhone()
        assertEquals(3, phone.traitColumns)
        assertTrue(phone.preview.width >= LANDSCAPE_MIN_PREVIEW)
    }

    @Test
    fun traitColumnsFillTheWidthOnUprightScreens() {
        assertEquals(4, portraitPhone().traitColumns)
        assertTrue(portraitTablet().traitColumns >= 5)
    }

    @Test
    fun uprightScreensShowAtLeastTwoTraitRows() {
        for (height in listOf(2204f, 2400f, 2754f)) {
            val layout = miiSingleScreenLayout(1240f, height, insetTop = 72f, insetBottom = 72f, insetStart = 0f, insetEnd = 0f)
            val rows = (layout.traits.height - 2f * TRAIT_GRID_PADDING) / (TRAIT_PILL_HEIGHT + TRAIT_ROW_GAP)
            assertTrue(rows >= SINGLE_TRAIT_MIN_ROWS - 0.01f, "only $rows rows at $height")
        }
    }

    @Test
    fun adjustmentButtonsStayOnScreenAndInOrder() {
        for (layout in listOf(portraitPhone(), odinThree(), landscapePhone(), portraitTablet(), landscapeTablet())) {
            val screen = Rect(0f, 0f, layout.width, layout.height)
            val buttons = (0 until 5).map(layout::adjustButton)
            buttons.forEach { assertTrue(it.inside(screen), "$it outside $screen") }
            buttons.zipWithNext().forEach { (first, second) ->
                if (layout.landscape) assertTrue(first.bottom <= second.top) else assertTrue(first.right <= second.left)
            }
        }
    }

    @Test
    fun expandedSlidersGrowFromTheirButtonAndStayInsideThePanel() {
        for (layout in listOf(portraitPhone(), odinThree(), landscapePhone())) {
            for (index in 0 until 5) {
                for (vertical in listOf(false, index == AdjustmentVisualSlot.Vertical.ordinal).distinct()) {
                    val button = layout.adjustButton(index)
                    val expanded = layout.expandedAdjustment(index, vertical)
                    assertTrue(button.inside(expanded), "button $index not inside its slider")
                    assertTrue(expanded.inside(Rect(0f, 0f, layout.width, layout.height)))
                    assertTrue(expanded.top >= layout.overlayRegion.top - 0.01f)
                }
            }
        }
    }

    @Test
    fun thePaletteFitsBelowThePreviewOrInsideThePanel() {
        for (layout in listOf(portraitPhone(), odinThree(), landscapePhone(), portraitTablet(), landscapeTablet())) {
            val palette = layout.palette(100, 10)
            assertTrue(palette.panel.inside(layout.overlayRegion), "${palette.panel} outside ${layout.overlayRegion}")
            assertTrue(palette.swatchAt(99).inside(palette.panel))
            assertTrue(palette.swatch >= 60f)
        }
    }

    @Test
    fun theSavePillSitsAtTheTopRightOfAnUprightPreview() {
        val layout = portraitPhone()
        val pill = layout.savePill(300f)
        assertEquals(layout.width - SINGLE_CONTROL_MARGIN, pill.right)
        assertTrue(pill.top >= layout.insetTop)
        assertTrue(pill.left > layout.backButton.right)
    }
}
