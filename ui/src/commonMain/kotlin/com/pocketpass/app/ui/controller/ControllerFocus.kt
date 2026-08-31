package com.pocketpass.app.ui.controller

import com.pocketpass.app.ui.platformAnimationsEnabled
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

enum class FocusDirection { Left, Right, Up, Down }

enum class FocusDisplay { Top, Bottom }

class ControllerFocusViewport(
    val shape: Shape? = null,
    val topInset: Float = 0f,
) {
    var bounds: Rect by mutableStateOf(Rect.Zero)
        internal set
    var layoutSize: Size = Size.Zero
        internal set
}

data class FocusEntry(
    val id: String,
    val layer: Int,
    val display: FocusDisplay,
    val bounds: Rect,
    val onActivate: () -> Unit,
    val scale: Float = 1f,
    val cornerRadius: Float? = null,
    val focusable: Boolean = true,
    val viewport: ControllerFocusViewport? = null,
    val onAdjust: ((Int) -> Unit)? = null,
    val group: String? = null,
    val neighbors: Map<FocusDirection, String> = emptyMap(),
)

private fun FocusEntry.isRevealed(): Boolean {
    val window = viewport?.bounds ?: return true
    return window.isEmpty || bounds.overlaps(window)
}

private fun Rect.inLine(other: Rect, direction: FocusDirection): Boolean = when (direction) {
    FocusDirection.Left, FocusDirection.Right -> top < other.bottom && bottom > other.top
    FocusDirection.Up, FocusDirection.Down -> left < other.right && right > other.left
}

internal fun chooseNextFocus(
    entries: List<FocusEntry>,
    currentId: String?,
    direction: FocusDirection,
    held: Boolean = false,
): String? {
    if (entries.isEmpty()) return null
    val current = entries.firstOrNull { it.id == currentId }
        ?: return entries.minByOrNull { it.bounds.top * 10_000f + it.bounds.left }?.id

    current.neighbors[direction]?.let { neighbor ->
        if (entries.any { it.id == neighbor }) return neighbor
    }

    val from = current.bounds.center
    val candidates = entries.filter { it.id != current.id }.filter { entry ->
        val to = entry.bounds.center
        when (direction) {
            FocusDirection.Left -> to.x < from.x - 1f
            FocusDirection.Right -> to.x > from.x + 1f
            FocusDirection.Up -> to.y < from.y - 1f
            FocusDirection.Down -> to.y > from.y + 1f
        }
    }.filter { entry ->
        entry.viewport == null || entry.viewport === current.viewport || entry.isRevealed()
    }.filter { entry ->
        !held || (entry.group == current.group && entry.bounds.inLine(current.bounds, direction))
    }
    if (candidates.isEmpty()) return current.id
    return candidates.minByOrNull { entry ->
        val to = entry.bounds.center
        val primary: Float
        val cross: Float
        when (direction) {
            FocusDirection.Left, FocusDirection.Right -> {
                primary = abs(to.x - from.x)
                cross = abs(to.y - from.y)
            }
            FocusDirection.Up, FocusDirection.Down -> {
                primary = abs(to.y - from.y)
                cross = abs(to.x - from.x)
            }
        }
        primary + cross * 2f
    }?.id
}

internal fun revealRect(size: Size, viewport: ControllerFocusViewport?): Rect {
    val margin = HIGHLIGHT_STROKE
    val topInset = viewport?.topInset ?: 0f
    return Rect(-margin, -margin - topInset, size.width + margin, size.height + margin)
}

internal operator fun Rect.plus(other: Rect): Rect =
    Rect(left + other.left, top + other.top, right + other.right, bottom + other.bottom)

internal operator fun Rect.minus(other: Rect): Rect =
    Rect(left - other.left, top - other.top, right - other.right, bottom - other.bottom)

internal fun FocusEntry.ringRadius(): Float =
    cornerRadius?.let { it * scale } ?: (minOf(bounds.width, bounds.height) / 2f)

internal sealed interface RingMotion {
    data object Keep : RingMotion
    data object Snap : RingMotion
    data class Slide(val shift: Rect, val radiusShift: Float) : RingMotion
}

internal fun ringMotion(
    previous: FocusEntry?,
    next: FocusEntry?,
    shift: Rect,
    radiusShift: Float,
    animate: Boolean,
): RingMotion = when {
    next == null || next.id == previous?.id -> RingMotion.Keep
    previous == null || previous.bounds.isEmpty || !animate -> RingMotion.Snap
    else -> RingMotion.Slide(
        shift = previous.bounds + shift - next.bounds,
        radiusShift = previous.ringRadius() + radiusShift - next.ringRadius(),
    )
}

internal const val RING_GAP_LIMIT_NANOS = 250_000_000L

private val ringTimeOrigin = kotlin.time.TimeSource.Monotonic.markNow()

internal fun monotonicNowNanos(): Long = ringTimeOrigin.elapsedNow().inWholeNanoseconds

internal class RingTracker(private val gapLimitNanos: Long = RING_GAP_LIMIT_NANOS) {
    private var previous: FocusEntry? = null
    private var gapStartNanos = 0L
    private var inGap = false

    fun previousFor(next: FocusEntry?, hidden: Boolean, nowNanos: Long): FocusEntry? {
        val from = if (!inGap || nowNanos - gapStartNanos <= gapLimitNanos) previous else null
        when {
            next != null -> {
                previous = next
                inGap = false
            }
            hidden -> {
                previous = null
                inGap = false
            }
            !inGap -> {
                inGap = true
                gapStartNanos = nowNanos
            }
        }
        return from
    }
}

class ControllerFocus(private val onMoved: (() -> Unit)? = null) {
    private val entries = mutableStateMapOf<String, FocusEntry>()
    var focusId: String? by mutableStateOf(null)
        private set
    var hidden: Boolean by mutableStateOf(false)
        private set
    var display: FocusDisplay by mutableStateOf(FocusDisplay.Bottom)
        private set
    private val lastFocusByDisplay = mutableMapOf<FocusDisplay, String>()
    var keyboardSubmit: (() -> Unit)? = null
    var keyboardBackspace: (() -> Unit)? = null
    var keyboardLayer: Int? = null

    fun register(
        id: String,
        layer: Int,
        display: FocusDisplay,
        cornerRadius: Float? = null,
        focusable: Boolean = true,
        viewport: ControllerFocusViewport? = null,
        onAdjust: ((Int) -> Unit)? = null,
        group: String? = null,
        neighbors: Map<FocusDirection, String> = emptyMap(),
        onActivate: () -> Unit,
    ) {
        val existing = entries[id]
        entries[id] = FocusEntry(
            id = id,
            layer = layer,
            display = display,
            bounds = existing?.bounds ?: Rect.Zero,
            onActivate = onActivate,
            scale = existing?.scale ?: 1f,
            cornerRadius = cornerRadius,
            focusable = focusable,
            viewport = viewport,
            onAdjust = onAdjust,
            group = group,
            neighbors = neighbors,
        )
    }

    fun updateBounds(id: String, bounds: Rect, scale: Float = 1f) {
        val existing = entries[id] ?: return
        if (existing.bounds != bounds || existing.scale != scale) {
            entries[id] = existing.copy(bounds = bounds, scale = scale)
        }
    }

    fun unregister(id: String) {
        entries.remove(id)
        if (focusId == id) focusId = null
    }

    fun focus(id: String, reveal: Boolean = true) {
        focusId = id
        entries[id]?.let { display = it.display }
        if (reveal) hidden = false
    }

    fun hide() {
        hidden = true
    }

    private fun topLayerEntries(): List<FocusEntry> {
        val laid = entries.values.filter { it.bounds.width > 0f && it.bounds.height > 0f }
        val topLayer = laid.maxOfOrNull { it.layer } ?: return emptyList()
        return laid.filter { it.layer == topLayer }
    }

    private fun activeEntries(): List<FocusEntry> {
        val top = topLayerEntries()
        val shown = if (top.any { it.display == display }) top.filter { it.display == display } else top
        return shown.filter { it.focusable }
    }

    private fun currentDisplay(): FocusDisplay =
        focusId?.let { entries[it] }?.display ?: display

    fun move(direction: FocusDirection, held: Boolean = false): Boolean {
        val active = activeEntries()
        if (active.isEmpty()) return false
        val current = focusId?.let { id -> active.firstOrNull { it.id == id } }
        if (hidden) {
            hidden = false
            if (current != null) return true
        }
        val next = if (current == null) {
            chooseNextFocus(active, null, direction)
        } else {
            chooseNextFocus(active, current.id, direction, held)
        }
        if (next != null && next != current?.id) onMoved?.invoke()
        focusId = next
        next?.let { id -> entries[id]?.let { display = it.display } }
        return next != null
    }

    fun adjust(delta: Int): Boolean {
        if (hidden) return false
        val active = activeEntries()
        val target = focusId?.let { id -> active.firstOrNull { it.id == id } } ?: return false
        val onAdjust = target.onAdjust ?: return false
        onAdjust(delta)
        return true
    }

    fun activate(): Boolean {
        val active = activeEntries()
        val target = focusId?.let { id -> active.firstOrNull { it.id == id } } ?: return false
        if (hidden) {
            hidden = false
            return true
        }
        target.onActivate()
        return true
    }

    fun focusedTarget(display: FocusDisplay?): FocusEntry? {
        if (hidden) return null
        val id = focusId ?: return null
        val entry = entries[id] ?: return null
        if (display != null && entry.display != display) return null
        return if (entry in activeEntries()) entry else null
    }

    fun hasTargets(): Boolean = activeEntries().isNotEmpty()

    private fun otherDisplayEntries(): List<FocusEntry> {
        val current = currentDisplay()
        return topLayerEntries().filter { it.focusable && it.display != current }
    }

    fun canSwapDisplay(): Boolean = otherDisplayEntries().isNotEmpty()

    fun swapDisplay(): Boolean {
        val candidates = otherDisplayEntries()
        if (candidates.isEmpty()) return false
        focusId?.let { lastFocusByDisplay[currentDisplay()] = it }
        val target = candidates.first().display
        val remembered = lastFocusByDisplay[target]?.takeIf { id -> candidates.any { it.id == id } }
        hidden = false
        display = target
        focusId = remembered ?: chooseNextFocus(candidates, null, FocusDirection.Down)
        onMoved?.invoke()
        return true
    }

    fun keyboardActive(): Boolean =
        keyboardLayer != null && activeEntries().firstOrNull()?.layer == keyboardLayer

    fun keyboardCanBackspace(): Boolean = keyboardActive() && keyboardBackspace != null
}

val LocalControllerFocus = staticCompositionLocalOf<ControllerFocus?> { null }
val LocalFocusDisplay = staticCompositionLocalOf { FocusDisplay.Bottom }
val LocalControllerFocusViewport = staticCompositionLocalOf<ControllerFocusViewport?> { null }
val LocalControllerFocusGroup = staticCompositionLocalOf<String?> { null }

private fun LayoutCoordinates.windowBounds(): Rect {
    val topLeft = localToWindow(Offset.Zero)
    val bottomRight = localToWindow(Offset(size.width.toFloat(), size.height.toFloat()))
    return Rect(topLeft, bottomRight)
}

private class TargetGeometry {
    var size = Size.Zero
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.controllerTarget(
    id: String,
    layer: Int = 0,
    cornerRadius: Float? = null,
    onAdjust: ((Int) -> Unit)? = null,
    neighbors: Map<FocusDirection, String> = emptyMap(),
    onActivate: () -> Unit,
): Modifier = composed {
    val focus = LocalControllerFocus.current ?: return@composed this
    val display = LocalFocusDisplay.current
    val viewport = LocalControllerFocusViewport.current
    val group = LocalControllerFocusGroup.current
    val latestActivate = rememberUpdatedState(onActivate)
    val latestAdjust = rememberUpdatedState(onAdjust)
    val bringIntoView = remember { BringIntoViewRequester() }
    val geometry = remember { TargetGeometry() }
    DisposableEffect(focus, id, layer, display, cornerRadius, viewport, onAdjust != null, group, neighbors) {
        focus.register(
            id,
            layer,
            display,
            cornerRadius,
            viewport = viewport,
            onAdjust = if (onAdjust == null) null else { delta -> latestAdjust.value?.invoke(delta) },
            group = group,
            neighbors = neighbors,
        ) {
            latestActivate.value()
        }
        onDispose { focus.unregister(id) }
    }
    LaunchedEffect(focus.focusId, focus.hidden) {
        if (focus.focusId == id && !focus.hidden) {
            bringIntoView.bringIntoView(revealRect(geometry.size, viewport))
        }
    }
    this
        .bringIntoViewRequester(bringIntoView)
        .onGloballyPositioned { coordinates ->
            val size = coordinates.size
            val bounds = coordinates.windowBounds()
            val scale = if (size.width > 0) bounds.width / size.width else 1f
            geometry.size = Size(size.width.toFloat(), size.height.toFloat())
            focus.updateBounds(id, bounds, scale)
        }
}

fun Modifier.controllerFocusBarrier(id: String, layer: Int): Modifier = composed {
    val focus = LocalControllerFocus.current ?: return@composed this
    val display = LocalFocusDisplay.current
    DisposableEffect(focus, id, layer, display) {
        focus.register(id, layer, display, focusable = false) {}
        onDispose { focus.unregister(id) }
    }
    this.onGloballyPositioned { coordinates ->
        focus.updateBounds(id, coordinates.windowBounds())
    }
}

fun Modifier.controllerFocusViewport(viewport: ControllerFocusViewport): Modifier =
    onGloballyPositioned { coordinates ->
        viewport.layoutSize = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
        viewport.bounds = coordinates.windowBounds()
    }

internal const val HIGHLIGHT_STROKE = 20f
internal const val HIGHLIGHT_GLOW_BLUR = 36f
internal const val HIGHLIGHT_GLOSS_BLUR = 16f
private val HighlightTop = Color(0xFFA2FF70)
private val HighlightBottom = Color(0xFF00E31B)
internal const val HIGHLIGHT_GLOW_COLOR = 0x406FFF62
const val FOCUS_SLIDE_DAMPING_RATIO = 0.88f
const val FOCUS_SLIDE_STIFFNESS = 220f
private const val HIGHLIGHT_LEAD_STIFFNESS = 400f
private val HighlightLeadSpring = spring<Float>(
    dampingRatio = FOCUS_SLIDE_DAMPING_RATIO,
    stiffness = HIGHLIGHT_LEAD_STIFFNESS,
    visibilityThreshold = 0.5f,
)
private val HighlightTrailSpring = spring<Float>(
    dampingRatio = FOCUS_SLIDE_DAMPING_RATIO,
    stiffness = FOCUS_SLIDE_STIFFNESS,
    visibilityThreshold = 0.5f,
)

internal fun edgeSprings(shift: Float): Pair<SpringSpec<Float>, SpringSpec<Float>> =
    if (shift < 0f) HighlightTrailSpring to HighlightLeadSpring else HighlightLeadSpring to HighlightTrailSpring

private class EdgeShift {
    val left = Animatable(0f)
    val top = Animatable(0f)
    val right = Animatable(0f)
    val bottom = Animatable(0f)

    val value: Rect
        get() = Rect(left.value, top.value, right.value, bottom.value)

    suspend fun snapTo(shift: Rect) {
        left.snapTo(shift.left)
        top.snapTo(shift.top)
        right.snapTo(shift.right)
        bottom.snapTo(shift.bottom)
    }
}

private fun CoroutineScope.slideEdge(
    edge: Animatable<Float, AnimationVector1D>,
    from: Float,
    spec: SpringSpec<Float>,
) {
    launch(start = CoroutineStart.UNDISPATCHED) {
        val velocity = edge.velocity
        edge.snapTo(from)
        edge.animateTo(0f, spec, velocity)
    }
}

@Composable
fun ControllerFocusHighlight(focus: ControllerFocus, display: FocusDisplay? = FocusDisplay.Bottom) {
    val focusedTarget = remember(focus, display) {
        derivedStateOf { focus.focusedTarget(display) }
    }
    val edges = remember { EdgeShift() }
    val radiusShift = remember { Animatable(0f) }
    LaunchedEffect(focus, display) {
        val tracker = RingTracker()
        snapshotFlow { focusedTarget.value }.collect { next ->
            val previous = tracker.previousFor(next, focus.hidden, monotonicNowNanos())
            val motion = ringMotion(
                previous,
                next,
                edges.value,
                radiusShift.value,
                platformAnimationsEnabled(),
            )
            when (motion) {
                RingMotion.Keep -> Unit
                RingMotion.Snap -> {
                    edges.snapTo(Rect.Zero)
                    radiusShift.snapTo(0f)
                }
                is RingMotion.Slide -> {
                    val shift = motion.shift
                    val (leftSpring, rightSpring) = edgeSprings(shift.left + shift.right)
                    val (topSpring, bottomSpring) = edgeSprings(shift.top + shift.bottom)
                    slideEdge(edges.left, shift.left, leftSpring)
                    slideEdge(edges.right, shift.right, rightSpring)
                    slideEdge(edges.top, shift.top, topSpring)
                    slideEdge(edges.bottom, shift.bottom, bottomSpring)
                    slideEdge(radiusShift, motion.radiusShift, HighlightTrailSpring)
                }
            }
        }
    }
    val masks = remember { HighlightMaskCache() }
    val viewportPath = remember { Path() }
    val viewportMatrix = remember { Matrix() }
    Canvas(Modifier.fillMaxSize()) {
        val target = focusedTarget.value ?: return@Canvas
        val bounds = target.bounds + edges.value
        if (bounds.width <= 0f || bounds.height <= 0f) return@Canvas
        val radius = (target.ringRadius() + radiusShift.value)
            .coerceIn(0f, minOf(bounds.width, bounds.height) / 2f)
        val viewport = target.viewport
        if (viewport == null || viewport.bounds.isEmpty) {
            drawHighlight(bounds, radius, target.scale, masks)
            return@Canvas
        }
        val layoutSize = viewport.layoutSize
        val viewportScale =
            if (layoutSize.width > 0f) viewport.bounds.width / layoutSize.width else 1f
        viewportPath.rewind()
        viewportPath.fillType = PathFillType.NonZero
        when (val outline = viewport.shape?.createOutline(layoutSize, layoutDirection, this)) {
            null -> viewportPath.addRect(Rect(Offset.Zero, layoutSize))
            is Outline.Rectangle -> viewportPath.addRect(outline.rect)
            is Outline.Rounded -> viewportPath.addRoundRect(outline.roundRect)
            is Outline.Generic -> {
                viewportPath.fillType = outline.path.fillType
                viewportPath.addPath(outline.path)
            }
        }
        viewportMatrix.reset()
        viewportMatrix.translate(viewport.bounds.left, viewport.bounds.top)
        viewportMatrix.scale(viewportScale, viewportScale)
        viewportPath.transform(viewportMatrix)
        clipPath(viewportPath) {
            drawHighlight(bounds, radius, target.scale, masks)
        }
    }
}

private fun DrawScope.drawHighlight(
    bounds: Rect,
    innerRadius: Float,
    scale: Float,
    masks: HighlightMaskCache,
) {
    val stroke = HIGHLIGHT_STROKE * scale
    val half = stroke / 2f
    val ring = bounds.inflate(half)
    val ringRadius = innerRadius + half
    val outer = bounds.inflate(stroke)

    drawHighlightGlow(masks, bounds, innerRadius, scale)

    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(HighlightTop, HighlightBottom),
            startY = outer.top,
            endY = outer.bottom,
        ),
        topLeft = ring.topLeft,
        size = ring.size,
        cornerRadius = CornerRadius(ringRadius),
        style = Stroke(width = stroke),
    )

    drawHighlightGloss(masks, bounds, innerRadius, scale)
}

internal const val HIGHLIGHT_MASK_SCALE = 0.25f
