package com.pocketpass.app.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.pocketpass.app.ui.controller.ControllerOverlayFocus
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.LocalControllerFocusLayer
import com.pocketpass.app.ui.controller.controllerFocusBarrier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.pocketpass.app.model.GameTarget
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.BOTTOM_DESIGN_HEIGHT
import com.pocketpass.app.ui.BOTTOM_DESIGN_WIDTH
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.DesignSurface
import com.pocketpass.app.ui.TOP_DESIGN_HEIGHT
import com.pocketpass.app.ui.TOP_DESIGN_WIDTH
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.screens.GameBottomOverlay
import com.pocketpass.app.ui.screens.TopActiveGame
import kotlin.math.min

internal data class PhoneGameLayout(
    val sideBySide: Boolean,
    val heroWidth: Float,
    val heroHeight: Float,
    val boardWidth: Float,
    val boardHeight: Float,
)

internal fun phoneGameLayout(width: Float, height: Float): PhoneGameLayout {
    val availableWidth = width.coerceAtLeast(0f)
    val availableHeight = height.coerceAtLeast(0f)
    val topAspect = TOP_DESIGN_WIDTH / TOP_DESIGN_HEIGHT
    val bottomAspect = BOTTOM_DESIGN_WIDTH / BOTTOM_DESIGN_HEIGHT
    return if (availableWidth > availableHeight) {
        val boardHeight = min(availableHeight, availableWidth / (topAspect * 0.75f + bottomAspect))
        val heroWidth = boardHeight * topAspect * 0.75f
        PhoneGameLayout(true, heroWidth, heroWidth / topAspect, boardHeight * bottomAspect, boardHeight)
    } else {
        val boardWidth = minOf(availableWidth, 1440f, availableHeight / (0.68f / topAspect + 1f / bottomAspect))
        val heroWidth = boardWidth * 0.68f
        PhoneGameLayout(false, heroWidth, heroWidth / topAspect, boardWidth, boardWidth / bottomAspect)
    }
}

@Composable
fun PhoneGamePage(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val insets = LocalPhoneInsets.current
    val active = state.games.activeGame
    val shown = remember { mutableStateOf(active) }
    if (active != null) shown.value = active
    val game = shown.value ?: return
    val title = when (game) {
        GameTarget.PuzzleSwap -> "Puzzle Swap"
        GameTarget.Bingo -> "Bingo"
        GameTarget.WorldTour -> "World Tour"
    }
    val dismissEvent = when {
        game == GameTarget.WorldTour && state.games.worldTourRegionsVisible ->
            PocketPassEvent.CloseWorldTourRegions
        game == GameTarget.Bingo && state.games.bingoGoalIndex != null ->
            PocketPassEvent.CloseBingoSquare
        game == GameTarget.PuzzleSwap && state.games.puzzleInfoVisible ->
            PocketPassEvent.ClosePuzzleInfo
        game == GameTarget.PuzzleSwap && state.games.puzzleBuyPromptVisible ->
            PocketPassEvent.CloseBuyPuzzlePiece
        else -> null
    }
    val dismissDialog: (() -> Unit)? = dismissEvent?.let { event -> { dispatch(event) } }
    val boardState = if (dismissDialog == null) state else state.copy(games = state.games.copy(
        worldTourRegionsVisible = false,
        bingoGoalIndex = null,
        puzzleInfoVisible = false,
        puzzleBuyPromptVisible = false,
    ))
    BoxWithConstraints(Modifier.fillMaxSize().testTag("single_screen_game")) {
        PhoneGameBackdrop(game, maxWidth > maxHeight)
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = metrics.dp(insets.start),
                    end = metrics.dp(insets.end),
                    top = metrics.dp(insets.top + 24f),
                    bottom = metrics.dp(insets.bottom + 40f),
                ),
        ) {
            PhonePageHeader(
                metrics, title, null, "game_back",
                onBack = { dispatch(PocketPassEvent.Back) },
                foregroundColor = Color.White,
            )
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = metrics.dp(24f)),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val layout = phoneGameLayout(with(density) { maxWidth.toPx() }, with(density) { maxHeight.toPx() })
                val top: @Composable () -> Unit = {
                    GameForeground(metrics, layout.heroWidth, layout.heroHeight, TOP_DESIGN_WIDTH, TOP_DESIGN_HEIGHT, "game_hero") {
                        TopActiveGame(it, state, showBackground = false)
                    }
                }
                val bottom: @Composable () -> Unit = {
                    GameForeground(metrics, layout.boardWidth, layout.boardHeight, BOTTOM_DESIGN_WIDTH, BOTTOM_DESIGN_HEIGHT, "game_board") {
                        GameBottomOverlay(it, boardState, dispatch, showBackground = false)
                    }
                }
                if (layout.sideBySide) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        top()
                        bottom()
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        top()
                        bottom()
                    }
                }
            }
        }
        ControllerOverlayFocus(dismissDialog != null)
        CompositionLocalProvider(
            LocalControllerFocusLayer provides LocalControllerFocusLayer.current + PHONE_DIALOG_FOCUS_LAYER,
            LocalControllerFocus provides LocalControllerFocus.current?.takeIf { dismissDialog != null },
        ) {
        if (dismissDialog != null) {
            Column(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .testTag("game_dialog_scrim")
                    .controllerFocusBarrier("game_dialog_scrim", layer = 0)
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = dismissDialog)
                    .padding(
                        start = metrics.dp(insets.start), end = metrics.dp(insets.end),
                        top = metrics.dp(insets.top + 24f), bottom = metrics.dp(insets.bottom + 40f),
                    ),
            ) {
                PhonePageHeader(metrics, title, null, "game_dialog_back", onBack = dismissDialog, foregroundColor = Color.White)
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val density = LocalDensity.current
                    val width = minOf(with(density) { maxWidth.toPx() } - 48f, 1440f,
                        with(density) { maxHeight.toPx() } * BOTTOM_DESIGN_WIDTH / BOTTOM_DESIGN_HEIGHT).coerceAtLeast(0f)
                    GameForeground(metrics, width, width * BOTTOM_DESIGN_HEIGHT / BOTTOM_DESIGN_WIDTH,
                        BOTTOM_DESIGN_WIDTH, BOTTOM_DESIGN_HEIGHT, "game_dialog") {
                        GameBottomOverlay(it, state, dispatch, showBackground = false, overlaysOnly = true)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun PhoneGameBackdrop(game: GameTarget, landscape: Boolean) {
    Box(Modifier.fillMaxSize().background(Color(0xFF06182D)).testTag("game_scene_background")) {
        if (game == GameTarget.WorldTour) {
            FigmaAsset(Assets.WorldTourSpace, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            FigmaAsset(
                Assets.WorldTourMap,
                Modifier.fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val mask = if (landscape) {
                            Brush.horizontalGradient(0f to Color.Transparent, 0.32f to Color.Transparent, 0.72f to Color.Black)
                        } else {
                            Brush.verticalGradient(0f to Color.Transparent, 0.24f to Color.Transparent, 0.62f to Color.Black)
                        }
                        onDrawWithContent {
                            drawContent()
                            drawRect(mask, blendMode = BlendMode.DstIn)
                        }
                    },
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Color.Transparent,
                0.55f to Color.Transparent,
                1f to Color(0xFF00264B),
            )))
        } else {
            FigmaAsset(Assets.GameWoodBottom, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.45f),
            0.22f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.12f),
        )))
    }
}

@Composable
private fun GameForeground(
    metrics: DesignMetrics,
    width: Float,
    height: Float,
    designWidth: Float,
    designHeight: Float,
    tag: String,
    content: @Composable (DesignMetrics) -> Unit,
) {
    DesignSurface(
        designWidth, designHeight,
        Modifier.size(metrics.dp(width), metrics.dp(height)).testTag(tag),
        viewportBackground = Color.Transparent,
    ) { content(it) }
}
