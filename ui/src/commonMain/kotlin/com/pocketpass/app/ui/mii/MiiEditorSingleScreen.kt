package com.pocketpass.app.ui.mii

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import com.pocketpass.app.mii.MiiAdjustmentField
import com.pocketpass.app.mii.MiiCategory
import com.pocketpass.app.mii.MiiColorField
import com.pocketpass.app.mii.MiiEditorEvent
import com.pocketpass.app.mii.MiiEditorMode
import com.pocketpass.app.mii.MiiEditorUiState
import com.pocketpass.app.mii.colorValue
import com.pocketpass.app.mii.toggleValue
import com.pocketpass.app.mii.traitValue
import com.pocketpass.app.mii.verticalUpDelta
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.PocketAsset
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.TOP_DESIGN_HEIGHT
import com.pocketpass.app.ui.TOP_DESIGN_WIDTH
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.pocketShadow
import com.pocketpass.app.ui.controller.FOCUS_SLIDE_DAMPING_RATIO
import com.pocketpass.app.ui.controller.FOCUS_SLIDE_STIFFNESS
import com.pocketpass.app.ui.controller.FocusDirection
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.LocalControllerFocusGroup
import com.pocketpass.app.ui.controller.controllerFocusBarrier
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.phone.LocalPhoneInsets
import com.pocketpass.app.ui.platformAnimationsEnabled
import com.pocketpass.app.ui.screens.cancelButtonBrush
import com.pocketpass.app.ui.screens.greyPanelBrush
import com.pocketpass.app.ui.screens.redButtonBrush
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal const val MII_EDITOR_BACK_TAG = "mii_editor_back"
internal const val MII_EDITOR_SAVE_TAG = "mii_editor_save"
private const val SINGLE_SCRIM_BARRIER = "mii_adjustment_scrim"
private const val CATEGORY_INDICATOR_INSET = 14f
private const val CATEGORY_INDICATOR_BORDER = 10f
private const val CATEGORY_DIVIDER_THICKNESS = 9.622f
private val CategoryDivider = Color(0x213D7E92)
private const val SWATCH_GAP = 18f
private const val LANDSCAPE_SWATCH_GAP = 22.36f
private const val LANDSCAPE_COLOR_TOP = 38.49f
private const val LANDSCAPE_COLOR_BOTTOM = 39.13f
private const val SLIDER_TRACK = 73f
private const val SLIDER_TRACK_INSET = 20.152f
private const val SLIDER_THUMB_SHORT = 83f
private const val SLIDER_THUMB_LONG = 138f
private const val LANDSCAPE_TRACK_START = 198.385f
private const val LANDSCAPE_TRACK_END = 37.115f
private const val PORTRAIT_TRACK_START = 170f
private const val PORTRAIT_TRACK_END = 50f
private const val LANDSCAPE_ICON_OFFSET = 71f
private const val PORTRAIT_ICON_OFFSET = 44f
private const val ADJUST_ICON_BOX = 96.385f
private const val FLUSH_RADIUS = 92.5f
private const val FLUSH_RING_EXTENSION = 140f
private const val SAVE_LABEL_SIZE = 60f
private const val SAVE_GLYPH_WIDTH = 58f
private const val SAVE_GLYPH_HEIGHT = 62.7f
private const val SAVE_PILL_PADDING = 52f
private const val SAVE_GLYPH_GAP = 18f
private const val SAVE_TAB_OVERHANG = 12.81f
private const val NOTICE_HEIGHT = 110f
private const val NOTICE_PADDING = 44f
private const val DISCARD_PANEL_WIDTH = 1080f
private const val DISCARD_PANEL_HEIGHT = 500f
private const val PALETTE_PANEL_RADIUS = 60f
private const val DISABLED_ALPHA = 0.6f

@Composable
fun MiiEditorSingleScreen(
    metrics: DesignMetrics,
    state: MiiEditorUiState,
    onEvent: (MiiEditorEvent) -> Unit,
    modifier: Modifier = Modifier,
    liveRender: @Composable BoxScope.() -> Unit,
) {
    val insets = LocalPhoneInsets.current
    val layout = remember(metrics.designWidth, metrics.designHeight, insets) {
        miiSingleScreenLayout(
            width = metrics.designWidth,
            height = metrics.designHeight,
            insetTop = insets.top,
            insetBottom = insets.bottom,
            insetStart = insets.start,
            insetEnd = insets.end,
        )
    }
    val focus = LocalControllerFocus.current
    val overlayOpen = state.activeAdjustment != null
    val promptOpen = state.discardPromptVisible
    val paletteField = state.colorPaletteField
    val paletteOpen = paletteField != null
    var retainedAdjustment by remember { mutableStateOf<MiiAdjustmentField?>(state.activeAdjustment) }
    state.activeAdjustment?.let { field -> SideEffect { retainedAdjustment = field } }
    var retainedPaletteField by remember { mutableStateOf<MiiColorField?>(null) }
    paletteField?.let { field -> SideEffect { retainedPaletteField = field } }
    val animatedPaletteField = paletteField ?: retainedPaletteField
    var paletteWasOpen by remember { mutableStateOf(false) }
    val editorBlur = animateFloatAsState(
        targetValue = if (overlayOpen || promptOpen || paletteOpen) 3.05f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "Single screen Piip editor blur",
    )
    val entrance = rememberMiiEditorEntrance()

    LaunchedEffect(Unit) {
        focus?.focus(miiCategoryTag(state.selectedCategory), reveal = false)
    }
    LaunchedEffect(paletteOpen) {
        if (paletteOpen) {
            paletteWasOpen = true
            paletteField?.let { field ->
                focus?.focus("mii_palette_${state.draft.colorValue(field)}", reveal = false)
            }
        } else if (paletteWasOpen) {
            paletteWasOpen = false
            val focusId = focus?.focusId
            if (focus != null && (focusId == null || focusId.startsWith("mii_palette_"))) {
                state.chipPaletteField()?.let { field ->
                    focus.focus(miiColorPaletteTag(field), reveal = false)
                }
            }
        }
    }
    val promptOpener = remember { arrayOfNulls<String>(1) }
    remember(promptOpen) {
        if (promptOpen) promptOpener[0] = focus?.focusId
        promptOpen
    }
    LaunchedEffect(promptOpen) {
        if (promptOpen) {
            focus?.focus(MII_DISCARD_KEEP_TAG, reveal = false)
        } else {
            val opener = promptOpener[0]?.takeUnless { it.startsWith("mii_discard") }
            promptOpener[0] = null
            val current = focus?.focusId
            if (opener != null || current == null || current.startsWith("mii_discard")) {
                focus?.focus(opener ?: miiCategoryTag(state.selectedCategory), reveal = false)
            }
        }
    }
    LaunchedEffect(overlayOpen) {
        if (overlayOpen) {
            focus?.focus(MII_ADJUSTMENT_SLIDER_TAG, reveal = false)
        } else {
            retainedAdjustment?.let { field ->
                focus?.focus("mii_adjust_${field.visualSlot().name}", reveal = false)
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        MiiEditorBackground(metrics)
        EditorStage(
            metrics = metrics,
            layout = layout,
            state = state,
            onEvent = onEvent,
            controllerActive = focus != null,
            liveRender = liveRender,
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val radius = metrics.dp(editorBlur.value).toPx()
                    renderEffect = if (radius > 0.01f) BlurEffect(radius, radius, TileMode.Clamp) else null
                    translationY = (1f - entrance.value) * 48f
                },
        ) {
            EditorPanel(metrics, layout, state, onEvent)
        }
        AdjustmentOverlay(metrics, layout, state, onEvent)
        AnimatedVisibility(
            visible = paletteOpen,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 140)),
        ) {
            Box(Modifier.fillMaxSize()) {
                RegionScrim(metrics, layout.overlayRegion) { onEvent(MiiEditorEvent.CloseColorPalette) }
                animatedPaletteField?.let { field ->
                    PalettePanel(
                        metrics = metrics,
                        layout = layout,
                        state = state,
                        field = field,
                        focusable = paletteOpen,
                        onEvent = onEvent,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = promptOpen,
            enter = fadeIn(animationSpec = tween(durationMillis = 160)),
            exit = fadeOut(animationSpec = tween(durationMillis = 140)),
        ) {
            DiscardPrompt(metrics, layout, onEvent)
        }
    }
}

private fun Modifier.at(metrics: DesignMetrics, rect: Rect): Modifier =
    designBounds(metrics, rect.left, rect.top, rect.width, rect.height)

private fun Modifier.animatedBounds(metrics: DesignMetrics, rect: () -> Rect): Modifier = this
    .graphicsLayer {
        val bounds = rect()
        translationX = bounds.left
        translationY = bounds.top
    }
    .layout { measurable, _ ->
        val bounds = rect()
        val widthPx = metrics.dp(bounds.width).roundToPx()
        val heightPx = metrics.dp(bounds.height).roundToPx()
        val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
        layout(widthPx, heightPx) { placeable.place(0, 0) }
    }

@Composable
private fun EditorStage(
    metrics: DesignMetrics,
    layout: MiiSingleScreenLayout,
    state: MiiEditorUiState,
    onEvent: (MiiEditorEvent) -> Unit,
    controllerActive: Boolean,
    liveRender: @Composable BoxScope.() -> Unit,
) {
    val stage = layout.stage
    val preview = layout.preview
    val scale = preview.height / TOP_DESIGN_HEIGHT
    Box(Modifier.at(metrics, stage).clipToBounds()) {
        FigmaAsset(
            resource = PocketAsset("files/figma/mii_editor_top_background.png"),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.60f),
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { blendMode = BlendMode.Overlay }
                .background(Color(0x991BFFEC)),
        )
        FigmaAsset(
            resource = PocketAsset("files/figma/mii_editor_ground_shadow.svg"),
            modifier = Modifier.designBounds(
                metrics,
                x = preview.center.x - stage.left + (720.64f - TOP_DESIGN_WIDTH / 2f) * scale,
                y = preview.top - stage.top + 853.32f * scale,
                width = 476.162f * scale,
                height = 347.864f * scale,
            ),
        )
    }
    Box(Modifier.at(metrics, preview).clipToBounds(), content = liveRender)

    if (state.mode == MiiEditorMode.EditExisting) {
        BackButton(metrics, layout.backButton) { onEvent(MiiEditorEvent.RequestCancel) }
    }
    if (layout.landscape) {
        SaveTab(metrics, layout, state.canContinue, controllerActive) { onEvent(MiiEditorEvent.Save) }
    } else {
        SavePill(metrics, layout, state.canContinue, controllerActive) { onEvent(MiiEditorEvent.Save) }
    }
    state.editorNotice()?.let { message -> StageNotice(metrics, layout, message) }
}

@Composable
private fun BackButton(
    metrics: DesignMetrics,
    rect: Rect,
    onClick: () -> Unit,
) {
    FigmaPillSurface(
        metrics = metrics,
        modifier = Modifier
            .at(metrics, rect)
            .testTag(MII_EDITOR_BACK_TAG)
            .controllerTarget(MII_EDITOR_BACK_TAG, cornerRadius = rect.height / 2f) { onClick() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(metrics.dp(rect.height / 2f)),
    ) {
        FigmaAsset(
            resource = Assets.SettingsArrow,
            modifier = Modifier
                .offset(x = -metrics.dp(4f))
                .requiredSize(metrics.dp(32.3f), metrics.dp(55f))
                .graphicsLayer { scaleX = -1f },
            colorFilter = ColorFilter.tint(PocketText),
            description = "Back",
        )
    }
}

@Composable
private fun SavePill(
    metrics: DesignMetrics,
    layout: MiiSingleScreenLayout,
    enabled: Boolean,
    glyph: Boolean,
    onClick: () -> Unit,
) {
    val style = continueLabelStyle(metrics, SAVE_LABEL_SIZE)
    val labelWidth = rememberContinueLabelWidth(SAVE_LABEL, style)
    val glyphSpace = if (glyph) SAVE_GLYPH_WIDTH + SAVE_GLYPH_GAP else 0f
    val rect = layout.savePill(SAVE_PILL_PADDING * 2f + glyphSpace + labelWidth)
    FigmaPillSurface(
        metrics = metrics,
        modifier = Modifier
            .at(metrics, rect)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .testTag(MII_EDITOR_SAVE_TAG)
            .controllerTarget(MII_EDITOR_SAVE_TAG, cornerRadius = rect.height / 2f) { if (enabled) onClick() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (enabled) Modifier else Modifier.blockMiiRendererGestures()),
        shape = RoundedCornerShape(metrics.dp(rect.height / 2f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (glyph) {
                FigmaAsset(
                    resource = PocketAsset("files/figma/mii_editor_ok_y.svg"),
                    modifier = Modifier.requiredSize(metrics.dp(SAVE_GLYPH_WIDTH), metrics.dp(SAVE_GLYPH_HEIGHT)),
                )
                Spacer(Modifier.requiredWidth(metrics.dp(SAVE_GLYPH_GAP)))
            }
            Text(
                text = SAVE_LABEL,
                style = style,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun SaveTab(
    metrics: DesignMetrics,
    layout: MiiSingleScreenLayout,
    enabled: Boolean,
    glyph: Boolean,
    onClick: () -> Unit,
) {
    val style = continueLabelStyle(metrics)
    val labelWidth = rememberContinueLabelWidth(SAVE_LABEL, style)
    val inset = layout.insetStart
    val labelX = inset + if (glyph) CONTINUE_LABEL_X else CONTINUE_LABEL_END_PADDING
    val width = labelX + labelWidth + CONTINUE_LABEL_END_PADDING
    val height = CONTINUE_TAB_HEIGHT + layout.insetBottom
    val top = layout.height + SAVE_TAB_OVERHANG - height
    Box(
        modifier = Modifier
            .designBounds(metrics, 0f, top, width, height)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .testTag(MII_EDITOR_SAVE_TAG)
            .controllerTarget(MII_EDITOR_SAVE_TAG, cornerRadius = 40f) { if (enabled) onClick() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (enabled) Modifier else Modifier.blockMiiRendererGestures()),
    ) {
        ContinueTabFace(
            metrics = metrics,
            width = width,
            height = height,
            label = SAVE_LABEL,
            labelStyle = style,
            labelX = labelX,
            labelWidth = labelWidth,
            glyph = glyph,
            glyphX = CONTINUE_GLYPH_X + inset,
        )
    }
}

private const val SAVE_LABEL = "Save"

@Composable
private fun StageNotice(
    metrics: DesignMetrics,
    layout: MiiSingleScreenLayout,
    message: String,
) {
    val style = TextStyle(
        color = EditorNoticeRed,
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = metrics.sp(40f),
    )
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textWidth = remember(message, density) {
        textMeasurer.measure(message, style).size.width.toFloat()
    }
    val stage = layout.stage
    val width = (textWidth + NOTICE_PADDING * 2f).coerceAtMost(stage.width - 2f * SINGLE_CONTROL_MARGIN)
    FigmaPillSurface(
        metrics = metrics,
        modifier = Modifier
            .designBounds(metrics, stage.center.x - width / 2f, layout.noticeTop, width, NOTICE_HEIGHT)
            .testTag("mii_editor_notice")
            .blockMiiRendererGestures(),
        shape = RoundedCornerShape(metrics.dp(NOTICE_HEIGHT / 2f)),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = metrics.dp(NOTICE_PADDING)),
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EditorPanel(
    metrics: DesignMetrics,
    layout: MiiSingleScreenLayout,
    state: MiiEditorUiState,
    onEvent: (MiiEditorEvent) -> Unit,
) {
    CompositionLocalProvider(LocalControllerFocusGroup provides "mii_rail") {
        CategoryBar(
            metrics = metrics,
            layout = layout,
            selected = state.selectedCategory,
            onSelect = { onEvent(MiiEditorEvent.SelectCategory(it)) },
        )
    }
    val entries = state.traitEntries()
    CompositionLocalProvider(LocalControllerFocusGroup provides "mii_traits") {
        key(state.selectedCategory, entries.first().field) {
            TraitGrid(metrics, layout, state, entries, onEvent)
        }
    }
    CompositionLocalProvider(LocalControllerFocusGroup provides "mii_colors") {
        key(state.selectedCategory, state.activeColorField) {
            Colors(metrics, layout, state, onEvent)
        }
    }
    CompositionLocalProvider(LocalControllerFocusGroup provides "mii_adjust") {
        Adjustments(metrics, layout, state, onEvent)
    }
}

@Composable
private fun CategoryBar(
    metrics: DesignMetrics,
    layout: MiiSingleScreenLayout,
    selected: MiiCategory,
    onSelect: (MiiCategory) -> Unit,
) {
    val rect = layout.categories
    val vertical = layout.landscape
    Box(
        Modifier
            .at(metrics, rect)
            .offset(y = metrics.dp(14f))
            .pocketShadow(metrics, 0f),
    )
    Box(
        Modifier
            .at(metrics, rect)
            .background(
                if (vertical) {
                    Brush.horizontalGradient(listOf(PocketPaleGreen, Color.White))
                } else {
                    Brush.verticalGradient(listOf(PocketPaleGreen, Color.White))
                },
            ),
    )
    Box(
        Modifier
            .at(
                metrics,
                if (vertical) {
                    Rect(rect.right - SINGLE_STRIPE, rect.top, rect.right, rect.bottom)
                } else {
                    Rect(rect.left, rect.bottom - SINGLE_STRIPE, rect.right, rect.bottom)
                },
            )
            .background(PocketBlue),
    )

    val count = categoryVisuals.size
    val cells = remember(layout) {
        if (vertical) {
            val top = layout.insetTop
            val cellHeight = (layout.height - layout.insetTop - layout.insetBottom) / count
            List(count) { index ->
                Rect(rect.left, top + index * cellHeight, rect.right - SINGLE_STRIPE, top + (index + 1) * cellHeight)
            }
        } else {
            val left = layout.insetStart
            val cellWidth = (layout.width - layout.insetStart - layout.insetEnd) / count
            List(count) { index ->
                Rect(left + index * cellWidth, rect.top, left + (index + 1) * cellWidth, rect.bottom - SINGLE_STRIPE)
            }
        }
    }
    val selectedIndex = categoryVisuals.indexOfFirst { it.category == selected }.coerceAtLeast(0)
    CategoryIndicator(metrics, cells[selectedIndex])

    categoryVisuals.forEachIndexed { index, visual ->
        val cell = cells[index]
        Box(
            Modifier
                .at(metrics, cell)
                .testTag(miiCategoryTag(visual.category))
                .selectable(
                    selected = selected == visual.category,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Tab,
                    onClick = { onSelect(visual.category) },
                ),
        )
        FigmaAsset(
            resource = visual.icon,
            modifier = Modifier.designBounds(
                metrics,
                x = cell.center.x - visual.width / 2f,
                y = cell.center.y - visual.height / 2f,
                width = visual.width,
                height = visual.height,
            ),
        )
        Box(
            Modifier
                .at(metrics, cell.deflate(CATEGORY_INDICATOR_INSET))
                .controllerTarget(miiCategoryTag(visual.category), cornerRadius = CATEGORY_INDICATOR_RADIUS) {
                    onSelect(visual.category)
                },
        )
        if (index < count - 1) {
            val divider = if (vertical) {
                Rect(cell.left, cell.bottom - CATEGORY_DIVIDER_THICKNESS / 2f, cell.right, cell.bottom + CATEGORY_DIVIDER_THICKNESS / 2f)
            } else {
                Rect(cell.right - CATEGORY_DIVIDER_THICKNESS / 2f, cell.top + 18f, cell.right + CATEGORY_DIVIDER_THICKNESS / 2f, cell.bottom - 18f)
            }
            Box(
                Modifier
                    .at(metrics, divider)
                    .clip(RoundedCornerShape(metrics.dp(CATEGORY_DIVIDER_THICKNESS / 2f)))
                    .background(CategoryDivider),
            )
        }
    }
}

private const val CATEGORY_INDICATOR_RADIUS = 36f

@Composable
private fun CategoryIndicator(
    metrics: DesignMetrics,
    cell: Rect,
) {
    val target = cell.deflate(CATEGORY_INDICATOR_INSET)
    val x = remember { Animatable(target.left) }
    val y = remember { Animatable(target.top) }
    LaunchedEffect(target) {
        if (!platformAnimationsEnabled()) {
            x.snapTo(target.left)
            y.snapTo(target.top)
            return@LaunchedEffect
        }
        val motion = spring(
            dampingRatio = FOCUS_SLIDE_DAMPING_RATIO,
            stiffness = FOCUS_SLIDE_STIFFNESS,
            visibilityThreshold = 0.5f,
        )
        launch { x.animateTo(target.left, motion) }
        launch { y.animateTo(target.top, motion) }
    }
    val shape = RoundedCornerShape(metrics.dp(CATEGORY_INDICATOR_RADIUS))
    Box(
        Modifier
            .designBounds(metrics, target.width, target.height) { Offset(x.value, y.value) }
            .clip(shape)
            .pocketFrame(
                Brush.verticalGradient(listOf(Color.White, PocketSelectedGreen)),
                metrics.dp(CATEGORY_INDICATOR_BORDER),
                PocketBlue,
                shape,
            ),
    )
}

@Composable
private fun TraitGrid(
    metrics: DesignMetrics,
    layout: MiiSingleScreenLayout,
    state: MiiEditorUiState,
    entries: List<TraitEntry>,
    onEvent: (MiiEditorEvent) -> Unit,
) {
    val maxPage = (entries.size - 1).coerceAtLeast(0) / TRAIT_PAGE_SIZE
    val initialPage = state.currentTraitPage.coerceIn(0, maxPage)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialPage * TRAIT_PAGE_SIZE)
    LaunchedEffect(gridState, state.selectedCategory) {
        snapshotFlow { gridState.firstVisibleItemIndex / TRAIT_PAGE_SIZE }
            .distinctUntilChanged()
            .collect { page -> onEvent(MiiEditorEvent.SetTraitPage(page.coerceIn(0, maxPage))) }
    }
    val padding = if (layout.landscape) TRAIT_GRID_PADDING else PORTRAIT_GRID_PADDING
    LazyVerticalGrid(
        columns = GridCells.Fixed(layout.traitColumns),
        state = gridState,
        modifier = Modifier.at(metrics, layout.traits).testTag("mii_trait_grid"),
        contentPadding = PaddingValues(top = metrics.dp(padding), bottom = metrics.dp(padding + 14f)),
        verticalArrangement = Arrangement.spacedBy(metrics.dp(TRAIT_ROW_GAP)),
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(TRAIT_PILL_GAP)),
    ) {
        items(
            count = entries.size,
            key = { position -> entries[position].let { "${it.field}:${it.index}" } },
        ) { position ->
            val entry = entries[position]
            TraitPill(
                metrics = metrics,
                field = entry.field,
                index = entry.index,
                state = state,
                selected = entry.index == state.draft.traitValue(entry.field),
                onClick = { onEvent(MiiEditorEvent.SelectTrait(entry.field, entry.index)) },
            )
        }
    }
}

private const val TRAIT_PAGE_SIZE = 12
private const val PORTRAIT_GRID_PADDING = 12f

@Composable
private fun Colors(
    metrics: DesignMetrics,
    layout: MiiSingleScreenLayout,
    state: MiiEditorUiState,
    onEvent: (MiiEditorEvent) -> Unit,
) {
    if (state.descriptor.colors.isEmpty()) return
    if (
        (state.activeColorField == MiiColorField.Hat || state.activeColorField == MiiColorField.HatSecondary) &&
        state.draft.extHatType < 0
    ) return
    val rect = layout.colors
    val paletteField = state.chipPaletteField()
    if (paletteField != null) {
        val paletteColors = paletteField.palette()
        val size = if (layout.landscape) COLOR_COLUMN_WIDTH else rect.height
        val chip = if (layout.landscape) {
            Rect(rect.left, rect.top + LANDSCAPE_COLOR_TOP, rect.left + size, rect.top + LANDSCAPE_COLOR_TOP + size)
        } else {
            Rect(rect.center.x - size / 2f, rect.top, rect.center.x + size / 2f, rect.top + size)
        }
        PaletteChip(
            metrics = metrics,
            rect = chip,
            field = paletteField,
            color = paletteColors.getOrElse(state.draft.colorValue(paletteField)) { paletteColors.first() },
            onOpen = { onEvent(MiiEditorEvent.OpenColorPalette(paletteField)) },
        )
        return
    }
    val colorDescriptor = state.descriptor.colors.firstOrNull { it.field == state.activeColorField }
        ?: state.descriptor.colors.firstOrNull { it.figmaPrimary }
    val activeField = colorDescriptor?.field
    val validCount = colorDescriptor?.optionCount ?: 0
    val palette = activeField?.palette() ?: MiiEditorColors.figmaEyes
    val displayCount = maxOf(6, validCount)
    val swatch: @Composable (Int, Float) -> Unit = { index, size ->
        val enabled = activeField != null && index < validCount
        val color = palette.getOrElse(index) { MiiEditorColors.figmaEyes[index % MiiEditorColors.figmaEyes.size] }
        ColorSwatch(
            metrics = metrics,
            size = size,
            index = index,
            color = color,
            selected = enabled && state.selectedColorIndex == index,
            enabled = enabled,
            field = activeField,
            onSelect = { field -> onEvent(MiiEditorEvent.SelectColor(field, index)) },
        )
    }
    if (layout.landscape) {
        val available = rect.height - LANDSCAPE_COLOR_TOP - LANDSCAPE_COLOR_BOTTOM
        val size = minOf(COLOR_COLUMN_WIDTH, (available - (displayCount - 1) * LANDSCAPE_SWATCH_GAP) / displayCount)
        LazyColumn(
            modifier = Modifier.at(metrics, rect),
            contentPadding = PaddingValues(
                top = metrics.dp(LANDSCAPE_COLOR_TOP),
                bottom = metrics.dp(LANDSCAPE_COLOR_BOTTOM),
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(metrics.dp(LANDSCAPE_SWATCH_GAP)),
        ) {
            items(count = displayCount, key = { index -> "${activeField ?: "figma"}:$index" }) { index ->
                swatch(index, size)
            }
        }
    } else {
        val size = minOf(rect.height, (rect.width - (displayCount - 1) * SWATCH_GAP) / displayCount)
        Row(
            modifier = Modifier.at(metrics, rect),
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(SWATCH_GAP), Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(displayCount) { index -> key(activeField, index) { swatch(index, size) } }
        }
    }
}

@Composable
private fun ColorSwatch(
    metrics: DesignMetrics,
    size: Float,
    index: Int,
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    field: MiiColorField?,
    onSelect: (MiiColorField) -> Unit,
) {
    val scale = size / COLOR_COLUMN_WIDTH
    val shape = RoundedCornerShape(metrics.dp(size / 2f))
    Box(
        modifier = Modifier
            .requiredSize(metrics.dp(size))
            .clip(shape)
            .pocketFrame(
                color,
                metrics.dp((if (selected) 15f else 10f) * scale),
                if (selected) PocketActionGreen else swatchBorder(index, color),
                shape,
            )
            .then(
                if (enabled && field != null) {
                    Modifier
                        .testTag("mii_color_${field.name}_$index")
                        .controllerTarget("mii_color_${field.name}_$index", cornerRadius = size / 2f) {
                            onSelect(field)
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = { onSelect(field) },
                        )
                } else {
                    Modifier.alpha(0.72f)
                },
            ),
    )
}

@Composable
private fun PaletteChip(
    metrics: DesignMetrics,
    rect: Rect,
    field: MiiColorField,
    color: Color,
    onOpen: () -> Unit,
) {
    val scale = rect.width / COLOR_COLUMN_WIDTH
    val shape = RoundedCornerShape(metrics.dp(rect.width / 2f))
    Box(
        modifier = Modifier
            .at(metrics, rect)
            .clip(shape)
            .background(Brush.sweepGradient(PaletteWheel))
            .testTag(miiColorPaletteTag(field))
            .controllerTarget(miiColorPaletteTag(field), cornerRadius = rect.width / 2f) { onOpen() }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onOpen,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .requiredSize(metrics.dp(104f * scale))
                .clip(RoundedCornerShape(metrics.dp(52f * scale)))
                .background(Color.White)
                .padding(metrics.dp(10f * scale))
                .clip(RoundedCornerShape(metrics.dp(42f * scale)))
                .background(color),
        )
    }
}

@Composable
private fun Adjustments(
    metrics: DesignMetrics,
    layout: MiiSingleScreenLayout,
    state: MiiEditorUiState,
    onEvent: (MiiEditorEvent) -> Unit,
) {
    val relevant = state.relevantAdjustments()
        .filter { it.gate?.let(state.draft::toggleValue) != false }
    val railToggle = state.railToggle()
    val hatColourToggle = state.hatColourToggle()
    AdjustmentVisualSlot.entries.forEachIndexed { index, slot ->
        val toggle = railToggle?.takeIf { slot == AdjustmentVisualSlot.Spacing }
        val colourToggle = hatColourToggle?.takeIf { slot == AdjustmentVisualSlot.Scale }
        val field = relevant.fieldFor(slot)
        val activate: (() -> Unit)? = when {
            toggle != null -> {
                { onEvent(toggle.event) }
            }
            colourToggle != null -> {
                { onEvent(colourToggle.event) }
            }
            field != null -> {
                { onEvent(MiiEditorEvent.OpenAdjustment(field)) }
            }
            else -> null
        }
        val selected = when {
            toggle != null -> toggle.on
            colourToggle != null -> colourToggle.on
            else -> field != null && field == state.activeAdjustment
        }
        val rect = layout.adjustButton(index)
        val radius = if (layout.landscape) FLUSH_RADIUS else rect.height / 2f
        FigmaPillSurface(
            metrics = metrics,
            modifier = Modifier
                .at(metrics, rect)
                .testTag("mii_adjust_${slot.name}")
                .then(
                    if (activate != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = activate,
                        )
                    } else {
                        Modifier
                    },
                ),
            shape = adjustmentShape(metrics, layout.landscape, radius),
            selected = selected,
            horizontalFill = layout.landscape,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(end = metrics.dp(if (layout.landscape) layout.insetEnd else 0f)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    toggle != null -> FigmaAsset(
                        resource = toggle.icon,
                        modifier = Modifier.requiredSize(metrics.dp(83f), metrics.dp(83f)),
                    )
                    colourToggle != null -> HatColourSplit(
                        metrics = metrics,
                        colour1 = colourToggle.colour1,
                        colour2 = colourToggle.colour2,
                    )
                    else -> AdjustmentIcon(
                        metrics = metrics,
                        slot = slot,
                        modifier = Modifier.alpha(if (activate == null) 0.26f else 1f),
                    )
                }
            }
        }
        if (activate != null) {
            val ring = if (layout.landscape) {
                Rect(rect.left, rect.top, rect.right + FLUSH_RING_EXTENSION, rect.bottom)
            } else {
                rect
            }
            Box(
                Modifier
                    .at(metrics, ring)
                    .controllerTarget("mii_adjust_${slot.name}", cornerRadius = radius) { activate() },
            )
        }
    }
}

private fun adjustmentShape(metrics: DesignMetrics, flush: Boolean, radius: Float): RoundedCornerShape =
    if (flush) {
        RoundedCornerShape(topStart = metrics.dp(radius), bottomStart = metrics.dp(radius))
    } else {
        RoundedCornerShape(metrics.dp(radius))
    }

@Composable
private fun RegionScrim(
    metrics: DesignMetrics,
    region: Rect,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .at(metrics, region)
            .background(Color(0x80171717))
            .controllerFocusBarrier(SINGLE_SCRIM_BARRIER, layer = MII_ADJUSTMENT_FOCUS_LAYER)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
    )
}

@Composable
private fun AdjustmentOverlay(
    metrics: DesignMetrics,
    layout: MiiSingleScreenLayout,
    state: MiiEditorUiState,
    onEvent: (MiiEditorEvent) -> Unit,
) {
    val overlayOpen = state.activeAdjustment != null
    var retainedAdjustment by remember { mutableStateOf<MiiAdjustmentField?>(state.activeAdjustment) }
    var retainedState by remember { mutableStateOf(state) }
    state.activeAdjustment?.let { field ->
        SideEffect {
            retainedAdjustment = field
            retainedState = state
        }
    }
    val animatedAdjustment = state.activeAdjustment ?: retainedAdjustment
    val animatedState = if (overlayOpen) state else retainedState

    AnimatedVisibility(
        visible = overlayOpen,
        enter = fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = fadeOut(animationSpec = tween(durationMillis = 140)),
    ) {
        RegionScrim(metrics, layout.overlayRegion) { onEvent(MiiEditorEvent.CloseAdjustment) }
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(overlayOpen) {
        if (!platformAnimationsEnabled()) {
            progress.snapTo(if (overlayOpen) 1f else 0f)
            return@LaunchedEffect
        }
        if (overlayOpen) {
            progress.animateTo(1f, spring(dampingRatio = 1f, stiffness = 380f))
        } else {
            progress.animateTo(0f, tween(durationMillis = 240, easing = FastOutSlowInEasing))
        }
    }
    val panelShown by remember { derivedStateOf { progress.value > 0.001f } }
    if (!panelShown && !overlayOpen) return
    val field = animatedAdjustment ?: return
    val slotIndex = field.visualSlot().ordinal
    val vertical = field.verticalUpDelta != null
    val collapsed = layout.adjustButton(slotIndex)
    val expanded = layout.expandedAdjustment(slotIndex, vertical)
    Box(Modifier.fillMaxSize()) {
        if (vertical) {
            VerticalSlider(metrics, layout, animatedState, field, collapsed, expanded, { progress.value }, onEvent)
        } else {
            HorizontalSlider(metrics, layout, animatedState, field, collapsed, expanded, { progress.value }, onEvent)
        }
    }
}

@Composable
private fun HorizontalSlider(
    metrics: DesignMetrics,
    layout: MiiSingleScreenLayout,
    state: MiiEditorUiState,
    field: MiiAdjustmentField,
    collapsed: Rect,
    expanded: Rect,
    progress: () -> Float,
    onEvent: (MiiEditorEvent) -> Unit,
) {
    val descriptor = state.descriptor.adjustments.firstOrNull { it.field == field } ?: return
    val value = state.activeAdjustmentValue ?: descriptor.defaultValue
    val range = (descriptor.maximum - descriptor.minimum).coerceAtLeast(1)
    val fraction = ((value - descriptor.minimum).toFloat() / range).coerceIn(0f, 1f)
    val flush = layout.landscape
    val radius = if (flush) FLUSH_RADIUS else expanded.height / 2f
    val shape = adjustmentShape(metrics, flush, radius)
    val current = { lerp(collapsed, expanded, progress()) }

    FigmaPillSurface(
        metrics = metrics,
        modifier = Modifier
            .animatedBounds(metrics, current)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        shape = shape,
        horizontalFill = flush,
        shadowAlpha = { adjustmentShadowAlpha(progress()) },
    )
    Box(
        Modifier
            .animatedBounds(metrics) {
                val bounds = current()
                if (flush) Rect(bounds.left, bounds.top, bounds.right + FLUSH_RING_EXTENSION, bounds.bottom) else bounds
            }
            .testTag(MII_ADJUSTMENT_SLIDER_TAG)
            .controllerTarget(MII_ADJUSTMENT_SLIDER_TAG, layer = MII_ADJUSTMENT_FOCUS_LAYER, cornerRadius = radius) {
                onEvent(MiiEditorEvent.CloseAdjustment)
            },
    )

    val iconHeight = (expanded.height - 2f * 23.5f).coerceAtLeast(ADJUST_ICON_BOX)
    val iconOffset = if (flush) LANDSCAPE_ICON_OFFSET else PORTRAIT_ICON_OFFSET
    Box(
        modifier = Modifier.designBounds(metrics, ADJUST_ICON_BOX, iconHeight) {
            val bounds = current()
            val centred = visibleCentreX(layout, collapsed) - ADJUST_ICON_BOX / 2f
            Offset(
                lerp(centred, bounds.left + iconOffset, progress()),
                bounds.center.y - iconHeight / 2f,
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        AdjustmentIcon(metrics, field.visualSlot())
    }

    val trackX = expanded.left + if (flush) LANDSCAPE_TRACK_START else PORTRAIT_TRACK_START
    val trackWidth = expanded.right - (if (flush) LANDSCAPE_TRACK_END + layout.insetEnd else PORTRAIT_TRACK_END) - trackX
    val trackY = expanded.center.y - SLIDER_TRACK / 2f
    val trackShape = RoundedCornerShape(metrics.dp(SLIDER_TRACK / 2f))
    val usableWidth = trackWidth - SLIDER_TRACK_INSET * 2f
    val fillWidth = SLIDER_TRACK_INSET * 2f + usableWidth * fraction

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = adjustmentContentAlpha(progress()) },
    ) {
        Box(
            Modifier
                .designBounds(metrics, trackX, trackY + 15.674f, trackWidth, SLIDER_TRACK)
                .clip(trackShape)
                .background(PocketShadow),
        )
        Box(
            Modifier
                .designBounds(metrics, trackX, trackY, trackWidth, SLIDER_TRACK)
                .clip(trackShape)
                .pocketFrame(Color.White, metrics.dp(SLIDER_TRACK_INSET), Color(0xFF9F9F9F), trackShape),
        )
        Box(
            Modifier
                .designBounds(metrics, trackX, trackY, fillWidth, SLIDER_TRACK)
                .clip(trackShape)
                .pocketFrame(sliderFill(vertical = false), metrics.dp(SLIDER_TRACK_INSET), PocketActionGreen, trackShape),
        )
        val thumbCenter = trackX + SLIDER_TRACK_INSET + usableWidth * fraction
        val thumbShape = RoundedCornerShape(metrics.dp(SLIDER_THUMB_SHORT / 2f))
        Box(
            Modifier
                .designBounds(
                    metrics,
                    thumbCenter - SLIDER_THUMB_SHORT / 2f,
                    expanded.center.y - SLIDER_THUMB_LONG / 2f,
                    SLIDER_THUMB_SHORT,
                    SLIDER_THUMB_LONG,
                )
                .clip(thumbShape)
                .pocketFrame(Color.White, metrics.dp(SLIDER_TRACK_INSET), Color(0xFFCECECE), thumbShape),
        )
    }

    Box(
        Modifier
            .designBounds(metrics, trackX, expanded.top, trackWidth, expanded.height)
            .pointerInput(field, descriptor.minimum, descriptor.maximum) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun update(localX: Float) {
                        val sliderFraction = ((localX - SLIDER_TRACK_INSET) / usableWidth).coerceIn(0f, 1f)
                        val next = (
                            descriptor.minimum + sliderFraction * (descriptor.maximum - descriptor.minimum)
                            ).roundToInt()
                        onEvent(MiiEditorEvent.SetAdjustment(field, next))
                    }
                    update(down.position.x)
                    down.consume()
                    do {
                        val pointerEvent = awaitPointerEvent()
                        val change = pointerEvent.changes.firstOrNull { it.id == down.id }
                        if (change != null) {
                            update(change.position.x)
                            change.consume()
                        }
                    } while (change?.pressed == true)
                }
            },
    )
}

@Composable
private fun VerticalSlider(
    metrics: DesignMetrics,
    layout: MiiSingleScreenLayout,
    state: MiiEditorUiState,
    field: MiiAdjustmentField,
    collapsed: Rect,
    expanded: Rect,
    progress: () -> Float,
    onEvent: (MiiEditorEvent) -> Unit,
) {
    val descriptor = state.descriptor.adjustments.firstOrNull { it.field == field } ?: return
    val value = state.activeAdjustmentValue ?: descriptor.defaultValue
    val range = (descriptor.maximum - descriptor.minimum).coerceAtLeast(1)
    val fraction = ((value - descriptor.minimum).toFloat() / range).coerceIn(0f, 1f)
    val topIsMinimum = (field.verticalUpDelta ?: -1) < 0
    val thumbFraction = if (topIsMinimum) fraction else 1f - fraction
    val flush = layout.landscape
    val radius = if (flush) FLUSH_RADIUS else collapsed.width / 2f
    val shape = adjustmentShape(metrics, flush, radius)
    val current = { lerp(collapsed, expanded, progress()) }

    FigmaPillSurface(
        metrics = metrics,
        modifier = Modifier
            .animatedBounds(metrics, current)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        shape = shape,
        shadowAlpha = { adjustmentShadowAlpha(progress()) },
    )
    Box(
        Modifier
            .animatedBounds(metrics) {
                val bounds = current()
                if (flush) Rect(bounds.left, bounds.top, bounds.right + FLUSH_RING_EXTENSION, bounds.bottom) else bounds
            }
            .testTag(MII_ADJUSTMENT_SLIDER_TAG)
            .controllerTarget(MII_ADJUSTMENT_SLIDER_TAG, layer = MII_ADJUSTMENT_FOCUS_LAYER, cornerRadius = radius) {
                onEvent(MiiEditorEvent.CloseAdjustment)
            },
    )

    val buttonCentre = Offset(visibleCentreX(layout, collapsed), collapsed.center.y)
    val iconHeight = (collapsed.height - 2f * 23.5f).coerceAtLeast(ADJUST_ICON_BOX)
    Box(
        modifier = Modifier.designBounds(
            metrics,
            x = buttonCentre.x - ADJUST_ICON_BOX / 2f,
            y = buttonCentre.y - iconHeight / 2f,
            width = ADJUST_ICON_BOX,
            height = iconHeight,
        ),
        contentAlignment = Alignment.Center,
    ) {
        AdjustmentIcon(metrics, field.visualSlot())
    }

    val trackX = buttonCentre.x - SLIDER_TRACK / 2f
    val trackY = if (flush) collapsed.bottom + 20f else expanded.top + 40f
    val trackBottom = if (flush) expanded.bottom - 40f else collapsed.top - 20f
    val trackHeight = (trackBottom - trackY).coerceAtLeast(SLIDER_TRACK * 2f)
    val trackShape = RoundedCornerShape(metrics.dp(SLIDER_TRACK / 2f))
    val usableHeight = trackHeight - SLIDER_TRACK_INSET * 2f
    val thumbCenter = trackY + SLIDER_TRACK_INSET + usableHeight * thumbFraction
    val fillTop = if (topIsMinimum) trackY else thumbCenter - SLIDER_TRACK_INSET
    val fillHeight = if (topIsMinimum) thumbCenter + SLIDER_TRACK_INSET - trackY else trackY + trackHeight - fillTop

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = adjustmentContentAlpha(progress()) },
    ) {
        Box(
            Modifier
                .designBounds(metrics, trackX, trackY + 15.674f, SLIDER_TRACK, trackHeight)
                .clip(trackShape)
                .background(PocketShadow),
        )
        Box(
            Modifier
                .designBounds(metrics, trackX, trackY, SLIDER_TRACK, trackHeight)
                .clip(trackShape)
                .pocketFrame(Color.White, metrics.dp(SLIDER_TRACK_INSET), Color(0xFF9F9F9F), trackShape),
        )
        Box(
            Modifier
                .designBounds(metrics, trackX, fillTop, SLIDER_TRACK, fillHeight)
                .clip(trackShape)
                .pocketFrame(sliderFill(vertical = true), metrics.dp(SLIDER_TRACK_INSET), PocketActionGreen, trackShape),
        )
        val thumbShape = RoundedCornerShape(metrics.dp(SLIDER_THUMB_SHORT / 2f))
        Box(
            Modifier
                .designBounds(
                    metrics,
                    buttonCentre.x - SLIDER_THUMB_LONG / 2f,
                    thumbCenter - SLIDER_THUMB_SHORT / 2f,
                    SLIDER_THUMB_LONG,
                    SLIDER_THUMB_SHORT,
                )
                .clip(thumbShape)
                .pocketFrame(Color.White, metrics.dp(SLIDER_TRACK_INSET), Color(0xFFCECECE), thumbShape),
        )
    }

    Box(
        Modifier
            .designBounds(metrics, collapsed.left, trackY, collapsed.width, trackHeight)
            .pointerInput(field, descriptor.minimum, descriptor.maximum) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun update(localY: Float) {
                        val sliderFraction = ((localY - SLIDER_TRACK_INSET) / usableHeight).coerceIn(0f, 1f)
                        val valueFraction = if (topIsMinimum) sliderFraction else 1f - sliderFraction
                        val next = (
                            descriptor.minimum + valueFraction * (descriptor.maximum - descriptor.minimum)
                            ).roundToInt()
                        onEvent(MiiEditorEvent.SetAdjustment(field, next))
                    }
                    update(down.position.y)
                    down.consume()
                    do {
                        val pointerEvent = awaitPointerEvent()
                        val change = pointerEvent.changes.firstOrNull { it.id == down.id }
                        if (change != null) {
                            update(change.position.y)
                            change.consume()
                        }
                    } while (change?.pressed == true)
                }
            },
    )
}

private fun visibleCentreX(layout: MiiSingleScreenLayout, button: Rect): Float =
    if (layout.landscape) button.left + LANDSCAPE_ADJUST_WIDTH / 2f else button.center.x

private fun sliderFill(vertical: Boolean): Brush {
    val stops = arrayOf(
        0f to Color(0xFF57E25F),
        0.50f to Color(0xFF5EED6F),
        0.55f to Color(0xFF57E25F),
        1f to Color(0xFF3CBC29),
    )
    return if (vertical) Brush.horizontalGradient(colorStops = stops) else Brush.verticalGradient(colorStops = stops)
}

@Composable
private fun PalettePanel(
    metrics: DesignMetrics,
    layout: MiiSingleScreenLayout,
    state: MiiEditorUiState,
    field: MiiColorField,
    focusable: Boolean,
    onEvent: (MiiEditorEvent) -> Unit,
) {
    val selectedIndex = state.draft.colorValue(field)
    val palette = field.palette()
    val order = field.paletteDisplayOrder()
    val geometry = layout.palette(order.size, MiiEditorColors.PALETTE_COLUMNS)
    FigmaPillSurface(
        metrics = metrics,
        modifier = Modifier
            .at(metrics, geometry.panel)
            .testTag("mii_palette_panel")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        shape = RoundedCornerShape(metrics.dp(PALETTE_PANEL_RADIUS)),
    )
    val swatchScale = geometry.swatch / 80f
    val swatchShape = RoundedCornerShape(metrics.dp(geometry.swatch / 2f))
    order.forEachIndexed { position, index ->
        val color = palette[index]
        val selected = index == selectedIndex
        val neighbors = mapOf(
            FocusDirection.Left to "mii_palette_${order[(position + order.size - 1) % order.size]}",
            FocusDirection.Right to "mii_palette_${order[(position + 1) % order.size]}",
        )
        Box(
            modifier = Modifier
                .at(metrics, geometry.swatchAt(position))
                .clip(swatchShape)
                .pocketFrame(
                    color,
                    metrics.dp((if (selected) 9f else 5f) * swatchScale),
                    if (selected) PocketActionGreen else swatchBorder(-1, color),
                    swatchShape,
                )
                .testTag("mii_palette_$index")
                .then(
                    if (focusable) {
                        Modifier.controllerTarget(
                            "mii_palette_$index",
                            layer = MII_ADJUSTMENT_FOCUS_LAYER,
                            cornerRadius = geometry.swatch / 2f,
                            neighbors = neighbors,
                        ) { onEvent(MiiEditorEvent.SelectColor(field, index)) }
                    } else {
                        Modifier
                    },
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = { onEvent(MiiEditorEvent.SelectColor(field, index)) },
                ),
        )
    }
}

@Composable
private fun DiscardPrompt(
    metrics: DesignMetrics,
    layout: MiiSingleScreenLayout,
    onEvent: (MiiEditorEvent) -> Unit,
) {
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .testTag("mii_discard_overlay")
            .controllerFocusBarrier("mii_discard_overlay", layer = MII_DISCARD_FOCUS_LAYER)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onEvent(MiiEditorEvent.DismissDiscardPrompt) },
    )
    val width = minOf(DISCARD_PANEL_WIDTH, layout.width - 2f * SINGLE_SIDE_MARGIN)
    val left = (layout.width - width) / 2f
    val top = (layout.height - DISCARD_PANEL_HEIGHT) / 2f
    Box(
        Modifier
            .designBounds(metrics, left, top + 14f, width, DISCARD_PANEL_HEIGHT)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, left, top, width, DISCARD_PANEL_HEIGHT)
            .graphicsLayer { translationY = entrance.value }
            .clip(panelShape)
            .pocketFrame(greyPanelBrush(), metrics.dp(15f), Color(0xFF9F9F9F), panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("mii_discard_panel"),
    ) {
        Text(
            text = "Discard changes?",
            modifier = Modifier.designBounds(metrics, 60f, 44f, width - 120f, 90f),
            color = Color(0xFF5C5C5C),
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(70f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = "Your Piip goes back to its last save.",
            modifier = Modifier.designBounds(metrics, 90f, 148f, width - 180f, 96f),
            color = Color(0x8F575757),
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            textAlign = TextAlign.Center,
        )
        val buttonWidth = (width - 140f) / 2f
        val buttonShape = RoundedCornerShape(metrics.dp(118f))
        DiscardButton(
            metrics = metrics,
            modifier = Modifier.designBounds(metrics, 60f, 300f, buttonWidth, 150f),
            label = "Keep editing",
            tag = MII_DISCARD_KEEP_TAG,
            fill = cancelButtonBrush(),
            border = Color(0xFF8A8A8A),
            shape = buttonShape,
        ) { onEvent(MiiEditorEvent.DismissDiscardPrompt) }
        DiscardButton(
            metrics = metrics,
            modifier = Modifier.designBounds(metrics, 80f + buttonWidth, 300f, buttonWidth, 150f),
            label = "Discard",
            tag = MII_DISCARD_CONFIRM_TAG,
            fill = redButtonBrush(),
            border = Color(0xFFC24B4B),
            shape = buttonShape,
        ) { onEvent(MiiEditorEvent.Cancel) }
    }
}

@Composable
private fun DiscardButton(
    metrics: DesignMetrics,
    modifier: Modifier,
    label: String,
    tag: String,
    fill: Brush,
    border: Color,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .pocketFrame(fill, metrics.dp(20.152f), border, shape)
            .testTag(tag)
            .controllerTarget(tag, layer = MII_DISCARD_FOCUS_LAYER) { onClick() }
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
            fontSize = metrics.sp(44f),
            maxLines = 1,
        )
    }
}
