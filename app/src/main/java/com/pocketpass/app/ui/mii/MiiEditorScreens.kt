package com.pocketpass.app.ui.mii

import android.animation.ValueAnimator
import androidx.annotation.RawRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.pocketpass.app.R
import com.pocketpass.app.mii.MiiAdjustmentDescriptor
import com.pocketpass.app.mii.MiiAdjustmentField
import com.pocketpass.app.mii.MiiCategory
import com.pocketpass.app.mii.MiiColorField
import com.pocketpass.app.mii.MiiEditorEvent
import com.pocketpass.app.mii.MiiEditorUiState
import com.pocketpass.app.mii.MiiToggleField
import com.pocketpass.app.mii.MiiTraitField
import com.pocketpass.app.mii.toggleValue
import com.pocketpass.app.mii.traitValue
import com.pocketpass.app.mii.colorValue
import com.pocketpass.app.mii.isPalette
import com.pocketpass.app.mii.verticalUpDelta
import com.pocketpass.app.model.StatusInfo
import com.pocketpass.app.ui.BOTTOM_DESIGN_HEIGHT
import com.pocketpass.app.ui.DesignAnchor
import com.pocketpass.app.ui.DesignBackdrop
import com.pocketpass.app.ui.DesignBox
import com.pocketpass.app.ui.BOTTOM_DESIGN_WIDTH
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.DesignSurface
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.TOP_DESIGN_HEIGHT
import com.pocketpass.app.ui.TOP_DESIGN_WIDTH
import com.pocketpass.app.ui.anchoredBounds
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.StatusConnectivityContent
import com.pocketpass.app.ui.components.pocketBorder
import com.pocketpass.app.ui.components.roundedShadowMask
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.pocketShadow
import com.pocketpass.app.ui.screens.cancelButtonBrush
import com.pocketpass.app.ui.screens.greyPanelBrush
import com.pocketpass.app.ui.screens.redButtonBrush
import com.pocketpass.app.ui.controller.FocusDirection
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.LocalControllerFocusGroup
import com.pocketpass.app.ui.controller.controllerFocusBarrier
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.designBounds
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

private val PocketBlue = Color(0xFF5E9AAC)
private val PocketText = Color(0xFF1D596B)
private val PocketPaleGreen = Color(0xFFBDF8CB)
private val PocketSelectedGreen = Color(0xFFA8FFC7)
private val PocketActionGreen = Color(0xFF4BC252)
private val PocketShadow = Color.Black.copy(alpha = 0.32f)

@Composable
fun MiiEditorTopScreen(
    state: MiiEditorUiState,
    status: StatusInfo,
    onEvent: (MiiEditorEvent) -> Unit,
    modifier: Modifier = Modifier,
    saveOnly: Boolean = false,
    liveRender: @Composable BoxScope.() -> Unit,
) {
    DesignSurface(
        designWidth = TOP_DESIGN_WIDTH,
        designHeight = TOP_DESIGN_HEIGHT,
        modifier = modifier,
    ) { metrics ->
        val entrance = rememberMiiEditorEntrance()
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val entering = entrance.value < 1f
                    compositingStrategy = if (entering) {
                        CompositingStrategy.Offscreen
                    } else {
                        CompositingStrategy.Auto
                    }
                    alpha = entrance.value
                    translationY = (1f - entrance.value) * 48f
                },
        ) {
            FigmaAsset(
                resource = R.raw.mii_editor_top_background,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
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
                    .graphicsLayer {
                        blendMode = BlendMode.Overlay
                    }
                    .background(Color(0x991BFFEC)),
            )

            FigmaAsset(
                resource = R.raw.mii_editor_ground_shadow,
                modifier = Modifier.designBounds(
                    metrics,
                    x = 720.64f,
                    y = 853.32f,
                    width = 476.162f,
                    height = 347.864f,
                ),
            )

            DesignBackdrop(metrics) {
                Box(Modifier.fillMaxSize().clipToBounds(), content = liveRender)
            }

            MiiTopStatusPills(metrics, status)
            ContinuePanel(
                metrics = metrics,
                enabled = state.canContinue,
                label = if (saveOnly || state.selectedCategory == MiiCategory.entries.last()) {
                    "Save"
                } else {
                    "Continue"
                },
                glyph = !saveOnly,
                onClick = { onEvent(if (saveOnly) MiiEditorEvent.Save else MiiEditorEvent.Continue) },
            )
        }
    }
}

@Composable
fun MiiEditorBottomScreen(
    state: MiiEditorUiState,
    onEvent: (MiiEditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    DesignSurface(
        designWidth = BOTTOM_DESIGN_WIDTH,
        designHeight = BOTTOM_DESIGN_HEIGHT,
        modifier = modifier,
    ) { metrics ->
        val overlayOpen = state.activeAdjustment != null
        var retainedAdjustment by remember {
            mutableStateOf<MiiAdjustmentField?>(state.activeAdjustment)
        }
        var retainedOverlayState by remember { mutableStateOf(state) }
        state.activeAdjustment?.let { activeAdjustment ->
            SideEffect {
                retainedAdjustment = activeAdjustment
                retainedOverlayState = state
            }
        }
        val animatedAdjustment = state.activeAdjustment ?: retainedAdjustment
        val animatedOverlayState = if (overlayOpen) state else retainedOverlayState
        val promptOpen = state.discardPromptVisible
        val paletteField = state.colorPaletteField
        val paletteOpen = paletteField != null
        val editorBlur = animateFloatAsState(
            targetValue = if (overlayOpen || promptOpen || paletteOpen) 3.05f else 0f,
            animationSpec = tween(durationMillis = 180),
            label = "Mii editor customization blur",
        )

        val entrance = rememberMiiEditorEntrance()
        val focus = LocalControllerFocus.current
        LaunchedEffect(Unit) {
            focus?.focus(miiCategoryTag(state.selectedCategory), reveal = false)
        }
        LaunchedEffect(paletteOpen) {
            if (paletteOpen) {
                paletteField?.let { field ->
                    focus?.focus("mii_palette_${state.draft.colorValue(field)}", reveal = false)
                }
            } else if (focus?.focusId?.startsWith("mii_palette_") == true) {
                state.chipPaletteField()?.let { field ->
                    focus.focus(miiColorPaletteTag(field), reveal = false)
                }
            }
        }
        LaunchedEffect(promptOpen) {
            if (promptOpen) {
                focus?.focus(MII_DISCARD_KEEP_TAG, reveal = false)
            } else if (focus?.focusId == null) {
                focus?.focus(miiCategoryTag(state.selectedCategory), reveal = false)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val radius = metrics.dp(editorBlur.value).toPx()
                    renderEffect = if (radius > 0.01f) {
                        BlurEffect(radius, radius, TileMode.Clamp)
                    } else {
                        null
                    }
                    alpha = entrance.value
                    translationY = (1f - entrance.value) * 48f
                },
        ) {
            MiiEditorBackground(metrics)
            CompositionLocalProvider(LocalControllerFocusGroup provides "mii_rail") {
                DesignBox(metrics, 0f, 0f, 1240f, 1080f, DesignAnchor.Start, DesignAnchor.Stretch) {
                    CategoryRail(
                        metrics = metrics,
                        selected = state.selectedCategory,
                        onSelect = { onEvent(MiiEditorEvent.SelectCategory(it)) },
                    )
                }
            }

            val entries = state.traitEntries()
            CompositionLocalProvider(LocalControllerFocusGroup provides "mii_traits") {
                key(state.selectedCategory, entries.first().field) {
                    TraitGrid(
                        metrics = metrics,
                        state = state,
                        entries = entries,
                        onEvent = onEvent,
                    )
                }
            }

            CompositionLocalProvider(LocalControllerFocusGroup provides "mii_colors") {
                key(state.selectedCategory, state.activeColorField) {
                    ColorSwatches(
                        metrics = metrics,
                        state = state,
                        onEvent = onEvent,
                    )
                }
            }
            CompositionLocalProvider(LocalControllerFocusGroup provides "mii_adjust") {
                DesignBox(metrics, 0f, 0f, 1240f, 1080f, DesignAnchor.End) {
                    AdjustmentButtons(
                        metrics = metrics,
                        state = state,
                        onEvent = onEvent,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = overlayOpen,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 140)),
        ) {
            AdjustmentScrim(
                onClose = { onEvent(MiiEditorEvent.CloseAdjustment) },
            )
        }

        val panelProgress = remember { Animatable(0f) }
        LaunchedEffect(overlayOpen) {
            if (!ValueAnimator.areAnimatorsEnabled()) {
                panelProgress.snapTo(if (overlayOpen) 1f else 0f)
                return@LaunchedEffect
            }
            if (overlayOpen) {
                panelProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 1f, stiffness = 380f),
                )
            } else {
                panelProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                )
            }
        }
        val panelShown by remember { derivedStateOf { panelProgress.value > 0.001f } }
        if (panelShown || overlayOpen) {
            animatedAdjustment?.let { field ->
                Box(Modifier.fillMaxSize()) {
                    if (field.verticalUpDelta != null) {
                        VerticalAdjustmentPanel(
                            metrics = metrics,
                            state = animatedOverlayState,
                            field = field,
                            rowTop = ADJUSTMENT_ROW_TOPS[field.visualSlot().ordinal],
                            progress = { panelProgress.value },
                            onEvent = onEvent,
                        )
                    } else {
                        AdjustmentPanel(
                            metrics = metrics,
                            state = animatedOverlayState,
                            field = field,
                            rowTop = ADJUSTMENT_ROW_TOPS[field.visualSlot().ordinal],
                            progress = { panelProgress.value },
                            onEvent = onEvent,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = paletteOpen && paletteField != null,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 140)),
        ) {
            Box(Modifier.fillMaxSize()) {
                AdjustmentScrim(
                    onClose = { onEvent(MiiEditorEvent.CloseColorPalette) },
                )
                paletteField?.let { field ->
                    ColorPalettePanel(
                        metrics = metrics,
                        state = state,
                        field = field,
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
            DiscardChangesPrompt(metrics = metrics, onEvent = onEvent)
        }
    }
}

@Composable
private fun DiscardChangesPrompt(
    metrics: DesignMetrics,
    onEvent: (MiiEditorEvent) -> Unit,
) {
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        )
    }
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 1240f, 1080f)
            .background(Color.Black.copy(alpha = 0.18f))
            .testTag("mii_discard_overlay")
            .controllerFocusBarrier("mii_discard_overlay", layer = MII_DISCARD_FOCUS_LAYER)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onEvent(MiiEditorEvent.DismissDiscardPrompt) },
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
            .pocketFrame(greyPanelBrush(), metrics.dp(15f), Color(0xFF9F9F9F), panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("mii_discard_panel"),
    ) {
        Text(
            text = "Discard changes?",
            modifier = Modifier.designBounds(metrics, 60f, 44f, 960f, 90f),
            color = Color(0xFF5C5C5C),
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(70f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = "Your Mii will go back to how it was last saved.",
            modifier = Modifier.designBounds(metrics, 90f, 148f, 900f, 96f),
            color = Color(0x8F575757),
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(34f),
            textAlign = TextAlign.Center,
        )
        val buttonShape = RoundedCornerShape(metrics.dp(118f))
        Box(
            modifier = Modifier
                .designBounds(metrics, 60f, 300f, 470f, 150f)
                .clip(buttonShape)
                .pocketFrame(
                    cancelButtonBrush(),
                    metrics.dp(20.152f),
                    Color(0xFF8A8A8A),
                    buttonShape,
                )
                .testTag(MII_DISCARD_KEEP_TAG)
                .controllerTarget(MII_DISCARD_KEEP_TAG, layer = MII_DISCARD_FOCUS_LAYER) {
                    onEvent(MiiEditorEvent.DismissDiscardPrompt)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onEvent(MiiEditorEvent.DismissDiscardPrompt) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Keep editing",
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
                .pocketFrame(redButtonBrush(), metrics.dp(20.152f), Color(0xFFC24B4B), buttonShape)
                .testTag(MII_DISCARD_CONFIRM_TAG)
                .controllerTarget(MII_DISCARD_CONFIRM_TAG, layer = MII_DISCARD_FOCUS_LAYER) {
                    onEvent(MiiEditorEvent.Cancel)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onEvent(MiiEditorEvent.Cancel) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Discard",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(44f),
                maxLines = 1,
            )
        }
    }
}

private const val MII_DISCARD_FOCUS_LAYER = 20
private const val MII_DISCARD_KEEP_TAG = "mii_discard_keep"
private const val MII_DISCARD_CONFIRM_TAG = "mii_discard_confirm"

@Composable
private fun MiiEditorBackground(metrics: DesignMetrics) {
    DesignBackdrop(metrics) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFFDDFFFC),
                            0.4375f to Color(0xFFDDFFFC),
                            1f to Color(0xFF6CD7CE),
                        ),
                    ),
                ),
        )
        FigmaAsset(
            resource = R.raw.mii_editor_bottom_pattern,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.19f,
        )
    }
}

private data class CategoryVisual(
    val category: MiiCategory,
    @RawRes val icon: Int,
    val width: Float,
    val height: Float,
)

private const val RAIL_DIVIDER_HEIGHT = 9.622f
private const val RAIL_SURFACE_WIDTH = 178f
private const val RAIL_STRIPE_CENTER = 185.5f
private const val RING_STROKE = 20f
private const val CATEGORY_RING_X = RING_STROKE
private const val CATEGORY_RING_WIDTH = RAIL_STRIPE_CENTER - RING_STROKE / 2f - CATEGORY_RING_X
private const val CATEGORY_RING_INSET_Y = RING_STROKE / 2f - RAIL_DIVIDER_HEIGHT / 2f
private const val CATEGORY_RING_RADIUS = 6f
private val ADJUSTMENT_ROW_TOPS = listOf(-0.3f, 223.19f, 446.68f, 670.17f, 894.66f)
private const val ADJUSTMENT_PANEL_LEFT = 253.5f
private const val ADJUSTMENT_PANEL_WIDTH = 987f
private const val ADJUSTMENT_PANEL_HEIGHT = 185f
private const val ADJUSTMENT_BUTTON_LEFT = 998f
private const val ADJUSTMENT_BUTTON_WIDTH = 242f
private const val RIGHT_FLUSH_RING_EXTENSION = 140f
private const val MII_ADJUSTMENT_FOCUS_LAYER = 10
private const val MII_ADJUSTMENT_SLIDER_TAG = "mii_adjustment_slider"

private fun miiCategoryTag(category: MiiCategory): String = "mii_category_${category.name}"

private val categoryVisuals = listOf(
    CategoryVisual(MiiCategory.Face, R.raw.mii_editor_category_face, 83f, 83f),
    CategoryVisual(MiiCategory.Hair, R.raw.mii_editor_category_hair, 83f, 83f),
    CategoryVisual(MiiCategory.Eyebrows, R.raw.mii_editor_category_eyebrow, 96f, 43f),
    CategoryVisual(MiiCategory.Eyes, R.raw.mii_editor_category_eye, 89f, 62f),
    CategoryVisual(MiiCategory.Nose, R.raw.mii_editor_category_nose, 84f, 61f),
    CategoryVisual(MiiCategory.Mouth, R.raw.mii_editor_category_mouth, 89f, 58f),
    CategoryVisual(MiiCategory.Glasses, R.raw.mii_editor_category_glasses, 99f, 35f),
    CategoryVisual(MiiCategory.Body, R.raw.mii_editor_category_body, 75f, 85f),
)

@Composable
private fun rememberMiiEditorEntrance(): Animatable<Float, AnimationVector1D> {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(entrance) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            entrance.snapTo(1f)
            return@LaunchedEffect
        }
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        )
    }
    return entrance
}

@Composable
private fun CategoryRail(
    metrics: DesignMetrics,
    selected: MiiCategory,
    onSelect: (MiiCategory) -> Unit,
) {
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 193f, 1080f)
            .offset(y = metrics.dp(14f))
            .pocketShadow(metrics, 0f),
    )
    Box(
        Modifier
            .designBounds(metrics, 0f, 0f, 193f, 1080f)
            .background(
                Brush.horizontalGradient(
                    listOf(PocketPaleGreen, Color.White),
                ),
            ),
    )
    Box(
        Modifier
            .designBounds(metrics, RAIL_SURFACE_WIDTH, 0f, 15f, 1080f)
            .background(PocketBlue),
    )

    categoryVisuals.forEachIndexed { index, visual ->
        val top = index * 136.077f
        Box(
            Modifier
                .designBounds(metrics, 0f, top, 208f, 126.455f)
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
                x = (RAIL_SURFACE_WIDTH - visual.width) / 2f,
                y = top + (126.455f - visual.height) / 2f,
                width = visual.width,
                height = visual.height,
            ),
        )
        Box(
            Modifier
                .designBounds(
                    metrics,
                    x = CATEGORY_RING_X,
                    y = top + CATEGORY_RING_INSET_Y,
                    width = CATEGORY_RING_WIDTH,
                    height = 126.455f - CATEGORY_RING_INSET_Y * 2f,
                )
                .controllerTarget(miiCategoryTag(visual.category), cornerRadius = CATEGORY_RING_RADIUS) {
                    onSelect(visual.category)
                },
        )

        if (index < categoryVisuals.lastIndex) {
            FigmaAsset(
                resource = R.raw.mii_editor_rail_divider,
                modifier = Modifier.designBounds(
                    metrics,
                    x = 0f,
                    y = top + 126.455f,
                    width = RAIL_SURFACE_WIDTH,
                    height = 9.622f,
                ),
            )
        }
    }
}

@Composable
private fun TraitGrid(
    metrics: DesignMetrics,
    state: MiiEditorUiState,
    entries: List<TraitEntry>,
    onEvent: (MiiEditorEvent) -> Unit,
) {
    val maxPage = ((entries.size - 1).coerceAtLeast(0) / 12)
    val initialPage = state.currentTraitPage.coerceIn(0, maxPage)
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialPage * 12)

    LaunchedEffect(gridState, state.selectedCategory) {
        snapshotFlow { gridState.firstVisibleItemIndex / 12 }
            .distinctUntilChanged()
            .collect { page ->
                onEvent(MiiEditorEvent.SetTraitPage(page.coerceIn(0, maxPage)))
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.designBounds(
            metrics,
            x = 254f,
            y = 0f,
            width = 473.57f,
            height = 1080f,
        ),
        contentPadding = PaddingValues(
            top = metrics.dp(38.49f),
            bottom = metrics.dp(38.49f),
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.dp(16f)),
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(38.489f)),
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
                onClick = {
                    onEvent(MiiEditorEvent.SelectTrait(entry.field, entry.index))
                },
            )
        }
    }
}

@Composable
private fun TraitPill(
    metrics: DesignMetrics,
    field: MiiTraitField,
    index: Int,
    state: MiiEditorUiState,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val locked = field == MiiTraitField.HatType && index >= 0 && index !in state.ownedHatTypes
    FigmaPillSurface(
        metrics = metrics,
        modifier = Modifier
            .requiredHeight(metrics.dp(153.73f))
            .testTag("mii_trait_${field.name}_$index")
            .controllerTarget("mii_trait_${field.name}_$index", cornerRadius = 76.865f) {
                if (!locked) onClick()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !locked,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(metrics.dp(76.865f)),
        selected = selected,
    ) {
        if (field == MiiTraitField.Gender) {
            FigmaAsset(
                resource = if (index == 0) {
                    R.raw.mii_editor_gender_male
                } else {
                    R.raw.mii_editor_gender_female
                },
                modifier = Modifier.requiredSize(metrics.dp(112f)),
                contentScale = ContentScale.Fit,
                description = if (index == 0) "Male" else "Female",
            )
        } else if (field == MiiTraitField.HatType && index < 0) {
            FigmaAsset(
                resource = R.raw.mii_editor_hat_none,
                modifier = Modifier.requiredSize(metrics.dp(112f)),
                contentScale = ContentScale.Fit,
                description = "No hat",
            )
        } else {
            val context = LocalContext.current
            val bytes = remember(field, index, state.draft) {
                MiiTraitIconCatalog.icon(
                    context = context.applicationContext,
                    field = field,
                    index = index,
                    appearance = state.draft,
                    centerContent = field == MiiTraitField.HatType,
                )
            }
            if (bytes != null) {
                Crossfade(
                    targetState = bytes,
                    modifier = Modifier
                        .requiredSize(metrics.dp(112f))
                        .alpha(if (locked) 0.4f else 1f),
                    animationSpec = tween(
                        durationMillis = 220,
                        easing = FastOutSlowInEasing,
                    ),
                    label = "Trait icon swap",
                ) { iconBytes ->
                    AsyncImage(
                        model = iconBytes,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(metrics.dp(36f))
                    .requiredSize(metrics.dp(44f))
                    .clip(CircleShape)
                    .background(Color.White)
                    .pocketBorder(metrics.dp(4f), PocketBlue, CircleShape)
                    .testTag("mii_trait_lock_$index"),
                contentAlignment = Alignment.Center,
            ) {
                FigmaAsset(
                    resource = R.raw.mii_editor_lock,
                    modifier = Modifier.requiredSize(metrics.dp(26f)),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}


@Composable
private fun ColorSwatches(
    metrics: DesignMetrics,
    state: MiiEditorUiState,
    onEvent: (MiiEditorEvent) -> Unit,
) {
    if (state.descriptor.colors.isEmpty()) return
    if (state.activeColorField == MiiColorField.Hat && state.draft.extHatType < 0) return
    val paletteField = state.chipPaletteField()
    if (paletteField != null) {
        val paletteColors = paletteField.palette()
        val paletteIndex = state.draft.colorValue(paletteField)
        ColorPaletteChip(
            metrics = metrics,
            field = paletteField,
            color = paletteColors.getOrElse(paletteIndex) { paletteColors.first() },
            onOpen = { onEvent(MiiEditorEvent.OpenColorPalette(paletteField)) },
        )
        return
    }
    val colorDescriptor = state.descriptor.colors.firstOrNull {
        it.field == state.activeColorField
    } ?: state.descriptor.colors.firstOrNull { it.figmaPrimary }
    val activeField = colorDescriptor?.field
    val validCount = colorDescriptor?.optionCount ?: 0
    val palette = activeField?.palette() ?: MiiEditorColors.figmaEyes
    val displayCount = maxOf(6, validCount)
    val available = 1080f - COLOR_COLUMN_TOP - COLOR_COLUMN_BOTTOM
    val swatchSize = minOf(
        COLOR_SWATCH_SIZE,
        (available - (displayCount - 1) * COLOR_SWATCH_GAP) / displayCount,
    )
    val swatchScale = swatchSize / COLOR_SWATCH_SIZE

    LazyColumn(
        modifier = Modifier.designBounds(
            metrics,
            x = COLOR_COLUMN_X,
            y = 0f,
            width = COLOR_SWATCH_SIZE,
            height = 1080f,
        ),
        contentPadding = PaddingValues(
            top = metrics.dp(COLOR_COLUMN_TOP),
            bottom = metrics.dp(COLOR_COLUMN_BOTTOM),
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(metrics.dp(COLOR_SWATCH_GAP)),
    ) {
        items(
            count = displayCount,
            key = { index -> "${activeField ?: "figma"}:$index" },
        ) { index ->
            val enabled = activeField != null && index < validCount
            val color = palette.getOrElse(index) {
                MiiEditorColors.figmaEyes[index % MiiEditorColors.figmaEyes.size]
            }
            val selected = enabled && state.selectedColorIndex == index
            val shape = RoundedCornerShape(metrics.dp(swatchSize / 2f))
            Box(
                modifier = Modifier
                    .requiredSize(metrics.dp(swatchSize))
                    .clip(shape)
                    .pocketFrame(
                        color,
                        metrics.dp((if (selected) 15f else 10f) * swatchScale),
                        if (selected) {
                            PocketActionGreen
                        } else {
                            swatchBorder(index, color)
                        },
                        shape,
                    )
                    .then(
                        if (enabled) {
                            Modifier
                                .controllerTarget(
                                    "mii_color_${activeField.name}_$index",
                                    cornerRadius = swatchSize / 2f,
                                ) { onEvent(MiiEditorEvent.SelectColor(activeField, index)) }
                                .clickable(
                                    interactionSource = remember {
                                        MutableInteractionSource()
                                    },
                                    indication = null,
                                    role = Role.Button,
                                    onClick = {
                                        onEvent(MiiEditorEvent.SelectColor(activeField, index))
                                    },
                                )
                        } else {
                            Modifier.alpha(0.72f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun ColorPaletteChip(
    metrics: DesignMetrics,
    field: MiiColorField,
    color: Color,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(74.215f))
    Box(
        modifier = Modifier
            .designBounds(metrics, COLOR_COLUMN_X, COLOR_COLUMN_TOP, COLOR_SWATCH_SIZE, COLOR_SWATCH_SIZE)
            .clip(shape)
            .background(Brush.sweepGradient(PaletteWheel))
            .controllerTarget(miiColorPaletteTag(field), cornerRadius = 74.215f) { onOpen() }
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
                .requiredSize(metrics.dp(104f))
                .clip(RoundedCornerShape(metrics.dp(52f)))
                .background(Color.White)
                .padding(metrics.dp(10f))
                .clip(RoundedCornerShape(metrics.dp(42f)))
                .background(color),
        )
    }
}

@Composable
private fun ColorPalettePanel(
    metrics: DesignMetrics,
    state: MiiEditorUiState,
    field: MiiColorField,
    onEvent: (MiiEditorEvent) -> Unit,
) {
    val selectedIndex = state.draft.colorValue(field)
    FigmaPillSurface(
        metrics = metrics,
        modifier = Modifier
            .designBounds(metrics, PALETTE_X, PALETTE_Y, PALETTE_WIDTH, PALETTE_HEIGHT)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        shape = RoundedCornerShape(metrics.dp(PALETTE_RADIUS)),
    )
    val swatchShape = RoundedCornerShape(metrics.dp(PALETTE_SWATCH / 2f))
    val palette = field.palette()
    val order = field.paletteDisplayOrder()
    order.forEachIndexed { position, index ->
        val color = palette[index]
        val column = position % PALETTE_COLUMNS
        val row = position / PALETTE_COLUMNS
        val selected = index == selectedIndex
        val neighbors = mapOf(
            FocusDirection.Left to "mii_palette_${order[(position + order.size - 1) % order.size]}",
            FocusDirection.Right to "mii_palette_${order[(position + 1) % order.size]}",
        )
        Box(
            modifier = Modifier
                .designBounds(
                    metrics,
                    x = PALETTE_X + PALETTE_INSET_X + column * PALETTE_PITCH,
                    y = PALETTE_Y + PALETTE_INSET_Y + row * PALETTE_PITCH,
                    width = PALETTE_SWATCH,
                    height = PALETTE_SWATCH,
                )
                .clip(swatchShape)
                .pocketFrame(
                    color,
                    metrics.dp(if (selected) 9f else 5f),
                    if (selected) PocketActionGreen else swatchBorder(-1, color),
                    swatchShape,
                )
                .controllerTarget(
                    "mii_palette_$index",
                    layer = MII_ADJUSTMENT_FOCUS_LAYER,
                    cornerRadius = PALETTE_SWATCH / 2f,
                    neighbors = neighbors,
                ) { onEvent(MiiEditorEvent.SelectColor(field, index)) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = { onEvent(MiiEditorEvent.SelectColor(field, index)) },
                ),
        )
    }
}

private data class TraitEntry(
    val field: MiiTraitField,
    val index: Int,
)

private val FACIAL_HAIR_FIELDS = setOf(
    MiiTraitField.MustacheType,
    MiiTraitField.BeardType,
)

private val MiiEditorUiState.facialHairMode: Boolean
    get() = selectedCategory == MiiCategory.Mouth && activeTraitField in FACIAL_HAIR_FIELDS

private val MiiEditorUiState.hatMode: Boolean
    get() = selectedCategory == MiiCategory.Hair && activeTraitField == MiiTraitField.HatType

private fun MiiEditorUiState.traitEntries(): List<TraitEntry> {
    if (hatMode) {
        val hats = descriptor.traits.first { it.field == MiiTraitField.HatType }
        return listOf(TraitEntry(MiiTraitField.HatType, NO_HAT_INDEX)) +
            (0 until hats.optionCount).map { TraitEntry(MiiTraitField.HatType, it) }
    }
    val traits = if (facialHairMode) {
        descriptor.traits.filter { it.field in FACIAL_HAIR_FIELDS }
    } else {
        listOf(
            descriptor.traits.firstOrNull { it.field == activeTraitField }
                ?: descriptor.traits.first { it.figmaPrimary },
        )
    }
    val rows = traits.maxOf { it.optionCount }
    return (0 until rows).flatMap { index ->
        traits
            .filter { index < it.optionCount && index !in it.hiddenOptions }
            .map { TraitEntry(it.field, index) }
    }
}

private val PaletteWheel = listOf(
    Color(0xFFFF5A5A),
    Color(0xFFFFB347),
    Color(0xFFFFE85C),
    Color(0xFF7ED957),
    Color(0xFF4FC3F7),
    Color(0xFF8E7CFF),
    Color(0xFFFF7AC8),
    Color(0xFFFF5A5A),
)

private fun miiColorPaletteTag(field: MiiColorField): String = "mii_color_palette_${field.name}"

private fun MiiEditorUiState.chipPaletteField(): MiiColorField? {
    val activeField = descriptor.colors.firstOrNull { it.field == activeColorField }?.field
        ?: descriptor.colors.firstOrNull { it.figmaPrimary }?.field
    return activeField?.takeIf { it.isPalette }
}
private const val NO_HAT_INDEX = -1
private const val COLOR_COLUMN_X = 788.57f
private const val COLOR_COLUMN_TOP = 38.49f
private const val COLOR_SWATCH_SIZE = 148.43f
private const val COLOR_COLUMN_BOTTOM = 39.13f
private const val COLOR_SWATCH_GAP = 22.36f
private const val PALETTE_COLUMNS = MiiEditorColors.PALETTE_COLUMNS
private const val PALETTE_X = 228f
private const val PALETTE_Y = 40f
private const val PALETTE_WIDTH = 992f
private const val PALETTE_HEIGHT = 1000f
private const val PALETTE_RADIUS = 60f
private const val PALETTE_SWATCH = 80f
private const val PALETTE_PITCH = 92f
private const val PALETTE_INSET_X = 36f
private const val PALETTE_INSET_Y = 40f

private fun MiiColorField.palette(): List<Color> = when (this) {
    MiiColorField.Skin -> MiiEditorColors.skin
    MiiColorField.Eyes -> MiiEditorColors.eyes
    MiiColorField.Hair,
    MiiColorField.Eyebrows,
    MiiColorField.FacialHair,
    MiiColorField.Mouth,
    MiiColorField.Glasses,
    -> MiiEditorColors.common
    MiiColorField.Favorite,
    MiiColorField.Hat,
    MiiColorField.FacePaint,
    -> MiiEditorColors.favorite
}

private fun MiiColorField.paletteDisplayOrder(): List<Int> =
    if (palette() === MiiEditorColors.common) MiiEditorColors.commonDisplayOrder else palette().indices.toList()

private fun swatchBorder(index: Int, color: Color): Color = when (index) {
    0 -> Color(0xCC777777)
    1 -> Color(0xCCBFBFBF)
    2 -> Color.White.copy(alpha = 0.35f)
    3 -> Color.White.copy(alpha = 0.38f)
    4 -> Color.White.copy(alpha = 0.51f)
    5 -> Color.White.copy(alpha = 0.30f)
    else -> if (color.red + color.green + color.blue > 2.2f) {
        Color(0x66808080)
    } else {
        Color.White.copy(alpha = 0.38f)
    }
}

private enum class AdjustmentVisualSlot(
    @RawRes val icon: Int,
    val visualWidth: Float,
    val visualHeight: Float,
) {
    Vertical(R.raw.mii_editor_adjust_vertical, 56.703f, 96.385f),
    Horizontal(R.raw.mii_editor_adjust_horizontal, 96.385f, 56.703f),
    Rotate(R.raw.mii_editor_adjust_rotate, 84.179f, 96.383f),
    Scale(R.raw.mii_editor_adjust_scale, 81.469f, 81.469f),
    Spacing(R.raw.mii_editor_adjust_spacing, 101.355f, 81.084f),
}

@Composable
private fun AdjustmentButtons(
    metrics: DesignMetrics,
    state: MiiEditorUiState,
    onEvent: (MiiEditorEvent) -> Unit,
) {
    val relevant = state.relevantAdjustments()
        .filter { it.gate?.let(state.draft::toggleValue) != false }
    val railToggle = state.railToggle()
    AdjustmentVisualSlot.entries.forEachIndexed { index, slot ->
        val toggle = railToggle?.takeIf { slot == AdjustmentVisualSlot.Spacing }
        val field = relevant.fieldFor(slot)
        val activate: (() -> Unit)? = when {
            toggle != null -> {
                { onEvent(toggle.event) }
            }
            field != null -> {
                { onEvent(MiiEditorEvent.OpenAdjustment(field)) }
            }
            else -> null
        }
        val selected = if (toggle != null) toggle.on else field != null && field == state.activeAdjustment
        val top = ADJUSTMENT_ROW_TOPS[index]
        val height = if (index == 3) 186f else 185f
        FigmaPillSurface(
            metrics = metrics,
            modifier = Modifier
                .designBounds(
                    metrics,
                    x = 998f,
                    y = top,
                    width = 242f,
                    height = height,
                )
                .then(
                    if (activate != null) {
                        Modifier
                            .clickable(
                                interactionSource = remember {
                                    MutableInteractionSource()
                                },
                                indication = null,
                                role = Role.Button,
                                onClick = activate,
                            )
                    } else {
                        Modifier
                    },
                ),
            shape = RoundedCornerShape(
                topStart = metrics.dp(92.5f),
                bottomStart = metrics.dp(92.5f),
            ),
            selected = selected,
            horizontalFill = true,
        ) {
            if (toggle != null) {
                FigmaAsset(
                    resource = toggle.icon,
                    modifier = Modifier.requiredSize(metrics.dp(83f), metrics.dp(83f)),
                )
            } else {
                AdjustmentIcon(
                    metrics = metrics,
                    slot = slot,
                    modifier = Modifier.alpha(if (activate == null) 0.26f else 1f),
                )
            }
        }
        if (activate != null) {
            Box(
                Modifier
                    .designBounds(
                        metrics,
                        x = 998f,
                        y = top,
                        width = 242f + RIGHT_FLUSH_RING_EXTENSION,
                        height = height,
                    )
                    .controllerTarget("mii_adjust_${slot.name}", cornerRadius = 92.5f) {
                        activate()
                    },
            )
        }
    }
}

private class RailToggle(
    @RawRes val icon: Int,
    val on: Boolean,
    val event: MiiEditorEvent,
)

private fun MiiEditorUiState.railToggle(): RailToggle? = when (selectedCategory) {
    MiiCategory.Hair -> RailToggle(
        icon = R.raw.mii_editor_adjust_hat,
        on = hatMode,
        event = MiiEditorEvent.SelectTraitField(
            if (hatMode) MiiTraitField.HairType else MiiTraitField.HatType,
        ),
    )
    MiiCategory.Face -> RailToggle(
        icon = R.raw.mii_editor_adjust_mole,
        on = draft.moleEnabled,
        event = MiiEditorEvent.SetToggle(MiiToggleField.Mole, !draft.moleEnabled),
    )
    MiiCategory.Mouth -> RailToggle(
        icon = R.raw.mii_editor_adjust_facial_hair,
        on = facialHairMode,
        event = MiiEditorEvent.SelectTraitField(
            if (facialHairMode) MiiTraitField.MouthType else MiiTraitField.MustacheType,
        ),
    )
    else -> null
}

private fun MiiEditorUiState.relevantAdjustments(): List<MiiAdjustmentDescriptor> {
    val all = descriptor.adjustments
    return when (activeTraitField) {
        MiiTraitField.MustacheType, MiiTraitField.BeardType -> all.filter {
            it.field.name.startsWith("Mustache")
        }
        MiiTraitField.MouthType -> all.filter {
            it.field.name.startsWith("Mouth")
        }
        else -> all
    }
}

private fun List<MiiAdjustmentDescriptor>.fieldFor(
    slot: AdjustmentVisualSlot,
): MiiAdjustmentField? = when (slot) {
    AdjustmentVisualSlot.Vertical -> firstOrNull {
        it.field.name.endsWith("YPosition") || it.field == MiiAdjustmentField.Height
    }?.field
    AdjustmentVisualSlot.Horizontal -> firstOrNull {
        it.field.name.endsWith("VerticalStretch") ||
            it.field.name.endsWith("HorizontalStretch") ||
            it.field == MiiAdjustmentField.MoleXPosition
    }?.field
    AdjustmentVisualSlot.Rotate -> firstOrNull {
        it.field.name.endsWith("Rotation")
    }?.field
    AdjustmentVisualSlot.Scale -> firstOrNull {
        it.field.name.endsWith("Scale") || it.field == MiiAdjustmentField.Build
    }?.field
    AdjustmentVisualSlot.Spacing -> firstOrNull {
        it.field.name.endsWith("Spacing")
    }?.field
}

private fun MiiAdjustmentField.visualSlot(): AdjustmentVisualSlot = when {
    name.endsWith("YPosition") || this == MiiAdjustmentField.Height ->
        AdjustmentVisualSlot.Vertical
    name.endsWith("VerticalStretch") ||
        name.endsWith("HorizontalStretch") ||
        this == MiiAdjustmentField.MoleXPosition ->
        AdjustmentVisualSlot.Horizontal
    name.endsWith("Rotation") -> AdjustmentVisualSlot.Rotate
    name.endsWith("Scale") || this == MiiAdjustmentField.Build ->
        AdjustmentVisualSlot.Scale
    name.endsWith("Spacing") -> AdjustmentVisualSlot.Spacing
    else -> AdjustmentVisualSlot.Scale
}

@Composable
private fun AdjustmentIcon(
    metrics: DesignMetrics,
    slot: AdjustmentVisualSlot,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.requiredSize(
            metrics.dp(slot.visualWidth),
            metrics.dp(slot.visualHeight),
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (slot == AdjustmentVisualSlot.Horizontal) {
            FigmaAsset(
                resource = slot.icon,
                modifier = Modifier
                    .requiredSize(metrics.dp(56.703f), metrics.dp(96.385f))
                    .graphicsLayer { rotationZ = 90f },
            )
        } else {
            FigmaAsset(
                resource = slot.icon,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun AdjustmentScrim(
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x80171717))
            .controllerFocusBarrier("mii_adjustment_scrim", layer = MII_ADJUSTMENT_FOCUS_LAYER)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
    )
}

@Composable
private fun AdjustmentPanel(
    metrics: DesignMetrics,
    state: MiiEditorUiState,
    field: MiiAdjustmentField,
    rowTop: Float,
    progress: () -> Float,
    onEvent: (MiiEditorEvent) -> Unit,
) {
    val descriptor = state.descriptor.adjustments.firstOrNull {
        it.field == field
    } ?: return
    val value = state.activeAdjustmentValue ?: descriptor.defaultValue
    val range = (descriptor.maximum - descriptor.minimum).coerceAtLeast(1)
    val fraction = ((value - descriptor.minimum).toFloat() / range).coerceIn(0f, 1f)
    val panelShape = RoundedCornerShape(
        topStart = metrics.dp(92.5f),
        bottomStart = metrics.dp(92.5f),
    )

    FigmaPillSurface(
        metrics = metrics,
        modifier = Modifier
            .adjustmentPanelBounds(metrics, rowTop, progress)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        shape = panelShape,
        horizontalFill = true,
        shadowAlpha = { adjustmentShadowAlpha(progress()) },
    )
    Box(
        Modifier
            .adjustmentPanelBounds(metrics, rowTop, progress, extraWidth = RIGHT_FLUSH_RING_EXTENSION)
            .controllerTarget(
                MII_ADJUSTMENT_SLIDER_TAG,
                layer = MII_ADJUSTMENT_FOCUS_LAYER,
                cornerRadius = 92.5f,
            ) { onEvent(MiiEditorEvent.CloseAdjustment) },
    )

    val slot = field.visualSlot()
    Box(
        modifier = Modifier.designBounds(metrics, 96.385f, 138f) {
            Offset(adjustmentPanelLeft(progress()) + ADJUSTMENT_ICON_OFFSET, rowTop + 23.5f)
        },
        contentAlignment = Alignment.Center,
    ) {
        AdjustmentIcon(metrics, slot)
    }

    val trackX = 451.885f
    val trackY = rowTop + 56f
    val trackWidth = 751f
    val trackHeight = 73f
    val trackShape = RoundedCornerShape(metrics.dp(36.5f))
    val trackInset = 20.152f
    val usableWidth = trackWidth - trackInset * 2f
    val fillWidth = trackInset * 2f + usableWidth * fraction

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = adjustmentContentAlpha(progress()) },
    ) {
        Box(
            Modifier
                .designBounds(
                    metrics,
                    x = trackX,
                    y = trackY + 15.674f,
                    width = trackWidth,
                    height = trackHeight,
                )
                .clip(trackShape)
                .background(PocketShadow),
        )
        Box(
            Modifier
                .designBounds(
                    metrics,
                    x = trackX,
                    y = trackY,
                    width = trackWidth,
                    height = trackHeight,
                )
                .clip(trackShape)
                .pocketFrame(Color.White, metrics.dp(20.152f), Color(0xFF9F9F9F), trackShape),
        )
        Box(
            Modifier
                .designBounds(
                    metrics,
                    x = trackX,
                    y = trackY,
                    width = fillWidth,
                    height = trackHeight,
                )
                .clip(trackShape)
                .pocketFrame(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFF57E25F),
                            0.50f to Color(0xFF5EED6F),
                            0.55f to Color(0xFF57E25F),
                            1f to Color(0xFF3CBC29),
                        ),
                    ),
                    metrics.dp(20.152f),
                    PocketActionGreen,
                    trackShape,
                ),
        )

        val thumbWidth = 83f
        val thumbHeight = 138f
        val thumbCenter = trackX + trackInset + usableWidth * fraction
        val thumbX = thumbCenter - thumbWidth / 2f
        val thumbShape = RoundedCornerShape(metrics.dp(41.5f))
        Box(
            Modifier
                .designBounds(
                    metrics,
                    x = thumbX,
                    y = rowTop + 23.5f,
                    width = thumbWidth,
                    height = thumbHeight,
                )
                .clip(thumbShape)
                .pocketFrame(Color.White, metrics.dp(20.152f), Color(0xFFCECECE), thumbShape),
        )
    }

    Box(
        Modifier
            .designBounds(
                metrics,
                x = trackX,
                y = rowTop,
                width = trackWidth,
                height = ADJUSTMENT_PANEL_HEIGHT,
            )
            .pointerInput(field, descriptor.minimum, descriptor.maximum) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun update(localX: Float) {
                        val sliderFraction =
                            ((localX - trackInset) / usableWidth).coerceIn(0f, 1f)
                        val next = (
                            descriptor.minimum +
                                sliderFraction *
                                (descriptor.maximum - descriptor.minimum)
                            ).roundToInt()
                        onEvent(MiiEditorEvent.SetAdjustment(field, next))
                    }
                    update(down.position.x)
                    down.consume()
                    do {
                        val pointerEvent = awaitPointerEvent()
                        val change = pointerEvent.changes.firstOrNull {
                            it.id == down.id
                        }
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
private fun VerticalAdjustmentPanel(
    metrics: DesignMetrics,
    state: MiiEditorUiState,
    field: MiiAdjustmentField,
    rowTop: Float,
    progress: () -> Float,
    onEvent: (MiiEditorEvent) -> Unit,
) {
    val descriptor = state.descriptor.adjustments.firstOrNull {
        it.field == field
    } ?: return
    val value = state.activeAdjustmentValue ?: descriptor.defaultValue
    val range = (descriptor.maximum - descriptor.minimum).coerceAtLeast(1)
    val fraction = ((value - descriptor.minimum).toFloat() / range).coerceIn(0f, 1f)
    val topIsMinimum = (field.verticalUpDelta ?: -1) < 0
    val thumbFraction = if (topIsMinimum) fraction else 1f - fraction
    val panelShape = RoundedCornerShape(
        topStart = metrics.dp(92.5f),
        bottomStart = metrics.dp(92.5f),
    )
    val panelHeight = VERTICAL_PANEL_BOTTOM - rowTop

    FigmaPillSurface(
        metrics = metrics,
        modifier = Modifier
            .verticalAdjustmentPanelBounds(metrics, rowTop, panelHeight, progress)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        shape = panelShape,
        shadowAlpha = { adjustmentShadowAlpha(progress()) },
    )
    Box(
        Modifier
            .verticalAdjustmentPanelBounds(
                metrics,
                rowTop,
                panelHeight,
                progress,
                extraWidth = RIGHT_FLUSH_RING_EXTENSION,
            )
            .controllerTarget(
                MII_ADJUSTMENT_SLIDER_TAG,
                layer = MII_ADJUSTMENT_FOCUS_LAYER,
                cornerRadius = 92.5f,
            ) { onEvent(MiiEditorEvent.CloseAdjustment) },
    )

    Box(
        modifier = Modifier.designBounds(
            metrics,
            x = ADJUSTMENT_BUTTON_LEFT + (ADJUSTMENT_BUTTON_WIDTH - 96.385f) / 2f,
            y = rowTop + 23.5f,
            width = 96.385f,
            height = 138f,
        ),
        contentAlignment = Alignment.Center,
    ) {
        AdjustmentIcon(metrics, field.visualSlot())
    }

    val trackWidth = 73f
    val trackX = ADJUSTMENT_BUTTON_LEFT + (ADJUSTMENT_BUTTON_WIDTH - trackWidth) / 2f
    val trackY = rowTop + ADJUSTMENT_PANEL_HEIGHT + 20f
    val trackHeight = VERTICAL_PANEL_BOTTOM - 40f - trackY
    val trackShape = RoundedCornerShape(metrics.dp(36.5f))
    val trackInset = 20.152f
    val usableHeight = trackHeight - trackInset * 2f
    val thumbCenter = trackY + trackInset + usableHeight * thumbFraction
    val fillTop = if (topIsMinimum) trackY else thumbCenter - trackInset
    val fillHeight = if (topIsMinimum) {
        thumbCenter + trackInset - trackY
    } else {
        trackY + trackHeight - fillTop
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = adjustmentContentAlpha(progress()) },
    ) {
        Box(
            Modifier
                .designBounds(
                    metrics,
                    x = trackX,
                    y = trackY + 15.674f,
                    width = trackWidth,
                    height = trackHeight,
                )
                .clip(trackShape)
                .background(PocketShadow),
        )
        Box(
            Modifier
                .designBounds(
                    metrics,
                    x = trackX,
                    y = trackY,
                    width = trackWidth,
                    height = trackHeight,
                )
                .clip(trackShape)
                .pocketFrame(Color.White, metrics.dp(20.152f), Color(0xFF9F9F9F), trackShape),
        )
        Box(
            Modifier
                .designBounds(
                    metrics,
                    x = trackX,
                    y = fillTop,
                    width = trackWidth,
                    height = fillHeight,
                )
                .clip(trackShape)
                .pocketFrame(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFF57E25F),
                            0.50f to Color(0xFF5EED6F),
                            0.55f to Color(0xFF57E25F),
                            1f to Color(0xFF3CBC29),
                        ),
                    ),
                    metrics.dp(20.152f),
                    PocketActionGreen,
                    trackShape,
                ),
        )

        val thumbWidth = 138f
        val thumbHeight = 83f
        val thumbShape = RoundedCornerShape(metrics.dp(41.5f))
        Box(
            Modifier
                .designBounds(
                    metrics,
                    x = ADJUSTMENT_BUTTON_LEFT + (ADJUSTMENT_BUTTON_WIDTH - thumbWidth) / 2f,
                    y = thumbCenter - thumbHeight / 2f,
                    width = thumbWidth,
                    height = thumbHeight,
                )
                .clip(thumbShape)
                .pocketFrame(Color.White, metrics.dp(20.152f), Color(0xFFCECECE), thumbShape),
        )
    }

    Box(
        Modifier
            .designBounds(
                metrics,
                x = ADJUSTMENT_BUTTON_LEFT,
                y = trackY,
                width = ADJUSTMENT_BUTTON_WIDTH,
                height = trackHeight,
            )
            .pointerInput(field, descriptor.minimum, descriptor.maximum) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun update(localY: Float) {
                        val sliderFraction =
                            ((localY - trackInset) / usableHeight).coerceIn(0f, 1f)
                        val valueFraction = if (topIsMinimum) sliderFraction else 1f - sliderFraction
                        val next = (
                            descriptor.minimum +
                                valueFraction *
                                (descriptor.maximum - descriptor.minimum)
                            ).roundToInt()
                        onEvent(MiiEditorEvent.SetAdjustment(field, next))
                    }
                    update(down.position.y)
                    down.consume()
                    do {
                        val pointerEvent = awaitPointerEvent()
                        val change = pointerEvent.changes.firstOrNull {
                            it.id == down.id
                        }
                        if (change != null) {
                            update(change.position.y)
                            change.consume()
                        }
                    } while (change?.pressed == true)
                }
            },
    )
}

private fun Modifier.verticalAdjustmentPanelBounds(
    metrics: DesignMetrics,
    rowTop: Float,
    panelHeight: Float,
    progress: () -> Float,
    extraWidth: Float = 0f,
): Modifier = this
    .graphicsLayer {
        translationX = ADJUSTMENT_BUTTON_LEFT
        translationY = rowTop
    }
    .layout { measurable, _ ->
        val widthPx = metrics.dp(ADJUSTMENT_BUTTON_WIDTH + extraWidth).roundToPx()
        val heightPx = metrics.dp(
            ADJUSTMENT_PANEL_HEIGHT + (panelHeight - ADJUSTMENT_PANEL_HEIGHT) * progress(),
        ).roundToPx()
        val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
        layout(widthPx, heightPx) { placeable.place(0, 0) }
    }

private const val VERTICAL_PANEL_BOTTOM = 1060f

private const val ADJUSTMENT_ICON_OFFSET = 71f

private fun adjustmentPanelLeft(progress: Float): Float =
    ADJUSTMENT_BUTTON_LEFT - (ADJUSTMENT_BUTTON_LEFT - ADJUSTMENT_PANEL_LEFT) * progress

private fun adjustmentPanelWidth(progress: Float): Float =
    ADJUSTMENT_BUTTON_WIDTH + (ADJUSTMENT_PANEL_WIDTH - ADJUSTMENT_BUTTON_WIDTH) * progress

private fun adjustmentContentAlpha(progress: Float): Float =
    ((progress - 0.7f) / 0.3f).coerceIn(0f, 1f)

private fun adjustmentShadowAlpha(progress: Float): Float =
    (progress / 0.35f).coerceIn(0f, 1f)

private fun Modifier.adjustmentPanelBounds(
    metrics: DesignMetrics,
    rowTop: Float,
    progress: () -> Float,
    extraWidth: Float = 0f,
): Modifier = this
    .graphicsLayer {
        translationX = adjustmentPanelLeft(progress())
        translationY = rowTop
    }
    .layout { measurable, _ ->
        val widthPx = metrics.dp(adjustmentPanelWidth(progress()) + extraWidth).roundToPx()
        val heightPx = metrics.dp(ADJUSTMENT_PANEL_HEIGHT).roundToPx()
        val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
        layout(widthPx, heightPx) { placeable.place(0, 0) }
    }

@Composable
private fun FigmaPillSurface(
    metrics: DesignMetrics,
    modifier: Modifier,
    shape: RoundedCornerShape,
    selected: Boolean = false,
    horizontalFill: Boolean = false,
    shadowAlpha: () -> Float = { 1f },
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this@drawWithCache)
            val radiusPx = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
            val mask = roundedShadowMask(size, radiusPx, metrics.dp(10f).toPx())
            val shadowPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            val shadowOffset = metrics.dp(14f).toPx()
            onDrawBehind {
                val alpha = (PocketShadow.alpha * shadowAlpha()).coerceIn(0f, 1f)
                if (alpha <= 0f) return@onDrawBehind
                shadowPaint.color = android.graphics.Color.argb((alpha * 255f).roundToInt(), 0, 0, 0)
                mask.draw(drawContext.canvas.nativeCanvas, shadowPaint, shadowOffset)
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .pocketFrame(
                    if (horizontalFill) {
                        Brush.horizontalGradient(
                            listOf(
                                Color.White,
                                if (selected) PocketSelectedGreen else PocketPaleGreen,
                            ),
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color.White,
                                if (selected) PocketSelectedGreen else PocketPaleGreen,
                            ),
                        )
                    },
                    metrics.dp(15f),
                    PocketBlue,
                    shape,
                ),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
private fun MiiTopStatusPills(
    metrics: DesignMetrics,
    status: StatusInfo,
) {
    val shape = RoundedCornerShape(metrics.dp(66f))
    FigmaPillSurface(
        metrics = metrics,
        modifier = Modifier.anchoredBounds(
            metrics,
            x = 48.5f,
            y = 38.81f,
            width = 301f,
            height = 132f,
            horizontal = DesignAnchor.Start,
            vertical = DesignAnchor.Start,
        ).blockMiiRendererGestures(),
        shape = shape,
    ) {
        Text(
            text = status.time,
            color = PocketText,
            fontFamily = Rubik,
            fontWeight = FontWeight.Medium,
            fontSize = metrics.sp(73.915f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
    FigmaPillSurface(
        metrics = metrics,
        modifier = Modifier.anchoredBounds(
            metrics,
            x = 1381.5f,
            y = 38.81f,
            width = 487f,
            height = 132f,
            horizontal = DesignAnchor.End,
            vertical = DesignAnchor.Start,
        ).blockMiiRendererGestures(),
        shape = shape,
    ) {
        StatusConnectivityContent(metrics, status)
    }
}

@Composable
private fun ContinuePanel(
    metrics: DesignMetrics,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
    glyph: Boolean = true,
) {
    val labelStyle = TextStyle(
        brush = Brush.verticalGradient(
            listOf(PocketBlue, Color(0xFF21677B)),
        ),
        fontFamily = Rubik,
        fontWeight = FontWeight.Bold,
        fontSize = metrics.sp(82.814f),
    )
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelWidth = remember(label, density) {
        textMeasurer.measure(label, labelStyle).size.width.toFloat()
    }
    val labelX = if (glyph) CONTINUE_LABEL_X else CONTINUE_LABEL_END_PADDING
    val width = labelX + labelWidth + CONTINUE_LABEL_END_PADDING
    val height = 178f
    val radius = 130f
    Box(
        modifier = Modifier
            .anchoredBounds(
                metrics,
                x = 0f,
                y = 914.81f,
                width = width,
                height = height,
                horizontal = DesignAnchor.Start,
                vertical = DesignAnchor.End,
            )
            .zIndex(2f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (enabled) {
                    Modifier
                } else {
                    Modifier.blockMiiRendererGestures()
                },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val panelPath = Path().apply {
                moveTo(0f, 0f)
                lineTo(width - radius, 0f)
                quadraticTo(width, 0f, width, radius)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(
                path = panelPath,
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.White,
                        0.44712f to Color.White,
                        1f to Color(0xFFBDF8CB),
                    ),
                ),
            )

            val borderPath = Path().apply {
                moveTo(0f, 8f)
                lineTo(width - radius, 8f)
                quadraticTo(width - 8f, 8f, width - 8f, radius)
                lineTo(width - 8f, height)
            }
            drawPath(
                path = borderPath,
                color = PocketBlue,
                style = Stroke(width = 16f),
            )
        }

        if (glyph) {
            FigmaAsset(
                resource = R.raw.mii_editor_ok_y,
                modifier = Modifier.designBounds(metrics, 68.78f, 46.28f, 85.44f, 92.44f),
            )
        }
        Text(
            text = label,
            modifier = Modifier.designBounds(metrics, labelX, 40f, labelWidth + 4f, 98f),
            style = labelStyle,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private const val CONTINUE_LABEL_X = 180.22f
private const val CONTINUE_LABEL_END_PADDING = 103.78f

private fun Modifier.blockMiiRendererGestures(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
