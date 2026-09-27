package com.pocketpass.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.WidgetSlot
import com.pocketpass.app.model.editingWidgetDesign
import com.pocketpass.app.model.widgetPreviewSnapshot
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignAnchor
import com.pocketpass.app.ui.DesignBox
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.anchoredBounds
import com.pocketpass.app.ui.components.EntranceMotion
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.PocketKey
import com.pocketpass.app.ui.components.PocketKeyboard
import com.pocketpass.app.ui.components.PocketKeyboardLayout
import com.pocketpass.app.ui.components.PocketKeyboardPalette
import com.pocketpass.app.ui.components.PocketPanel
import com.pocketpass.app.ui.components.TYPING_CARET_INLINE_ID
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.components.pocketBorder
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.pocketShadow
import com.pocketpass.app.ui.components.rememberPocketAssetBytes
import com.pocketpass.app.ui.components.typingCaretInline
import com.pocketpass.app.ui.controller.ControllerFocusViewport
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.LocalControllerFocusViewport
import com.pocketpass.app.ui.controller.controllerFocusBarrier
import com.pocketpass.app.ui.controller.controllerFocusViewport
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.theme.pocketPalette
import com.pocketpass.app.ui.widget.settingsGlyph
import com.pocketpass.app.ui.widget.widgetGlyph
import com.pocketpass.app.widget.WidgetBlock
import com.pocketpass.app.widget.WidgetBlockGroup
import com.pocketpass.app.widget.WidgetBlockValues
import com.pocketpass.app.widget.WidgetDesign
import com.pocketpass.app.widget.WidgetFrames
import com.pocketpass.app.widget.WidgetLayout
import com.pocketpass.app.widget.WidgetSize
import com.pocketpass.app.widget.WidgetSnapshot
import com.pocketpass.app.widget.WidgetTextAlign
import com.pocketpass.app.widget.WidgetTextBox
import com.pocketpass.app.widget.theme
import kotlin.math.roundToInt
import kotlin.time.Clock
import org.jetbrains.compose.resources.decodeToImageBitmap

internal const val WIDGET_PICKER_FOCUS_LAYER = 15
private const val WIDGET_DELETE_FOCUS_LAYER = 20
internal const val WIDGET_BUTTON_HEIGHT = 166f
internal const val WIDGET_BANNER_HEIGHT = 160f
internal const val WIDGET_MESSAGE_HEIGHT = 130f
private const val WIDGET_SIZE_PANEL_HEIGHT = THEME_PANEL_HEIGHT

private val WidgetGreenBorder = Color(0xFF3CBC29)
private val WidgetRedBorder = Color(0xFFC24B4B)

@Composable
internal fun WidgetsPanel(
    metrics: DesignMetrics,
    y: Float,
    designCount: Int,
    onClick: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "widgets",
        onClick = onClick,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsWidgets,
            title = "Widgets",
            subtitle = when (designCount) {
                0 -> "Create home-screen widgets"
                1 -> "1 design · make more or edit it"
                else -> "$designCount designs · make more or edit them"
            },
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

@Composable
internal fun WidgetsBottom(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    BottomPage(entrance = EntranceMotion.None) { metrics ->
        val assigning = state.widgetMaker.assigningAppWidgetId != null
        SubpageHeader(
            metrics = metrics,
            title = "Widgets",
            subtitle = if (assigning) "Pick a design for your new widget." else "Design one, then add it to your home screen.",
            backTag = "widgets_back",
        ) { dispatch(PocketPassEvent.Back) }
        val snapshot = remember(state) { state.widgetPreviewSnapshot(Clock.System.now().toEpochMilliseconds()) }
        val scroll = rememberScrollState()
        val belowHeader = remember(metrics) { BelowSubpageHeaderShape(metrics) }
        val focusViewport = rememberSubpageFocusViewport(metrics, belowHeader)
        val rows = widgetListRows(state)
        DesignBox(
            metrics,
            0f,
            0f,
            1240f,
            1080f,
            DesignAnchor.Stretch,
            DesignAnchor.Stretch,
            modifier = Modifier
                .clip(belowHeader)
                .controllerFocusViewport(focusViewport)
                .verticalScroll(scroll)
                .testTag("widgets_scroll"),
        ) {
            CompositionLocalProvider(LocalControllerFocusViewport provides focusViewport) {
                Box(
                    modifier = Modifier
                        .padding(top = metrics.dp(SUBPAGE_CONTENT_TOP))
                        .requiredWidth(metrics.dp(1240f + 2f * metrics.overscanX))
                        .requiredHeight(metrics.dp(rows.totalHeight)),
                ) {
                    rows.entries.forEachIndexed { index, row ->
                        val panelY = row.y - SUBPAGE_CONTENT_TOP
                        SubpagePanelPop(y = row.y, height = row.height, order = index + 1) {
                            when (val kind = row.kind) {
                                WidgetListRow.Banner -> WidgetAssignBanner(metrics, panelY)
                                WidgetListRow.Empty -> WidgetEmptyPanel(metrics, panelY)
                                is WidgetListRow.Message -> WidgetMessagePanel(metrics, panelY, kind.text) {
                                    dispatch(PocketPassEvent.DismissWidgetMessage)
                                }
                                is WidgetListRow.Design -> WidgetDesignRow(
                                    metrics = metrics,
                                    y = panelY,
                                    design = kind.design,
                                    snapshot = snapshot,
                                    ownAvatar = state.profile?.avatar,
                                    assigning = assigning,
                                ) {
                                    if (assigning) {
                                        dispatch(PocketPassEvent.AssignWidgetDesign(kind.design.id))
                                    } else {
                                        dispatch(PocketPassEvent.OpenWidgetEditor(kind.design.id))
                                    }
                                }
                                WidgetListRow.NewButton -> WidgetActionButton(
                                    metrics = metrics,
                                    y = panelY,
                                    label = "NEW WIDGET",
                                    fill = greenButtonBrush(),
                                    borderColor = WidgetGreenBorder,
                                    tag = "widget_new",
                                ) { dispatch(PocketPassEvent.CreateWidgetDesign) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberSubpageFocusViewport(metrics: DesignMetrics, shape: BelowSubpageHeaderShape): ControllerFocusViewport {
    val density = LocalDensity.current
    return remember(metrics, shape, density) {
        ControllerFocusViewport(
            shape = shape,
            topInset = with(density) { metrics.dp(SUBPAGE_CONTENT_TOP).toPx() },
        )
    }
}

internal sealed interface WidgetListRow {
    data object Banner : WidgetListRow
    data object Empty : WidgetListRow
    data class Message(val text: String) : WidgetListRow
    data class Design(val design: WidgetDesign) : WidgetListRow
    data object NewButton : WidgetListRow
}

internal class PlacedRow(val kind: WidgetListRow, val y: Float, val height: Float)

internal class PlacedRows(val entries: List<PlacedRow>, val totalHeight: Float)

internal fun widgetListRows(state: PocketPassUiState): PlacedRows {
    val rows = mutableListOf<PlacedRow>()
    var y = SUBPAGE_FIRST_ROW_Y
    fun place(kind: WidgetListRow, height: Float) {
        rows += PlacedRow(kind, y, height)
        y += height + SETTINGS_PANEL_GAP
    }
    if (state.widgetMaker.assigningAppWidgetId != null) place(WidgetListRow.Banner, WIDGET_BANNER_HEIGHT)
    state.widgetMaker.message?.let { place(WidgetListRow.Message(it), WIDGET_MESSAGE_HEIGHT) }
    if (state.widgetDesigns.isEmpty()) place(WidgetListRow.Empty, SETTINGS_ROW_HEIGHT)
    state.widgetDesigns.forEach { place(WidgetListRow.Design(it), SETTINGS_ROW_HEIGHT) }
    place(WidgetListRow.NewButton, WIDGET_BUTTON_HEIGHT)
    return PlacedRows(rows, y - SUBPAGE_CONTENT_TOP)
}

@Composable
internal fun WidgetEditorBottom(
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    designId: String,
) {
    val design = state.widgetDesigns.firstOrNull { it.id == designId }
    BottomPage(entrance = EntranceMotion.None) { metrics ->
        SubpageHeader(
            metrics = metrics,
            title = design?.name ?: "Widget",
            subtitle = design?.let { "${it.size.label} · ${it.size.cellsLabel} cells" } ?: "This widget was deleted.",
            backTag = "widget_editor_back",
        ) { dispatch(PocketPassEvent.Back) }
        if (design == null) return@BottomPage
        val snapshot = remember(state) { state.widgetPreviewSnapshot(Clock.System.now().toEpochMilliseconds()) }
        val scroll = rememberScrollState()
        val belowHeader = remember(metrics) { BelowSubpageHeaderShape(metrics) }
        val focusViewport = rememberSubpageFocusViewport(metrics, belowHeader)
        val rows = widgetEditorRows(design, state.widgetMaker.message)
        DesignBox(
            metrics,
            0f,
            0f,
            1240f,
            1080f,
            DesignAnchor.Stretch,
            DesignAnchor.Stretch,
            modifier = Modifier
                .clip(belowHeader)
                .controllerFocusViewport(focusViewport)
                .verticalScroll(scroll)
                .testTag("widget_editor_scroll"),
        ) {
            CompositionLocalProvider(LocalControllerFocusViewport provides focusViewport) {
                Box(
                    modifier = Modifier
                        .padding(top = metrics.dp(SUBPAGE_CONTENT_TOP))
                        .requiredWidth(metrics.dp(1240f + 2f * metrics.overscanX))
                        .requiredHeight(metrics.dp(rows.totalHeight)),
                ) {
                    rows.entries.forEachIndexed { index, row ->
                        val panelY = row.y - SUBPAGE_CONTENT_TOP
                        SubpagePanelPop(y = row.y, height = row.height, order = index + 1) {
                            when (val kind = row.kind) {
                                WidgetEditorRow.Preview -> WidgetPreviewPanel(
                                    metrics = metrics,
                                    y = panelY,
                                    design = design,
                                    snapshot = snapshot,
                                    ownAvatar = state.profile?.avatar,
                                )
                                WidgetEditorRow.Name -> WidgetRenamePanel(metrics, panelY, design.name) {
                                    dispatch(PocketPassEvent.OpenWidgetRename)
                                }
                                WidgetEditorRow.Size -> WidgetSizePanel(metrics, panelY, design.size) { size ->
                                    dispatch(PocketPassEvent.UpdateWidgetDesign(design.withSize(size, design.updatedAtEpochMillis)))
                                }
                                is WidgetEditorRow.Slot -> WidgetSlotPanel(
                                    metrics = metrics,
                                    y = panelY,
                                    slot = kind.slot,
                                    block = design.blockAt(kind.slot),
                                    snapshot = snapshot,
                                ) { dispatch(PocketPassEvent.OpenWidgetBlockPicker(kind.slot)) }
                                is WidgetEditorRow.Message -> WidgetMessagePanel(metrics, panelY, kind.text) {
                                    dispatch(PocketPassEvent.DismissWidgetMessage)
                                }
                                WidgetEditorRow.Pin -> WidgetActionButton(
                                    metrics = metrics,
                                    y = panelY,
                                    label = "ADD TO HOME SCREEN",
                                    fill = greenButtonBrush(),
                                    borderColor = WidgetGreenBorder,
                                    tag = "widget_pin",
                                ) { dispatch(PocketPassEvent.PinWidgetDesign(design.id)) }
                                WidgetEditorRow.Delete -> WidgetActionButton(
                                    metrics = metrics,
                                    y = panelY,
                                    label = "DELETE WIDGET",
                                    fill = redButtonBrush(),
                                    borderColor = WidgetRedBorder,
                                    tag = "widget_delete",
                                ) { dispatch(PocketPassEvent.OpenWidgetDeletePrompt) }
                            }
                        }
                    }
                }
            }
        }
        WidgetBlockPickerOverlay(metrics, state, dispatch)
        WidgetRenameOverlay(metrics, state, dispatch)
        if (state.widgetMaker.deletePromptVisible) {
            WidgetDeleteDialog(metrics, design, dispatch)
        }
    }
}

internal sealed interface WidgetEditorRow {
    data object Preview : WidgetEditorRow
    data object Name : WidgetEditorRow
    data object Size : WidgetEditorRow
    data class Slot(val slot: WidgetSlot) : WidgetEditorRow
    data class Message(val text: String) : WidgetEditorRow
    data object Pin : WidgetEditorRow
    data object Delete : WidgetEditorRow
}

internal class PlacedEditorRow(val kind: WidgetEditorRow, val y: Float, val height: Float)

internal class PlacedEditorRows(val entries: List<PlacedEditorRow>, val totalHeight: Float)

internal fun widgetEditorRows(design: WidgetDesign, message: String?): PlacedEditorRows {
    val rows = mutableListOf<PlacedEditorRow>()
    var y = SUBPAGE_FIRST_ROW_Y
    fun place(kind: WidgetEditorRow, height: Float) {
        rows += PlacedEditorRow(kind, y, height)
        y += height + SETTINGS_PANEL_GAP
    }
    place(WidgetEditorRow.Preview, widgetPreviewPanelHeight(design.size))
    place(WidgetEditorRow.Name, SETTINGS_ROW_HEIGHT)
    place(WidgetEditorRow.Size, WIDGET_SIZE_PANEL_HEIGHT)
    place(WidgetEditorRow.Slot(WidgetSlot.Hero), SETTINGS_ROW_HEIGHT)
    repeat(design.size.tileCount) { index -> place(WidgetEditorRow.Slot(WidgetSlot.Tile(index)), SETTINGS_ROW_HEIGHT) }
    message?.let { place(WidgetEditorRow.Message(it), WIDGET_MESSAGE_HEIGHT) }
    place(WidgetEditorRow.Pin, WIDGET_BUTTON_HEIGHT)
    place(WidgetEditorRow.Delete, WIDGET_BUTTON_HEIGHT)
    return PlacedEditorRows(rows, y - SUBPAGE_CONTENT_TOP)
}

internal fun WidgetDesign.blockAt(slot: WidgetSlot): WidgetBlock? = when (slot) {
    WidgetSlot.Hero -> hero
    is WidgetSlot.Tile -> tiles.getOrNull(slot.index)
}

internal fun WidgetSlot.label(): String = when (this) {
    WidgetSlot.Hero -> "Big slot"
    is WidgetSlot.Tile -> "Tile ${index + 1}"
}

internal fun WidgetSlot.pickerSubtitle(): String = when (this) {
    WidgetSlot.Hero -> "for the big slot"
    is WidgetSlot.Tile -> "for Tile ${index + 1}"
}

internal fun widgetPreviewSize(size: WidgetSize): Pair<Float, Float> = when (size) {
    WidgetSize.Mini -> 300f to 300f
    WidgetSize.Small -> 360f to 360f
    WidgetSize.Wide -> 720f to 360f
    WidgetSize.Tall -> 300f to 600f
}

internal const val WIDGET_PREVIEW_PADDING = 50f

internal fun widgetPreviewPanelHeight(size: WidgetSize): Float =
    widgetPreviewSize(size).second + 2f * WIDGET_PREVIEW_PADDING

@Composable
internal fun WidgetPreviewPanel(
    metrics: DesignMetrics,
    y: Float,
    design: WidgetDesign,
    snapshot: WidgetSnapshot,
    ownAvatar: AvatarReference?,
) {
    val (width, height) = widgetPreviewSize(design.size)
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = widgetPreviewPanelHeight(design.size),
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 118f,
        fillBrush = greyPanelBrush(),
        tag = "widget_preview",
        onControllerActivate = {},
    ) {
        WidgetPreview(
            metrics = metrics,
            design = design,
            snapshot = snapshot,
            ownAvatar = ownAvatar,
            x = (1140f - width) / 2f + metrics.overscanX,
            y = WIDGET_PREVIEW_PADDING,
            width = width,
            height = height,
        )
    }
}

@Composable
internal fun WidgetDesignRow(
    metrics: DesignMetrics,
    y: Float,
    design: WidgetDesign,
    snapshot: WidgetSnapshot,
    ownAvatar: AvatarReference?,
    assigning: Boolean,
    onClick: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "widget_design_${design.id}",
        onClick = onClick,
    ) {
        val (previewWidth, previewHeight) = when (design.size) {
            WidgetSize.Mini -> 130f to 130f
            WidgetSize.Small -> 150f to 150f
            WidgetSize.Wide -> 260f to 130f
            WidgetSize.Tall -> 80f to 160f
        }
        WidgetPreview(
            metrics = metrics,
            design = design,
            snapshot = snapshot,
            ownAvatar = ownAvatar,
            x = 43f + (260f - previewWidth) / 2f,
            y = 30f + (160f - previewHeight) / 2f,
            width = previewWidth,
            height = previewHeight,
        )
        Text(
            text = design.name,
            modifier = Modifier.anchoredBounds(metrics, 340f, 42f, 640f, 76f, DesignAnchor.Stretch),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(64f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (assigning) "Use this design" else "${design.size.label} · ${design.summary()}",
            modifier = Modifier.anchoredBounds(metrics, 340f, 117f, 680f, 55f, DesignAnchor.Stretch),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

@Composable
internal fun WidgetRenamePanel(
    metrics: DesignMetrics,
    y: Float,
    name: String,
    onClick: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = "widget_rename",
        onClick = onClick,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsEditName,
            title = name,
            subtitle = "Tap to rename this widget",
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

private const val WIDGET_SIZE_CHOICE_WIDTH = 226f
private const val WIDGET_SIZE_CHOICE_PITCH = 270f

private fun widgetSizeChoiceX(size: WidgetSize): Float = 52f + WidgetSize.entries.indexOf(size) * WIDGET_SIZE_CHOICE_PITCH

private fun widgetSizeTag(size: WidgetSize) = "widget_size_${size.name.lowercase()}"

@Composable
internal fun WidgetSizePanel(
    metrics: DesignMetrics,
    y: Float,
    selected: WidgetSize,
    onSelect: (WidgetSize) -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = WIDGET_SIZE_PANEL_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 118f,
        fillBrush = greyPanelBrush(),
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsWidgets,
            title = "Size",
            subtitle = "${selected.cellsLabel} cells on your home screen",
        )
        Box(
            Modifier
                .anchoredBounds(metrics, 52f, 228.65f, 1036f, 9f, DesignAnchor.Stretch)
                .clip(RoundedCornerShape(metrics.dp(4.5f)))
                .background(pocketPalette.borderSoft),
        )
        WidgetSize.entries.forEach { size ->
            val chosen = size == selected
            val shape = RoundedCornerShape(metrics.dp(52.5f))
            Box(
                modifier = Modifier
                    .anchoredBounds(metrics, widgetSizeChoiceX(size), 297.65f, WIDGET_SIZE_CHOICE_WIDTH, 105f, DesignAnchor.Center, DesignAnchor.Center)
                    .clip(shape)
                    .testTag(widgetSizeTag(size))
                    .controllerTarget(widgetSizeTag(size), cornerRadius = 52.5f) { onSelect(size) }
                    .clickable(
                        interactionSource = remember(size) { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(size) }
                    .background(if (chosen) greenButtonBrush() else greyButtonBrush())
                    .themeChoiceBorder(metrics, if (chosen) ThemeChoiceGreen else ThemeChoiceGrey)
                    .then(if (chosen) Modifier.pocketBorder(metrics.dp(4f), Color.White.copy(alpha = 0.34f), shape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = size.label,
                    color = Color.White,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = metrics.sp(42f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun WidgetSlotPanel(
    metrics: DesignMetrics,
    y: Float,
    slot: WidgetSlot,
    block: WidgetBlock?,
    snapshot: WidgetSnapshot,
    onClick: () -> Unit,
) {
    val tag = when (slot) {
        WidgetSlot.Hero -> "widget_slot_hero"
        is WidgetSlot.Tile -> "widget_slot_tile_${slot.index}"
    }
    val now = remember { Clock.System.now().toEpochMilliseconds() }
    val subtitle = when {
        block == null -> "Empty · tap to choose"
        slot == WidgetSlot.Hero -> block.label
        else -> WidgetBlockValues.tile(block, snapshot, now).let { "${it.label} · ${it.value}" }
    }
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
        tag = tag,
        onClick = onClick,
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = block?.settingsGlyph() ?: Assets.SettingsWidgets,
            title = slot.label(),
            subtitle = subtitle,
        )
        FigmaAsset(
            resource = Assets.SettingsArrow,
            colorFilter = chevronTint(),
            modifier = Modifier.anchoredBounds(metrics, 1028f, 75.637f, 40.372f, 68.725f, DesignAnchor.End),
        )
    }
}

@Composable
internal fun WidgetActionButton(
    metrics: DesignMetrics,
    y: Float,
    label: String,
    fill: Brush,
    borderColor: Color,
    tag: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(118f))
    Box(
        modifier = Modifier
            .anchoredBounds(metrics, 102f, y, 1036f, WIDGET_BUTTON_HEIGHT, DesignAnchor.Stretch)
            .clip(shape)
            .pocketFrame(fill, metrics.dp(20.152f), borderColor, shape)
            .testTag(tag)
            .controllerTarget(tag, cornerRadius = 118f) { onClick() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(48f),
            maxLines = 1,
        )
    }
}

@Composable
internal fun WidgetAssignBanner(metrics: DesignMetrics, y: Float) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = WIDGET_BANNER_HEIGHT,
        borderColor = pocketPalette.tealBorder,
        borderWidth = 15f,
        radius = 80f,
        fillBrush = tealPanelBrush(),
    ) {
        Box(
            modifier = Modifier.anchoredBounds(metrics, 60f, 0f, 1020f, WIDGET_BANNER_HEIGHT, DesignAnchor.Stretch),
            contentAlignment = Alignment.Center,
        ) {
        Text(
            text = "Your new home-screen widget needs a design. Pick one below.",
            color = pocketPalette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(40f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        }
    }
}

@Composable
internal fun WidgetEmptyPanel(metrics: DesignMetrics, y: Float) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = SETTINGS_ROW_HEIGHT,
        borderColor = pocketPalette.borderGrey,
        borderWidth = 20.152f,
        radius = 110f,
        fillBrush = greyPanelBrush(),
    ) {
        SettingsHeading(
            metrics = metrics,
            icon = Assets.SettingsWidgets,
            title = "No widgets yet",
            subtitle = "Create your first widget below",
        )
    }
}

@Composable
internal fun WidgetMessagePanel(
    metrics: DesignMetrics,
    y: Float,
    message: String,
    onDismiss: () -> Unit,
) {
    PocketPanel(
        metrics = metrics,
        x = 50f,
        y = y,
        width = 1140f,
        height = WIDGET_MESSAGE_HEIGHT,
        borderColor = pocketPalette.tealBorder,
        borderWidth = 15f,
        radius = 65f,
        fillBrush = tealPanelBrush(),
        tag = "widget_message",
        onClick = onDismiss,
    ) {
        Box(
            modifier = Modifier.anchoredBounds(metrics, 60f, 0f, 1020f, WIDGET_MESSAGE_HEIGHT, DesignAnchor.Stretch),
            contentAlignment = Alignment.Center,
        ) {
        Text(
            text = message,
            color = pocketPalette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(36f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        }
    }
}

@Composable
internal fun tealPanelBrush(): Brush {
    val palette = pocketPalette
    return Brush.verticalGradient(
        colorStops = arrayOf(
            0f to palette.surface,
            0.62f to palette.surface,
            1f to palette.tint(Color(0xFFBDF8CB)),
        ),
    )
}

@Composable
internal fun WidgetPreview(
    metrics: DesignMetrics,
    design: WidgetDesign,
    snapshot: WidgetSnapshot,
    ownAvatar: AvatarReference?,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
) {
    val theme = design.theme()
    val now = remember { Clock.System.now().toEpochMilliseconds() }
    val heroBlock = design.hero
    val heroText = heroBlock?.let { WidgetBlockValues.heroNumber(it, snapshot, now) } ?: "PocketPass"
    val tiles = design.tiles.filterNotNull()
    val frames = remember(design, width, height, heroText) {
        WidgetLayout.frames(design.size, width, height, tiles.size, heroText.length)
    }
    val shape = RoundedCornerShape(metrics.dp(frames.cornerRadius))
    val background = Color(theme.background)
    val ink = Color(theme.ink)
    val soft = Color(theme.softInk)
    Box(
        modifier = Modifier
            .designBounds(metrics, x, y, width, height)
            .clip(shape)
            .background(Brush.verticalGradient(colorStops = arrayOf(0f to background, 0.5f to background, 1f to Color.White))),
    ) {
        PreviewPattern(metrics, frames)
        Box(Modifier.fillMaxSize().border(metrics.dp(frames.border), Color.White.copy(alpha = 0.38f), shape))
        val glyph = frames.heroGlyph
        when (heroBlock) {
            null -> Unit
            WidgetBlock.Profile -> Box(Modifier.designBounds(metrics, glyph.x, glyph.y, glyph.width, glyph.width)) {
                PreviewAvatar(metrics, ownAvatar, glyph.width)
            }
            else -> FigmaAsset(
                heroBlock.widgetGlyph(),
                Modifier.designBounds(metrics, glyph.x, glyph.y, glyph.width, glyph.width * WIDGET_GLYPH_ASPECT),
            )
        }
        PreviewText(metrics, heroText, frames.heroValue, ink)
        frames.heroLabel?.let { PreviewText(metrics, heroBlock?.let { block -> WidgetBlockValues.heroLabel(block, snapshot) } ?: "", it, soft) }
        frames.divider?.let { divider ->
            Box(
                Modifier
                    .designBounds(metrics, divider.x, divider.y, divider.width, divider.height)
                    .clip(RoundedCornerShape(metrics.dp(minOf(divider.width, divider.height) / 2f)))
                    .background(Color.White.copy(alpha = 0.61f)),
            )
        }
        tiles.forEachIndexed { index, block ->
            val frame = frames.tiles[index]
            FigmaAsset(
                block.widgetGlyph(),
                Modifier.designBounds(metrics, frame.glyph.x, frame.glyph.y, frame.glyph.width, frame.glyph.width * WIDGET_GLYPH_ASPECT),
            )
            val value = if (frame.label != null) {
                WidgetBlockValues.tile(block, snapshot, now).value
            } else {
                WidgetBlockValues.tileNumber(block, snapshot, now)
            }
            PreviewText(metrics, value, frame.value, ink)
            frame.label?.let { PreviewText(metrics, block.shortLabel, it, soft) }
        }
    }
}

internal const val WIDGET_GLYPH_ASPECT = 123.241f / 119f
private const val WIDGET_PATTERN_OPACITY = 0.37f

@Composable
private fun PreviewPattern(metrics: DesignMetrics, frames: WidgetFrames) {
    val bytes = rememberPocketAssetBytes(Assets.WidgetPattern) ?: return
    val image = remember(bytes) { bytes.decodeToImageBitmap() }
    val patternSize = metrics.dp(frames.patternTile)
    Box(
        Modifier.fillMaxSize().drawBehind {
            val side = patternSize.toPx().roundToInt()
            drawImage(
                image = image,
                dstOffset = IntOffset(((size.width - side) / 2f).roundToInt(), ((size.height - side) / 2f).roundToInt()),
                dstSize = IntSize(side, side),
                alpha = WIDGET_PATTERN_OPACITY,
                blendMode = BlendMode.ColorBurn,
            )
        },
    )
}

@Composable
private fun PreviewText(metrics: DesignMetrics, text: String, box: WidgetTextBox, color: Color) {
    val estimated = text.length * 0.62f * box.fontSize
    val fontSize = if (estimated > box.rect.width && estimated > 0f) box.fontSize * box.rect.width / estimated else box.fontSize
    Box(
        modifier = Modifier.designBounds(metrics, box.rect.x, box.rect.y, box.rect.width, box.rect.height),
        contentAlignment = when (box.align) {
            WidgetTextAlign.Start -> Alignment.CenterStart
            WidgetTextAlign.Center -> Alignment.Center
            WidgetTextAlign.End -> Alignment.CenterEnd
        },
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = Rubik,
            fontWeight = if (box.heavy) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = metrics.sp(fontSize),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun PreviewAvatar(metrics: DesignMetrics, avatar: AvatarReference?, size: Float) {
    val palette = pocketPalette
    val ring = size * (22f / 449f)
    Box(
        modifier = Modifier
            .requiredSize(metrics.dp(size))
            .clip(CircleShape)
            .background(palette.surface)
            .pocketBorder(metrics.dp(ring), palette.tealBorder, CircleShape),
    ) {
        DynamicAvatar(
            avatar = avatar,
            fallbackResource = Assets.HomeAvatarPetah,
            modifier = Modifier
                .fillMaxSize()
                .padding(metrics.dp(ring))
                .clip(CircleShape),
        )
    }
}

@Composable
internal fun WidgetBlockPickerOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val slot = state.widgetMaker.blockPicker ?: return
    val design = state.editingWidgetDesign ?: return
    val current = design.blockAt(slot)
    val palette = pocketPalette
    val focus = LocalControllerFocus.current
    LaunchedEffect(slot) {
        focus?.focus(widgetBlockTag(current), reveal = false)
    }
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .background(palette.scrim)
            .testTag("widget_picker_overlay")
            .controllerFocusBarrier("widget_picker_overlay", layer = WIDGET_PICKER_FOCUS_LAYER)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.CloseWidgetBlockPicker) },
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 74f, 1080f, 940f)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 60f, 1080f, 940f)
            .graphicsLayer { translationY = entrance.value }
            .clip(panelShape)
            .pocketFrame(tealPanelBrush(), metrics.dp(15f), palette.tealBorder, panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("widget_picker_panel"),
    ) {
        Text(
            text = "Choose a block",
            modifier = Modifier.designBounds(metrics, 58f, 46f, 760f, 94f),
            color = palette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(72f),
            maxLines = 1,
        )
        Text(
            text = slot.pickerSubtitle(),
            modifier = Modifier.designBounds(metrics, 58f, 150f, 900f, 50f),
            color = palette.tealBorder,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(36f),
            maxLines = 1,
        )
        CloseCross(metrics, "close_widget_picker", WIDGET_PICKER_FOCUS_LAYER) {
            dispatch(PocketPassEvent.CloseWidgetBlockPicker)
        }
        val scroll = rememberScrollState()
        val viewport = remember { ControllerFocusViewport() }
        val choices = widgetBlockChoices(slot)
        Box(
            Modifier
                .designBounds(metrics, 58f, 220f, 964f, 690f)
                .clip(RoundedCornerShape(metrics.dp(40f)))
                .controllerFocusViewport(viewport)
                .verticalScroll(scroll),
        ) {
            CompositionLocalProvider(LocalControllerFocusViewport provides viewport) {
                Column(Modifier.fillMaxWidth()) {
                    WidgetBlockOption(metrics, null, current == null, WIDGET_PICKER_FOCUS_LAYER) {
                        dispatch(PocketPassEvent.PickWidgetBlock(null))
                    }
                    choices.forEach { (group, blocks) ->
                        Text(
                            text = group.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = metrics.dp(30f), top = metrics.dp(28f), bottom = metrics.dp(10f)),
                            color = palette.textMuted,
                            fontFamily = Rubik,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = metrics.sp(34f),
                            maxLines = 1,
                        )
                        blocks.forEach { block ->
                            WidgetBlockOption(metrics, block, current == block, WIDGET_PICKER_FOCUS_LAYER) {
                                dispatch(PocketPassEvent.PickWidgetBlock(block))
                            }
                        }
                    }
                    Spacer(Modifier.height(metrics.dp(20f)))
                }
            }
        }
    }
}

internal fun widgetBlockChoices(slot: WidgetSlot): List<Pair<WidgetBlockGroup, List<WidgetBlock>>> {
    val eligible = when (slot) {
        WidgetSlot.Hero -> WidgetBlock.heroChoices
        is WidgetSlot.Tile -> WidgetBlock.tileChoices
    }
    return WidgetBlockGroup.entries
        .map { group -> group to eligible.filter { it.group == group } }
        .filter { (_, blocks) -> blocks.isNotEmpty() }
}

internal fun widgetBlockTag(block: WidgetBlock?): String = "widget_block_${block?.name?.lowercase() ?: "empty"}"

@Composable
internal fun WidgetBlockOption(
    metrics: DesignMetrics,
    block: WidgetBlock?,
    selected: Boolean,
    focusLayer: Int,
    onPick: () -> Unit,
) {
    val palette = pocketPalette
    val shape = RoundedCornerShape(metrics.dp(52.5f))
    val tag = widgetBlockTag(block)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.dp(8f), vertical = metrics.dp(8f))
            .height(metrics.dp(112f))
            .clip(shape)
            .pocketFrame(
                if (selected) greenButtonBrush() else greyPanelBrush(),
                metrics.dp(7f),
                if (selected) WidgetGreenBorder else palette.borderGrey,
                shape,
            )
            .testTag(tag)
            .controllerTarget(tag, layer = focusLayer, cornerRadius = 52.5f) { onPick() }
            .clickable(
                interactionSource = remember(tag) { MutableInteractionSource() },
                indication = null,
                onClick = onPick,
            )
            .padding(horizontal = metrics.dp(28f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (block != null) {
            FigmaAsset(block.settingsGlyph(), Modifier.requiredSize(metrics.dp(64f)))
        } else {
            Box(
                Modifier
                    .requiredSize(metrics.dp(64f))
                    .clip(CircleShape)
                    .pocketBorder(metrics.dp(6f), if (selected) Color.White else palette.borderGrey, CircleShape),
            )
        }
        Spacer(Modifier.width(metrics.dp(28f)))
        Text(
            text = block?.label ?: "Empty",
            modifier = Modifier.weight(1f),
            color = if (selected) Color.White else palette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(42f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (block != null) {
            Text(
                text = block.group.title,
                color = if (selected) Color.White.copy(alpha = 0.85f) else palette.textMuted,
                fontFamily = Rubik,
                fontWeight = FontWeight.Medium,
                fontSize = metrics.sp(30f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BoxScope.CloseCross(
    metrics: DesignMetrics,
    tag: String,
    layer: Int,
    onClose: () -> Unit,
) {
    val palette = pocketPalette
    val color = palette.ink(Color(0xFF2F948C))
    androidx.compose.foundation.Canvas(
        Modifier
            .designBounds(metrics, 945f, 55f, 72f, 72f)
            .testTag(tag)
            .controllerTarget(tag, layer = layer) { onClose() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClose() },
    ) {
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.2f),
            androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.8f),
            strokeWidth = 9f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.2f),
            androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.8f),
            strokeWidth = 9f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
internal fun WidgetRenameOverlay(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val draft = state.widgetMaker.renameDraft ?: return
    val palette = pocketPalette
    val valid = draft.isNotBlank()
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .background(palette.scrim)
            .testTag("widget_rename_overlay")
            .controllerFocusBarrier("widget_rename_overlay", layer = NAME_EDITOR_FOCUS_LAYER)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.CloseWidgetRename) },
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 74f, 1080f, 500f)
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 60f, 1080f, 500f)
            .clip(panelShape)
            .pocketFrame(tealPanelBrush(), metrics.dp(15f), palette.tealBorder, panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("widget_rename_panel"),
    ) {
        Text(
            text = "Rename Widget",
            modifier = Modifier.designBounds(metrics, 58f, 46f, 760f, 94f),
            color = palette.teal,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(72f),
            maxLines = 1,
        )
        CloseCross(metrics, "close_widget_rename", NAME_EDITOR_FOCUS_LAYER) {
            dispatch(PocketPassEvent.CloseWidgetRename)
        }
        Text(
            text = "How this design is listed in Settings",
            modifier = Modifier.designBounds(metrics, 58f, 150f, 964f, 50f),
            color = palette.tealBorder,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(36f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val fieldShape = RoundedCornerShape(metrics.dp(83f))
        Box(
            modifier = Modifier
                .designBounds(metrics, 58f, 222f, 964f, 166f)
                .clip(fieldShape)
                .pocketFrame(palette.surfaceSunken, metrics.dp(8f), palette.tealBorder, fieldShape)
                .testTag("widget_rename_field"),
            contentAlignment = Alignment.Center,
        ) {
            if (draft.isEmpty()) {
                Text(
                    text = buildAnnotatedString {
                        appendInlineContent(TYPING_CARET_INLINE_ID, "|")
                        append("Widget name")
                    },
                    inlineContent = typingCaretInline(metrics, palette.teal, 56f),
                    color = palette.ink(Color(0xFF8FB9C6)),
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Medium,
                    fontSize = metrics.sp(55f),
                    maxLines = 1,
                )
            } else {
                Text(
                    text = buildAnnotatedString {
                        append(draft)
                        appendInlineContent(TYPING_CARET_INLINE_ID, "|")
                    },
                    inlineContent = typingCaretInline(metrics, palette.teal, 56f),
                    color = palette.teal,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Medium,
                    fontSize = metrics.sp(55f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        Text(
            text = "${draft.length}/${WidgetDesign.MAX_NAME_LENGTH}",
            modifier = Modifier.designBounds(metrics, 58f, 414f, 964f, 40f),
            color = palette.tealBorder,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(30f),
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
    PocketKeyboard(
        metrics = metrics,
        layout = PocketKeyboardLayout.Text,
        submitLabel = "Save",
        submitEnabled = valid,
        canBackspace = draft.isNotEmpty(),
        onKey = { key ->
            when (key) {
                is PocketKey.Character ->
                    dispatch(PocketPassEvent.UpdateWidgetNameDraft(draft + key.value))
                PocketKey.Space ->
                    dispatch(PocketPassEvent.UpdateWidgetNameDraft("$draft "))
                PocketKey.Backspace ->
                    dispatch(PocketPassEvent.UpdateWidgetNameDraft(draft.dropLast(1)))
                PocketKey.Submit -> dispatch(PocketPassEvent.SaveWidgetName)
                PocketKey.Alphabet, PocketKey.Emoji -> Unit
            }
        },
        palette = PocketKeyboardPalette.Messages,
        focusLayer = NAME_EDITOR_FOCUS_LAYER,
    )
}

@Composable
internal fun WidgetDeleteDialog(
    metrics: DesignMetrics,
    design: WidgetDesign,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val palette = pocketPalette
    DialogFocusHandoff("widget_delete_cancel")
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .background(palette.scrim)
            .testTag("widget_delete_overlay")
            .controllerFocusBarrier("widget_delete_overlay", layer = WIDGET_DELETE_FOCUS_LAYER)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.CloseWidgetDeletePrompt) },
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 294f, 1080f, 500f)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 280f, 1080f, 500f)
            .graphicsLayer { translationY = entrance.value }
            .clip(panelShape)
            .pocketFrame(greyPanelBrush(), metrics.dp(15f), palette.borderGrey, panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("widget_delete_panel"),
    ) {
        Text(
            text = "Delete ${design.name}?",
            modifier = Modifier.designBounds(metrics, 60f, 44f, 960f, 90f),
            color = palette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(70f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Copies on your home screen will ask for a new design.",
            modifier = Modifier.designBounds(metrics, 90f, 148f, 900f, 130f),
            color = palette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val buttonShape = RoundedCornerShape(metrics.dp(118f))
        Box(
            modifier = Modifier
                .designBounds(metrics, 60f, 300f, 470f, 150f)
                .clip(buttonShape)
                .pocketFrame(cancelButtonBrush(), metrics.dp(20.152f), Color(0xFF8A8A8A), buttonShape)
                .testTag("widget_delete_cancel")
                .controllerTarget("widget_delete_cancel", layer = WIDGET_DELETE_FOCUS_LAYER) {
                    dispatch(PocketPassEvent.CloseWidgetDeletePrompt)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dispatch(PocketPassEvent.CloseWidgetDeletePrompt) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Cancel",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(44f),
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .designBounds(metrics, 550f, 300f, 470f, 150f)
                .clip(buttonShape)
                .pocketFrame(redButtonBrush(), metrics.dp(20.152f), WidgetRedBorder, buttonShape)
                .testTag("widget_delete_confirm")
                .controllerTarget("widget_delete_confirm", layer = WIDGET_DELETE_FOCUS_LAYER) {
                    dispatch(PocketPassEvent.DeleteWidgetDesign(design.id))
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dispatch(PocketPassEvent.DeleteWidgetDesign(design.id)) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Delete",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(44f),
                maxLines = 1,
            )
        }
    }
}
