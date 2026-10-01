package com.pocketpass.app.ui.mii

import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.floor

internal const val SINGLE_LANDSCAPE_RATIO = 1.2f
internal const val SINGLE_SIDE_MARGIN = 50f
internal const val SINGLE_STRIP_HEIGHT = 150f
internal const val SINGLE_STRIPE = 15f
internal const val SINGLE_COLOR_ROW_HEIGHT = 132f
internal const val SINGLE_ROW_GAP = 24f
internal const val SINGLE_ADJUST_HEIGHT = 170f
internal const val SINGLE_ADJUST_GAP = 30f
internal const val SINGLE_BOTTOM_PADDING = 36f
internal const val SINGLE_STAGE_SHARE = 0.4f
internal const val SINGLE_MIN_PREVIEW_HEIGHT = 560f
internal const val SINGLE_TRAIT_MIN_ROWS = 2
internal const val SINGLE_CONTROL_SIZE = 120f
internal const val SINGLE_CONTROL_MARGIN = 40f
internal const val SINGLE_CONTROL_TOP = 28f
internal const val TRAIT_PILL_WIDTH = 217.5f
internal const val TRAIT_PILL_HEIGHT = 153.73f
internal const val TRAIT_PILL_GAP = 38.49f
internal const val TRAIT_ROW_GAP = 16f
internal const val TRAIT_GRID_PADDING = 38.49f
internal const val RAIL_WIDTH = 193f
internal const val RAIL_TO_GRID = 61f
internal const val COLOR_COLUMN_WIDTH = 148.43f
internal const val LANDSCAPE_ADJUST_WIDTH = 242f
internal const val LANDSCAPE_ADJUST_HEIGHT = 185f
internal const val LANDSCAPE_MIN_PREVIEW = 900f
internal const val LANDSCAPE_PREVIEW_SHARE = 0.75f
internal const val LANDSCAPE_MAX_TRAIT_COLUMNS = 3
internal const val LANDSCAPE_SLIDER_BOTTOM_GAP = 20f
internal const val PALETTE_EDGE_X = 36f
internal const val PALETTE_EDGE_Y = 40f
internal const val PALETTE_SWATCH_SHARE = 80f / 92f
internal const val PALETTE_MAX_PITCH = 120f
internal const val PALETTE_SLACK = 12f

internal fun miiEditorLandscape(width: Float, height: Float): Boolean =
    width >= height * SINGLE_LANDSCAPE_RATIO

internal fun traitGridWidth(columns: Int): Float =
    columns * TRAIT_PILL_WIDTH + (columns - 1).coerceAtLeast(0) * TRAIT_PILL_GAP

internal fun landscapePanelWidth(columns: Int): Float =
    RAIL_WIDTH + RAIL_TO_GRID + traitGridWidth(columns) + RAIL_TO_GRID + COLOR_COLUMN_WIDTH + RAIL_TO_GRID +
        LANDSCAPE_ADJUST_WIDTH

internal class MiiPaletteGeometry(
    val panel: Rect,
    val pitch: Float,
    val swatch: Float,
    val columns: Int,
) {
    fun swatchAt(position: Int): Rect {
        val column = position % columns
        val row = position / columns
        val left = panel.left + PALETTE_EDGE_X + column * pitch
        val top = panel.top + PALETTE_EDGE_Y + row * pitch
        return Rect(left, top, left + swatch, top + swatch)
    }
}

internal class MiiSingleScreenLayout(
    val landscape: Boolean,
    val width: Float,
    val height: Float,
    val insetTop: Float,
    val insetBottom: Float,
    val insetStart: Float,
    val insetEnd: Float,
    val stage: Rect,
    val preview: Rect,
    val panel: Rect,
    val categories: Rect,
    val colors: Rect,
    val traits: Rect,
    val adjustments: Rect,
    val traitColumns: Int,
) {
    val overlayRegion: Rect
        get() = if (landscape) panel else Rect(0f, categories.top, width, height)

    val backButton: Rect
        get() {
            val left = insetStart + SINGLE_CONTROL_MARGIN
            val top = insetTop + SINGLE_CONTROL_TOP
            return Rect(left, top, left + SINGLE_CONTROL_SIZE, top + SINGLE_CONTROL_SIZE)
        }

    fun savePill(buttonWidth: Float): Rect {
        val right = width - insetEnd - SINGLE_CONTROL_MARGIN
        val top = insetTop + SINGLE_CONTROL_TOP
        return Rect(right - buttonWidth, top, right, top + SINGLE_CONTROL_SIZE)
    }

    val noticeTop: Float
        get() = insetTop + SINGLE_CONTROL_TOP + SINGLE_CONTROL_SIZE + 20f

    fun adjustButton(index: Int): Rect {
        if (landscape) {
            val buttonHeight = minOf(LANDSCAPE_ADJUST_HEIGHT, (adjustments.height - 4f * SINGLE_ROW_GAP) / 5f)
            val gap = (adjustments.height - 5f * buttonHeight) / 4f
            val top = adjustments.top + index * (buttonHeight + gap)
            return Rect(adjustments.left, top, width, top + buttonHeight)
        }
        val buttonWidth = (adjustments.width - 4f * SINGLE_ADJUST_GAP) / 5f
        val left = adjustments.left + index * (buttonWidth + SINGLE_ADJUST_GAP)
        return Rect(left, adjustments.top, left + buttonWidth, adjustments.bottom)
    }

    fun expandedAdjustment(index: Int, vertical: Boolean): Rect {
        val button = adjustButton(index)
        return when {
            landscape && vertical -> Rect(button.left, button.top, width, height - insetBottom - LANDSCAPE_SLIDER_BOTTOM_GAP)
            landscape -> Rect(traits.left - 0.5f, button.top, width, button.bottom)
            vertical -> Rect(button.left, colors.top, button.right, button.bottom)
            else -> Rect(adjustments.left, button.top, adjustments.right, button.bottom)
        }
    }

    fun palette(count: Int, columns: Int): MiiPaletteGeometry {
        val rows = ceil(count / columns.toFloat()).toInt().coerceAtLeast(1)
        val region = if (landscape) {
            Rect(traits.left - 26f, insetTop, width - insetEnd, height - insetBottom)
        } else {
            Rect(insetStart + SINGLE_SIDE_MARGIN, categories.bottom, width - insetEnd - SINGLE_SIDE_MARGIN, height - insetBottom)
        }
        val widthSpan = (columns - 1) + PALETTE_SWATCH_SHARE
        val heightSpan = (rows - 1) + PALETTE_SWATCH_SHARE
        val pitch = minOf(
            (region.width - 2f * PALETTE_EDGE_X - PALETTE_SLACK) / widthSpan,
            (region.height - 2f * PALETTE_EDGE_Y - PALETTE_SLACK - 2f * SINGLE_ROW_GAP) / heightSpan,
            PALETTE_MAX_PITCH,
        )
        val panelWidth = 2f * PALETTE_EDGE_X + widthSpan * pitch + PALETTE_SLACK
        val panelHeight = 2f * PALETTE_EDGE_Y + heightSpan * pitch + PALETTE_SLACK
        val left = region.left + (region.width - panelWidth) / 2f
        val top = region.top + (region.height - panelHeight) / 2f
        return MiiPaletteGeometry(
            panel = Rect(left, top, left + panelWidth, top + panelHeight),
            pitch = pitch,
            swatch = pitch * PALETTE_SWATCH_SHARE,
            columns = columns,
        )
    }
}

internal fun miiSingleScreenLayout(
    width: Float,
    height: Float,
    insetTop: Float,
    insetBottom: Float,
    insetStart: Float,
    insetEnd: Float,
): MiiSingleScreenLayout =
    if (miiEditorLandscape(width, height)) {
        landscapeLayout(width, height, insetTop, insetBottom, insetStart, insetEnd)
    } else {
        portraitLayout(width, height, insetTop, insetBottom, insetStart, insetEnd)
    }

private fun portraitLayout(
    width: Float,
    height: Float,
    insetTop: Float,
    insetBottom: Float,
    insetStart: Float,
    insetEnd: Float,
): MiiSingleScreenLayout {
    val left = insetStart + SINGLE_SIDE_MARGIN
    val right = width - insetEnd - SINGLE_SIDE_MARGIN
    val adjustBottom = height - insetBottom - SINGLE_BOTTOM_PADDING
    val adjustTop = adjustBottom - SINGLE_ADJUST_HEIGHT
    val fixedBelowStage = SINGLE_STRIP_HEIGHT + SINGLE_ROW_GAP + SINGLE_COLOR_ROW_HEIGHT + SINGLE_ROW_GAP +
        SINGLE_ROW_GAP + SINGLE_ADJUST_HEIGHT + SINGLE_BOTTOM_PADDING + insetBottom
    val traitMinimum = SINGLE_TRAIT_MIN_ROWS * (TRAIT_PILL_HEIGHT + TRAIT_ROW_GAP) + 2f * TRAIT_GRID_PADDING
    val stageCap = height - fixedBelowStage - traitMinimum
    val stageBottom = (height * SINGLE_STAGE_SHARE)
        .coerceAtMost(insetTop + width)
        .coerceAtMost(stageCap)
        .coerceAtLeast(insetTop + SINGLE_MIN_PREVIEW_HEIGHT)
    val stripBottom = stageBottom + SINGLE_STRIP_HEIGHT
    val colorsTop = stripBottom + SINGLE_ROW_GAP
    val colorsBottom = colorsTop + SINGLE_COLOR_ROW_HEIGHT
    val traitsTop = colorsBottom + SINGLE_ROW_GAP
    val traitsBottom = (adjustTop - SINGLE_ROW_GAP).coerceAtLeast(traitsTop)
    val gridWidth = right - left
    val columns = floor((gridWidth + TRAIT_PILL_GAP) / (TRAIT_PILL_WIDTH + TRAIT_PILL_GAP)).toInt().coerceAtLeast(2)
    return MiiSingleScreenLayout(
        landscape = false,
        width = width,
        height = height,
        insetTop = insetTop,
        insetBottom = insetBottom,
        insetStart = insetStart,
        insetEnd = insetEnd,
        stage = Rect(0f, 0f, width, stageBottom),
        preview = Rect(insetStart, insetTop, width - insetEnd, stageBottom),
        panel = Rect(0f, stripBottom, width, height),
        categories = Rect(0f, stageBottom, width, stripBottom),
        colors = Rect(left, colorsTop, right, colorsBottom),
        traits = Rect(left, traitsTop, right, traitsBottom),
        adjustments = Rect(left, adjustTop, right, adjustBottom),
        traitColumns = columns,
    )
}

private fun landscapeLayout(
    width: Float,
    height: Float,
    insetTop: Float,
    insetBottom: Float,
    insetStart: Float,
    insetEnd: Float,
): MiiSingleScreenLayout {
    val minimumPreview = maxOf(LANDSCAPE_MIN_PREVIEW, height * LANDSCAPE_PREVIEW_SHARE)
    val columns = (LANDSCAPE_MAX_TRAIT_COLUMNS downTo 2).firstOrNull { columns ->
        width - insetStart - insetEnd - landscapePanelWidth(columns) >= minimumPreview
    } ?: 2
    val panelLeft = width - insetEnd - landscapePanelWidth(columns)
    val contentTop = insetTop
    val contentBottom = height - insetBottom
    val traitsLeft = panelLeft + RAIL_WIDTH + RAIL_TO_GRID
    val traitsRight = traitsLeft + traitGridWidth(columns)
    val colorsLeft = traitsRight + RAIL_TO_GRID
    val adjustLeft = width - insetEnd - LANDSCAPE_ADJUST_WIDTH
    return MiiSingleScreenLayout(
        landscape = true,
        width = width,
        height = height,
        insetTop = insetTop,
        insetBottom = insetBottom,
        insetStart = insetStart,
        insetEnd = insetEnd,
        stage = Rect(0f, 0f, panelLeft, height),
        preview = Rect(insetStart, insetTop, panelLeft, height),
        panel = Rect(panelLeft, 0f, width, height),
        categories = Rect(panelLeft, 0f, panelLeft + RAIL_WIDTH, height),
        colors = Rect(colorsLeft, contentTop, colorsLeft + COLOR_COLUMN_WIDTH, contentBottom),
        traits = Rect(traitsLeft, contentTop, traitsRight, contentBottom),
        adjustments = Rect(adjustLeft, contentTop, width - insetEnd, contentBottom),
        traitColumns = columns,
    )
}
