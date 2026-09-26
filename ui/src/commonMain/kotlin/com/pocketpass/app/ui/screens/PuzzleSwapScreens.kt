package com.pocketpass.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.rememberAsyncImagePainter
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.PuzzleArtwork
import com.pocketpass.app.domain.model.PuzzleProgress
import com.pocketpass.app.model.PocketPassEvent
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.model.PuzzleUiState
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.DesignAnchor
import com.pocketpass.app.ui.PocketAsset
import com.pocketpass.app.ui.Rubik
import com.pocketpass.app.ui.components.FigmaAsset
import com.pocketpass.app.ui.components.FullBleedArtwork
import com.pocketpass.app.ui.components.Text
import com.pocketpass.app.ui.components.jigsawPieceOutline
import com.pocketpass.app.ui.components.jigsawPieceShape
import com.pocketpass.app.ui.components.pocketFrame
import com.pocketpass.app.ui.components.pocketShadow
import com.pocketpass.app.ui.components.puzzleBoardLayout
import com.pocketpass.app.ui.components.rememberPocketAssetBytes
import com.pocketpass.app.ui.components.toPath
import com.pocketpass.app.ui.controller.FocusDirection
import com.pocketpass.app.ui.controller.LocalControllerFocus
import com.pocketpass.app.ui.controller.controllerFocusBarrier
import com.pocketpass.app.ui.controller.controllerTarget
import com.pocketpass.app.ui.designBounds
import com.pocketpass.app.ui.anchoredBounds
import com.pocketpass.app.ui.fileExists
import com.pocketpass.app.ui.theme.pocketPalette
import kotlin.math.max
import okio.Path.Companion.toPath

private val MissingPieceFill = Color(0xFF3F7FD6)
private val MissingPieceOutline = Color(0xFF1E4F9C)
private val ArrowInk = Color(0xFF1D596B)
private val ArrowFrame = Color(0xFF5E9AAC)
private val NoticeInk = Color(0xFFFFD9D9)
private const val PUZZLE_GAME_LAYER = 20
private const val PUZZLE_DIALOG_LAYER = 30
private const val PUZZLE_INFO_BODY =
    "Your first puzzle is your own Piip. Finish it and the next panel opens.\n\n" +
        "Pieces come from passing other Piips, from 5,000 and 10,000 steps a day, " +
        "and from tokens at 15 each.\n\n" +
        "Pass someone who has a piece you are missing and they hand it over."

@Composable
internal fun TopPuzzleSwap(metrics: DesignMetrics, showBackground: Boolean = true) {
    if (showBackground) FullBleedArtwork(metrics, Assets.GameWoodTop)
    FigmaAsset(
        resource = Assets.PuzzleSwapTitle,
        modifier = Modifier.designBounds(metrics, 135.99f, 405.5f, 1648f, 491f),
    )
}

@Composable
internal fun PuzzleSwapBottom(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    showBackground: Boolean = true,
) {
    Box(Modifier.fillMaxSize().testTag("game_puzzle_swap")) {
        if (showBackground) FullBleedArtwork(metrics, Assets.GameWoodBottom)
    }
    val puzzleState = state.puzzle
    val collection = puzzleState.collection
    val puzzle = puzzleState.viewed
    val viewedIndex = puzzleState.viewedIndex

    Text(
        text = puzzle?.title ?: "Puzzle Swap",
        modifier = Modifier
            .anchoredBounds(metrics, 60f, 42f, 800f, 96f, DesignAnchor.Start, DesignAnchor.Center)
            .testTag("puzzle_title"),
        color = Color.White,
        fontFamily = Rubik,
        fontWeight = FontWeight.Bold,
        fontSize = metrics.sp(72f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = when {
            puzzle == null || viewedIndex == null -> "Loading your puzzles"
            puzzle.isComplete -> "Puzzle ${viewedIndex + 1} of ${collection.puzzles.size} · Complete!"
            else -> "Puzzle ${viewedIndex + 1} of ${collection.puzzles.size} · " +
                "${puzzle.totalPieces - puzzle.ownedCount} pieces to go"
        },
        modifier = Modifier.anchoredBounds(metrics, 60f, 140f, 800f, 44f, DesignAnchor.Start, DesignAnchor.Center),
        color = Color.White.copy(alpha = 0.82f),
        fontFamily = Rubik,
        fontWeight = FontWeight.SemiBold,
        fontSize = metrics.sp(30f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (puzzle != null) {
        Box(
            modifier = Modifier
                .anchoredBounds(metrics, 920f, 36f, 280f, 96f, DesignAnchor.End, DesignAnchor.Center)
                .clip(RoundedCornerShape(metrics.dp(48f)))
                .background(Color.Black.copy(alpha = 0.35f))
                .testTag("puzzle_count"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${puzzle.ownedCount}/${puzzle.totalPieces}",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(60f),
                maxLines = 1,
            )
        }
        PuzzleBoard(
            metrics = metrics,
            puzzle = puzzle,
            model = puzzleArtworkModel(puzzle.artwork, state),
            x = 220f,
            y = 200f,
            maxWidth = 800f,
            maxHeight = 660f,
            tag = "puzzle_board",
        )
    }

    PuzzleArrowButton(
        metrics = metrics,
        x = 40f,
        anchor = DesignAnchor.Start,
        enabled = puzzleState.canBrowseBack,
        pointsLeft = true,
        tag = "puzzle_prev",
        neighbor = FocusDirection.Right to "puzzle_buy",
    ) { dispatch(PocketPassEvent.PreviousPuzzle) }
    PuzzleArrowButton(
        metrics = metrics,
        x = 1080f,
        anchor = DesignAnchor.End,
        enabled = puzzleState.canBrowseForward,
        pointsLeft = false,
        tag = "puzzle_next",
        neighbor = FocusDirection.Left to "puzzle_buy",
    ) { dispatch(PocketPassEvent.NextPuzzle) }

    PuzzleBuyButton(metrics, puzzleState, dispatch)
    PuzzleInfoButton(metrics) { dispatch(PocketPassEvent.OpenPuzzleInfo) }

    val notice = puzzleState.purchaseError ?: puzzleState.refreshError
    if (notice != null) {
        Text(
            text = notice,
            modifier = Modifier
                .anchoredBounds(metrics, 60f, 1010f, 1120f, 44f, DesignAnchor.Stretch, DesignAnchor.Center)
                .testTag("puzzle_notice")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dispatch(PocketPassEvent.DismissPuzzleNotice) },
            color = NoticeInk,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(26f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PuzzleBuyButton(
    metrics: DesignMetrics,
    puzzleState: PuzzleUiState,
    dispatch: (PocketPassEvent) -> Unit,
) {
    val puzzle = puzzleState.viewed ?: return
    val price = puzzleState.collection.piecePriceTokens
    val label = when {
        puzzle.isComplete -> "Complete!"
        !puzzleState.isViewingCurrent -> "Not started yet"
        puzzleState.buying -> "Buying a piece"
        puzzleState.tokenBalance < price -> "Not enough tokens"
        else -> "Buy a piece · $price tokens"
    }
    val enabled = puzzleState.canBuy
    val shape = RoundedCornerShape(metrics.dp(60f))
    var modifier = Modifier
        .designBounds(metrics, 340f, 880f, 560f, 120f)
        .alpha(if (enabled) 1f else 0.55f)
        .clip(shape)
        .pocketFrame(
            if (enabled) greenButtonBrush() else cancelButtonBrush(),
            metrics.dp(14f),
            if (enabled) Color(0xFF3CBC29) else Color(0xFF8A8A8A),
            shape,
        )
        .testTag("puzzle_buy")
        .controllerTarget(
            "puzzle_buy",
            layer = PUZZLE_GAME_LAYER,
            neighbors = mapOf(
                FocusDirection.Left to "puzzle_prev",
                FocusDirection.Right to "puzzle_next",
            ),
        ) { if (enabled) dispatch(PocketPassEvent.OpenBuyPuzzlePiece) }
    if (enabled) {
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.OpenBuyPuzzlePiece) }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(40f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PuzzleInfoButton(
    metrics: DesignMetrics,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .anchoredBounds(metrics, 1096f, 896f, 88f, 88f, DesignAnchor.End, DesignAnchor.Center)
            .testTag("puzzle_info")
            .controllerTarget(
                "puzzle_info",
                layer = PUZZLE_GAME_LAYER,
                neighbors = mapOf(
                    FocusDirection.Left to "puzzle_buy",
                    FocusDirection.Up to "puzzle_next",
                ),
                onActivate = onClick,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = 8f }
                .pocketShadow(metrics, 44f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .pocketFrame(Color.White, metrics.dp(11f), ArrowFrame, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "i",
                color = ArrowInk,
                fontFamily = Rubik,
                fontWeight = FontWeight.Bold,
                fontSize = metrics.sp(52f),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun PuzzleInfoDialog(
    metrics: DesignMetrics,
    dispatch: (PocketPassEvent) -> Unit,
    showScrim: Boolean = true,
) {
    val focus = LocalControllerFocus.current
    LaunchedEffect(Unit) { focus?.focus("puzzle_info_close", reveal = false) }
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        )
    }
    Box(
        Modifier
            .anchoredBounds(metrics, 0f, 0f, 1240f, 1080f, DesignAnchor.Stretch, DesignAnchor.Stretch)
            .background(if (showScrim) pocketPalette.scrim else Color.Transparent)
            .testTag("puzzle_info_overlay")
            .controllerFocusBarrier("puzzle_info_overlay", layer = PUZZLE_DIALOG_LAYER)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.ClosePuzzleInfo) },
    )
    Box(
        Modifier
            .designBounds(metrics, 80f, 204f, 1080f, 700f)
            .graphicsLayer { translationY = entrance.value }
            .pocketShadow(metrics, 80f),
    )
    val panelShape = RoundedCornerShape(metrics.dp(80f))
    Box(
        Modifier
            .designBounds(metrics, 80f, 190f, 1080f, 700f)
            .graphicsLayer { translationY = entrance.value }
            .clip(panelShape)
            .pocketFrame(greyPanelBrush(), metrics.dp(15f), pocketPalette.borderGrey, panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("puzzle_info_panel"),
    ) {
        Text(
            text = "Puzzle Swap",
            modifier = Modifier.designBounds(metrics, 60f, 44f, 960f, 90f),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(70f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = PUZZLE_INFO_BODY,
            modifier = Modifier.designBounds(metrics, 60f, 150f, 960f, 380f),
            color = pocketPalette.textSecondary,
            fontFamily = Rubik,
            fontWeight = FontWeight.SemiBold,
            fontSize = metrics.sp(31f),
            lineHeight = metrics.sp(40f),
            textAlign = TextAlign.Center,
            maxLines = 9,
            overflow = TextOverflow.Ellipsis,
        )
        val buttonShape = RoundedCornerShape(metrics.dp(118f))
        Box(
            modifier = Modifier
                .designBounds(metrics, 305f, 550f, 470f, 120f)
                .clip(buttonShape)
                .pocketFrame(greenButtonBrush(), metrics.dp(20.152f), Color(0xFF3CBC29), buttonShape)
                .testTag("puzzle_info_close")
                .controllerTarget("puzzle_info_close", layer = PUZZLE_DIALOG_LAYER) {
                    dispatch(PocketPassEvent.ClosePuzzleInfo)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dispatch(PocketPassEvent.ClosePuzzleInfo) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Got it",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(44f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PuzzleArrowButton(
    metrics: DesignMetrics,
    x: Float,
    anchor: DesignAnchor,
    enabled: Boolean,
    pointsLeft: Boolean,
    tag: String,
    neighbor: Pair<FocusDirection, String>,
    onClick: () -> Unit,
) {
    var modifier = Modifier
        .anchoredBounds(metrics, x, 460f, 120f, 120f, anchor, DesignAnchor.Center)
        .alpha(if (enabled) 1f else 0.35f)
        .testTag(tag)
    if (enabled) {
        modifier = modifier
            .controllerTarget(tag, layer = PUZZLE_GAME_LAYER, neighbors = mapOf(neighbor), onActivate = onClick)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
    }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = 10f }
                .pocketShadow(metrics, 60f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .pocketFrame(Color.White, metrics.dp(15f), ArrowFrame, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = size.width * 0.09f
                val half = size.width * 0.13f
                val center = Offset(size.width / 2f + if (pointsLeft) -half * 0.35f else half * 0.35f, size.height / 2f)
                val direction = if (pointsLeft) -1f else 1f
                val path = Path().apply {
                    moveTo(center.x - direction * half, center.y - half * 1.4f)
                    lineTo(center.x + direction * half, center.y)
                    lineTo(center.x - direction * half, center.y + half * 1.4f)
                }
                drawPath(path, ArrowInk, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
internal fun PuzzleBuyPieceConfirmDialog(
    metrics: DesignMetrics,
    state: PocketPassUiState,
    dispatch: (PocketPassEvent) -> Unit,
    showScrim: Boolean = true,
) {
    val focus = LocalControllerFocus.current
    val returnTarget = remember { focus?.focusedTarget(null) }
    DisposableEffect(focus) {
        onDispose { returnTarget?.let { focus?.restoreFocus(it) } }
    }
    LaunchedEffect(Unit) { focus?.focus("puzzle_buy_cancel", reveal = false) }
    val entrance = remember { Animatable(56f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            targetValue = 0f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
        )
    }
    val price = state.puzzle.collection.piecePriceTokens
    Box(
        Modifier
            .anchoredBounds(metrics, 0f, 0f, 1240f, 1080f, DesignAnchor.Stretch, DesignAnchor.Stretch)
            .background(if (showScrim) pocketPalette.scrim else Color.Transparent)
            .testTag("puzzle_buy_overlay")
            .controllerFocusBarrier("puzzle_buy_overlay", layer = PUZZLE_DIALOG_LAYER)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { dispatch(PocketPassEvent.CloseBuyPuzzlePiece) },
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
            .pocketFrame(greyPanelBrush(), metrics.dp(15f), pocketPalette.borderGrey, panelShape)
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("puzzle_buy_panel"),
    ) {
        Text(
            text = "Buy a puzzle piece?",
            modifier = Modifier.designBounds(metrics, 60f, 44f, 960f, 90f),
            color = pocketPalette.textPrimary,
            fontFamily = Rubik,
            fontWeight = FontWeight.Bold,
            fontSize = metrics.sp(70f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "It costs $price tokens. You have ${state.puzzle.tokenBalance}. The piece is picked for you.",
            modifier = Modifier.designBounds(metrics, 90f, 148f, 900f, 130f),
            color = pocketPalette.textSecondary,
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
                .testTag("puzzle_buy_cancel")
                .controllerTarget("puzzle_buy_cancel", layer = PUZZLE_DIALOG_LAYER) {
                    dispatch(PocketPassEvent.CloseBuyPuzzlePiece)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dispatch(PocketPassEvent.CloseBuyPuzzlePiece) },
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
                .pocketFrame(greenButtonBrush(), metrics.dp(20.152f), Color(0xFF3CBC29), buttonShape)
                .testTag("puzzle_buy_confirm")
                .controllerTarget("puzzle_buy_confirm", layer = PUZZLE_DIALOG_LAYER) {
                    dispatch(PocketPassEvent.ConfirmBuyPuzzlePiece)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dispatch(PocketPassEvent.ConfirmBuyPuzzlePiece) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Buy",
                color = Color.White,
                fontFamily = Rubik,
                fontWeight = FontWeight.SemiBold,
                fontSize = metrics.sp(44f),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun puzzleArtworkModel(
    artwork: PuzzleArtwork,
    state: PocketPassUiState,
): Any? = when (artwork) {
    is PuzzleArtwork.File -> remember(artwork.path) { artwork.path.takeIf(::fileExists)?.toPath() }
    is PuzzleArtwork.Remote -> artwork.url
    is PuzzleArtwork.Bundled -> puzzleArtworkResourceForKey(artwork.key)?.let { rememberPocketAssetBytes(it) }
    PuzzleArtwork.OwnPortrait -> ownPortraitModel(state)
}

@Composable
private fun ownPortraitModel(state: PocketPassUiState): Any? {
    val portraitPath = state.miiEditor.activePortraitFilePath
    val localPortrait = remember(portraitPath) { portraitPath?.takeIf(::fileExists)?.toPath() }
    val avatar = state.profile?.avatar
    val bundled = when (avatar) {
        is AvatarReference.Remote -> null
        is AvatarReference.Bundled -> avatarResourceForKey(avatar.key) ?: Assets.HomeAvatarPetah
        null -> Assets.HomeAvatarPetah
    }
    val bundledBytes = bundled?.let { rememberPocketAssetBytes(it) }
    return localPortrait
        ?: (avatar as? AvatarReference.Remote)?.url
        ?: bundledBytes
}

internal fun puzzleArtworkResourceForKey(key: String): PocketAsset? = when (key) {
    "pocki_happy" -> Assets.PuzzlePockiHappy
    else -> null
}

private class PuzzlePathCache {
    var key: Any? = null
    var pieces: List<Path> = emptyList()
    var owned: Path = Path()
    var missing: Path = Path()
}

@Composable
internal fun PuzzleBoard(
    metrics: DesignMetrics,
    puzzle: PuzzleProgress,
    model: Any?,
    x: Float,
    y: Float,
    maxWidth: Float,
    maxHeight: Float,
    tag: String,
) {
    val layout = remember(puzzle.columns, puzzle.rows, maxWidth, maxHeight) {
        puzzleBoardLayout(puzzle.columns, puzzle.rows, maxWidth, maxHeight)
    }
    val painter = rememberAsyncImagePainter(model = model, contentScale = ContentScale.Crop)
    val seed = remember(puzzle.id) { puzzle.id.hashCode() }
    val cache = remember(puzzle.id, puzzle.columns, puzzle.rows) { PuzzlePathCache() }
    val pad = layout.tabDepth
    val boardX = x + (maxWidth - layout.width) / 2f
    val boardY = y + (maxHeight - layout.height) / 2f
    Canvas(
        Modifier
            .designBounds(metrics, boardX - pad, boardY - pad, layout.width + 2f * pad, layout.height + 2f * pad)
            .testTag(tag),
    ) {
        val scale = size.width / (layout.width + 2f * pad)
        val padPx = pad * scale
        val cellPx = layout.cell * scale
        val tabPx = layout.tabDepth * scale
        val boardSize = Size(layout.width * scale, layout.height * scale)
        val cacheKey = listOf(size, puzzle.ownedPieces)
        if (cache.key != cacheKey) {
            val pieces = List(puzzle.totalPieces) { index ->
                val cell = Rect(
                    left = puzzle.columnOf(index) * cellPx,
                    top = puzzle.rowOf(index) * cellPx,
                    right = (puzzle.columnOf(index) + 1) * cellPx,
                    bottom = (puzzle.rowOf(index) + 1) * cellPx,
                )
                jigsawPieceOutline(jigsawPieceShape(index, puzzle.columns, puzzle.rows, seed), cell, tabPx).toPath()
            }
            cache.pieces = pieces
            cache.owned = unionOf(pieces.filterIndexed { index, _ -> puzzle.owns(index) })
            cache.missing = unionOf(pieces.filterIndexed { index, _ -> !puzzle.owns(index) })
            cache.key = cacheKey
        }
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(padPx * 0.25f, padPx * 0.25f),
            size = Size(size.width - padPx * 0.5f, size.height - padPx * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(padPx, padPx),
        )
        translate(padPx, padPx) {
            drawPath(cache.missing, MissingPieceFill.copy(alpha = 0.45f))
            cache.pieces.forEachIndexed { index, path ->
                if (!puzzle.owns(index)) {
                    drawPath(path, MissingPieceOutline.copy(alpha = 0.7f), style = Stroke(width = 2.5f * scale))
                }
            }
            clipPath(cache.owned) { drawArtwork(painter, boardSize) }
            cache.pieces.forEachIndexed { index, path ->
                if (puzzle.owns(index)) {
                    drawPath(path, Color.White.copy(alpha = 0.4f), style = Stroke(width = 2.2f * scale))
                }
            }
        }
    }
}

private fun unionOf(paths: List<Path>): Path = Path().apply {
    fillType = PathFillType.NonZero
    paths.forEach { addPath(it) }
}

private fun DrawScope.drawArtwork(painter: Painter, boardSize: Size) {
    val intrinsic = painter.intrinsicSize
    if (intrinsic.isSpecified && intrinsic.width > 0f && intrinsic.height > 0f) {
        val zoom = max(boardSize.width / intrinsic.width, boardSize.height / intrinsic.height)
        val drawSize = Size(intrinsic.width * zoom, intrinsic.height * zoom)
        translate((boardSize.width - drawSize.width) / 2f, (boardSize.height - drawSize.height) / 2f) {
            with(painter) { draw(drawSize) }
        }
    } else {
        with(painter) { draw(boardSize) }
    }
}
