package com.pocketpass.app.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import com.pocketpass.app.model.GameTarget
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.BOTTOM_DESIGN_HEIGHT
import com.pocketpass.app.ui.BOTTOM_DESIGN_WIDTH
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.DesignSurface
import com.pocketpass.app.ui.TOP_DESIGN_HEIGHT
import com.pocketpass.app.ui.TOP_DESIGN_WIDTH
import com.pocketpass.app.ui.components.pocketBorder
import com.pocketpass.app.ui.screens.GameBottomOverlay
import com.pocketpass.app.ui.screens.TopActiveGame
import com.pocketpass.app.ui.theme.pocketPalette
import kotlin.math.min

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
    val title = when (shown.value) {
        GameTarget.PuzzleSwap -> "Puzzle Swap"
        GameTarget.Bingo -> "Bingo"
        GameTarget.WorldTour -> "World Tour"
        null -> ""
    }
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
        PhonePageHeader(metrics, title, null, "game_back", onBack = { dispatch(PocketPassEvent.Back) })
        Spacer(Modifier.height(metrics.dp(24f)))
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = metrics.dp(PHONE_CONTENT_MARGIN)),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val width = with(density) { maxWidth.toPx() }
            val height = with(density) { maxHeight.toPx() }
            val topAspect = TOP_DESIGN_WIDTH / TOP_DESIGN_HEIGHT
            val bottomAspect = BOTTOM_DESIGN_WIDTH / BOTTOM_DESIGN_HEIGHT
            val gap = 30f
            val top: @Composable (Float, Float) -> Unit = { w, h ->
                GameBoard(metrics, w, h, TOP_DESIGN_WIDTH, TOP_DESIGN_HEIGHT) { TopActiveGame(it, state) }
            }
            val bottom: @Composable (Float, Float) -> Unit = { w, h ->
                GameBoard(metrics, w, h, BOTTOM_DESIGN_WIDTH, BOTTOM_DESIGN_HEIGHT) { GameBottomOverlay(it, state, dispatch) }
            }
            if (width > height) {
                val boardHeight = min(height, (width - gap) / (topAspect + bottomAspect))
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(gap)), verticalAlignment = Alignment.CenterVertically) {
                    top(boardHeight * topAspect, boardHeight)
                    bottom(boardHeight * bottomAspect, boardHeight)
                }
            } else {
                val boardWidth = min(width, (height - gap) / (1f / topAspect + 1f / bottomAspect))
                Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(gap)), horizontalAlignment = Alignment.CenterHorizontally) {
                    top(boardWidth, boardWidth / topAspect)
                    bottom(boardWidth, boardWidth / bottomAspect)
                }
            }
        }
    }
}

@Composable
private fun GameBoard(
    metrics: DesignMetrics,
    width: Float,
    height: Float,
    designWidth: Float,
    designHeight: Float,
    content: @Composable (DesignMetrics) -> Unit,
) {
    val shape = RoundedCornerShape(metrics.dp(36f))
    Box(
        Modifier
            .size(metrics.dp(width), metrics.dp(height))
            .phoneShadow(metrics, 36f, 12f)
            .clip(shape)
            .pocketBorder(metrics.dp(6f), pocketPalette.tealBorder, shape),
    ) {
        DesignSurface(designWidth, designHeight, Modifier.fillMaxSize()) { content(it) }
    }
}
