package com.pocketpass.spike

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import com.pocketpass.spike.resources.Res
import com.pocketpass.spike.resources.rubik_400
import com.pocketpass.spike.resources.rubik_500
import com.pocketpass.spike.resources.rubik_700
import kotlin.math.min
import org.jetbrains.compose.resources.Font

const val TOP_DESIGN_WIDTH = 1920f
const val TOP_DESIGN_HEIGHT = 1080f
const val BOTTOM_DESIGN_WIDTH = 1240f
const val BOTTOM_DESIGN_HEIGHT = 1080f

val Rubik: FontFamily
    @Composable get() = FontFamily(
        Font(Res.font.rubik_400, FontWeight.Normal),
        Font(Res.font.rubik_500, FontWeight.Medium),
        Font(Res.font.rubik_700, FontWeight.Bold),
    )

@Immutable
class DesignMetrics internal constructor(
    private val density: Density,
    val designWidth: Float = 0f,
    val designHeight: Float = 0f,
    val overscanX: Float = 0f,
    val overscanY: Float = 0f,
    val scale: Float = 1f,
) {
    fun dp(px: Number): Dp = with(density) { px.toFloat().toDp() }
    fun sp(px: Number): TextUnit = with(density) { px.toFloat().toSp() }

    val hasOverscan: Boolean
        get() = overscanX > 0.5f || overscanY > 0.5f
}

fun overscanFor(
    viewportWidth: Float,
    viewportHeight: Float,
    designWidth: Float,
    designHeight: Float,
): Offset {
    val scale = min(viewportWidth / designWidth, viewportHeight / designHeight)
    return Offset(
        ((viewportWidth / scale - designWidth) / 2f).coerceAtLeast(0f),
        ((viewportHeight / scale - designHeight) / 2f).coerceAtLeast(0f),
    )
}

@Stable
class DesignBackdropHost internal constructor() {
    private val entries = mutableStateListOf<Pair<Any, @Composable () -> Unit>>()

    val layers: List<Pair<Any, @Composable () -> Unit>>
        get() = entries

    internal fun set(owner: Any, content: @Composable () -> Unit) {
        val index = entries.indexOfFirst { it.first === owner }
        if (index >= 0) entries[index] = owner to content else entries += owner to content
    }

    internal fun clear(owner: Any) {
        entries.removeAll { it.first === owner }
    }
}

val LocalDesignBackdrop = staticCompositionLocalOf<DesignBackdropHost?> { null }

@Composable
fun DesignSurface(
    designWidth: Float,
    designHeight: Float,
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    content: @Composable BoxScope.(DesignMetrics) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .background(Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val scale = min(
            viewportWidthPx / designWidth,
            viewportHeightPx / designHeight,
        )
        val overscan = overscanFor(viewportWidthPx, viewportHeightPx, designWidth, designHeight)
        val metrics = remember(density, designWidth, designHeight, overscan, scale) {
            DesignMetrics(density, designWidth, designHeight, overscan.x, overscan.y, scale)
        }
        val backdrop = remember { DesignBackdropHost() }
        val panelWidth = designWidth + 2f * overscan.x
        val panelHeight = designHeight + 2f * overscan.y
        val layer = Modifier
            .requiredSize(metrics.dp(panelWidth), metrics.dp(panelHeight))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
            }

        if (metrics.hasOverscan) {
            Box(layer) {
                backdrop.layers.forEach { (owner, content) -> key(owner) { content() } }
            }
        }
        Box(layer.clipToBounds()) {
            Box(
                Modifier
                    .designBounds(metrics, overscan.x, overscan.y, designWidth, designHeight)
                    .background(background),
            ) {
                CompositionLocalProvider(
                    LocalDesignBackdrop provides backdrop,
                    LocalDesignMetrics provides metrics,
                ) {
                    content(metrics)
                }
            }
        }
    }
}

@Composable
fun DesignBackdrop(
    metrics: DesignMetrics,
    alpha: () -> Float = { 1f },
    key: Any? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val host = LocalDesignBackdrop.current
    if (host == null || !metrics.hasOverscan) {
        Box(Modifier.fillMaxSize(), content = content)
        return
    }
    val latest = rememberUpdatedState(content)
    val latestAlpha = rememberUpdatedState(alpha)
    val latestKey = rememberUpdatedState(key)
    val owner = remember { Any() }
    DisposableEffect(host, owner) {
        host.set(owner) {
            if (latestAlpha.value() > 0f) {
                Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = latestAlpha.value() }) {
                    key(latestKey.value) { latest.value(this) }
                }
            }
        }
        onDispose { host.clear(owner) }
    }
}

val LocalDesignMetrics = staticCompositionLocalOf<DesignMetrics?> { null }

val LocalDesignOrigin = compositionLocalOf { Offset.Zero }

enum class DesignAnchor { Start, Center, End, Stretch }

private fun anchorShift(anchor: DesignAnchor, overscan: Float): Float = when (anchor) {
    DesignAnchor.Start, DesignAnchor.Stretch -> -overscan
    DesignAnchor.Center -> 0f
    DesignAnchor.End -> overscan
}

fun DesignMetrics.anchorOrigin(horizontal: DesignAnchor, vertical: DesignAnchor): Offset =
    Offset(anchorShift(horizontal, overscanX), anchorShift(vertical, overscanY))

@Composable
fun Modifier.anchoredBounds(
    metrics: DesignMetrics,
    x: Number,
    y: Number,
    width: Number,
    height: Number,
    horizontal: DesignAnchor = DesignAnchor.Center,
    vertical: DesignAnchor = DesignAnchor.Center,
): Modifier {
    val origin = LocalDesignOrigin.current
    val left = x.toFloat() + anchorShift(horizontal, metrics.overscanX) - origin.x
    val top = y.toFloat() + anchorShift(vertical, metrics.overscanY) - origin.y
    val spanX = if (horizontal == DesignAnchor.Stretch) 2f * metrics.overscanX else 0f
    val spanY = if (vertical == DesignAnchor.Stretch) 2f * metrics.overscanY else 0f
    return graphicsLayer {
        translationX = left
        translationY = top
    }.fixedDesignSize(metrics.dp(width.toFloat() + spanX), metrics.dp(height.toFloat() + spanY))
}

private fun Modifier.fixedDesignSize(width: Dp, height: Dp): Modifier = layout { measurable, constraints ->
    val widthPx = width.roundToPx()
    val heightPx = height.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
    layout(constraints.constrainWidth(widthPx), constraints.constrainHeight(heightPx)) {
        placeable.place(0, 0)
    }
}

@Composable
fun DesignBox(
    metrics: DesignMetrics,
    x: Number,
    y: Number,
    width: Number,
    height: Number,
    horizontal: DesignAnchor = DesignAnchor.Center,
    vertical: DesignAnchor = DesignAnchor.Center,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .anchoredBounds(metrics, x, y, width, height, horizontal, vertical)
            .then(modifier),
        contentAlignment = contentAlignment,
    ) {
        val scope = this
        CompositionLocalProvider(
            LocalDesignOrigin provides metrics.anchorOrigin(horizontal, vertical),
        ) {
            scope.content()
        }
    }
}

fun Modifier.designBounds(
    metrics: DesignMetrics,
    x: Number,
    y: Number,
    width: Number,
    height: Number,
): Modifier = this
    .graphicsLayer {
        translationX = x.toFloat()
        translationY = y.toFloat()
    }
    .requiredSize(metrics.dp(width), metrics.dp(height))
