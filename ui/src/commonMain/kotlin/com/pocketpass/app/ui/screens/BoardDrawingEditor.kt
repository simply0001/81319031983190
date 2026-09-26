package com.pocketpass.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import com.pocketpass.app.ui.theme.pocketPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.pocketpass.app.boards.*
import com.pocketpass.app.model.PocketPassUiState
import com.pocketpass.app.ui.DesignMetrics
import com.pocketpass.app.ui.Assets
import com.pocketpass.app.ui.components.FigmaAsset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pocketpass.app.ui.components.EntranceMotion
import com.pocketpass.app.ui.controller.LocalControllerFocus
import kotlinx.serialization.json.JsonObject

@Composable
internal fun BoardComposer(m: DesignMetrics, s: BoardsUiState, send: (BoardAction) -> Unit) {
    val draft = s.draft ?: return
    val locked = s.busy || draft.pendingPublishId != null
    val branding = draft.content.brandingKind
    var draw by remember(draft.id) { mutableStateOf(draft.content.drawing != null || branding != null) }
    var pan by remember(draft.id) { mutableStateOf(false) }
    var zoom by remember(draft.id) { mutableFloatStateOf(1f) }
    var offset by remember(draft.id) { mutableStateOf(Offset.Zero) }
    var canvasSize by remember(draft.id) { mutableStateOf(IntSize.Zero) }
    var toolsOpen by remember(draft.id) { mutableStateOf(false) }
    var fillStatus by remember(draft.id) { mutableStateOf<String?>(null) }
    val controllerFocus = LocalControllerFocus.current
    DisposableEffect(controllerFocus, draft.id, draw) {
        if(draw) controllerFocus?.boardCanvasPan = { x, y ->
            val maxX = canvasSize.width * (zoom - 1f)
            val maxY = canvasSize.height * (zoom - 1f)
            offset = Offset(
                (offset.x - x * canvasSize.width * .9f).coerceIn(-maxX, 0f),
                (offset.y - y * canvasSize.height * .9f).coerceIn(-maxY, 0f),
            )
        }
        onDispose { controllerFocus?.boardCanvasPan = null }
    }
    LaunchedEffect(draft.id, draw, zoom, offset, canvasSize) {
        val clamped = Offset(
            offset.x.coerceIn(-canvasSize.width * (zoom - 1f), 0f),
            offset.y.coerceIn(-canvasSize.height * (zoom - 1f), 0f),
        )
        if (offset != clamped) offset = clamped
        val fraction = 1f / zoom
        send(BoardAction.CanvasViewport(BoardCanvasViewport(
            left = if(canvasSize.width == 0) 0f else (-clamped.x / (canvasSize.width * zoom)).coerceIn(0f, 1f - fraction),
            top = if(canvasSize.height == 0) 0f else (-clamped.y / (canvasSize.height * zoom)).coerceIn(0f, 1f - fraction),
            width = fraction, height = fraction, zoom = zoom, drawing = draw,
        )))
    }
    val compact = LocalBoardCompactComposer.current
    if(draft.recovered) BoardLabel(m, "Recovered copy · Your other draft is still saved.", 32f)
    if(branding == null) BoardTabs(m, listOf(
        BoardTab("Text", "note_text", !draw) { draw = false },
        BoardTab("Drawing", "note_drawing", draw) { draw = true },
        BoardTab("Paper", "note_stationery", false) { send(BoardAction.Stationery) },
    ))
    if(draw) {
        val paper = s.stationery.firstOrNull { it.id == draft.content.stationeryId }?.artwork
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(m.dp(16f)), verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(m.dp(12f))) {
                NoteTool(m, "Pixel", "pen_pixel", "pixel", s.pen == "pixel" && !pan, !locked) { send(BoardAction.Tool(pen = "pixel")); pan = false }
                NoteTool(m, "Smooth", "pen_smooth", "smooth", s.pen == "smooth" && !pan, !locked) { send(BoardAction.Tool(pen = "smooth")); pan = false }
                NoteTool(m, "Erase", "pen_eraser", "eraser", s.pen == "eraser" && !pan, !locked) { send(BoardAction.Tool(pen = "eraser")); pan = false }
                NoteTool(m, "Fill", "pen_bucket", "bucket", s.pen == "bucket" && !pan, !locked) { send(BoardAction.Tool(pen = "bucket")); pan = false; fillStatus = null }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BoardPaper(m, if(compact) Modifier.width(m.dp(720f)) else Modifier.fillMaxWidth()) {
                    BoardDrawingCanvas(s.drawingHistory.drawing, Modifier.fillMaxWidth().aspectRatio(4f/3f)
                        .onSizeChanged { canvasSize = it }.testTag("board_drawing_canvas"), paper = paper,
                        activeStroke = s.activeStroke, editable = !locked, pen = s.pen, ink = s.ink, penSize = s.penSize,
                        zoom = zoom, pan = offset, panMode = pan, onPan = { offset = it },
                        onPreview = { send(BoardAction.PreviewStroke(it)) }, onStroke = { send(BoardAction.Stroke(it)); fillStatus = null },
                        onFillProblem = { fillStatus = it })
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(m.dp(12f))) {
                NoteTool(m, "Undo", "drawing_undo", "undo", enabled = !locked && s.drawingHistory.drawing.strokes.isNotEmpty()) { send(BoardAction.Undo) }
                NoteTool(m, "Redo", "drawing_redo", "redo", enabled = !locked && s.drawingHistory.redo.isNotEmpty()) { send(BoardAction.Redo) }
                NoteTool(m, "Tools", "drawing_tools", "tools", toolsOpen) { toolsOpen = !toolsOpen }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(m.dp(14f), Alignment.CenterHorizontally)) {
            val names = listOf("Black", "Red", "Yellow", "Green", "Blue", "Purple", "White")
            BoardDrawingTools.colors.forEachIndexed { index, color ->
                val select = { send(BoardAction.Tool(color = color)) }
                Box(Modifier.size(m.dp(88f)).testTag("ink_$index").semantics { contentDescription = "${names[index]} ink" }
                    .boardControllerTarget("ink_$index", radius = 44f, enabled = !locked, onActivate = select)
                    .selectable(s.ink == color, enabled = !locked, role = Role.RadioButton, onClick = select)
                    .border(m.dp(if(s.ink == color) 7f else 2f), if(s.ink == color) ThemeChoiceGreen else pocketPalette.borderSoft, CircleShape)
                    .padding(m.dp(13f)).background(boardInk(color), CircleShape))
            }
        }
        BoardReveal(m, toolsOpen) { BoardCard(m) {
            BoardLabel(m, "Pen size", 34f, true)
            Row(horizontalArrangement = Arrangement.spacedBy(m.dp(16f))) {
                BoardDrawingTools.sizes.forEach { size -> BoardButton(m, size.toInt().toString(), "pen_size_$size", s.penSize == size, !locked) { send(BoardAction.Tool(size = size)) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(m.dp(18f)), verticalAlignment = Alignment.CenterVertically) {
                NoteTool(m, "Move", "drawing_pan", "pan", pan) { pan = !pan }
                NoteTool(m, "Out", "drawing_zoom_out", "minus") { zoom = (zoom / 1.5f).coerceAtLeast(1f); if(zoom == 1f) offset = Offset.Zero }
                NoteTool(m, "In", "drawing_zoom_in", "plus") { zoom = (zoom * 1.5f).coerceAtMost(4f) }
                BoardLabel(m, "${(zoom * 100).toInt()}%", 32f)
            }
        } }
        if(pan) BoardLabel(m, "Drag to move the paper. Choose a pen to draw again.", 30f)
        else if(zoom > 1f) BoardLabel(m, "Left stick moves the zoomed paper while you draw.", 28f)
        if(s.pen == "bucket" && !pan) BoardLabel(m, "Tap an area to fill it with your chosen colour.", 28f)
        fillStatus?.let { BoardLabel(m, it, 28f) }
    }
    if(branding != null) BoardLabel(m, "Board $branding", 34f)
    if(branding == null) {
        BoardField(m, draft.content.body, { send(BoardAction.Text(it)) }, if(draw || draft.content.drawing != null) "Add a caption…" else "What's on your mind?", true, !locked)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            BoardLabel(m, "${draft.content.body.length}/${if(draft.content.threadId == null) 1000 else 500}", 28f, color = pocketPalette.textSecondary)
        }
        BoardToggle(m, "Spoiler", null, draft.content.spoiler, "draft_spoiler", !locked) { send(BoardAction.Spoiler(!draft.content.spoiler)) }
    }
    BoardDivider(m)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(24f))) {
        BoardLabel(m, if(draft.pendingPublishId != null) "Not sent yet. Retry to finish publishing."
            else if(draft.cloudSynced) "Private draft saved" else "Draft saved on this device", 30f,
            modifier = Modifier.weight(1f), color = pocketPalette.textSecondary)
        BoardButton(m, if(s.busy) "Saving…" else if(draft.pendingPublishId != null) "Retry" else if(branding != null) "Save $branding" else "Post note", "publish_note", true, !s.busy) { send(BoardAction.Publish) }
    }
}

@Composable
private fun NoteTool(m: DesignMetrics, label: String, tag: String, glyph: String, selected: Boolean = false, enabled: Boolean = true, click: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(m.dp(22f))
    val ink = (if(selected) Color.White else pocketPalette.teal).copy(alpha = if(enabled) 1f else .35f)
    Column(Modifier.width(m.dp(126f)).height(m.dp(116f)).testTag(tag).semantics { contentDescription = label }
        .background(if(selected) greenButtonBrush() else greyPanelBrush(), shape)
        .border(m.dp(3f), if(selected) ThemeChoiceGreen else pocketPalette.borderSoft, shape)
        .boardControllerTarget(tag, radius = 22f, enabled = enabled, onActivate = click)
        .clickable(enabled = enabled, onClick = click).padding(m.dp(10f)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(m.dp(48f))) {
            val k = size.width / 48f
            withTransform({ scale(k, k, Offset.Zero) }) {
                fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(ink, Offset(x1,y1), Offset(x2,y2), 4f, StrokeCap.Round)
                when(glyph) {
                    "pixel", "smooth" -> {
                        val nib = Path().apply { moveTo(9f,38f); lineTo(13f,25f); lineTo(32f,6f); lineTo(42f,16f); lineTo(23f,35f); close() }
                        drawPath(nib, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(3.5f))
                        line(13f,25f,23f,35f)
                        if(glyph == "pixel") { line(5f,44f,16f,44f); line(16f,44f,16f,40f) }
                        else drawPath(Path().apply { moveTo(4f,44f); cubicTo(18f,35f,25f,49f,40f,39f) }, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
                    }
                    "eraser" -> {
                        drawPath(Path().apply { moveTo(5f,29f); lineTo(27f,7f); lineTo(43f,23f); lineTo(23f,43f); lineTo(19f,43f); close() }, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(3.5f))
                        line(15f,20f,32f,36f); line(23f,43f,44f,43f)
                    }
                    "bucket" -> {
                        drawPath(Path().apply { moveTo(12f, 12f); lineTo(23f, 4f); lineTo(40f, 25f); lineTo(27f, 38f); close() }, ink,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(3.5f))
                        line(9f, 29f, 26f, 13f)
                        drawCircle(ink, 4f, Offset(42f, 40f))
                    }
                    "undo", "redo" -> {
                        withTransform({ if(glyph == "redo") { translate(48f,0f); scale(-1f,1f,Offset.Zero) } }) {
                            drawPath(Path().apply { moveTo(8f,17f); lineTo(27f,17f); cubicTo(47f,17f,47f,40f,26f,40f) }, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
                            line(8f,17f,18f,7f); line(8f,17f,18f,27f)
                        }
                    }
                    "tools" -> { for(i in 0..2) { line(7f,10f+i*14f,41f,10f+i*14f); drawCircle(ink,5f,Offset(if(i==1) 30f else 17f,10f+i*14f)) } }
                    "pan" -> { line(24f,5f,24f,43f); line(5f,24f,43f,24f); line(5f,24f,12f,17f); line(43f,24f,36f,31f); line(24f,5f,17f,12f); line(24f,43f,31f,36f) }
                    else -> { line(9f,24f,39f,24f); if(glyph == "plus") line(24f,9f,24f,39f) }
                }
            }
        }
        Spacer(Modifier.height(m.dp(6f)))
        BoardLabel(m, label, 25f, true, maxLines = 1, color = ink)
    }
}
internal fun boardInk(value: String): Color = Color((0xFF000000L or value.removePrefix("#").toLong(16)).toInt())

@Composable
internal fun BoardDrawingCanvas(
    drawing: BoardDrawing, modifier: Modifier = Modifier, activeStroke: BoardStroke? = null,
    paper: JsonObject? = null,
    editable: Boolean = false, pen: String = "pixel", ink: String = "#222222", penSize: Float = 2f,
    zoom: Float = 1f, pan: Offset = Offset.Zero, panMode: Boolean = false,
    onPan: (Offset) -> Unit = {}, onPreview: (BoardStroke?) -> Unit = {}, onStroke: (BoardStroke) -> Unit = {},
    onFillProblem: (String) -> Unit = {},
) {
    val currentStroke by rememberUpdatedState(onStroke)
    val preview by rememberUpdatedState(onPreview)
    val currentPan by rememberUpdatedState(pan)
    val updatePan by rememberUpdatedState(onPan)
    val fillProblem by rememberUpdatedState(onFillProblem)
    val currentDrawing by rememberUpdatedState(drawing)
    val decorationStrokes = remember(paper) {
        (paper?.get("drawing") as? JsonObject)
            ?.let { runCatching { it.boardDecode<BoardDrawing>() }.getOrNull() }
            ?.strokes.orEmpty()
    }
    val currentDecoration by rememberUpdatedState(decorationStrokes)
    val savedStrokes = remember(decorationStrokes, drawing.strokes) {
        (decorationStrokes + drawing.strokes).map { stroke ->
            stroke to if(stroke.pen == "pixel") BoardPixelRaster.runs(stroke) else emptyList()
        }
    }
    val previewRuns = remember(activeStroke) {
        activeStroke?.takeIf { it.pen == "pixel" }?.let(BoardPixelRaster::runs).orEmpty()
    }
    Canvas(modifier.clipToBounds().background(Color.White).pointerInput(editable, pen, ink, penSize, zoom, panMode) {
        if(!editable) return@pointerInput
        val points = ArrayList<List<Float>>(128)
        fun paper(position: Offset): List<Float> {
            val x = ((position.x - currentPan.x) / (size.width / 800f * zoom)).coerceIn(0f, 800f)
            val y = ((position.y - currentPan.y) / (size.height / 600f * zoom)).coerceIn(0f, 600f)
            return listOf(x, y)
        }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            if(pen == "bucket" && !panMode) {
                val point = paper(down.position)
                when(val result = BoardBucketFill.fill(
                    currentDrawing.copy(strokes = currentDecoration + currentDrawing.strokes), point[0], point[1], ink,
                )) {
                    is BoardBucketResult.Filled -> {
                        val valid = runCatching { currentDrawing.copy(strokes = currentDrawing.strokes + result.stroke).validate() }.isSuccess
                        if(valid) currentStroke(result.stroke)
                        else fillProblem("This fill is too detailed for one note. Try a smaller area.")
                    }
                    BoardBucketResult.AlreadyFilled -> fillProblem("That area already has this colour.")
                    BoardBucketResult.TooDetailed -> fillProblem("This fill is too detailed for one note. Try a smaller area.")
                }
                do {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                } while(change.pressed)
                return@awaitEachGesture
            }
            points.clear()
            if(!panMode) {
                points.add(paper(down.position))
                preview(BoardStroke(pen, ink, penSize, points.toList()))
            }
            do {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                val amount = change.positionChange()
                change.consume()
                if(panMode) updatePan(Offset((currentPan.x + amount.x).coerceIn(-size.width * (zoom - 1), 0f), (currentPan.y + amount.y).coerceIn(-size.height * (zoom - 1), 0f)))
                else if(points.size < 4000) {
                    val point = paper(change.position)
                    if(points.lastOrNull() != point) {
                        points.add(point)
                        preview(BoardStroke(pen, ink, penSize, points.toList()))
                    }
                }
            } while(change.pressed)
            if(!panMode && points.isNotEmpty()) currentStroke(BoardStroke(pen, ink, penSize, points.toList()))
            points.clear(); preview(null)
        }
    }) {
        clipRect {
            withTransform({ translate(pan.x, pan.y); scale(size.width / 800f * zoom, size.height / 600f * zoom, Offset.Zero) }) {
                val strokes = savedStrokes + listOfNotNull(activeStroke?.let { it to previewRuns })
                strokes.forEach { (stroke, runs) ->
                    if(stroke.points.isEmpty()) return@forEach
                    if(stroke.pen == "pixel") {
                        val paint = Paint().apply {
                            color = boardInk(stroke.color)
                            isAntiAlias = false
                        }
                        drawIntoCanvas { canvas ->
                            runs.forEach { run ->
                                val x = run.x * BoardPixelRaster.cellSize
                                val y = run.y * BoardPixelRaster.cellSize
                                canvas.drawRect(
                                    x, y,
                                    x + run.length * BoardPixelRaster.cellSize,
                                    y + BoardPixelRaster.cellSize,
                                    paint,
                                )
                            }
                        }
                        return@forEach
                    }
                    if(stroke.pen == "bucket") {
                        val paint = Paint().apply { color = boardInk(stroke.color); isAntiAlias = false }
                        drawIntoCanvas { canvas ->
                            stroke.points.chunked(2).forEach { pair ->
                                if(pair.size == 2) canvas.drawRect(pair[0][0], pair[0][1], pair[1][0],
                                    pair[0][1] + BoardPixelRaster.cellSize, paint)
                            }
                        }
                        return@forEach
                    }
                    val paint = Paint().apply {
                        color = if(stroke.pen == "eraser") Color.White else boardInk(stroke.color)
                        isAntiAlias = true
                        style = PaintingStyle.Stroke
                        strokeWidth = stroke.size
                        strokeCap = StrokeCap.Round
                        strokeJoin = StrokeJoin.Round
                    }
                    val path = Path().apply {
                        moveTo(stroke.points.first()[0], stroke.points.first()[1])
                        if(stroke.points.size == 1) lineTo(stroke.points.first()[0] + .01f, stroke.points.first()[1])
                        stroke.points.drop(1).forEach { lineTo(it[0], it[1]) }
                    }
                    drawIntoCanvas { it.drawPath(path, paint) }
                }
            }
        }
    }
}

@Composable
internal fun BoardsTop(state: PocketPassUiState) {
    BottomPage(EntranceMotion.BoardOpen) { m -> BoardTopPreview(m, state, Modifier.padding(top = m.dp(210f))) }
}

@Composable
internal fun BoardTopPreview(m: DesignMetrics, state: PocketPassUiState, modifier: Modifier = Modifier) {
    val s = state.boards
    if(s.screen == BoardsScreen.Chats) {
        BoardConversationPreview(m, state, modifier)
        return
    }
    if(s.screen == BoardsScreen.Directory || s.screen == BoardsScreen.Chooser) {
        BoardDirectoryPreview(m, s, modifier)
        return
    }
    val composing = s.screen == BoardsScreen.Compose
    val previewBoard = s.board.takeIf { s.screen in listOf(BoardsScreen.Board, BoardsScreen.Thread, BoardsScreen.Compose, BoardsScreen.Manage) }
    val pinned = if(s.screen == BoardsScreen.Thread) (listOfNotNull(s.thread) + s.posts).firstOrNull { it.id == s.pinnedPostId } else null
    val post = pinned ?: s.focused.takeIf { s.screen == BoardsScreen.Board || s.screen == BoardsScreen.Thread }
    val spoiler = !composing && post?.spoiler == true && post.id !in s.revealed
    val drawing = if(composing) s.draft?.content?.drawing else post?.drawing
    val hasDrawing = !spoiler && post?.removed != true && (drawing != null || (composing && (s.activeStroke != null || s.canvasViewport.drawing)))
    val author = if(composing) state.profile?.displayName ?: "Your note" else post?.authorName ?: previewBoard?.name ?: "PocketPass Boards"
    val body = when {
        spoiler -> "Spoiler · Reveal this note on the lower screen"
        post?.removed == true -> "This note was removed."
        composing -> s.draft?.content?.body.orEmpty()
        post != null -> post.body
        else -> previewBoard?.description?.takeIf { it.isNotBlank() } ?: "Notes, drawings and conversations."
    }
    val preview: @Composable (Modifier) -> Unit = { canvas ->
        Box(canvas.testTag("board_top_drawing").border(m.dp(5f), pocketPalette.borderSoft).padding(m.dp(5f))) {
            BoardDrawingCanvas(drawing ?: BoardDrawing(), Modifier.fillMaxSize(), activeStroke = s.activeStroke.takeIf { composing },
                paper = if(composing) s.stationery.firstOrNull { it.id == s.draft?.content?.stationeryId }?.artwork else post?.stationery?.artwork)
            if(composing && s.canvasViewport.drawing && s.canvasViewport.zoom > 1f) {
                val viewport = s.canvasViewport
                val accent = pocketPalette.teal
                Canvas(Modifier.fillMaxSize()) {
                    val origin = Offset(size.width * viewport.left, size.height * viewport.top)
                    val extent = Size(size.width * viewport.width, size.height * viewport.height)
                    drawRect(accent.copy(alpha = .12f), origin, extent)
                    drawRect(Color.White, origin, extent, style = Stroke(m.dp(9f).toPx()))
                    drawRect(accent, origin, extent, style = Stroke(m.dp(4f).toPx()))
                }
            }
        }
    }
    val context: @Composable () -> Unit = {
        if(pinned != null) BoardLabel(m, "Pinned · Browse replies below", 30f, true, color = pocketPalette.teal)
        if(composing && hasDrawing) BoardDrawingToolPreview(m, s)
    }
    BoxWithConstraints(modifier.fillMaxSize().padding(m.dp(55f))) {
        if(!composing && post == null) {
            Column(Modifier.align(Alignment.Center).widthIn(max = m.dp(1200f)),
                verticalArrangement = Arrangement.spacedBy(m.dp(36f)), horizontalAlignment = Alignment.CenterHorizontally) {
                FigmaAsset(Assets.SettingsSocial, Modifier.size(m.dp(160f)), contentScale = ContentScale.Fit)
                BoardLabel(m, author, 64f, true, maxLines = 2, color = pocketPalette.teal)
                BoardLabel(m, body, 40f, maxLines = 3)
                if(s.screen in listOf(BoardsScreen.Directory, BoardsScreen.Chooser) && s.boards.isNotEmpty()) BoardCard(m) {
                    s.boards.take(3).forEach { board ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(26f))) {
                            BoardBranding(m, board, s.assets)
                            Column(Modifier.weight(1f)) {
                                BoardLabel(m, board.name, 40f, true, maxLines = 1)
                                BoardLabel(m, "${board.memberCount} members", 30f, color = pocketPalette.textSecondary)
                            }
                        }
                    }
                }
            }
        } else if(hasDrawing && maxWidth > maxHeight * 1.3f) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(60f))) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    preview(Modifier.aspectRatio(4f/3f, matchHeightConstraintsFirst = true))
                }
                Column(Modifier.weight(.65f), verticalArrangement = Arrangement.spacedBy(m.dp(28f))) {
                    BoardLabel(m, if(composing) "Writing a note" else s.board?.name.orEmpty(), 30f, color = pocketPalette.textSecondary)
                    BoardLabel(m, author, 58f, true, maxLines = 2, color = pocketPalette.teal)
                    context()
                    if(body.isNotBlank()) BoardLabel(m, body, 44f, maxLines = 10)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(m.dp(22f), Alignment.CenterVertically)) {
                BoardLabel(m, author, 58f, true, maxLines = 2)
                context()
                if(hasDrawing) preview(Modifier.weight(1f, fill = false).aspectRatio(4f/3f))
                if(body.isNotBlank()) BoardLabel(m, body, 44f, maxLines = if(hasDrawing) 4 else 12)
            }
        }
    }
}
